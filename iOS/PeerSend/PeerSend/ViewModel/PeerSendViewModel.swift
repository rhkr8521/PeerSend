import Foundation
import SwiftUI
import UIKit
import Combine

enum BlockingPrompt: Identifiable, Equatable {
    case notificationPermission
    case updateRequired(AppStoreUpdateInfo)
    case initialTunnelChoice

    var id: String {
        switch self {
        case .notificationPermission: return "notification"
        case .updateRequired(let info): return "update-\(info.version)"
        case .initialTunnelChoice: return "initial-tunnel-choice"
        }
    }

    static func == (lhs: BlockingPrompt, rhs: BlockingPrompt) -> Bool {
        lhs.id == rhs.id
    }
}

final class PeerSendViewModel: ObservableObject {
    @Published var mode: TransferMode = .lan
    @Published var myName: String
    @Published var tunnelSubdomain: String
    @Published var myIP: String
    @Published var saveLocationLabel: String
    @Published var selectedPeerID: String?
    @Published var lanPeers: [PeerDevice] = []
    @Published var tunnelPeers: [PeerDevice] = []
    @Published var usePublicTunnel: Bool
    @Published var tunnelHost: String
    @Published var tunnelSSL: Bool
    @Published var tunnelToken: String
    @Published var tunnelStatus: String = L10n.tunnelDisconnected
    @Published var transferProgress: TransferProgressUI?
    @Published var pendingRequest: IncomingTransferRequest?
    @Published var isBusy = false
    @Published var eventMessage: String?
    @Published var blockingPrompt: BlockingPrompt?

    private let preferences: AppPreferences
    private let storage: FileTransferStorage
    private let notifications: PeerSendNotificationManager
    private let updateChecker = AppStoreUpdateChecker()
    private let udpDiscovery = UDPDiscoveryService()
    private let urlSession = URLSession(configuration: .default)
    private let lock = NSLock()

    private let baseDeviceName: String
    private let lanNameSuffix: String

    private var selectedLANPeerID: String?
    private var selectedTunnelPeerID: String?
    private var lanPeerStore: [String: PeerDevice] = [:]
    private var tunnelPeerStore: [String: PeerDevice] = [:]

    private var activeUsePublicTunnel: Bool
    private var activeTunnelHost: String
    private var activeTunnelSSL: Bool
    private var activeTunnelToken: String

    private var lanListener: TCPListenerService?
    private var tunnelLocalListener: TCPListenerService?
    private var cleanupTask: Task<Void, Never>?
    private var broadcastTask: Task<Void, Never>?
    private var tunnelPollTask: Task<Void, Never>?
    private var tunnelReceiveTask: Task<Void, Never>?
    private var tunnelStarted = false
    private var tunnelRegistered = false
    private var tunnelWebSocketTask: URLSessionWebSocketTask?
    private var lastTunnelPeerSnapshot = Date.distantPast
    private var tunnelBridgeChannels: [String: TCPChannel] = [:]
    private var tunnelBridgeTasks: [String: Task<Void, Never>] = [:]
    private var pendingTunnelPackets: [String: [Data]] = [:]

    private var progressStartTime = Date()
    private var progressTransferredBytes: Int64 = 0
    private var progressTotalBytes: Int64 = 0

    private var cancelRequested = false
    private var activeTransferMode: String?
    private var activeTransferChannel: TCPChannel?
    private var incomingDecisionContinuations: [String: CheckedContinuation<Bool, Never>] = [:]
    private var hasCheckedForUpdate = false
    private var backgroundTaskIdentifier: UIBackgroundTaskIdentifier = .invalid

    init(
        preferences: AppPreferences = .shared,
        storage: FileTransferStorage = FileTransferStorage(),
        notifications: PeerSendNotificationManager = .shared
    ) {
        self.preferences = preferences
        self.storage = storage
        self.notifications = notifications

        let baseDeviceName = preferences.baseDeviceName()
        self.baseDeviceName = baseDeviceName

        let lanNameSuffix = preferences.lanNameSuffix ?? Self.generateLANNameSuffix().also {
            preferences.persistLanNameSuffix($0)
        }
        self.lanNameSuffix = lanNameSuffix

        let displayName = "\(baseDeviceName) \(lanNameSuffix)"
        self.myName = displayName
        self.myIP = NetworkInterfaceInfo.currentIPv4Address()
        self.saveLocationLabel = storage.saveLocationLabel()

        let tunnelSubdomain = preferences.tunnelSubdomain ?? makeSubdomain(prefix: defaultTunnelSubPrefix, seed: baseDeviceName)
        preferences.persistTunnelSubdomain(tunnelSubdomain)
        self.tunnelSubdomain = tunnelSubdomain

        self.usePublicTunnel = preferences.usePublicTunnel
        self.tunnelHost = preferences.savedTunnelHost
        self.tunnelSSL = preferences.savedTunnelSSL
        self.tunnelToken = preferences.savedTunnelToken

        self.activeUsePublicTunnel = preferences.usePublicTunnel
        self.activeTunnelHost = preferences.usePublicTunnel ? defaultTunnelHost : preferences.savedTunnelHost
        self.activeTunnelSSL = preferences.usePublicTunnel ? true : preferences.savedTunnelSSL
        self.activeTunnelToken = preferences.usePublicTunnel ? defaultTunnelToken : preferences.savedTunnelToken

        startLANServices()
    }

    deinit {
        broadcastTask?.cancel()
        cleanupTask?.cancel()
        tunnelPollTask?.cancel()
        tunnelReceiveTask?.cancel()
        udpDiscovery.stop()
        lanListener?.stop()
        tunnelLocalListener?.stop()
        tunnelWebSocketTask?.cancel(with: .normalClosure, reason: nil)
    }

    func performStartupChecks() {
        Task.detached { [weak self] in
            guard let self else { return }
            await self.refreshStartupPrompts()
        }
    }

    func refreshStartupPrompts() async {
        publish {
            self.saveLocationLabel = self.storage.saveLocationLabel()
        }
        let notificationsGranted = await notifications.authorizationGranted()
        if !notificationsGranted {
            publish { self.blockingPrompt = .notificationPermission }
            return
        }

        if !hasCheckedForUpdate {
            hasCheckedForUpdate = true
            if let bundleID = Bundle.main.bundleIdentifier,
               let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
               let info = await updateChecker.check(bundleIdentifier: bundleID, currentVersion: currentVersion) {
                publish { self.blockingPrompt = .updateRequired(info) }
                return
            }
        }

        publish {
            if case .notificationPermission = self.blockingPrompt {
                self.blockingPrompt = nil
            }
        }
    }

