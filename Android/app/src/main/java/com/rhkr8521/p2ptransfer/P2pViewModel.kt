package com.rhkr8521.p2ptransfer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.content.ContentUris
import androidx.documentfile.provider.DocumentFile
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rhkr8521.p2ptransfer.core.BROADCAST_PORT
import com.rhkr8521.p2ptransfer.core.BUFFER_SIZE
import com.rhkr8521.p2ptransfer.core.ControlWire
import com.rhkr8521.p2ptransfer.core.DATA_PORT
import com.rhkr8521.p2ptransfer.core.DEFAULT_TUNNEL_HOST
import com.rhkr8521.p2ptransfer.core.DEFAULT_TUNNEL_SUB_PREFIX
import com.rhkr8521.p2ptransfer.core.DEFAULT_TUNNEL_TOKEN
import com.rhkr8521.p2ptransfer.core.DEVICE_TIMEOUT_MS
import com.rhkr8521.p2ptransfer.core.IncomingTransferRequest
import com.rhkr8521.p2ptransfer.core.LOCAL_FILE_PORT
import com.rhkr8521.p2ptransfer.core.P2pStorage
import com.rhkr8521.p2ptransfer.core.P2pUiState
import com.rhkr8521.p2ptransfer.core.PeerDevice
import com.rhkr8521.p2ptransfer.core.POLL_INTERVAL_MS
import com.rhkr8521.p2ptransfer.core.SelectedDocument
import com.rhkr8521.p2ptransfer.core.TUNNEL_TCP_NAME
import com.rhkr8521.p2ptransfer.core.TransferMode
import com.rhkr8521.p2ptransfer.core.TransferProgressUi
import com.rhkr8521.p2ptransfer.core.array
import com.rhkr8521.p2ptransfer.core.asObjectOrNull
import com.rhkr8521.p2ptransfer.core.bool
import com.rhkr8521.p2ptransfer.core.buildTunnelEndpoints
import com.rhkr8521.p2ptransfer.core.closeQuietly
import com.rhkr8521.p2ptransfer.core.formatRemaining
import com.rhkr8521.p2ptransfer.core.humanReadableSize
import com.rhkr8521.p2ptransfer.core.int
import com.rhkr8521.p2ptransfer.core.makeSubdomain
import com.rhkr8521.p2ptransfer.core.safeName
import com.rhkr8521.p2ptransfer.core.string
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.min

