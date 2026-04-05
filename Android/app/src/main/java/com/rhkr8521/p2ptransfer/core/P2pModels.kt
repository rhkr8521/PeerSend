package com.rhkr8521.p2ptransfer.core

import android.net.Uri

enum class TransferMode {
    LAN,
    TUNNEL,
}

data class PeerDevice(
    val id: String,
    val title: String,
    val addressLabel: String,
    val connectHost: String,
    val connectPort: Int,
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val isTunnel: Boolean = false,
)

data class TransferProgressUi(
    val title: String,
    val itemLabel: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val itemBytes: Long,
    val itemTransferredBytes: Long,
    val speedBytesPerSecond: Double,
    val remainingSeconds: Double,
    val isReceiving: Boolean,
    val showItemProgress: Boolean,
)

data class IncomingTransferRequest(
    val requestId: String,
    val senderName: String,
    val senderAddress: String,
    val displayName: String,
    val totalBytes: Long,
    val isZip: Boolean,
    val fileCount: Int,
)

data class SelectedDocument(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
)

data class TunnelEndpoints(
    val wsUrl: String,
    val adminBaseUrl: String,
    val tcpHost: String,
)

data class P2pUiState(
    val mode: TransferMode = TransferMode.LAN,
    val myName: String = "",
    val tunnelSubdomain: String = "",
    val myIp: String = "",
    val saveLocationLabel: String = "",
    val selectedPeerId: String? = null,
    val lanPeers: List<PeerDevice> = emptyList(),
    val tunnelPeers: List<PeerDevice> = emptyList(),
    val usePublicTunnel: Boolean = true,
    val tunnelHost: String = DEFAULT_TUNNEL_HOST,
    val tunnelSsl: Boolean = true,
    val tunnelToken: String = DEFAULT_TUNNEL_TOKEN,
    val tunnelStatus: String = "",
    val transferProgress: TransferProgressUi? = null,
    val pendingRequest: IncomingTransferRequest? = null,
    val isBusy: Boolean = false,
)