    func requestNotificationPermission() {
        Task.detached { [weak self] in
            guard let self else { return }
            let centerGranted = await self.notifications.authorizationGranted()
            let granted = centerGranted ? centerGranted : await self.notifications.requestAuthorization()
            if granted {
                await self.refreshStartupPrompts()
            } else {
                await MainActor.run {
                    self.openSettings()
                }
            }
        }
    }

    func openAppStoreUpdate(_ info: AppStoreUpdateInfo) {
        UIApplication.shared.open(info.url)
    }

    func requestToggleMode() {
        if mode == .lan && !preferences.hasTunnelChoice {
            blockingPrompt = .initialTunnelChoice
            return
        }
        toggleMode()
    }

    func rememberInitialTunnelChoice(usePublic: Bool) {
        preferences.rememberInitialTunnelChoice(usePublic: usePublic)
        self.usePublicTunnel = usePublic
        activeUsePublicTunnel = usePublic
        if usePublic {
            activeTunnelHost = defaultTunnelHost
            activeTunnelSSL = true
            activeTunnelToken = defaultTunnelToken
        } else {
            activeTunnelHost = tunnelHost.trimmingCharacters(in: .whitespacesAndNewlines)
            activeTunnelSSL = tunnelSSL
            activeTunnelToken = tunnelToken.trimmingCharacters(in: .whitespacesAndNewlines)
            if !hasActiveTunnelConfig() {
                updateTunnelStatus(L10n.tunnelExternalRequired)
            }
        }
        blockingPrompt = nil
        toggleMode()
    }

    func toggleMode() {
        mode = mode == .lan ? .tunnel : .lan
        selectedPeerID = mode == .lan ? selectedLANPeerID : selectedTunnelPeerID
        if mode == .tunnel {
            startTunnelIfConfigured()
        }
    }

    func selectPeer(_ peerID: String) {
        let shouldClear = selectedPeerID == peerID
        let nextPeerID = shouldClear ? nil : peerID
        if mode == .lan {
            selectedLANPeerID = nextPeerID
        } else {
            selectedTunnelPeerID = nextPeerID
        }
        selectedPeerID = nextPeerID
    }

    func updateTunnelHost(_ value: String) {
        tunnelHost = value
    }

    func updateTunnelToken(_ value: String) {
        tunnelToken = value
    }

    func updateTunnelSSL(_ value: Bool) {
        tunnelSSL = value
    }

    func updateUsePublicTunnel(_ value: Bool) {
        usePublicTunnel = value
    }

    func applyTunnelSettings() {
        let host = tunnelHost.trimmingCharacters(in: .whitespacesAndNewlines)
        let token = tunnelToken.trimmingCharacters(in: .whitespacesAndNewlines)
        tunnelHost = host
        tunnelToken = token
        preferences.saveTunnel(host: host, ssl: tunnelSSL, token: token, usePublic: usePublicTunnel)

        activeUsePublicTunnel = usePublicTunnel
        if activeUsePublicTunnel {
            activeTunnelHost = defaultTunnelHost
            activeTunnelSSL = true
            activeTunnelToken = defaultTunnelToken
        } else {
            activeTunnelHost = host
            activeTunnelSSL = tunnelSSL
            activeTunnelToken = token
        }

        guard hasActiveTunnelConfig() else {
            disconnectTunnel()
            updateTunnelStatus(L10n.tunnelExternalRequired)
            return
        }

        updateTunnelStatus(L10n.tunnelStarting)
        startTunnelStackIfNeeded()
        reconnectTunnelWebSocket()
    }

    func rememberSaveDirectory(_ url: URL) {
        do {
            try storage.rememberSaveDirectory(url)
            saveLocationLabel = storage.saveLocationLabel()
        } catch {
            emitEvent(error.localizedDescription)
        }
    }

    func manualRefresh() {
        if mode == .lan {
            Task.detached { [weak self] in
                self?.broadcastDiscovery()
            }
        } else {
            startTunnelIfConfigured()
            if tunnelRegistered {
                Task.detached { [weak self] in
                    try? await self?.pollTunnelPeersOnce()
                }
            }
        }
    }

    func emitPickedMediaLoadFailure() {
        emitEvent(L10n.cannotLoadSelectedMedia)
    }

    func respondToPendingRequest(accept: Bool) {
        guard let request = pendingRequest else { return }
        let continuation = withLock { incomingDecisionContinuations.removeValue(forKey: request.id) }
        continuation?.resume(returning: accept)
        notifications.clearIncomingRequest()
        publish { self.pendingRequest = nil }
    }

    func cancelTransfer() {
        withLock {
            cancelRequested = true
        }
        let mode = withLock { activeTransferMode }
        let channel = withLock { activeTransferChannel }
        if mode == "receive", let channel {
            Task.detached {
                try? await ControlWire.writeMessage(channel: channel, payload: ["type": "CANCEL"])
            }
        }
    }

    func sendSelectedFiles(urls: [URL], useZIP: Bool) {
        let selectedPeer = self.selectedPeer()
        guard let peer = selectedPeer else {
            emitEvent(L10n.selectTargetFirst)
            return
        }
        guard !isBusy else {
            emitEvent(L10n.busyMessage)
            return
        }

        Task.detached { [weak self] in
            guard let self else { return }
            let documents = self.resolveSelectedDocuments(urls: urls)
            guard !documents.isEmpty else {
                self.updateBusy(false)
                self.emitEvent(L10n.cannotReadSelected)
                return
            }

            self.withLock { self.cancelRequested = false }
            self.updateBusy(true)

            let fileCount = documents.count
            let displayName = makeMultiDisplayName(documents.first?.displayName ?? "file", fileCount: fileCount)
            var tempZIPURL: URL?

            do {
                let payloadFilename: String
                let payloadSize: Int64

                if useZIP && fileCount > 1 {
                    let firstName = documents.first?.displayName ?? "files"
                    let zipBase = URL(fileURLWithPath: firstName).deletingPathExtension().lastPathComponent
                    let zipName = buildZIPFileName(baseName: zipBase, extraFileCount: fileCount - 1)
                    let zipURL = try self.createZIPArchive(documents: documents, zipName: zipName)
                    tempZIPURL = zipURL
                    payloadFilename = zipName
                    payloadSize = self.fileSize(at: zipURL)
                } else {
                    payloadFilename = documents.first?.displayName ?? "file.dat"
                    payloadSize = documents.reduce(0) { $0 + $1.sizeBytes }
                }

                let channel = try TCPChannel(host: peer.connectHost, port: peer.connectPort)
                try await channel.start()

                let requestPayload = compactJSONObject([
                    "type": "REQUEST_SEND",
                    "name": peer.isTunnel ? self.tunnelSubdomain : self.myName,
                    "ip": peer.isTunnel ? self.tunnelSubdomain : self.myIP,
                    "tunnel_name": peer.isTunnel ? self.tunnelSubdomain : nil,
                    "filename": payloadFilename,
                    "display_name": displayName,
                    "size": payloadSize,
                    "is_zip": useZIP && fileCount > 1,
                    "file_count": fileCount,
                ])
                try await ControlWire.writeMessage(channel: channel, payload: requestPayload)

                let response = try await ControlWire.readMessage(channel: channel)
                switch response?.string("type") {
                case "ACCEPT":
                    try await self.startOutgoingTransfer(
                        channel: channel,
                        documents: documents,
                        tempZIPURL: tempZIPURL,
                        displayName: displayName,
                        payloadFilename: payloadFilename,
                        payloadSize: payloadSize,
                        useZIP: useZIP && fileCount > 1
                    )
                case "REJECT":
                    self.emitEvent(L10n.otherRejected)
                    self.updateBusy(false)
                    channel.cancel()
                default:
                    self.emitEvent(L10n.noResponse)
                    self.updateBusy(false)
                    channel.cancel()
                }
            } catch {
                self.updateBusy(false)
                self.emitEvent(L10n.connectFailed(error.localizedDescription))
            }

            if let tempZIPURL {
                try? FileManager.default.removeItem(at: tempZIPURL)
            }
        }
    }