class P2pViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private data class PreparedZipEntry(
        val document: SelectedDocument,
        val entryName: String,
        val sizeBytes: Long,
        val crc32: Long,
    )

    companion object {
        private const val PREFS_NAME = "p2p_android_prefs"
        private const val SECURE_PREFS_NAME = "p2p_android_secure_prefs"
        private const val KEY_TUNNEL_HOST = "tunnel_host"
        private const val KEY_TUNNEL_SSL = "tunnel_ssl"
        private const val KEY_TUNNEL_TOKEN = "tunnel_token"
        private const val KEY_USE_PUBLIC_TUNNEL = "use_public_tunnel"
        private const val KEY_TUNNEL_SUBDOMAIN = "tunnel_subdomain"
        private const val KEY_LAN_NAME_SUFFIX = "lan_name_suffix"
        private val IGNORED_INTERFACE_PREFIXES = listOf("lo", "docker", "veth", "br-", "vm", "tap", "virbr", "wg")
    }

    private val appContext = application.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs = createSecurePrefs()
    private val storage = P2pStorage(appContext)
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val lanPeers = ConcurrentHashMap<String, PeerDevice>()
    private val tunnelPeers = ConcurrentHashMap<String, PeerDevice>()
    private val incomingDecisions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val tunnelBridgeSockets = ConcurrentHashMap<String, Socket>()
    private val pendingTunnelPackets = ConcurrentHashMap<String, MutableList<ByteArray>>()
    private val savedTunnelHost = prefs.getString(KEY_TUNNEL_HOST, "") ?: ""
    private val savedTunnelSsl = prefs.getBoolean(KEY_TUNNEL_SSL, true)
    private val savedTunnelToken = readStoredTunnelToken()
    private val baseDeviceName = buildBaseDeviceName()
    private val lanNameSuffix = prefs.getString(KEY_LAN_NAME_SUFFIX, null)
        ?: generateLanNameSuffix().also { prefs.edit().putString(KEY_LAN_NAME_SUFFIX, it).apply() }
    private val lanDeviceName = buildLanDeviceName(baseDeviceName)
    private var activeUsePublicTunnel = prefs.getBoolean(KEY_USE_PUBLIC_TUNNEL, true)
    private var activeTunnelHost = if (activeUsePublicTunnel) DEFAULT_TUNNEL_HOST else savedTunnelHost
    private var activeTunnelSsl = if (activeUsePublicTunnel) true else savedTunnelSsl
    private var activeTunnelToken = if (activeUsePublicTunnel) DEFAULT_TUNNEL_TOKEN else savedTunnelToken

    private val _uiState = MutableStateFlow(
        P2pUiState(
            myName = lanDeviceName,
            myIp = resolveLocalIp(),
            saveLocationLabel = storage.saveLocationLabel(),
            usePublicTunnel = activeUsePublicTunnel,
            tunnelHost = savedTunnelHost,
            tunnelSsl = savedTunnelSsl,
            tunnelToken = savedTunnelToken,
            tunnelStatus = string(R.string.tunnel_status_disconnected),
        ),
    )
    val uiState: StateFlow<P2pUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private var lanBroadcastJob: Job? = null
    private var lanListenJob: Job? = null
    private var lanServerJob: Job? = null
    private var cleanupJob: Job? = null

    private var tunnelStarted = false
    private var tunnelRegistered = false
    private var tunnelLocalServerJob: Job? = null
    private var tunnelPollJob: Job? = null
    private var tunnelReconnectJob: Job? = null
    private var tunnelWebSocket: WebSocket? = null
    private var lastTunnelPeerSnapshotAt = 0L

    private var progressStartTimeMs = 0L
    private var progressTransferredBytes = 0L
    private var progressTotalBytes = 0L

    @Volatile
    private var cancelFlag = false

    @Volatile
    private var activeTransferMode: String? = null

    @Volatile
    private var activeReceiveSocket: Socket? = null

    private var selectedLanPeerId: String? = null
    private var selectedTunnelPeerId: String? = null

    private val tunnelSubdomain: String = prefs.getString(KEY_TUNNEL_SUBDOMAIN, null)
        ?: makeSubdomain(DEFAULT_TUNNEL_SUB_PREFIX, baseDeviceName).also {
            prefs.edit().putString(KEY_TUNNEL_SUBDOMAIN, it).apply()
        }

    init {
        _uiState.update { it.copy(tunnelSubdomain = tunnelSubdomain) }
        acquireMulticastLock()
        startLanBroadcastLoop()
        startLanListenLoop()
        startLanTransferServer()
        startCleanupLoop()
        refreshReceivedFiles()
    }

    fun refreshReceivedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = scanReceivedFiles()
            _uiState.update { it.copy(receivedFiles = files) }
        }
    }

    fun deleteReceivedFiles(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                runCatching {
                    val doc = DocumentFile.fromSingleUri(appContext, uri)
                    if (doc?.canWrite() == true) {
                        doc.delete()
                    } else {
                        appContext.contentResolver.delete(uri, null, null)
                    }
                }
            }
            refreshReceivedFiles()
        }
    }

    private fun scanReceivedFiles(): List<com.rhkr8521.p2ptransfer.core.ReceivedFile> {
        val result = mutableListOf<com.rhkr8521.p2ptransfer.core.ReceivedFile>()

        val treeUri = storage.currentSaveTreeUri()
        if (treeUri != null) {
            val treeRoot = DocumentFile.fromTreeUri(appContext, treeUri)
            treeRoot?.listFiles()?.forEach { doc ->
                if (!doc.isFile) return@forEach
                val mime = doc.type ?: return@forEach
                val isImage = mime.startsWith("image/")
                val isVideo = mime.startsWith("video/")
                if (!isImage && !isVideo) return@forEach
                result += com.rhkr8521.p2ptransfer.core.ReceivedFile(
                    uri = doc.uri,
                    name = doc.name ?: "file",
                    mimeType = mime,
                    dateMs = doc.lastModified(),
                    isVideo = isVideo,
                    isImage = isImage,
                )
            }
            return result.sortedByDescending { it.dateMs }
        }

        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
        )
        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND " +
            "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%' OR " +
            "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%')"
        val selectionArgs = arrayOf("Download/%")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        runCatching {
            appContext.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol) ?: continue
                    val date = cursor.getLong(dateCol) * 1000L
                    val isImage = mime.startsWith("image/")
                    val isVideo = mime.startsWith("video/")
                    if (!isImage && !isVideo) continue
                    val uri = ContentUris.withAppendedId(collection, id)
                    result += com.rhkr8521.p2ptransfer.core.ReceivedFile(
                        uri = uri,
                        name = name,
                        mimeType = mime,
                        dateMs = date,
                        isVideo = isVideo,
                        isImage = isImage,
                    )
                }
            }
        }
        return result
    }

    override fun onCleared() {
        tunnelReconnectJob?.cancel()
        tunnelPollJob?.cancel()
        lanBroadcastJob?.cancel()
        lanListenJob?.cancel()
        lanServerJob?.cancel()
        tunnelLocalServerJob?.cancel()
        tunnelWebSocket?.cancel()
        tunnelBridgeSockets.values.forEach { it.closeQuietly() }
        multicastLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        super.onCleared()
    }

    fun toggleMode() {
        _uiState.update { current ->
            val nextMode = if (current.mode == TransferMode.LAN) TransferMode.TUNNEL else TransferMode.LAN
            current.copy(
                mode = nextMode,
                selectedPeerId = if (nextMode == TransferMode.LAN) selectedLanPeerId else selectedTunnelPeerId,
            )
        }
        if (_uiState.value.mode == TransferMode.TUNNEL) {
            startTunnelIfConfigured()
        }
    }

    fun selectPeer(peerId: String) {
        val shouldClear = _uiState.value.selectedPeerId == peerId
        val nextPeerId = if (shouldClear) null else peerId
        if (_uiState.value.mode == TransferMode.LAN) {
            selectedLanPeerId = nextPeerId
        } else {
            selectedTunnelPeerId = nextPeerId
        }
        _uiState.update { it.copy(selectedPeerId = nextPeerId) }
    }

    fun updateTunnelHost(host: String) {
        _uiState.update { it.copy(tunnelHost = host) }
    }

    fun updateTunnelToken(token: String) {
        _uiState.update { it.copy(tunnelToken = token) }
    }

    fun updateTunnelSsl(enabled: Boolean) {
        _uiState.update { it.copy(tunnelSsl = enabled) }
    }

    fun updateUsePublicTunnel(enabled: Boolean) {
        _uiState.update { it.copy(usePublicTunnel = enabled) }
    }

    fun shouldShowInitialTunnelChoice(): Boolean = !prefs.contains(KEY_USE_PUBLIC_TUNNEL)

    fun rememberInitialTunnelChoice(usePublic: Boolean) {
        _uiState.update { it.copy(usePublicTunnel = usePublic) }
        prefs.edit().putBoolean(KEY_USE_PUBLIC_TUNNEL, usePublic).apply()
        activeUsePublicTunnel = usePublic
        if (usePublic) {
            activeTunnelHost = DEFAULT_TUNNEL_HOST
            activeTunnelSsl = true
            activeTunnelToken = DEFAULT_TUNNEL_TOKEN
        } else {
            activeTunnelHost = _uiState.value.tunnelHost.trim()
            activeTunnelSsl = _uiState.value.tunnelSsl
            activeTunnelToken = _uiState.value.tunnelToken.trim()
            if (!hasActiveTunnelConfig()) {
                updateTunnelStatus(string(R.string.tunnel_external_required))
            }
        }
    }

    fun rememberSaveDirectory(uri: Uri?) {
        storage.persistSaveTreeUri(uri)
        _uiState.update { it.copy(saveLocationLabel = storage.saveLocationLabel()) }
    }

    fun manualRefresh() {
        if (_uiState.value.mode == TransferMode.LAN) {
            viewModelScope.launch(Dispatchers.IO) {
                broadcastDiscovery()
            }
        } else {
            startTunnelIfConfigured()
            if (tunnelRegistered) {
                viewModelScope.launch(Dispatchers.IO) {
                    pollTunnelPeersOnce()
                }
            }
        }
    }

    fun applyTunnelSettings() {
        val state = _uiState.value
        val host = state.tunnelHost.trim()
        val token = state.tunnelToken.trim()
        val ssl = state.tunnelSsl
        _uiState.update { it.copy(tunnelHost = host, tunnelToken = token, tunnelSsl = ssl) }
        prefs.edit()
            .putString(KEY_TUNNEL_HOST, host)
            .putBoolean(KEY_TUNNEL_SSL, ssl)
            .putBoolean(KEY_USE_PUBLIC_TUNNEL, state.usePublicTunnel)
            .apply()
        persistTunnelToken(token)

        activeUsePublicTunnel = state.usePublicTunnel
        if (activeUsePublicTunnel) {
            activeTunnelHost = DEFAULT_TUNNEL_HOST
            activeTunnelSsl = true
            activeTunnelToken = DEFAULT_TUNNEL_TOKEN
        } else {
            activeTunnelHost = host
            activeTunnelSsl = ssl
            activeTunnelToken = token
        }

        if (!hasActiveTunnelConfig()) {
            disconnectTunnel()
            updateTunnelStatus(string(R.string.tunnel_external_required))
            return
        }

        updateTunnelStatus(string(R.string.tunnel_status_starting))
        startTunnelStackIfNeeded()
        reconnectTunnelWebSocket()
    }

    private fun createSecurePrefs() = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { prefs }

    private fun readStoredTunnelToken(): String {
        val token = securePrefs.getString(KEY_TUNNEL_TOKEN, null)
            ?: prefs.getString(KEY_TUNNEL_TOKEN, "")
            ?: ""
        if (securePrefs !== prefs && prefs.contains(KEY_TUNNEL_TOKEN)) {
            prefs.edit().remove(KEY_TUNNEL_TOKEN).apply()
        }
        return token
    }

    private fun persistTunnelToken(token: String) {
        securePrefs.edit().putString(KEY_TUNNEL_TOKEN, token).apply()
        if (securePrefs !== prefs && prefs.contains(KEY_TUNNEL_TOKEN)) {
            prefs.edit().remove(KEY_TUNNEL_TOKEN).apply()
        }
    }

    fun respondToPendingRequest(accept: Boolean) {
        val request = _uiState.value.pendingRequest ?: return
        incomingDecisions.remove(request.requestId)?.complete(accept)
        IncomingRequestNotifier.cancel(appContext)
        _uiState.update { it.copy(pendingRequest = null) }
    }

    fun cancelTransfer() {
        cancelFlag = true
        if (activeTransferMode == "receive") {
            activeReceiveSocket?.let { socket ->
                runCatching {
                    ControlWire.writeMessage(
                        BufferedOutputStream(socket.getOutputStream()),
                        mapOf("type" to "CANCEL"),
                    )
                }
            }
        }
    }

    fun sendSelectedFiles(uris: List<Uri>, useZip: Boolean) {
        val peer = selectedPeer() ?: run {
            emitEvent(string(R.string.event_select_target))
            return
        }

        if (_uiState.value.isBusy) {
            emitEvent(string(R.string.event_busy))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val documents = resolveSelectedDocuments(uris)
            if (documents.isEmpty()) {
                updateBusy(false)
                emitEvent(string(R.string.event_cannot_read_selected))
                return@launch
            }

            cancelFlag = false
            updateBusy(true)

            val fileCount = documents.size
            val displayName = makeMultiDisplayName(documents.first().displayName, fileCount)
            var tempZipFile: File? = null

            try {
                val payloadFilename: String
                val payloadSize: Long

                if (useZip && fileCount > 1) {
                    val zipBase = documents.first().displayName.substringBeforeLast('.', documents.first().displayName)
                    val zipName = buildZipFileName(zipBase, fileCount - 1)
                    tempZipFile = createZipArchive(documents, zipName)
                    payloadFilename = zipName
                    payloadSize = tempZipFile.length()
                } else {
                    payloadFilename = documents.first().displayName
                    payloadSize = documents.sumOf { it.sizeBytes }
                }

                Socket().use { socket ->
                    socket.soTimeout = 15_000
                    socket.connect(InetSocketAddress(peer.connectHost, peer.connectPort), 15_000)

                    val input = BufferedInputStream(socket.getInputStream())
                    val output = BufferedOutputStream(socket.getOutputStream())

                    ControlWire.writeMessage(
                        output,
                        mapOf(
                            "type" to "REQUEST_SEND",
                            "name" to if (peer.isTunnel) tunnelSubdomain else _uiState.value.myName,
                            "ip" to if (peer.isTunnel) tunnelSubdomain else _uiState.value.myIp,
                            "tunnel_name" to if (peer.isTunnel) tunnelSubdomain else null,
                            "filename" to payloadFilename,
                            "display_name" to displayName,
                            "size" to payloadSize,
                            "is_zip" to (useZip && fileCount > 1),
                            "file_count" to fileCount,
                        ),
                    )

                    when (ControlWire.readMessage(input)?.string("type")) {
                        "ACCEPT" -> {
                            startOutgoingTransfer(
                                socket = socket,
                                input = input,
                                output = output,
                                documents = documents,
                                tempZipFile = tempZipFile,
                                displayName = displayName,
                                payloadFilename = payloadFilename,
                                payloadSize = payloadSize,
                                useZip = useZip && fileCount > 1,
                            )
                        }

                        "REJECT" -> {
                            emitEvent(string(R.string.event_rejected))
                            updateBusy(false)
                        }

                        else -> {
                            emitEvent(string(R.string.event_no_response))
                            updateBusy(false)
                        }
                    }
                }
            } catch (e: Exception) {
                updateBusy(false)
                emitEvent(string(R.string.event_connect_failed, e.message ?: string(R.string.unknown_error)))
            } finally {
                tempZipFile?.delete()
            }
        }
    }

    private fun startLanBroadcastLoop() {
        if (lanBroadcastJob?.isActive == true) return
        lanBroadcastJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                broadcastDiscovery()
                delay(5_000L)
            }
        }
    }

    private fun startLanListenLoop() {
        if (lanListenJob?.isActive == true) return
        lanListenJob = viewModelScope.launch(Dispatchers.IO) {
            val socket = DatagramSocket(null)
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", BROADCAST_PORT))
                socket.soTimeout = 1_000
                val buffer = ByteArray(8_192)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val body = String(packet.data, 0, packet.length)
                        val msg = JsonParser.parseString(body).asJsonObject
                        if (msg.string("type") == "DISCOVERY") {
                            val ip = msg.string("ip") ?: return@launch
                            if (ip == _uiState.value.myIp) {
                                continue
                            }
                            val peer = PeerDevice(
                                id = ip,
                                title = msg.string("name") ?: "Unknown",
                                addressLabel = ip,
                                connectHost = ip,
                                connectPort = msg.int("port") ?: DATA_PORT,
                                lastSeenEpochMs = System.currentTimeMillis(),
                                isTunnel = false,
                            )
                            lanPeers[ip] = peer
                            syncLanPeers()
                        }
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                    }
                }
            } finally {
                socket.close()
            }
        }
    }

    private fun startLanTransferServer() {
        if (lanServerJob?.isActive == true) return
        lanServerJob = viewModelScope.launch(Dispatchers.IO) {
            val serverSocket = ServerSocket()
            try {
                serverSocket.reuseAddress = true
                serverSocket.bind(InetSocketAddress("0.0.0.0", DATA_PORT))
                serverSocket.soTimeout = 1_000
                while (isActive) {
                    try {
                        val client = serverSocket.accept()
                        viewModelScope.launch(Dispatchers.IO) {
                            handleTransferConnection(client)
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            } catch (e: Exception) {
                emitEvent(string(R.string.event_lan_server_failed, e.message ?: string(R.string.unknown_error)))
            } finally {
                serverSocket.closeQuietly()
            }
        }
    }

    private fun startCleanupLoop() {
        if (cleanupJob?.isActive == true) return
        cleanupJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val threshold = System.currentTimeMillis() - DEVICE_TIMEOUT_MS
                lanPeers.entries.removeIf { it.value.lastSeenEpochMs < threshold }
                syncLanPeers()
                delay(5_000L)
            }
        }
    }

    private fun broadcastDiscovery() {
        refreshLocalIp()
        val payload = gson.toJson(
            mapOf(
                "type" to "DISCOVERY",
                "name" to _uiState.value.myName,
                "ip" to _uiState.value.myIp,
                "port" to DATA_PORT,
            ),
        )
        val packetBytes = payload.toByteArray()
        val broadcastAddresses = resolveBroadcastAddresses()

        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (address in broadcastAddresses) {
                runCatching {
                    socket.send(DatagramPacket(packetBytes, packetBytes.size, address, BROADCAST_PORT))
                }
            }
        }
    }

    private fun startTunnelStackIfNeeded() {
        if (tunnelStarted) return
        tunnelStarted = true
        startTunnelLocalTransferServer()
        startTunnelPolling()
        reconnectTunnelWebSocket()
    }

    private fun startTunnelIfConfigured() {
        if (!hasActiveTunnelConfig()) {
            updateTunnelStatus(string(R.string.tunnel_external_required))
            return
        }
        if (!tunnelStarted) {
            startTunnelStackIfNeeded()
        } else if (tunnelWebSocket == null) {
            reconnectTunnelWebSocket()
        }
    }

    private fun hasActiveTunnelConfig(): Boolean {
        return activeUsePublicTunnel || (activeTunnelHost.isNotBlank() && activeTunnelToken.isNotBlank())
    }

    private fun disconnectTunnel() {
        tunnelReconnectJob?.cancel()
        tunnelRegistered = false
        tunnelWebSocket?.cancel()
        tunnelWebSocket = null
        pendingTunnelPackets.clear()
        tunnelBridgeSockets.values.forEach { it.closeQuietly() }
        tunnelBridgeSockets.clear()
        tunnelPeers.clear()
        lastTunnelPeerSnapshotAt = 0L
        syncTunnelPeers()
    }

    private fun startTunnelLocalTransferServer() {
        if (tunnelLocalServerJob?.isActive == true) return
        tunnelLocalServerJob = viewModelScope.launch(Dispatchers.IO) {
            val serverSocket = ServerSocket()
            try {
                serverSocket.reuseAddress = true
                serverSocket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), LOCAL_FILE_PORT))
                serverSocket.soTimeout = 1_000
                while (isActive) {
                    try {
                        val client = serverSocket.accept()
                        viewModelScope.launch(Dispatchers.IO) {
                            handleTransferConnection(client)
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            } catch (e: Exception) {
                updateTunnelStatus(string(R.string.tunnel_local_server_failed, e.message ?: string(R.string.generic_error)))
            } finally {
                serverSocket.closeQuietly()
            }
        }
    }

    private fun startTunnelPolling() {
        if (tunnelPollJob?.isActive == true) return
        tunnelPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (tunnelRegistered) {
                    pollTunnelPeersOnce()
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun reconnectTunnelWebSocket() {
        if (!tunnelStarted) return
        tunnelReconnectJob?.cancel()
        tunnelRegistered = false
        tunnelReconnectJob = viewModelScope.launch(Dispatchers.IO) {
            connectTunnelWebSocket()
        }
    }

    private fun connectTunnelWebSocket() {
        val endpoints = buildTunnelEndpoints(activeTunnelHost, activeTunnelSsl)
        val request = Request.Builder()
            .url(endpoints.wsUrl)
            .build()

        tunnelWebSocket?.cancel()
        tunnelWebSocket = null
        tunnelRegistered = false
        updateTunnelStatus(string(R.string.tunnel_status_ws_connecting))

        tunnelWebSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    updateTunnelStatus(string(R.string.tunnel_status_ws_connected_registering))
                    webSocket.send(
                        gson.toJson(
                            mapOf(
                                "type" to "register",
                                "subdomain" to tunnelSubdomain,
                                "auth_token" to activeTunnelToken,
                                "name" to currentTunnelDisplayName(),
                                "display_name" to currentTunnelDisplayName(),
                                "client_name" to currentTunnelDisplayName(),
                                "metadata" to mapOf(
                                    "device_name" to currentTunnelDisplayName(),
                                    "display_name" to currentTunnelDisplayName(),
                                    "platform" to "android",
                                ),
                                "tcp_configs" to listOf(mapOf("name" to TUNNEL_TCP_NAME, "remote_port" to 0)),
                                "udp_configs" to emptyList<Map<String, Any>>(),
                            ),
                        ),
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching {
                        handleTunnelMessage(webSocket, text)
                    }.onFailure {
                        emitEvent(string(R.string.event_tunnel_message_failed, it.message ?: string(R.string.generic_error)))
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (tunnelWebSocket === webSocket) {
                        tunnelWebSocket = null
                    }
                    tunnelRegistered = false
                    updateTunnelStatus(string(R.string.tunnel_status_ws_closed_prompt))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (tunnelWebSocket === webSocket) {
                        tunnelWebSocket = null
                    }
                    tunnelRegistered = false
                    updateTunnelStatus(string(R.string.tunnel_status_ws_failed, t.message ?: string(R.string.ws_failed)))
                }
            },
        )
    }

    private fun handleTunnelMessage(webSocket: WebSocket, text: String) {
        val payload = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
        when (payload.string("type")) {
            "register_result" -> {
                if (payload.bool("ok") == true) {
                    tunnelRegistered = true
                    var assignedPort = 0
                    val assigned = payload.array("tcp_assigned")
                    for (index in 0 until (assigned?.size() ?: 0)) {
                        val entry = assigned?.get(index)?.asObjectOrNull() ?: continue
                        if (entry.string("name") == TUNNEL_TCP_NAME) {
                            assignedPort = entry.int("remote_port") ?: 0
                            break
                        }
                    }
                    if (assignedPort > 0) {
                        updateTunnelStatus(string(R.string.tunnel_status_registered_with_port, assignedPort))
                    } else {
                        updateTunnelStatus(string(R.string.tunnel_status_registered))
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        pollTunnelPeersOnce()
                    }
                } else {
                    tunnelRegistered = false
                    updateTunnelStatus(
                        string(
                            R.string.tunnel_status_register_failed,
                            payload.string("reason") ?: string(R.string.unknown_value),
                        ),
                    )
                    webSocket.close(1000, "register failed")
                }
            }

            "tcp_open" -> {
                val name = payload.string("name")
                val streamId = payload.string("stream_id")
                if (name != TUNNEL_TCP_NAME || streamId.isNullOrBlank()) {
                    webSocket.send(gson.toJson(mapOf("type" to "tcp_close", "stream_id" to streamId, "who" to "client")))
                    return
                }

                try {
                    openTunnelStream(webSocket, streamId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    webSocket.send(gson.toJson(mapOf("type" to "tcp_close", "stream_id" to streamId, "who" to "client")))
                    emitEvent(string(R.string.event_tunnel_stream_failed, e.message ?: string(R.string.generic_error)))
                }
            }

            "tcp_data" -> {
                val streamId = payload.string("stream_id") ?: return
                val base64 = payload.string("b64") ?: return
                val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return
                val socket = tunnelBridgeSockets[streamId]
                if (socket == null) {
                    val queue = pendingTunnelPackets.getOrPut(streamId) { mutableListOf() }
                    synchronized(queue) {
                        queue += bytes
                    }
                    return
                }
                writeTunnelPacket(socket, bytes)
            }

            "tcp_close" -> {
                payload.string("stream_id")?.let { closeTunnelStream(it) }
            }
        }
    }

    private fun openTunnelStream(webSocket: WebSocket, streamId: String) {
        val socket = Socket("127.0.0.1", LOCAL_FILE_PORT)
        tunnelBridgeSockets[streamId] = socket
        flushPendingTunnelPackets(streamId, socket)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                pumpTunnelStream(webSocket, streamId, socket)
            } catch (_: Exception) {
            } finally {
                webSocket.send(gson.toJson(mapOf("type" to "tcp_close", "stream_id" to streamId, "who" to "client")))
                closeTunnelStream(streamId)
            }
        }
    }

    private fun flushPendingTunnelPackets(streamId: String, socket: Socket) {
        val queued = pendingTunnelPackets.remove(streamId) ?: return
        val snapshot = synchronized(queued) { queued.toList() }
        snapshot.forEach { packet ->
            writeTunnelPacket(socket, packet)
        }
    }

    private fun writeTunnelPacket(socket: Socket, bytes: ByteArray) {
        runCatching {
            val output = BufferedOutputStream(socket.getOutputStream())
            output.write(bytes)
            output.flush()
        }
    }

    private suspend fun pumpTunnelStream(
        webSocket: WebSocket,
        streamId: String,
        socket: Socket,
    ) {
        val input = BufferedInputStream(socket.getInputStream())
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            val packet = mapOf(
                "type" to "tcp_data",
                "stream_id" to streamId,
                "b64" to Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP),
            )
            if (!webSocket.send(gson.toJson(packet))) {
                break
            }
        }
    }

    private fun closeTunnelStream(streamId: String) {
        pendingTunnelPackets.remove(streamId)
        tunnelBridgeSockets.remove(streamId)?.closeQuietly()
    }

    private suspend fun pollTunnelPeersOnce() {
        val endpoints = buildTunnelEndpoints(activeTunnelHost, activeTunnelSsl)
        val request = Request.Builder()
            .url("${endpoints.adminBaseUrl}/_health")
            .addHeader("Authorization", "Bearer $activeTunnelToken")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string() ?: error(string(R.string.error_response_empty))
                val json = JsonParser.parseString(body).asJsonObject
                if (json.bool("ok") != true) error(string(R.string.error_server_response_invalid))

                val nextPeers = linkedMapOf<String, PeerDevice>()
                val tunnels = json.getAsJsonObject("tunnels") ?: JsonObject()
                for ((subdomain, info) in tunnels.entrySet()) {
                    if (subdomain == tunnelSubdomain) continue
                    val tunnelInfo = info.asObjectOrNull() ?: continue
                    val tcp = tunnelInfo.getAsJsonObject("tcp") ?: continue
                    val remotePort = tcp.get(TUNNEL_TCP_NAME)?.asInt ?: continue
                    val peerSubdomain = resolveTunnelPeerSubdomain(subdomain, tunnelInfo)
                    val displayTitle = resolveTunnelPeerTitle(peerSubdomain, tunnelInfo)
                    nextPeers[subdomain] = PeerDevice(
                        id = subdomain,
                        title = peerSubdomain,
                        addressLabel = if (displayTitle == peerSubdomain) {
                            "port:$remotePort"
                        } else {
                            "$displayTitle / port:$remotePort"
                        },
                        connectHost = endpoints.tcpHost,
                        connectPort = remotePort,
                        lastSeenEpochMs = System.currentTimeMillis(),
                        isTunnel = true,
                    )
                }
                val now = System.currentTimeMillis()
                if (nextPeers.isNotEmpty()) {
                    tunnelPeers.clear()
                    tunnelPeers.putAll(nextPeers)
                    lastTunnelPeerSnapshotAt = now
                } else if (tunnelPeers.isEmpty() || now - lastTunnelPeerSnapshotAt > 15_000L) {
                    tunnelPeers.clear()
                }
                syncTunnelPeers()
            }
        }.onFailure {
            updateTunnelStatus(string(R.string.tunnel_status_peer_fetch_failed, it.message ?: string(R.string.generic_error)))
        }
    }

    private suspend fun handleTransferConnection(socket: Socket) {
        if (_uiState.value.isBusy && activeTransferMode != null) {
            runCatching {
                val output = BufferedOutputStream(socket.getOutputStream())
                ControlWire.writeMessage(output, mapOf("type" to "REJECT"))
            }
            socket.closeQuietly()
            return
        }

        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())

        try {
            while (true) {
                val msg = ControlWire.readMessage(input) ?: break
                when (msg.string("type")) {
                    "REQUEST_SEND" -> {
                        val accepted = handleIncomingRequest(msg)
                        ControlWire.writeMessage(output, mapOf("type" to if (accepted) "ACCEPT" else "REJECT"))
                        if (!accepted) {
                            return
                        }
                    }

                    "START_TRANSFER" -> {
                        cancelFlag = false
                        activeTransferMode = "receive"
                        activeReceiveSocket = socket
                        val isZip = msg.bool("is_zip") == true
                        val fileCount = msg.int("file_count") ?: 1
                        val totalSize = msg.get("size")?.asLong ?: 0L
                        val filename = msg.string("filename") ?: "downloaded_file.dat"

                        if (isZip || fileCount <= 1) {
                            receiveSingleFile(
                                socket = socket,
                                input = input,
                                filename = filename,
                                fileSize = totalSize,
                                isZip = isZip,
                            )
                        } else {
                            receiveMultiFileTransfer(
                                socket = socket,
                                input = input,
                                totalSize = totalSize,
                                fileCount = fileCount,
                            )
                        }
                        return
                    }
                }
            }
        } catch (e: Exception) {
            emitEvent(string(R.string.event_request_handle_failed, e.message ?: string(R.string.unknown_error)))
        } finally {
            if (activeReceiveSocket === socket) {
                activeReceiveSocket = null
                activeTransferMode = null
            }
            socket.closeQuietly()
        }
    }

    private suspend fun handleIncomingRequest(message: JsonObject): Boolean {
        val requestId = UUID.randomUUID().toString()
        val request = IncomingTransferRequest(
            requestId = requestId,
            senderName = message.string("tunnel_name")
                ?: message.string("name")
                ?: string(R.string.unknown_sender),
            senderAddress = message.string("tunnel_name")
                ?: message.string("ip")
                ?: "?",
            displayName = message.string("display_name")
                ?: message.string("filename")
                ?: string(R.string.generic_file_label),
            totalBytes = message.get("size")?.asLong ?: 0L,
            isZip = message.bool("is_zip") == true,
            fileCount = message.int("file_count") ?: 1,
        )

        val deferred = CompletableDeferred<Boolean>()
        incomingDecisions[requestId] = deferred
        _uiState.update { it.copy(pendingRequest = request) }
        IncomingRequestNotifier.show(appContext, request)

        val result = withTimeoutOrNull(120_000L) { deferred.await() } ?: false
        incomingDecisions.remove(requestId)
        IncomingRequestNotifier.cancel(appContext)
        _uiState.update { current ->
            if (current.pendingRequest?.requestId == requestId) {
                current.copy(pendingRequest = null)
            } else {
                current
            }
        }
        return result
    }

    private suspend fun receiveSingleFile(
        socket: Socket,
        input: BufferedInputStream,
        filename: String,
        fileSize: Long,
        isZip: Boolean,
    ) {
        val saveRoot = storage.currentRoot()
        val directTarget = storage.createUniqueOutputTarget(filename, saveRoot)
        val targetsToDelete = mutableListOf<P2pStorage.OutputTarget>()
        directTarget?.let(targetsToDelete::add)

        beginProgress(
            title = filename,
            totalBytes = fileSize,
            isReceiving = true,
            showItemProgress = false,
        )

        var completedBytes = 0L
        var interrupted = false

        try {
            storage.openOutputStream(directTarget).use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (completedBytes < fileSize && !cancelFlag) {
                    val maxRead = min(BUFFER_SIZE.toLong(), fileSize - completedBytes).toInt()
                    val read = input.read(buffer, 0, maxRead)
                    if (read <= 0) {
                        interrupted = true
                        break
                    }
                    out.write(buffer, 0, read)
                    completedBytes += read
                    updateProgress(
                        deltaBytes = read.toLong(),
                        itemDone = completedBytes,
                        itemTotal = fileSize,
                        itemLabel = string(R.string.item_receiving_file, safeName(filename)),
                    )
                }
            }
        } catch (_: Exception) {
            interrupted = true
        } finally {
            socket.closeQuietly()
        }

        if (cancelFlag) {
            targetsToDelete.forEach(storage::deleteTarget)
            clearProgress()
            emitEvent(string(R.string.event_receive_cancelled))
            return
        }

        if (interrupted || completedBytes < fileSize) {
            targetsToDelete.forEach(storage::deleteTarget)
            clearProgress()
            emitEvent(string(R.string.event_sender_cancelled))
            return
        }

        if (isZip) {
            try {
                val extractDirName = filename.substringBeforeLast('.', filename)
                val extractDir = storage.createUniqueDirectory(extractDirName, saveRoot)
                val extracted = storage.openInputStream(directTarget).use { zipInput ->
                    extractZip(zipInput, extractDir)
                }
                storage.deleteTarget(directTarget)
                clearProgress()
                emitEvent(string(R.string.event_zip_extract_complete, extracted.size))
                refreshReceivedFiles()
            } catch (e: Exception) {
                clearProgress()
                emitEvent(string(R.string.event_zip_extract_failed))
            }
        } else {
            clearProgress()
            emitEvent(string(R.string.event_receive_complete, directTarget.displayName))
            refreshReceivedFiles()
        }
    }

    private suspend fun receiveMultiFileTransfer(
        socket: Socket,
        input: BufferedInputStream,
        totalSize: Long,
        fileCount: Int,
    ) {
        val metadata = ControlWire.readMessage(input)
        val files = metadata?.array("files")
        if (metadata?.string("type") != "FILES_META" || files == null || files.size() != fileCount) {
            socket.closeQuietly()
            updateBusy(false)
            emitEvent(string(R.string.event_multi_meta_failed))
            return
        }

        val saveRoot = storage.currentRoot()
        val savedTargets = mutableListOf<P2pStorage.OutputTarget>()
        var senderInterrupted = false
        var receiveError: String? = null

        beginProgress(
            title = string(R.string.transfer_title_multi_files),
            totalBytes = totalSize,
            isReceiving = true,
            showItemProgress = true,
        )

        try {
            for (index in 0 until files.size()) {
                if (cancelFlag) break
                val item = files.get(index).asObjectOrNull() ?: continue
                val fileName = safeName(item.string("name") ?: "file_${index + 1}.dat")
                val fileSize = item.get("size")?.asLong ?: 0L
                val target = storage.createOutputInDirectory(saveRoot, fileName)
                savedTargets += target
                var fileDone = 0L

                storage.openOutputStream(target).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (fileDone < fileSize && !cancelFlag) {
                        val maxRead = min(BUFFER_SIZE.toLong(), fileSize - fileDone).toInt()
                        val read = input.read(buffer, 0, maxRead)
                        if (read <= 0) {
                            senderInterrupted = true
                            break
                        }
                        out.write(buffer, 0, read)
                        fileDone += read
                        updateProgress(
                            deltaBytes = read.toLong(),
                            itemDone = fileDone,
                            itemTotal = fileSize,
                            itemLabel = string(R.string.item_receiving_file_index, fileName, index + 1, fileCount),
                        )
                    }
                }

                if (!cancelFlag && !senderInterrupted && fileDone < fileSize) {
                    senderInterrupted = true
                }

                if (cancelFlag || senderInterrupted) {
                    break
                }
            }
        } catch (e: Exception) {
            receiveError = e.message ?: e.javaClass.simpleName
        } finally {
            socket.closeQuietly()
        }

        if (cancelFlag) {
            savedTargets.forEach(storage::deleteTarget)
            clearProgress()
            emitEvent(string(R.string.event_receive_cancelled))
            return
        }

        if (receiveError != null) {
            savedTargets.forEach(storage::deleteTarget)
            clearProgress()
            emitEvent(string(R.string.event_multi_receive_error, receiveError))
            return
        }

        if (senderInterrupted) {
            savedTargets.forEach(storage::deleteTarget)
            clearProgress()
            emitEvent(string(R.string.event_sender_cancelled))
            return
        }

        clearProgress()
        emitEvent(string(R.string.event_multi_receive_complete, savedTargets.size))
        refreshReceivedFiles()
    }

    private suspend fun startOutgoingTransfer(
        socket: Socket,
        input: BufferedInputStream,
        output: BufferedOutputStream,
        documents: List<SelectedDocument>,
        tempZipFile: File?,
        displayName: String,
        payloadFilename: String,
        payloadSize: Long,
        useZip: Boolean,
    ) {
        activeTransferMode = "send"
        cancelFlag = false
        beginProgress(
            title = displayName,
            totalBytes = payloadSize,
            isReceiving = false,
            showItemProgress = !(useZip || documents.size <= 1),
        )

        val cancelListener = viewModelScope.launch(Dispatchers.IO) {
            try {
                while (isActive && !cancelFlag) {
                    val msg = ControlWire.readMessage(input) ?: break
                    if (msg.string("type") == "CANCEL") {
                        cancelFlag = true
                        emitEvent(string(R.string.event_remote_cancelled))
                        break
                    }
                }
            } catch (_: Exception) {
                // The socket is closed as part of normal transfer teardown.
            }
        }

        try {
            ControlWire.writeMessage(
                output,
                mapOf(
                    "type" to "START_TRANSFER",
                    "filename" to payloadFilename,
                    "size" to payloadSize,
                    "is_zip" to useZip,
                    "file_count" to documents.size,
                ),
            )

            if (useZip) {
                val zipFile = tempZipFile ?: error(string(R.string.error_missing_zip))
                sendBinaryFile(
                    file = zipFile,
                    output = output,
                    itemLabel = string(R.string.item_sending_zip, zipFile.name),
                )
            } else if (documents.size == 1) {
                sendDocument(
                    document = documents.first(),
                    output = output,
                    itemLabel = string(R.string.item_sending_file, documents.first().displayName),
                )
            } else {
                ControlWire.writeMessage(
                    output,
                    mapOf(
                        "type" to "FILES_META",
                        "files" to documents.map { doc ->
                            mapOf("name" to doc.displayName, "size" to doc.sizeBytes)
                        },
                    ),
                )

                documents.forEachIndexed { index, document ->
                    if (cancelFlag) return@forEachIndexed
                    sendDocument(
                        document = document,
                        output = output,
                        itemLabel = string(R.string.item_sending_file_index, document.displayName, index + 1, documents.size),
                    )
                }
            }
        } finally {
            cancelListener.cancel()
            activeTransferMode = null
            socket.closeQuietly()
            clearProgress()
        }

        if (cancelFlag) {
            emitEvent(string(R.string.event_transfer_cancelled))
        } else {
            emitEvent(string(R.string.event_transfer_complete, displayName))
        }
    }

    private suspend fun sendBinaryFile(
        file: File,
        output: BufferedOutputStream,
        itemLabel: String,
    ) {
        FileInputStream(file).use { input ->
            streamToSocket(
                totalSize = file.length(),
                itemLabel = itemLabel,
                inputProvider = { input },
                output = output,
            )
        }
    }

    private suspend fun sendDocument(
        document: SelectedDocument,
        output: BufferedOutputStream,
        itemLabel: String,
    ) {
        appContext.contentResolver.openInputStream(document.uri)?.use { input ->
            streamToSocket(
                totalSize = document.sizeBytes,
                itemLabel = itemLabel,
                inputProvider = { input },
                output = output,
            )
        } ?: throw IOException(string(R.string.error_open_input_stream, document.displayName))
    }

    private suspend fun streamToSocket(
        totalSize: Long,
        itemLabel: String,
        inputProvider: () -> java.io.InputStream,
        output: BufferedOutputStream,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var itemDone = 0L
        val input = inputProvider()
        while (itemDone < totalSize && !cancelFlag) {
            val read = input.read(buffer, 0, min(BUFFER_SIZE.toLong(), totalSize - itemDone).toInt())
            if (read <= 0) break
            output.write(buffer, 0, read)
            output.flush()
            itemDone += read
            updateProgress(
                deltaBytes = read.toLong(),
                itemDone = itemDone,
                itemTotal = totalSize,
                itemLabel = itemLabel,
            )
        }
    }

    private suspend fun createZipArchive(
        documents: List<SelectedDocument>,
        zipName: String,
    ): File {
        val tempZip = File.createTempFile("p2p_send_", ".zip", appContext.cacheDir)
        val totalSourceSize = documents.sumOf { it.sizeBytes }
        beginProgress(
            title = string(R.string.transfer_title_zip),
            totalBytes = totalSourceSize * 2,
            isReceiving = false,
            showItemProgress = true,
        )

        try {
            val preparedEntries = prepareStoredZipEntries(documents)
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zipOut ->
                preparedEntries.forEachIndexed { index, prepared ->
                    if (cancelFlag) error(string(R.string.event_transfer_cancelled))
                    val entry = ZipEntry(prepared.entryName).apply {
                        method = ZipEntry.STORED
                        size = prepared.sizeBytes
                        compressedSize = prepared.sizeBytes
                        crc = prepared.crc32
                    }
                    zipOut.putNextEntry(entry)
                    appContext.contentResolver.openInputStream(prepared.document.uri)?.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var fileDone = 0L
                        while (!cancelFlag) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            zipOut.write(buffer, 0, read)
                            fileDone += read
                            updateProgress(
                                deltaBytes = read.toLong(),
                                itemDone = fileDone,
                                itemTotal = prepared.sizeBytes,
                                itemLabel = string(R.string.item_zipping, prepared.entryName, index + 1, documents.size),
                            )
                        }
                    } ?: throw IOException(string(R.string.error_open_input_stream, prepared.entryName))
                    zipOut.closeEntry()
                }
            }
        } catch (e: Exception) {
            tempZip.delete()
            clearProgress()
            updateBusy(false)
            throw e
        }

        clearProgress()
        return tempZip.renameTo(File(tempZip.parentFile, zipName)).let { renamed ->
            if (renamed) File(tempZip.parentFile, zipName) else tempZip
        }
    }

    private suspend fun prepareStoredZipEntries(documents: List<SelectedDocument>): List<PreparedZipEntry> {
        return documents.mapIndexed { index, document ->
            val entryName = safeName(document.displayName)
            val crc32 = CRC32()
            var actualSize = 0L
            appContext.contentResolver.openInputStream(document.uri)?.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var fileDone = 0L
                while (!cancelFlag) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    crc32.update(buffer, 0, read)
                    actualSize += read
                    fileDone += read
                    updateProgress(
                        deltaBytes = read.toLong(),
                        itemDone = fileDone,
                        itemTotal = document.sizeBytes,
                        itemLabel = string(R.string.item_zipping, entryName, index + 1, documents.size),
                    )
                }
            } ?: throw IOException(string(R.string.error_open_input_stream, entryName))

            if (cancelFlag) {
                error(string(R.string.event_transfer_cancelled))
            }

            PreparedZipEntry(
                document = document,
                entryName = entryName,
                sizeBytes = actualSize,
                crc32 = crc32.value,
            )
        }
    }

    private fun extractZip(zipInput: java.io.InputStream, directory: P2pStorage.DirectoryTarget): List<String> {
        val extractedNames = mutableListOf<String>()
        ZipInputStream(BufferedInputStream(zipInput)).use { zipIn ->
            while (true) {
                val entry = zipIn.nextEntry ?: break
                if (entry.isDirectory) {
                    continue
                }
                val safeEntryName = safeName(entry.name.substringAfterLast('/'))
                val target = storage.createOutputInDirectory(directory, safeEntryName)
                storage.openOutputStream(target).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = zipIn.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                }
                extractedNames += target.displayName
            }
        }
        return extractedNames
    }

    private suspend fun resolveSelectedDocuments(uris: List<Uri>): List<SelectedDocument> {
        return withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                val name = queryDisplayName(uri) ?: return@mapNotNull null
                val size = querySize(uri).takeIf { it > 0 } ?: measureSize(uri)
                if (size <= 0) {
                    null
                } else {
                    SelectedDocument(uri = uri, displayName = safeName(name), sizeBytes = size)
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }

    private fun querySize(uri: Uri): Long {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
            } ?: -1L
    }

    private fun measureSize(uri: Uri): Long {
        return appContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
            }
            total
        } ?: -1L
    }

    private fun beginProgress(
        title: String,
        totalBytes: Long,
        isReceiving: Boolean,
        showItemProgress: Boolean,
    ) {
        progressStartTimeMs = System.currentTimeMillis()
        progressTransferredBytes = 0L
        progressTotalBytes = totalBytes.coerceAtLeast(0L)
        _uiState.update {
            it.copy(
                isBusy = true,
                transferProgress = TransferProgressUi(
                    title = title,
                    itemLabel = if (isReceiving) string(R.string.progress_preparing_receive) else string(R.string.progress_preparing_send),
                    totalBytes = totalBytes,
                    transferredBytes = 0L,
                    itemBytes = 0L,
                    itemTransferredBytes = 0L,
                    speedBytesPerSecond = 0.0,
                    remainingSeconds = 0.0,
                    isReceiving = isReceiving,
                    showItemProgress = showItemProgress,
                ),
            )
        }
        _uiState.value.transferProgress?.let { progress ->
            TransferForegroundController.activate(appContext, progress)
        }
    }

    private fun updateProgress(
        deltaBytes: Long,
        itemDone: Long,
        itemTotal: Long,
        itemLabel: String,
    ) {
        progressTransferredBytes += deltaBytes
        val elapsedSeconds = ((System.currentTimeMillis() - progressStartTimeMs).coerceAtLeast(1L)) / 1000.0
        val bytesPerSecond = progressTransferredBytes / elapsedSeconds
        val remaining = if (bytesPerSecond > 0) {
            (progressTotalBytes - progressTransferredBytes).coerceAtLeast(0L) / bytesPerSecond
        } else {
            0.0
        }

        _uiState.update { current ->
            val progress = current.transferProgress ?: return@update current
            current.copy(
                transferProgress = progress.copy(
                    itemLabel = itemLabel,
                    transferredBytes = progressTransferredBytes,
                    itemBytes = itemTotal,
                    itemTransferredBytes = itemDone,
                    speedBytesPerSecond = bytesPerSecond,
                    remainingSeconds = remaining,
                ),
            )
        }
        _uiState.value.transferProgress?.let(TransferForegroundController::refresh)
    }

    private fun clearProgress() {
        progressStartTimeMs = 0L
        progressTransferredBytes = 0L
        progressTotalBytes = 0L
        _uiState.update { it.copy(transferProgress = null, isBusy = false) }
        TransferForegroundController.deactivate(appContext)
    }

    private fun updateBusy(isBusy: Boolean) {
        _uiState.update { it.copy(isBusy = isBusy) }
    }

    private fun selectedPeer(): PeerDevice? {
        val selectedId = _uiState.value.selectedPeerId ?: return null
        return currentPeers().firstOrNull { it.id == selectedId }
    }

    private fun currentPeers(): List<PeerDevice> {
        return if (_uiState.value.mode == TransferMode.LAN) _uiState.value.lanPeers else _uiState.value.tunnelPeers
    }

    private fun syncLanPeers() {
        val sorted = lanPeers.values.sortedWith(compareBy(PeerDevice::title, PeerDevice::addressLabel))
        selectedLanPeerId = selectedLanPeerId?.takeIf { id -> sorted.any { it.id == id } }
        _uiState.update { current ->
            current.copy(
                lanPeers = sorted,
                selectedPeerId = if (current.mode == TransferMode.LAN) {
                    selectedLanPeerId
                } else {
                    current.selectedPeerId
                },
            )
        }
    }

    private fun syncTunnelPeers() {
        val sorted = tunnelPeers.values.sortedBy(PeerDevice::title)
        if (selectedTunnelPeerId != null && sorted.none { it.id == selectedTunnelPeerId }) {
            selectedTunnelPeerId = if (sorted.size == 1) {
                sorted.first().id
            } else {
                selectedTunnelPeerId
            }
        }
        _uiState.update { current ->
            current.copy(
                tunnelPeers = sorted,
                selectedPeerId = if (current.mode == TransferMode.TUNNEL) {
                    selectedTunnelPeerId
                } else {
                    current.selectedPeerId
                },
            )
        }
    }

    private fun updateTunnelStatus(status: String) {
        _uiState.update { it.copy(tunnelStatus = status) }
    }

    private fun buildBaseDeviceName(): String {
        val parts = listOfNotNull(
            Build.MANUFACTURER?.takeIf { it.isNotBlank() },
            Build.MODEL?.takeIf { it.isNotBlank() },
        ).distinct()
        return parts.joinToString(" ").ifBlank { "Android Device" }
    }

    private fun buildLanDeviceName(baseName: String): String {
        return "$baseName $lanNameSuffix"
    }

    private fun refreshLocalIp() {
        val ip = resolveLocalIp()
        _uiState.update { it.copy(myIp = ip) }
    }

    private fun resolveLocalIp(): String {
        runCatching {
            DatagramSocket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53))
                val address = socket.localAddress
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress ?: "127.0.0.1"
                }
            }
        }

        return Collections.list(NetworkInterface.getNetworkInterfaces())
            .firstNotNullOfOrNull { networkInterface ->
                if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                    return@firstNotNullOfOrNull null
                }
                if (IGNORED_INTERFACE_PREFIXES.any { networkInterface.name.startsWith(it) }) {
                    return@firstNotNullOfOrNull null
                }
                Collections.list(networkInterface.inetAddresses)
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress }
                    ?.hostAddress
            }
            ?: "127.0.0.1"
    }

    private fun resolveBroadcastAddresses(): List<InetAddress> {
        val addresses = Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .filterNot { networkInterface ->
                IGNORED_INTERFACE_PREFIXES.any { networkInterface.name.startsWith(it) }
            }
            .flatMap { networkInterface ->
                networkInterface.interfaceAddresses.mapNotNull { it.broadcast }
            }

        return if (addresses.isNotEmpty()) {
            addresses
        } else {
            listOf(InetAddress.getByName("255.255.255.255"))
        }
    }

    private fun acquireMulticastLock() {
        val lock = wifiManager?.createMulticastLock("rhkr8521-p2p-discovery") ?: return
        lock.setReferenceCounted(false)
        runCatching { lock.acquire() }
        multicastLock = lock
    }

    private fun makeMultiDisplayName(firstName: String, fileCount: Int): String {
        return if (fileCount > 1) {
            quantityString(R.plurals.multi_display_name, fileCount, firstName, fileCount - 1)
        } else {
            firstName
        }
    }

    private fun currentTunnelDisplayName(): String {
        return baseDeviceName.ifBlank { tunnelSubdomain }
    }

    private fun resolveTunnelPeerSubdomain(fallbackKey: String, tunnelInfo: JsonObject): String {
        return listOfNotNull(
            tunnelInfo.string("subdomain"),
            tunnelInfo.string("requested_subdomain"),
            tunnelInfo.string("assigned_subdomain"),
            tunnelInfo.string("hostname"),
            tunnelInfo.string("host"),
            tunnelInfo.string("domain"),
            tunnelInfo.string("url"),
            findNestedTunnelSubdomain(tunnelInfo),
            fallbackKey,
        ).mapNotNull(::extractTunnelSubdomainCandidate)
            .firstOrNull { it.isNotBlank() && it != TUNNEL_TCP_NAME }
            ?: fallbackKey
    }

    private fun resolveTunnelPeerTitle(subdomain: String, tunnelInfo: JsonObject): String {
        return listOfNotNull(
            tunnelInfo.string("display_name"),
            tunnelInfo.string("name"),
            tunnelInfo.string("device_name"),
            tunnelInfo.string("client_name"),
            findNestedTunnelTitle(tunnelInfo),
            subdomain,
        ).firstOrNull { it.isNotBlank() && it != TUNNEL_TCP_NAME } ?: subdomain
    }

    private fun extractTunnelSubdomainCandidate(raw: String?): String? {
        var text = raw?.trim().orEmpty()
        if (text.isBlank()) return null
        text = text.substringAfter("://", text)
        text = text.substringBefore('/')
        text = text.substringAfterLast('@', text)
        if (text.startsWith('[') && text.contains(']')) {
            text = text.substringBefore(']').removePrefix("[")
        } else if (text.count { it == ':' } == 1) {
            text = text.substringBefore(':')
        }
        val candidate = text.substringBefore('.').trim().trim('"', '\'')
        if (candidate.isBlank() || candidate == TUNNEL_TCP_NAME || candidate.contains(' ')) {
            return null
        }
        return candidate
    }

    private fun findNestedTunnelSubdomain(element: com.google.gson.JsonElement, depth: Int = 0): String? {
        if (depth > 4) return null
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val preferredKeys = listOf("subdomain", "requested_subdomain", "assigned_subdomain", "hostname", "host", "domain", "url")
            preferredKeys.forEach { key ->
                obj.get(key)
                    ?.takeUnless { it.isJsonNull || it.isJsonObject || it.isJsonArray }
                    ?.asString
                    ?.let(::extractTunnelSubdomainCandidate)
                    ?.let { return it }
            }
            obj.entrySet().forEach { (_, value) ->
                findNestedTunnelSubdomain(value, depth + 1)?.let { return it }
            }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { child ->
                findNestedTunnelSubdomain(child, depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun findNestedTunnelTitle(element: com.google.gson.JsonElement, depth: Int = 0): String? {
        if (depth > 4) return null
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val preferredKeys = listOf("display_name", "device_name", "client_name", "name", "title", "hostname")
            preferredKeys.forEach { key ->
                obj.get(key)
                    ?.takeUnless { it.isJsonNull || it.isJsonObject || it.isJsonArray }
                    ?.asString
                    ?.takeIf { it.isNotBlank() && it != TUNNEL_TCP_NAME }
                    ?.let { return it }
            }
            obj.entrySet().forEach { (key, value) ->
                if (key == "tcp" || key == "udp") return@forEach
                findNestedTunnelTitle(value, depth + 1)?.let { return it }
            }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { child ->
                findNestedTunnelTitle(child, depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun emitEvent(message: String) {
        viewModelScope.launch {
            _events.emit(message)
        }
    }

    private fun buildZipFileName(baseName: String, extraFileCount: Int): String {
        val raw = if (isKoreanLocale()) {
            "${baseName}_외_${extraFileCount}개.zip"
        } else {
            "${baseName}_plus_${extraFileCount}_files.zip"
        }
        return safeName(raw)
    }

    private fun isKoreanLocale(): Boolean {
        val locale = appContext.resources.configuration.locales[0]
        return locale?.language?.startsWith("ko") == true || Locale.getDefault().language.startsWith("ko")
    }

    private fun string(@StringRes resId: Int, vararg args: Any): String = appContext.getString(resId, *args)

    private fun quantityString(@PluralsRes resId: Int, quantity: Int, vararg args: Any): String {
        return appContext.resources.getQuantityString(resId, quantity, *args)
    }

    private fun generateLanNameSuffix(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..3)
            .map { chars.random() }
            .joinToString("")
    }
}

private fun ServerSocket.closeQuietly() {
    runCatching { close() }
}
