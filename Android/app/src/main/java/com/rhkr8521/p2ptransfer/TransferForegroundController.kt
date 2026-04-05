package com.rhkr8521.p2ptransfer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rhkr8521.p2ptransfer.core.TransferProgressUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TransferForegroundSnapshot(
    val title: String,
    val itemLabel: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val speedBytesPerSecond: Double,
    val remainingSeconds: Double,
    val isReceiving: Boolean,
)

object TransferForegroundController {
    private val _state = MutableStateFlow<TransferForegroundSnapshot?>(null)
    val state = _state.asStateFlow()

    fun activate(context: Context, progress: TransferProgressUi) {
        val snapshot = progress.toSnapshot()
        _state.value = snapshot
        ContextCompat.startForegroundService(
            context,
            Intent(context, TransferForegroundService::class.java).apply {
                action = TransferForegroundService.ACTION_START_OR_REFRESH
                putExtra(TransferForegroundService.EXTRA_TITLE, snapshot.title)
                putExtra(TransferForegroundService.EXTRA_ITEM_LABEL, snapshot.itemLabel)
                putExtra(TransferForegroundService.EXTRA_TOTAL_BYTES, snapshot.totalBytes)
                putExtra(TransferForegroundService.EXTRA_TRANSFERRED_BYTES, snapshot.transferredBytes)
                putExtra(TransferForegroundService.EXTRA_SPEED_BYTES_PER_SECOND, snapshot.speedBytesPerSecond)
                putExtra(TransferForegroundService.EXTRA_REMAINING_SECONDS, snapshot.remainingSeconds)
                putExtra(TransferForegroundService.EXTRA_IS_RECEIVING, snapshot.isReceiving)
            },
        )
    }

    fun refresh(progress: TransferProgressUi) {
        _state.value = progress.toSnapshot()
    }

    fun deactivate(context: Context) {
        _state.value = null
    }

    private fun TransferProgressUi.toSnapshot(): TransferForegroundSnapshot {
        return TransferForegroundSnapshot(
            title = title,
            itemLabel = itemLabel,
            totalBytes = totalBytes,
            transferredBytes = transferredBytes,
            speedBytesPerSecond = speedBytesPerSecond,
            remainingSeconds = remainingSeconds,
            isReceiving = isReceiving,
        )
    }
}