    func handleScenePhase(_ phase: ScenePhase) {
        if phase == .active {
            performStartupChecks()
            if mode == .lan {
                broadcastDiscovery()
            }
            if mode == .tunnel && !isBusy {
                resumeTunnelAfterActivation()
            }
        }
    }

    private func startLANServices() {
        udpDiscovery.startListening { [weak self] data in
            self?.handleDiscoveryPacket(data)
        }
        do {
            let listener = try TCPListenerService(port: dataPort, label: "com.rhkr8521.peersend.lan.listener")
            listener.start { [weak self] connection in
                guard let self else { return }
                Task.detached { [weak self] in
                    guard let self else { return }
                    try? await self.handleTransferConnection(channel: TCPChannel(existing: connection))
                }
            }
            lanListener = listener
        } catch {
            emitEvent(L10n.connectFailed(error.localizedDescription))
        }

        broadcastTask = Task.detached { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                self.broadcastDiscovery()
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }

        cleanupTask = Task.detached { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                self.cleanupPeers()
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
    }

    private func broadcastDiscovery() {
        let ip = NetworkInterfaceInfo.currentIPv4Address()
        publish { self.myIP = ip }
        let payload: [String: Any] = [
            "type": "DISCOVERY",
            "name": myName,
            "ip": ip,
            "port": Int(dataPort),
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return }
        udpDiscovery.broadcast(data)
    }

    private func handleDiscoveryPacket(_ data: Data) {
        guard
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            object.string("type") == "DISCOVERY",
            let ip = object.string("ip"),
            ip != myIP
        else {
            return
        }

        let peer = PeerDevice(
            id: ip,
            title: object.string("name") ?? "Unknown",
            addressLabel: ip,
            connectHost: ip,
            connectPort: UInt16(object.int("port") ?? Int(dataPort)),
            lastSeen: Date(),
            isTunnel: false
        )
        withLock {
            lanPeerStore[ip] = peer
        }
        syncLANPeers()
    }

    private func cleanupPeers() {
        let cutoff = Date().addingTimeInterval(-deviceTimeout)
        withLock {
            lanPeerStore = lanPeerStore.filter { $0.value.lastSeen >= cutoff }
        }
        syncLANPeers()
    }

    private func syncLANPeers() {
        let peers = withLock { lanPeerStore.values.sorted { lhs, rhs in
            if lhs.title == rhs.title {
                return lhs.addressLabel < rhs.addressLabel
            }
            return lhs.title < rhs.title
        } }
        let nextSelected = selectedLANPeerID.flatMap { id in peers.contains(where: { $0.id == id }) ? id : nil }
        publish {
            self.selectedLANPeerID = nextSelected
            self.lanPeers = peers
            if self.mode == .lan {
                self.selectedPeerID = nextSelected
            }
        }
    }

    private func syncTunnelPeers() {
        let peers = withLock { tunnelPeerStore.values.sorted { $0.title < $1.title } }
        let selected = selectedTunnelPeerID.flatMap { id in peers.contains(where: { $0.id == id }) ? id : nil }
        publish {
            self.selectedTunnelPeerID = selected
            self.tunnelPeers = peers
            if self.mode == .tunnel {
                self.selectedPeerID = selected
            }
        }
    }

    private func startTunnelIfConfigured() {
        guard hasActiveTunnelConfig() else {
            updateTunnelStatus(L10n.tunnelExternalRequired)
            return
        }
        if !tunnelStarted {
            startTunnelStackIfNeeded()
        } else if tunnelWebSocketTask == nil {
            reconnectTunnelWebSocket()
        }
    }

    private func resumeTunnelAfterActivation() {
        guard hasActiveTunnelConfig() else {
            updateTunnelStatus(L10n.tunnelExternalRequired)
            return
        }
        if !tunnelStarted {
            startTunnelStackIfNeeded()
            return
        }
        reconnectTunnelWebSocket()
    }

    private func startTunnelStackIfNeeded() {
        guard !tunnelStarted else { return }
        tunnelStarted = true
        startTunnelLocalTransferServer()
        startTunnelPolling()
        reconnectTunnelWebSocket()
    }

    private func startTunnelLocalTransferServer() {
        do {
            let listener = try TCPListenerService(port: localFilePort, label: "com.rhkr8521.peersend.tunnel.listener")
            listener.start { [weak self] connection in
                guard let self else { return }
                Task.detached { [weak self] in
                    guard let self else { return }
                    try? await self.handleTransferConnection(channel: TCPChannel(existing: connection))
                }
            }
            tunnelLocalListener = listener
        } catch {
            updateTunnelStatus(L10n.tunnelLocalServerFailed(error.localizedDescription))
        }
    }

    private func startTunnelPolling() {
        guard tunnelPollTask == nil else { return }
        tunnelPollTask = Task.detached { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                if self.tunnelRegistered {
                    try? await self.pollTunnelPeersOnce()
                }
                try? await Task.sleep(nanoseconds: UInt64(pollInterval * 1_000_000_000))
            }
        }
    }

    private func reconnectTunnelWebSocket() {
        guard tunnelStarted else { return }
        tunnelRegistered = false
        tunnelReceiveTask?.cancel()
        tunnelWebSocketTask?.cancel(with: .goingAway, reason: nil)
        connectTunnelWebSocket()
    }

