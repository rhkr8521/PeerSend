package com.rhkr8521.p2ptransfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import com.rhkr8521.p2ptransfer.core.formatRemaining
import com.rhkr8521.p2ptransfer.core.humanReadableSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TransferForegroundService : Service() {

    companion object {
        const val ACTION_START_OR_REFRESH = "com.rhkr8521.p2ptransfer.action.START_OR_REFRESH"
        const val ACTION_STOP = "com.rhkr8521.p2ptransfer.action.STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ITEM_LABEL = "extra_item_label"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"
        const val EXTRA_TRANSFERRED_BYTES = "extra_transferred_bytes"
        const val EXTRA_SPEED_BYTES_PER_SECOND = "extra_speed_bytes_per_second"
        const val EXTRA_REMAINING_SECONDS = "extra_remaining_seconds"
        const val EXTRA_IS_RECEIVING = "extra_is_receiving"

        private const val CHANNEL_ID = "p2p_transfer"
        private const val NOTIFICATION_ID = 8521
        private const val MIN_UPDATE_INTERVAL_MS = 350L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startedInForeground = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastNotificationUpdateMs = 0L
    private var lastSnapshot: TransferForegroundSnapshot? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            TransferForegroundController.state.collectLatest { snapshot ->
                if (snapshot == null) {
                    releaseLocks()
                    stopForegroundCompat()
                    stopSelf()
                } else {
                    pushSnapshot(snapshot, force = !startedInForeground)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseLocks()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START_OR_REFRESH) {
            parseSnapshot(intent)?.let { snapshot ->
                pushSnapshot(snapshot, force = true)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    private fun stopForegroundCompat() {
        if (startedInForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            startedInForeground = false
        }
        lastNotificationUpdateMs = 0L
        lastSnapshot = null
    }

    private fun pushSnapshot(snapshot: TransferForegroundSnapshot, force: Boolean) {
        ensureLocks()
        val now = SystemClock.elapsedRealtime()
        val previous = lastSnapshot
        val shouldUpdate = force ||
            previous == null ||
            now - lastNotificationUpdateMs >= MIN_UPDATE_INTERVAL_MS ||
            snapshot.title != previous.title ||
            snapshot.itemLabel != previous.itemLabel ||
            snapshot.transferredBytes >= snapshot.totalBytes

        if (!startedInForeground) {
            startForegroundCompat(buildNotification(snapshot))
            startedInForeground = true
            lastNotificationUpdateMs = now
            lastSnapshot = snapshot
            return
        }

        if (shouldUpdate) {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(snapshot))
            lastNotificationUpdateMs = now
            lastSnapshot = snapshot
        }
    }

    private fun parseSnapshot(intent: Intent): TransferForegroundSnapshot? {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return null
        val itemLabel = intent.getStringExtra(EXTRA_ITEM_LABEL) ?: return null
        return TransferForegroundSnapshot(
            title = title,
            itemLabel = itemLabel,
            totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L),
            transferredBytes = intent.getLongExtra(EXTRA_TRANSFERRED_BYTES, 0L),
            speedBytesPerSecond = intent.getDoubleExtra(EXTRA_SPEED_BYTES_PER_SECOND, 0.0),
            remainingSeconds = intent.getDoubleExtra(EXTRA_REMAINING_SECONDS, 0.0),
            isReceiving = intent.getBooleanExtra(EXTRA_IS_RECEIVING, false),
        )
    }

    private fun buildNotification(snapshot: TransferForegroundSnapshot): android.app.Notification {
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            ?: Intent(
                Intent.ACTION_VIEW,
                "package:$packageName".toUri(),
            )

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val progressMax = 1000
        val progressValue = if (snapshot.totalBytes > 0) {
            ((snapshot.transferredBytes.toDouble() / snapshot.totalBytes.toDouble()) * progressMax)
                .coerceIn(0.0, progressMax.toDouble())
                .roundToInt()
        } else {
            0
        }

        val titlePrefix = if (snapshot.isReceiving) {
            getString(R.string.foreground_receiving)
        } else {
            getString(R.string.foreground_sending)
        }
        val contentText = "${humanReadableSize(snapshot.transferredBytes)} / ${humanReadableSize(snapshot.totalBytes)}"
        val detailText = buildString {
            append(snapshot.itemLabel)
            append('\n')
            append(
                getString(
                    R.string.progress_speed_remaining,
                    String.format("%.2f MB/s", snapshot.speedBytesPerSecond / (1024.0 * 1024.0)),
                    formatRemaining(snapshot.remainingSeconds),
                ),
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.foreground_title, titlePrefix, snapshot.title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(progressMax, progressValue, snapshot.totalBytes <= 0L)
            .build()
    }

    private fun ensureLocks() {
        if (wakeLock?.isHeld != true) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:p2p-transfer")
                ?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }

        if (wifiLock?.isHeld != true) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val wifiLockMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wifiManager
                ?.createWifiLock(wifiLockMode, "$packageName:p2p-transfer")
                ?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        wifiLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        wakeLock = null
        wifiLock = null
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.foreground_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.foreground_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
