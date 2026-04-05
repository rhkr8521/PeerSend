package com.rhkr8521.p2ptransfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.rhkr8521.p2ptransfer.core.IncomingTransferRequest
import com.rhkr8521.p2ptransfer.core.humanReadableSize

object IncomingRequestNotifier {
    private const val CHANNEL_ID = "p2p_incoming_request"
    private const val NOTIFICATION_ID = 8522

    fun show(context: Context, request: IncomingTransferRequest) {
        createChannel(context)

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            ?: Intent(
                Intent.ACTION_VIEW,
                "package:${context.packageName}".toUri(),
            )

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val countLabel = if (request.fileCount > 1) {
            context.resources.getQuantityString(R.plurals.total_file_count, request.fileCount, request.fileCount)
        } else {
            context.getString(R.string.incoming_notification_single)
        }
        val contentText = context.getString(
            R.string.incoming_notification_content,
            request.senderName,
            request.displayName,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.incoming_notification_title))
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$contentText\n${humanReadableSize(request.totalBytes)} | $countLabel",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.incoming_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.incoming_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }
}