    private func connectTunnelWebSocket() {
        guard let endpoints = buildTunnelEndpoints(hostPort: activeTunnelHost, useSSL: activeTunnelSSL) else {
            updateTunnelStatus(L10n.tunnelExternalRequired)
            return
        }

        let task = urlSession.webSocketTask(with: endpoints.wsURL)
        tunnelWebSocketTask = task
        updateTunnelStatus(L10n.tunnelWSConnecting)
        task.resume()

        tunnelReceiveTask = Task.detached { [weak self] in
            guard let self else { return }
            do {
                self.updateTunnelStatus(L10n.tunnelWSConnectedRegistering)
                try await self.sendWebSocketJSON([
                    "type": "register",
                    "subdomain": self.tunnelSubdomain,
                    "auth_token": self.activeTunnelToken,
                    "name": self.currentTunnelDisplayName(),
                    "display_name": self.currentTunnelDisplayName(),
                    "client_name": self.currentTunnelDisplayName(),
                    "metadata": [
                        "device_name": self.currentTunnelDisplayName(),
                        "display_name": self.currentTunnelDisplayName(),
                        "platform": "ios",
                    ],
                    "tcp_configs": [["name": tunnelTCPName, "remote_port": 0]],
                    "udp_configs": [],
                ])

                while !Task.isCancelled {
                    let message = try await task.receive()
                    switch message {
                    case .string(let text):
                        try await self.handleTunnelMessage(text)
                    case .data(let data):
                        if let text = String(data: data, encoding: .utf8) {
                            try await self.handleTunnelMessage(text)
                        }
                    @unknown default:
                        break
                    }
                }
            } catch {
                self.tunnelRegistered = false
                if self.tunnelWebSocketTask === task {
                    self.tunnelWebSocketTask = nil
                }
                self.updateTunnelStatus(L10n.tunnelWSFailed(error.localizedDescription))
            }
        }
    }

    private func handleTunnelMessage(_ text: String) async throws {
        guard
            let data = text.data(using: .utf8),
            let payload = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return
        }

        switch payload.string("type") {
        case "register_result":
            if payload.bool("ok") == true {
                tunnelRegistered = true
                var assignedPort = 0
                if let assigned = payload["tcp_assigned"] as? [[String: Any]] {
                    for entry in assigned where entry.string("name") == tunnelTCPName {
                        assignedPort = entry.int("remote_port") ?? 0
                        break
                    }
                }
                updateTunnelStatus(assignedPort > 0 ? L10n.tunnelRegisteredPort(assignedPort) : L10n.tunnelRegistered)
                try? await pollTunnelPeersOnce()
            } else {
                tunnelRegistered = false
                updateTunnelStatus(L10n.tunnelRegisterFailed(payload.string("reason") ?? L10n.unknownValue))
                tunnelWebSocketTask?.cancel(with: .normalClosure, reason: nil)
            }

        case "tcp_open":
            let name = payload.string("name")
            let streamID = payload.string("stream_id")
            guard name == tunnelTCPName, let streamID, !streamID.isEmpty else {
                try? await sendWebSocketJSON(["type": "tcp_close", "stream_id": streamID ?? "", "who": "client"])
                return
            }
            do {
                try await openTunnelStream(streamID: streamID)
            } catch {
                try? await sendWebSocketJSON(["type": "tcp_close", "stream_id": streamID, "who": "client"])
                emitEvent(L10n.tunnelStreamFailed(error.localizedDescription))
            }

        case "tcp_data":
            guard let streamID = payload.string("stream_id"),
                  let base64 = payload.string("b64"),
                  let bytes = Data(base64Encoded: base64) else {
                return
            }
            if let channel = withLock({ tunnelBridgeChannels[streamID] }) {
                try? await channel.send(bytes)
            } else {
                withLock {
                    pendingTunnelPackets[streamID, default: []].append(bytes)
                }
            }

        case "tcp_close":
            if let streamID = payload.string("stream_id") {
                closeTunnelStream(streamID: streamID)
            }

        default:
            break
        }
    }

    private func openTunnelStream(streamID: String) async throws {
        let channel = try TCPChannel(host: "127.0.0.1", port: localFilePort)
        try await channel.start()
        withLock {
            tunnelBridgeChannels[streamID] = channel
        }

        let queued = withLock { pendingTunnelPackets.removeValue(forKey: streamID) ?? [] }
        for packet in queued {
            try? await channel.send(packet)
        }

        let task = Task.detached { [weak self] in
            guard let self else { return }
            do {
                while let chunk = try await channel.receiveChunk(maximumLength: bufferSize) {
                    if chunk.isEmpty { continue }
                    try await self.sendWebSocketJSON([
                        "type": "tcp_data",
                        "stream_id": streamID,
                        "b64": chunk.base64EncodedString(),
                    ])
                }
            } catch {
            }
            try? await self.sendWebSocketJSON(["type": "tcp_close", "stream_id": streamID, "who": "client"])
            self.closeTunnelStream(streamID: streamID)
        }

        withLock {
            tunnelBridgeTasks[streamID] = task
        }
    }

    private func closeTunnelStream(streamID: String) {
        let channel = withLock { tunnelBridgeChannels.removeValue(forKey: streamID) }
        let task = withLock { tunnelBridgeTasks.removeValue(forKey: streamID) }
        withLock { pendingTunnelPackets.removeValue(forKey: streamID) }
        task?.cancel()
        channel?.cancel()
    }

    private func pollTunnelPeersOnce() async throws {
        guard let endpoints = buildTunnelEndpoints(hostPort: activeTunnelHost, useSSL: activeTunnelSSL) else {
            updateTunnelStatus(L10n.tunnelExternalRequired)
            return
        }

        var components = URLComponents(url: endpoints.adminBaseURL.appendingPathComponent("_health"), resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "token", value: activeTunnelToken)]
        guard let url = components?.url else { return }

        do {
            let (data, response) = try await urlSession.data(from: url)
            if let response = response as? HTTPURLResponse, !(200...299).contains(response.statusCode) {
                throw NSError(domain: "Tunnel", code: response.statusCode, userInfo: [NSLocalizedDescriptionKey: "HTTP \(response.statusCode)"])
            }
            guard
                let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                root.bool("ok") == true
            else {
                throw NSError(domain: "Tunnel", code: 1, userInfo: [NSLocalizedDescriptionKey: L10n.errorServerResponseInvalid])
            }

            var nextPeers: [String: PeerDevice] = [:]
            let tunnels = root["tunnels"] as? [String: Any] ?? [:]
            for (subdomain, rawInfo) in tunnels {
                guard subdomain != tunnelSubdomain,
                      let tunnelInfo = rawInfo as? [String: Any],
                      let tcp = tunnelInfo["tcp"] as? [String: Any]
                else {
                    continue
                }
                let remotePort = (tcp[tunnelTCPName] as? NSNumber)?.intValue ?? (tcp[tunnelTCPName] as? Int) ?? 0
                guard remotePort > 0 else { continue }
                let peerSubdomain = resolveTunnelPeerSubdomain(fallbackKey: subdomain, tunnelInfo: tunnelInfo)
                let displayTitle = resolveTunnelPeerTitle(subdomain: peerSubdomain, tunnelInfo: tunnelInfo)
                nextPeers[subdomain] = PeerDevice(
                    id: subdomain,
                    title: peerSubdomain,
                    addressLabel: displayTitle == peerSubdomain ? "port:\(remotePort)" : "\(displayTitle) / port:\(remotePort)",
                    connectHost: endpoints.tcpHost,
                    connectPort: UInt16(remotePort),
                    lastSeen: Date(),
                    isTunnel: true
                )
            }

            let now = Date()
            withLock {
                if !nextPeers.isEmpty {
                    tunnelPeerStore = nextPeers
                    lastTunnelPeerSnapshot = now
                } else if tunnelPeerStore.isEmpty || now.timeIntervalSince(lastTunnelPeerSnapshot) > 15 {
                    tunnelPeerStore.removeAll()
                }
            }
            syncTunnelPeers()
        } catch {
            updateTunnelStatus(L10n.tunnelPeerFetchFailed(error.localizedDescription))
        }
    }

    private func resolveTunnelPeerSubdomain(fallbackKey: String, tunnelInfo: [String: Any]) -> String {
        let candidates: [String?] = [
            tunnelInfo.string("subdomain"),
            tunnelInfo.string("requested_subdomain"),
            tunnelInfo.string("assigned_subdomain"),
            tunnelInfo.string("hostname"),
            tunnelInfo.string("host"),
            tunnelInfo.string("domain"),
            tunnelInfo.string("url"),
            findNestedTunnelSubdomain(tunnelInfo),
            fallbackKey,
        ]
        return candidates.compactMap(extractTunnelSubdomainCandidate).first(where: { !$0.isEmpty && $0 != tunnelTCPName }) ?? fallbackKey
    }

    private func resolveTunnelPeerTitle(subdomain: String, tunnelInfo: [String: Any]) -> String {
        [
            tunnelInfo.string("display_name"),
            tunnelInfo.string("name"),
            tunnelInfo.string("device_name"),
            tunnelInfo.string("client_name"),
            findNestedTunnelTitle(tunnelInfo),
            subdomain,
        ].compactMap { $0 }.first(where: { !$0.isEmpty && $0 != tunnelTCPName }) ?? subdomain
    }

    private func disconnectTunnel() {
        tunnelRegistered = false
        tunnelReceiveTask?.cancel()
        tunnelWebSocketTask?.cancel(with: .goingAway, reason: nil)
        tunnelWebSocketTask = nil
        withLock {
            for (_, task) in tunnelBridgeTasks { task.cancel() }
            for (_, channel) in tunnelBridgeChannels { channel.cancel() }
            tunnelBridgeTasks.removeAll()
            tunnelBridgeChannels.removeAll()
            pendingTunnelPackets.removeAll()
            tunnelPeerStore.removeAll()
            lastTunnelPeerSnapshot = .distantPast
        }
        syncTunnelPeers()
    }

    private func handleTransferConnection(channel: TCPChannel) async throws {
        try await channel.start()

        if isBusyAndActiveTransfer() {
            try? await ControlWire.writeMessage(channel: channel, payload: ["type": "REJECT"])
            channel.cancel()
            return
        }

        do {
            while let message = try await ControlWire.readMessage(channel: channel) {
                switch message.string("type") {
                case "REQUEST_SEND":
                    let accepted = await handleIncomingRequest(message)
                    try await ControlWire.writeMessage(channel: channel, payload: ["type": accepted ? "ACCEPT" : "REJECT"])
                    if !accepted {
                        channel.cancel()
                        return
                    }

                case "START_TRANSFER":
                    withLock {
                        cancelRequested = false
                        activeTransferMode = "receive"
                        activeTransferChannel = channel
                    }
                    let isZIP = message.bool("is_zip") == true
                    let fileCount = message.int("file_count") ?? 1
                    let totalSize = message.int64("size") ?? 0
                    let filename = message.string("filename") ?? "downloaded_file.dat"

                    if isZIP || fileCount <= 1 {
                        try await receiveSingleFile(
                            channel: channel,
                            filename: filename,
                            fileSize: totalSize,
                            isZIP: isZIP
                        )
                    } else {
                        try await receiveMultiFileTransfer(channel: channel, totalSize: totalSize, fileCount: fileCount)
                    }
                    return

                default:
                    break
                }
            }
        } catch {
            emitEvent(L10n.requestHandleFailed(error.localizedDescription))
        }

        clearActiveTransferIfNeeded(channel)
        channel.cancel()
    }

    private func handleIncomingRequest(_ message: [String: Any]) async -> Bool {
        let requestID = UUID().uuidString
        let request = IncomingTransferRequest(
            id: requestID,
            senderName: message.string("tunnel_name") ?? message.string("name") ?? L10n.unknownSender,
            senderAddress: message.string("tunnel_name") ?? message.string("ip") ?? "?",
            displayName: message.string("display_name") ?? message.string("filename") ?? L10n.genericFileLabel,
            totalBytes: message.int64("size") ?? 0,
            isZIP: message.bool("is_zip") == true,
            fileCount: message.int("file_count") ?? 1
        )

        notifications.showIncomingRequest(request)
        publish { self.pendingRequest = request }

        return await withCheckedContinuation { continuation in
            withLock {
                incomingDecisionContinuations[requestID] = continuation
            }
            Task.detached { [weak self] in
                try? await Task.sleep(nanoseconds: 120_000_000_000)
                guard let self else { return }
                let timedOutContinuation = self.withLock { self.incomingDecisionContinuations.removeValue(forKey: requestID) }
                guard let timedOutContinuation else { return }
                timedOutContinuation.resume(returning: false)
                self.notifications.clearIncomingRequest()
                self.publish {
                    if self.pendingRequest?.id == requestID {
                        self.pendingRequest = nil
                    }
                }
            }
        }
    }

    private func receiveSingleFile(channel: TCPChannel, filename: String, fileSize: Int64, isZIP: Bool) async throws {
        let scopedRoot: ScopedDirectory
        do {
            scopedRoot = try storage.accessCurrentRoot()
        } catch {
            channel.cancel()
            updateBusy(false)
            emitEvent(L10n.receiveFailed(error.localizedDescription))
            return
        }
        defer { scopedRoot.stopAccessing() }

        let saveRoot = scopedRoot.url
        let saveRootLabel = saveRoot.path
        let directURL = storage.createUniqueFileURL(named: filename, in: saveRoot)
        beginProgress(title: filename, totalBytes: fileSize, isReceiving: true, showItemProgress: false)

        var completed: Int64 = 0
        var interrupted = false
        var receiveError: String?

        do {
            guard FileManager.default.createFile(atPath: directURL.path, contents: nil) else {
                throw NSError(domain: "PeerSend", code: 20, userInfo: [NSLocalizedDescriptionKey: "Could not create \(directURL.lastPathComponent)"])
            }
            let handle = try FileHandle(forWritingTo: directURL)
            defer { try? handle.close() }

            while completed < fileSize && !withLock({ cancelRequested }) {
                let maxLength = Int(min(Int64(bufferSize), fileSize - completed))
                guard let chunk = try await channel.receiveChunk(maximumLength: maxLength), !chunk.isEmpty else {
                    interrupted = true
                    break
                }
                try handle.write(contentsOf: chunk)
                completed += Int64(chunk.count)
                updateProgress(
                    deltaBytes: Int64(chunk.count),
                    itemDone: completed,
                    itemTotal: fileSize,
                    itemLabel: L10n.receiveFileLabel(safeName(filename))
                )
            }
        } catch {
            receiveError = error.localizedDescription
        }

        clearActiveTransferIfNeeded(channel)
        channel.cancel()

        if withLock({ cancelRequested }) {
            storage.deleteItem(at: directURL)
            clearProgress()
            emitEvent(L10n.receiveCancelled)
            return
        }

        if let receiveError {
            storage.deleteItem(at: directURL)
            clearProgress()
            emitEvent(L10n.receiveFailed(receiveError))
            return
        }

        if interrupted || completed < fileSize {
            storage.deleteItem(at: directURL)
            clearProgress()
            emitEvent(L10n.senderCancelled)
            return
        }

        if isZIP {
            do {
                let directoryName = URL(fileURLWithPath: filename).deletingPathExtension().lastPathComponent
                let extractDir = storage.createUniqueDirectoryURL(named: directoryName, in: saveRoot)
                let extractedCount = try ZIPArchiveUtility.extractArchive(at: directURL, to: extractDir)
                storage.deleteItem(at: directURL)
                clearProgress()
                emitEvent(L10n.zipExtractComplete(extractedCount))
            } catch {
                clearProgress()
                emitEvent(L10n.zipExtractFailed(error.localizedDescription, saveRootLabel))
            }
        } else {
            clearProgress()
            emitEvent(L10n.receiveComplete(directURL.lastPathComponent))
        }
    }

    private func receiveMultiFileTransfer(channel: TCPChannel, totalSize: Int64, fileCount: Int) async throws {
        guard
            let metadata = try await ControlWire.readMessage(channel: channel),
            metadata.string("type") == "FILES_META",
            let files = metadata["files"] as? [[String: Any]],
            files.count == fileCount
        else {
            channel.cancel()
            updateBusy(false)
            emitEvent(L10n.multiMetaFailed)
            return
        }

        let scopedRoot: ScopedDirectory
        do {
            scopedRoot = try storage.accessCurrentRoot()
        } catch {
            channel.cancel()
            updateBusy(false)
            emitEvent(L10n.multiReceiveError(error.localizedDescription))
            return
        }
        defer { scopedRoot.stopAccessing() }

        let saveRoot = scopedRoot.url
        let saveRootLabel = saveRoot.path
        var savedURLs: [URL] = []
        var senderInterrupted = false
        var receiveError: String?

        beginProgress(title: L10n.transferTitleMultiFiles, totalBytes: totalSize, isReceiving: true, showItemProgress: true)

        do {
            for (index, item) in files.enumerated() {
                if withLock({ cancelRequested }) { break }
                let fileName = safeName(item.string("name") ?? "file_\(index + 1).dat")
                let fileSize = item.int64("size") ?? 0
                let targetURL = storage.createUniqueFileURL(named: fileName, in: saveRoot)
                savedURLs.append(targetURL)
                guard FileManager.default.createFile(atPath: targetURL.path, contents: nil) else {
                    throw NSError(domain: "PeerSend", code: 21, userInfo: [NSLocalizedDescriptionKey: "Could not create \(targetURL.lastPathComponent)"])
                }
                let handle = try FileHandle(forWritingTo: targetURL)
                var fileDone: Int64 = 0

                while fileDone < fileSize && !withLock({ cancelRequested }) {
                    let maxLength = Int(min(Int64(bufferSize), fileSize - fileDone))
                    guard let chunk = try await channel.receiveChunk(maximumLength: maxLength), !chunk.isEmpty else {
                        senderInterrupted = true
                        break
                    }
                    try handle.write(contentsOf: chunk)
                    fileDone += Int64(chunk.count)
                    updateProgress(
                        deltaBytes: Int64(chunk.count),
                        itemDone: fileDone,
                        itemTotal: fileSize,
                        itemLabel: L10n.receiveFileIndexLabel(fileName, index + 1, fileCount)
                    )
                }

                try? handle.close()

                if !withLock({ cancelRequested }) && !senderInterrupted && fileDone < fileSize {
                    senderInterrupted = true
                }
                if withLock({ cancelRequested }) || senderInterrupted {
                    break
                }
            }
        } catch {
            receiveError = error.localizedDescription
        }

        clearActiveTransferIfNeeded(channel)
        channel.cancel()

        if withLock({ cancelRequested }) {
            savedURLs.forEach(storage.deleteItem(at:))
            clearProgress()
            emitEvent(L10n.receiveCancelled)
            return
        }

        if let receiveError {
            savedURLs.forEach(storage.deleteItem(at:))
            clearProgress()
            emitEvent(L10n.multiReceiveError(receiveError))
            return
        }

        if senderInterrupted {
            savedURLs.forEach(storage.deleteItem(at:))
            clearProgress()
            emitEvent(L10n.senderCancelled)
            return
        }

        clearProgress()
        emitEvent(L10n.multiReceiveComplete(savedURLs.count))
    }

    private func startOutgoingTransfer(
        channel: TCPChannel,
        documents: [SelectedDocument],
        tempZIPURL: URL?,
        displayName: String,
        payloadFilename: String,
        payloadSize: Int64,
        useZIP: Bool
    ) async throws {
        withLock {
            activeTransferMode = "send"
            activeTransferChannel = channel
            cancelRequested = false
        }
        beginProgress(title: displayName, totalBytes: payloadSize, isReceiving: false, showItemProgress: !(useZIP || documents.count <= 1))

        let cancelListener = Task.detached { [weak self] in
            guard let self else { return }
            do {
                while !Task.isCancelled && !self.withLock({ self.cancelRequested }) {
                    guard let message = try await ControlWire.readMessage(channel: channel) else { break }
                    if message.string("type") == "CANCEL" {
                        self.withLock { self.cancelRequested = true }
                        self.emitEvent(L10n.remoteCancelled)
                        break
                    }
                }
            } catch {
            }
        }

        defer {
            cancelListener.cancel()
            withLock {
                activeTransferMode = nil
                activeTransferChannel = nil
            }
            channel.cancel()
            clearProgress()
        }

        try await ControlWire.writeMessage(channel: channel, payload: [
            "type": "START_TRANSFER",
            "filename": payloadFilename,
            "size": payloadSize,
            "is_zip": useZIP,
            "file_count": documents.count,
        ])

        if useZIP {
            guard let zipURL = tempZIPURL else {
                throw NSError(domain: "PeerSend", code: 1, userInfo: [NSLocalizedDescriptionKey: L10n.missingZIP])
            }
            try await sendBinaryFile(fileURL: zipURL, channel: channel, itemLabel: L10n.sendZipLabel(zipURL.lastPathComponent))
        } else if documents.count == 1, let document = documents.first {
            try await sendDocument(document, channel: channel, itemLabel: L10n.sendFileLabel(document.displayName))
        } else {
            try await ControlWire.writeMessage(channel: channel, payload: [
                "type": "FILES_META",
                "files": documents.map { ["name": $0.displayName, "size": $0.sizeBytes] },
            ])
            for (index, document) in documents.enumerated() {
                if withLock({ cancelRequested }) { break }
                try await sendDocument(document, channel: channel, itemLabel: L10n.sendFileIndexLabel(document.displayName, index + 1, documents.count))
            }
        }

        if withLock({ cancelRequested }) {
            emitEvent(L10n.transferCancelled)
        } else {
            notifications.showTransferStatus(title: "PeerSend", body: L10n.transferComplete(displayName))
            emitEvent(L10n.transferComplete(displayName))
        }
    }

    private func sendBinaryFile(fileURL: URL, channel: TCPChannel, itemLabel: String) async throws {
        let totalSize = fileSize(at: fileURL)
        try await streamFile(
            totalSize: totalSize,
            itemLabel: itemLabel,
            openStream: { try InputStream(url: fileURL).unwrap(or: L10n.openInputFailed(fileURL.lastPathComponent)) },
            channel: channel
        )
    }

    private func sendDocument(_ document: SelectedDocument, channel: TCPChannel, itemLabel: String) async throws {
        let started = document.requiresSecurityScope ? document.url.startAccessingSecurityScopedResource() : false
        defer {
            if started { document.url.stopAccessingSecurityScopedResource() }
        }

        try await streamFile(
            totalSize: document.sizeBytes,
            itemLabel: itemLabel,
            openStream: { try InputStream(url: document.url).unwrap(or: L10n.openInputFailed(document.displayName)) },
            channel: channel
        )
    }

    private func streamFile(
        totalSize: Int64,
        itemLabel: String,
        openStream: () throws -> InputStream,
        channel: TCPChannel
    ) async throws {
        let stream = try openStream()
        stream.open()
        defer { stream.close() }

        var buffer = [UInt8](repeating: 0, count: bufferSize)
        var itemDone: Int64 = 0

        while itemDone < totalSize && !withLock({ cancelRequested }) {
            let maxRead = Int(min(Int64(bufferSize), totalSize - itemDone))
            let read = stream.read(&buffer, maxLength: maxRead)
            if read <= 0 { break }
            let chunk = Data(buffer[0..<read])
            try await channel.send(chunk)
            itemDone += Int64(read)
            updateProgress(
                deltaBytes: Int64(read),
                itemDone: itemDone,
                itemTotal: totalSize,
                itemLabel: itemLabel
            )
        }
    }

    private func createZIPArchive(documents: [SelectedDocument], zipName: String) throws -> URL {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        let stagingDir = tempDir.appendingPathComponent("payload", isDirectory: true)
        try FileManager.default.createDirectory(at: stagingDir, withIntermediateDirectories: true)
        let zipURL = tempDir.appendingPathComponent(zipName)

        let totalSize = documents.reduce(0) { $0 + $1.sizeBytes }
        beginProgress(title: L10n.transferTitleZIP, totalBytes: totalSize * 2, isReceiving: false, showItemProgress: true)

        do {
            for (index, document) in documents.enumerated() {
                if withLock({ cancelRequested }) {
                    throw NSError(domain: "PeerSend", code: 2, userInfo: [NSLocalizedDescriptionKey: L10n.transferCancelled])
                }

                let targetURL = storage.createUniqueFileURL(named: document.displayName, in: stagingDir)
                let started = document.requiresSecurityScope ? document.url.startAccessingSecurityScopedResource() : false
                defer {
                    if started { document.url.stopAccessingSecurityScopedResource() }
                }

                guard let input = InputStream(url: document.url) else {
                    throw NSError(domain: "PeerSend", code: 3, userInfo: [NSLocalizedDescriptionKey: L10n.openInputFailed(document.displayName)])
                }
                FileManager.default.createFile(atPath: targetURL.path, contents: nil)
                let output = try FileHandle(forWritingTo: targetURL)
                input.open()
                var buffer = [UInt8](repeating: 0, count: bufferSize)
                var fileDone: Int64 = 0

                while !withLock({ cancelRequested }) {
                    let read = input.read(&buffer, maxLength: bufferSize)
                    if read <= 0 { break }
                    try output.write(contentsOf: Data(buffer[0..<read]))
                    fileDone += Int64(read)
                    updateProgress(
                        deltaBytes: Int64(read),
                        itemDone: fileDone,
                        itemTotal: document.sizeBytes,
                        itemLabel: L10n.zippingLabel(document.displayName, index + 1, documents.count)
                    )
                }

                input.close()
                try? output.close()
            }

            let entries = try FileManager.default.contentsOfDirectory(at: stagingDir, includingPropertiesForKeys: nil)
                .filter { !$0.hasDirectoryPath }
                .map { ZIPArchiveUtility.Entry(displayName: $0.lastPathComponent, fileURL: $0) }
            try ZIPArchiveUtility.createArchive(at: zipURL, entries: entries) { [weak self] entry, index, totalEntries, deltaBytes, itemDone, itemTotal in
                guard let self else { return }
                self.updateProgress(
                    deltaBytes: deltaBytes,
                    itemDone: itemDone,
                    itemTotal: itemTotal,
                    itemLabel: L10n.zippingLabel(entry.displayName, index + 1, totalEntries)
                )
            }
            clearProgress()
            return zipURL
        } catch {
            clearProgress()
            updateBusy(false)
            throw error
        }
    }

    private func resolveSelectedDocuments(urls: [URL]) -> [SelectedDocument] {
        urls.compactMap { url in
            let needsSecurityScope = url.startAccessingSecurityScopedResource()
            defer {
                if needsSecurityScope {
                    url.stopAccessingSecurityScopedResource()
                }
            }
            let values = try? url.resourceValues(forKeys: [.fileSizeKey, .nameKey, .isRegularFileKey])
            guard values?.isRegularFile != false else { return nil }
            let displayName = safeName(values?.name ?? url.lastPathComponent)
            let size = Int64(values?.fileSize ?? 0)
            let measuredSize = size > 0 ? size : measureFileSize(url)
            guard measuredSize > 0 else { return nil }
            return SelectedDocument(
                url: url,
                displayName: displayName,
                sizeBytes: measuredSize,
                requiresSecurityScope: needsSecurityScope
            )
        }
    }

    private func measureFileSize(_ url: URL) -> Int64 {
        guard let stream = InputStream(url: url) else { return -1 }
        let started = url.startAccessingSecurityScopedResource()
        defer {
            if started { url.stopAccessingSecurityScopedResource() }
        }

        stream.open()
        defer { stream.close() }
        var buffer = [UInt8](repeating: 0, count: bufferSize)
        var total: Int64 = 0
        while true {
            let read = stream.read(&buffer, maxLength: bufferSize)
            if read <= 0 { break }
            total += Int64(read)
        }
        return total
    }

    private func selectedPeer() -> PeerDevice? {
        guard let id = selectedPeerID else { return nil }
        let peers = mode == .lan ? lanPeers : tunnelPeers
        return peers.first(where: { $0.id == id })
    }

    private func hasActiveTunnelConfig() -> Bool {
        activeUsePublicTunnel || (!activeTunnelHost.isEmpty && !activeTunnelToken.isEmpty)
    }

    private func currentTunnelDisplayName() -> String {
        baseDeviceName.isEmpty ? tunnelSubdomain : baseDeviceName
    }

    private func updateTunnelStatus(_ status: String) {
        publish { self.tunnelStatus = status }
    }

    private func updateBusy(_ busy: Bool) {
        publish { self.isBusy = busy }
    }

    private func beginProgress(title: String, totalBytes: Int64, isReceiving: Bool, showItemProgress: Bool) {
        withLock {
            progressStartTime = Date()
            progressTransferredBytes = 0
            progressTotalBytes = max(totalBytes, 0)
            cancelRequested = false
        }
        beginBackgroundTask()
        publish {
            self.isBusy = true
            self.transferProgress = TransferProgressUI(
                title: title,
                itemLabel: isReceiving ? L10n.preparingReceive : L10n.preparingSend,
                totalBytes: totalBytes,
                transferredBytes: 0,
                itemBytes: 0,
                itemTransferredBytes: 0,
                speedBytesPerSecond: 0,
                remainingSeconds: 0,
                isReceiving: isReceiving,
                showItemProgress: showItemProgress
            )
        }
    }

    private func updateProgress(deltaBytes: Int64, itemDone: Int64, itemTotal: Int64, itemLabel: String) {
        let snapshot = withLock { () -> (Double, Double, Int64) in
            progressTransferredBytes += deltaBytes
            let elapsed = max(Date().timeIntervalSince(progressStartTime), 1)
            let bytesPerSecond = Double(progressTransferredBytes) / elapsed
            let remaining = bytesPerSecond > 0 ? Double(max(progressTotalBytes - progressTransferredBytes, 0)) / bytesPerSecond : 0
            return (bytesPerSecond, remaining, progressTransferredBytes)
        }

        publish {
            guard let progress = self.transferProgress else { return }
            self.transferProgress = TransferProgressUI(
                title: progress.title,
                itemLabel: itemLabel,
                totalBytes: progress.totalBytes,
                transferredBytes: snapshot.2,
                itemBytes: itemTotal,
                itemTransferredBytes: itemDone,
                speedBytesPerSecond: snapshot.0,
                remainingSeconds: snapshot.1,
                isReceiving: progress.isReceiving,
                showItemProgress: progress.showItemProgress
            )
        }
    }

    private func clearProgress() {
        withLock {
            progressTransferredBytes = 0
            progressTotalBytes = 0
        }
        endBackgroundTask()
        publish {
            self.transferProgress = nil
            self.isBusy = false
        }
    }

    private func clearActiveTransferIfNeeded(_ channel: TCPChannel) {
        withLock {
            if activeTransferChannel === channel {
                activeTransferChannel = nil
                activeTransferMode = nil
            }
        }
    }

    private func isBusyAndActiveTransfer() -> Bool {
        withLock {
            isBusy && activeTransferMode != nil
        }
    }

    private func emitEvent(_ message: String) {
        publish { self.eventMessage = message }
        Task.detached { [weak self] in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            guard let self else { return }
            self.publish {
                if self.eventMessage == message {
                    self.eventMessage = nil
                }
            }
        }
    }

    private func fileSize(at url: URL) -> Int64 {
        (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map(Int64.init) ?? 0
    }

    private func countFiles(in directory: URL) -> Int {
        let enumerator = FileManager.default.enumerator(at: directory, includingPropertiesForKeys: [.isRegularFileKey])
        var count = 0
        while let fileURL = enumerator?.nextObject() as? URL {
            let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey])
            if values?.isRegularFile == true {
                count += 1
            }
        }
        return count
    }

    private func sendWebSocketJSON(_ payload: [String: Any]) async throws {
        guard let task = tunnelWebSocketTask else { throw URLError(.networkConnectionLost) }
        let data = try JSONSerialization.data(withJSONObject: payload)
        guard let text = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "PeerSend", code: 9, userInfo: [NSLocalizedDescriptionKey: L10n.genericError])
        }
        try await task.send(.string(text))
    }

    private func publish(_ block: @escaping () -> Void) {
        if Thread.isMainThread {
            block()
        } else {
            DispatchQueue.main.async(execute: block)
        }
    }

    private func withLock<T>(_ block: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return block()
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func beginBackgroundTask() {
        DispatchQueue.main.async {
            guard self.backgroundTaskIdentifier == .invalid else { return }
            self.backgroundTaskIdentifier = UIApplication.shared.beginBackgroundTask(withName: "PeerSendTransfer") {
                self.endBackgroundTask()
            }
        }
    }

    private func endBackgroundTask() {
        DispatchQueue.main.async {
            guard self.backgroundTaskIdentifier != .invalid else { return }
            UIApplication.shared.endBackgroundTask(self.backgroundTaskIdentifier)
            self.backgroundTaskIdentifier = .invalid
        }
    }

    private static func generateLANNameSuffix() -> String {
        let chars = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
        return String((0..<3).map { _ in chars.randomElement() ?? "A" })
    }
}

private extension Optional {
    func unwrap(or message: String) throws -> Wrapped {
        guard let value = self else {
            throw NSError(domain: "PeerSend", code: 10, userInfo: [NSLocalizedDescriptionKey: message])
        }
        return value
    }
}

private extension String {
    func also(_ block: (String) -> Void) -> String {
        block(self)
        return self
    }
}
