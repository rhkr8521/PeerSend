import SwiftUI
import UniformTypeIdentifiers
import PhotosUI
import UIKit

struct ContentView: View {
    private enum PendingSendSourceAction {
        case files
        case photos
    }

    @StateObject private var viewModel = PeerSendViewModel()
    @Environment(\.scenePhase) private var scenePhase

    @State private var showHelp = false
    @State private var showSendSourceDialog = false
    @State private var showDocumentPicker = false
    @State private var showFolderImporter = false
    @State private var showPhotoPicker = false
    @State private var photoPickerItems: [PhotosPickerItem] = []
    @State private var pendingPickedURLs: [URL] = []
    @State private var pendingSendSourceAction: PendingSendSourceAction?

    private var usesPadModalDialogs: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }

    private var currentPeers: [PeerDevice] {
        viewModel.mode == .lan ? viewModel.lanPeers : viewModel.tunnelPeers
    }

    var body: some View {
        ZStack {
            mainScrollContent
            overlayContent
        }
        .fileImporter(
            isPresented: $showFolderImporter,
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            guard case .success(let urls) = result, let url = urls.first else { return }
            viewModel.rememberSaveDirectory(url)
        }
        .task {
            viewModel.performStartupChecks()
        }
        .photosPicker(
            isPresented: $showPhotoPicker,
            selection: $photoPickerItems,
            maxSelectionCount: nil,
            matching: .any(of: [.images, .videos])
        )
        .sheet(
            isPresented: Binding(
                get: { !usesPadModalDialogs && showSendSourceDialog },
                set: { showSendSourceDialog = $0 }
            ),
            onDismiss: handlePendingSendSourceAction
        ) {
            SendSourceSheet(
                onChooseFiles: {
                    completeSendSourceSelection(.files)
                },
                onChoosePhotos: {
                    completeSendSourceSelection(.photos)
                }
            )
            .presentationDetents([.height(220)])
            .presentationDragIndicator(.visible)
        }
        .sheet(
            isPresented: Binding(
                get: { !usesPadModalDialogs && !pendingPickedURLs.isEmpty },
                set: { isPresented in
                    if !isPresented {
                        pendingPickedURLs = []
                    }
                }
            )
        ) {
            ZipChoiceSheet(
                count: pendingPickedURLs.count,
                onCancel: { pendingPickedURLs = [] },
                onSendZIP: {
                    viewModel.sendSelectedFiles(urls: pendingPickedURLs, useZIP: true)
                    pendingPickedURLs = []
                },
                onSendIndividually: {
                    viewModel.sendSelectedFiles(urls: pendingPickedURLs, useZIP: false)
                    pendingPickedURLs = []
                }
            )
            .presentationDetents([.height(228)])
            .presentationDragIndicator(.visible)
        }
        .sheet(
            item: Binding(
                get: { usesPadModalDialogs ? nil : viewModel.pendingRequest },
                set: { newValue in
                    if newValue == nil, viewModel.pendingRequest != nil {
                        viewModel.respondToPendingRequest(accept: false)
                    }
                }
            )
        ) { request in
            IncomingRequestSheet(
                request: request,
                onReceive: { viewModel.respondToPendingRequest(accept: true) },
                onReject: { viewModel.respondToPendingRequest(accept: false) }
            )
            .presentationDetents([.height(260)])
            .presentationDragIndicator(.visible)
            .interactiveDismissDisabled()
        }
        .sheet(isPresented: $showDocumentPicker) {
            SendDocumentPicker { urls in
                guard !urls.isEmpty else { return }
                handlePickedTransferURLs(urls)
            }
        }
        .onChange(of: photoPickerItems) { items in
            guard !items.isEmpty else { return }
            Task {
                let urls = await PhotoPickerTransferLoader.loadURLs(from: items)
                await MainActor.run {
                    photoPickerItems = []
                    guard !urls.isEmpty else {
                        viewModel.emitPickedMediaLoadFailure()
                        return
                    }
                    handlePickedTransferURLs(urls)
                }
            }
        }
        .onChange(of: scenePhase) { newValue in
            viewModel.handleScenePhase(newValue)
        }
    }

    private var mainScrollContent: some View {
        ScrollView {
            VStack(spacing: 14) {
                HeroSection(
                    mode: viewModel.mode,
                    myName: viewModel.myName,
                    tunnelSubdomain: viewModel.tunnelSubdomain,
                    myIP: viewModel.myIP,
                    tunnelStatus: viewModel.tunnelStatus,
                    onToggleMode: viewModel.requestToggleMode,
                    onRefresh: viewModel.manualRefresh,
                    onShowHelp: { showHelp = true }
                )

                ActionSection(
                    saveLocationLabel: viewModel.saveLocationLabel,
                    canSend: viewModel.selectedPeerID != nil && !viewModel.isBusy,
                    onChooseSaveFolder: { showFolderImporter = true },
                    onOpenFilePicker: { showSendSourceDialog = true }
                )

                tunnelSettingsContent

                PeerListCard(
                    mode: viewModel.mode,
                    peers: currentPeers,
                    selectedPeerID: viewModel.selectedPeerID,
                    onSelectPeer: viewModel.selectPeer
                )

                if let progress = viewModel.transferProgress {
                    ProgressCard(progress: progress, onCancelTransfer: viewModel.cancelTransfer)
                }
            }
            .padding(16)
        }
        .background(
            LinearGradient(
                colors: [.appBackgroundTop, .appBackgroundBottom],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        )
    }

    @ViewBuilder
    private var tunnelSettingsContent: some View {
        if viewModel.mode == .tunnel {
            TunnelSettingsCard(
                usePublicTunnel: viewModel.usePublicTunnel,
                tunnelHost: viewModel.tunnelHost,
                tunnelToken: viewModel.tunnelToken,
                tunnelSSL: viewModel.tunnelSSL,
                tunnelStatus: viewModel.tunnelStatus,
                onUsePublicTunnelChanged: viewModel.updateUsePublicTunnel,
                onTunnelHostChanged: viewModel.updateTunnelHost,
                onTunnelTokenChanged: viewModel.updateTunnelToken,
                onTunnelSSLChanged: viewModel.updateTunnelSSL,
                onApply: viewModel.applyTunnelSettings
            )
        }
    }

    @ViewBuilder
    private var overlayContent: some View {
        if let message = viewModel.eventMessage {
            ToastView(message: message)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, 28)
                .transition(.move(edge: .bottom).combined(with: .opacity))
        }

        if usesPadModalDialogs && showSendSourceDialog {
            OverlayModalBackdrop(onDismiss: { showSendSourceDialog = false }) {
                SendSourceSheet(
                    inDialog: true,
                    onChooseFiles: { completeSendSourceSelection(.files) },
                    onChoosePhotos: { completeSendSourceSelection(.photos) }
                )
            }
        }

        if usesPadModalDialogs && !pendingPickedURLs.isEmpty {
            OverlayModalBackdrop(onDismiss: { pendingPickedURLs = [] }) {
                ZipChoiceSheet(
                    inDialog: true,
                    count: pendingPickedURLs.count,
                    onCancel: { pendingPickedURLs = [] },
                    onSendZIP: {
                        viewModel.sendSelectedFiles(urls: pendingPickedURLs, useZIP: true)
                        pendingPickedURLs = []
                    },
                    onSendIndividually: {
                        viewModel.sendSelectedFiles(urls: pendingPickedURLs, useZIP: false)
                        pendingPickedURLs = []
                    }
                )
            }
        }

        if usesPadModalDialogs, let request = viewModel.pendingRequest {
            ModalBackdrop {
                IncomingRequestSheet(
                    inDialog: true,
                    request: request,
                    onReceive: { viewModel.respondToPendingRequest(accept: true) },
                    onReject: { viewModel.respondToPendingRequest(accept: false) }
                )
            }
        }

        if showHelp {
            ModalBackdrop {
                HelpDialog(onClose: { showHelp = false })
            }
        }

        if let prompt = viewModel.blockingPrompt {
            ModalBackdrop {
                switch prompt {
                case .notificationPermission:
                    BlockingDialog(
                        title: L10n.notificationPermissionTitle,
                        message: L10n.notificationPermissionBody,
                        primaryTitle: L10n.notificationPermissionAllow,
                        primaryAction: viewModel.requestNotificationPermission
                    )
                case .updateRequired(let info):
                    BlockingDialog(
                        title: L10n.updateRequiredTitle,
                        message: L10n.updateRequiredBody,
                        primaryTitle: L10n.updateNow,
                        primaryAction: { viewModel.openAppStoreUpdate(info) }
                    )
                case .initialTunnelChoice:
                    InitialTunnelChoiceDialog(
                        onChoosePublic: { viewModel.rememberInitialTunnelChoice(usePublic: true) },
                        onChooseExternal: { viewModel.rememberInitialTunnelChoice(usePublic: false) }
                    )
                }
            }
        }
    }

    private func handlePickedTransferURLs(_ urls: [URL]) {
        if urls.count == 1 {
            viewModel.sendSelectedFiles(urls: urls, useZIP: false)
        } else {
            pendingPickedURLs = urls
        }
    }

    private func presentFilesPicker() {
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 80_000_000)
            showDocumentPicker = true
        }
    }

    private func presentPhotoPicker() {
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 80_000_000)
            showPhotoPicker = true
        }
    }

    private func handlePendingSendSourceAction() {
        guard let action = pendingSendSourceAction else { return }
        pendingSendSourceAction = nil
        switch action {
        case .files:
            presentFilesPicker()
        case .photos:
            presentPhotoPicker()
        }
    }

    private func completeSendSourceSelection(_ action: PendingSendSourceAction) {
        pendingSendSourceAction = action
        showSendSourceDialog = false
        if usesPadModalDialogs {
            handlePendingSendSourceAction()
        }
    }
}

private struct SendDocumentPicker: UIViewControllerRepresentable {
    let onPick: ([URL]) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPick: onPick)
    }

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(
            forOpeningContentTypes: [.item, .content, .data, .image, .movie],
            asCopy: false
        )
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = true
        picker.shouldShowFileExtensions = true
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {
    }

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        private let onPick: ([URL]) -> Void

        init(onPick: @escaping ([URL]) -> Void) {
            self.onPick = onPick
        }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            onPick(urls)
        }

        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            onPick([])
        }
    }
}

private struct SendSourceSheet: View {
    let inDialog: Bool
    let onChooseFiles: () -> Void
    let onChoosePhotos: () -> Void

    init(
        inDialog: Bool = false,
        onChooseFiles: @escaping () -> Void,
        onChoosePhotos: @escaping () -> Void
    ) {
        self.inDialog = inDialog
        self.onChooseFiles = onChooseFiles
        self.onChoosePhotos = onChoosePhotos
    }

    var body: some View {
        SheetSurface(inDialog: inDialog) {
            VStack(alignment: .leading, spacing: 18) {
                Text(L10n.sendSourceTitle)
                    .font(.headline)
                    .foregroundStyle(Color.appPrimaryText)

                Text(L10n.sendSourceBody)
                    .font(.body)
                    .foregroundStyle(Color.appSecondaryText)

                VStack(spacing: 10) {
                    PrimaryButton(title: L10n.chooseFromFiles, enabled: true, action: onChooseFiles)
                    SecondaryButton(title: L10n.chooseFromPhotos, action: onChoosePhotos)
                }
            }
        }
    }
}

private struct HeroSection: View {
    let mode: TransferMode
    let myName: String
    let tunnelSubdomain: String
    let myIP: String
    let tunnelStatus: String
    let onToggleMode: () -> Void
    let onRefresh: () -> Void
    let onShowHelp: () -> Void

    private var displayedDeviceName: String {
        mode == .tunnel && !tunnelSubdomain.isEmpty ? tunnelSubdomain : myName
    }

    private var deviceFont: Font {
        if displayedDeviceName.count >= 24 { return .caption2 }
        if displayedDeviceName.count >= 16 { return .caption }
        return .subheadline.weight(.semibold)
    }

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("PeerSend")
                        .font(.title3.bold())
                    Spacer()
                    Button(L10n.help, action: onShowHelp)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 8)
                        .contentShape(Rectangle())
                        .buttonStyle(.plain)
                        .foregroundStyle(Color.appAccent)
                }

                HStack(spacing: 10) {
                    PillView(text: "\(L10n.deviceNamePrefix) \(displayedDeviceName)", font: deviceFont)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    PillView(text: "\(L10n.ipPrefix) \(myIP)", font: .subheadline.weight(.semibold))
                }

                HStack(spacing: 10) {
                    ModeButton(title: L10n.modeLAN, selected: mode == .lan) {
                        if mode != .lan { onToggleMode() }
                    }
                    ModeButton(title: L10n.modeTunnel, selected: mode == .tunnel) {
                        if mode != .tunnel { onToggleMode() }
                    }
                    SecondaryButton(title: L10n.refresh, action: onRefresh)
                }

                Text(mode == .lan ? L10n.lanSearching : tunnelStatus)
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
            }
        }
    }
}

private struct ActionSection: View {
    let saveLocationLabel: String
    let canSend: Bool
    let onChooseSaveFolder: () -> Void
    let onOpenFilePicker: () -> Void

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Text(L10n.actionSectionTitle)
                    .font(.headline)
                Text(saveLocationLabel)
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                    .lineLimit(2)

                HStack(spacing: 10) {
                    SecondaryButton(title: L10n.saveFolder, action: onChooseSaveFolder)
                    PrimaryButton(title: L10n.sendFiles, enabled: canSend, action: onOpenFilePicker)
                }
            }
        }
    }
}

private struct TunnelSettingsCard: View {
    let usePublicTunnel: Bool
    let tunnelHost: String
    let tunnelToken: String
    let tunnelSSL: Bool
    let tunnelStatus: String
    let onUsePublicTunnelChanged: (Bool) -> Void
    let onTunnelHostChanged: (String) -> Void
    let onTunnelTokenChanged: (String) -> Void
    let onTunnelSSLChanged: (Bool) -> Void
    let onApply: () -> Void

    @State private var expanded = false

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Button {
                    expanded.toggle()
                } label: {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(L10n.tunnelSettings)
                                .font(.headline)
                                .foregroundStyle(Color.appPrimaryText)
                            Text(usePublicTunnel ? L10n.publicTunnelSummary : (tunnelHost.isEmpty ? L10n.externalTunnelSummary : tunnelHost))
                                .font(.footnote)
                                .foregroundStyle(Color.appSecondaryText)
                                .lineLimit(1)
                            Text(tunnelStatus)
                                .font(.footnote)
                                .foregroundStyle(Color.appAccent)
                                .lineLimit(1)
                        }
                        Spacer()
                        Image(systemName: expanded ? "chevron.up" : "chevron.down")
                            .foregroundStyle(Color.appSecondaryText)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if expanded {
                    Divider()
                    Toggle(L10n.publicTunnelAccess, isOn: Binding(
                        get: { usePublicTunnel },
                        set: onUsePublicTunnelChanged
                    ))

                    if usePublicTunnel {
                        Text(L10n.publicTunnelSummary)
                            .font(.footnote)
                            .foregroundStyle(Color.appSecondaryText)
                    } else {
                        VStack(spacing: 10) {
                            TextField(L10n.tunnelServerAddress, text: Binding(
                                get: { tunnelHost },
                                set: onTunnelHostChanged
                            ))
                            .textFieldStyle(.roundedBorder)

                            SecureField(L10n.tunnelToken, text: Binding(
                                get: { tunnelToken },
                                set: onTunnelTokenChanged
                            ))
                            .textFieldStyle(.roundedBorder)

                            Toggle(L10n.sslEnabled, isOn: Binding(
                                get: { tunnelSSL },
                                set: onTunnelSSLChanged
                            ))
                        }
                    }

                    PrimaryButton(title: L10n.applyReconnect, enabled: true, action: onApply)
                }
            }
        }
    }
}

private struct PeerListCard: View {
    let mode: TransferMode
    let peers: [PeerDevice]
    let selectedPeerID: String?
    let onSelectPeer: (String) -> Void

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 10) {
                Text(mode == .lan ? L10n.peerListLAN : L10n.peerListTunnel)
                    .font(.headline)

                if peers.isEmpty {
                    Text(mode == .lan ? L10n.noLANDevices : L10n.noTunnelPeers)
                        .font(.body)
                        .foregroundStyle(Color.appSecondaryText)
                } else {
                    ForEach(Array(peers.enumerated()), id: \.element.id) { index, peer in
                        PeerRow(peer: peer, selected: peer.id == selectedPeerID) {
                            onSelectPeer(peer.id)
                        }
                        if index != peers.count - 1 {
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

private struct PeerRow: View {
    let peer: PeerDevice
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected ? Color.appAccent : Color.appSecondaryText)
                    .font(.title3)

                VStack(alignment: .leading, spacing: 4) {
                    Text(peer.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.appPrimaryText)
                    Text(peer.addressLabel)
                        .font(.footnote)
                        .foregroundStyle(Color.appSecondaryText)
                }

                Spacer()
                PillView(text: peer.isTunnel ? L10n.tunnelBadge : L10n.lanBadge, font: .caption.weight(.semibold))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct ProgressCard: View {
    let progress: TransferProgressUI
    let onCancelTransfer: () -> Void

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 10) {
                Text(progress.title)
                    .font(.headline)
                Text(progress.itemLabel)
                    .font(.body)
                    .lineLimit(2)

                if progress.showItemProgress {
                    ProgressView(value: fraction(progress.itemTransferredBytes, progress.itemBytes))
                    Text("\(humanReadableSize(progress.itemTransferredBytes)) / \(humanReadableSize(progress.itemBytes))")
                        .font(.footnote)
                }

                ProgressView(value: fraction(progress.transferredBytes, progress.totalBytes))
                    .scaleEffect(y: 1.4)
                Text("\(humanReadableSize(progress.transferredBytes)) / \(humanReadableSize(progress.totalBytes))")
                    .font(.subheadline.weight(.medium))
                Text(L10n.progressSpeedRemaining(humanReadableSize(Int64(progress.speedBytesPerSecond)), formatRemaining(progress.remainingSeconds)))
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)

                SecondaryButton(title: progress.isReceiving ? L10n.cancelReceive : L10n.cancelSend, action: onCancelTransfer)
            }
        }
    }
}

private struct ZipChoiceSheet: View {
    let inDialog: Bool
    let count: Int
    let onCancel: () -> Void
    let onSendZIP: () -> Void
    let onSendIndividually: () -> Void

    init(
        inDialog: Bool = false,
        count: Int,
        onCancel: @escaping () -> Void,
        onSendZIP: @escaping () -> Void,
        onSendIndividually: @escaping () -> Void
    ) {
        self.inDialog = inDialog
        self.count = count
        self.onCancel = onCancel
        self.onSendZIP = onSendZIP
        self.onSendIndividually = onSendIndividually
    }

    var body: some View {
        SheetSurface(inDialog: inDialog) {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.zipChoiceTitle)
                    .font(.headline)
                Text(L10n.zipChoiceBody(count))
                    .font(.body)
                HStack(spacing: 10) {
                    SecondaryButton(title: L10n.cancel, action: onCancel)
                    PrimaryButton(title: L10n.sendIndividually, enabled: true, action: onSendIndividually)
                    PrimaryButton(title: L10n.zipSend, enabled: true, action: onSendZIP)
                }
            }
        }
    }
}

private struct IncomingRequestSheet: View {
    let inDialog: Bool
    let request: IncomingTransferRequest
    let onReceive: () -> Void
    let onReject: () -> Void

    init(
        inDialog: Bool = false,
        request: IncomingTransferRequest,
        onReceive: @escaping () -> Void,
        onReject: @escaping () -> Void
    ) {
        self.inDialog = inDialog
        self.request = request
        self.onReceive = onReceive
        self.onReject = onReject
    }

    var body: some View {
        SheetSurface(inDialog: inDialog) {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.incomingRequestTitle)
                    .font(.headline)
                    .foregroundStyle(Color.appPrimaryText)
                Text("\(request.senderName) (\(request.senderAddress))")
                    .font(.body)
                    .foregroundStyle(Color.appPrimaryText)
                Text(request.displayName)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.appPrimaryText)
                Text(L10n.incomingRequestSize(humanReadableSize(request.totalBytes)))
                    .font(.footnote)
                    .foregroundStyle(Color.appPrimaryText)
                Text(request.fileCount > 1 ? L10n.totalFileCount(request.fileCount) : L10n.incomingRequestSingle)
                    .font(.footnote)
                    .foregroundStyle(Color.appSecondaryText)
                HStack(spacing: 10) {
                    SecondaryButton(title: L10n.reject, action: onReject)
                    PrimaryButton(title: L10n.receive, enabled: true, action: onReceive)
                }
            }
        }
    }
}

private struct InitialTunnelChoiceDialog: View {
    let onChoosePublic: () -> Void
    let onChooseExternal: () -> Void

    var body: some View {
        DialogCard {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.initialTunnelChoiceTitle)
                    .font(.headline)
                Text(L10n.initialTunnelChoiceBody)
                    .font(.body)
                HStack(spacing: 10) {
                    PrimaryButton(title: L10n.initialTunnelChoicePublic, enabled: true, action: onChoosePublic)
                    SecondaryButton(title: L10n.initialTunnelChoiceExternal, action: onChooseExternal)
                }
            }
        }
    }
}

private struct HelpDialog: View {
    let onClose: () -> Void

    var body: some View {
        DialogCard {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.help)
                    .font(.headline)
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(L10n.helpTextLAN)
                        Text(L10n.helpTextTunnel)
                        Text(L10n.helpTextRefresh)
                        Text(L10n.helpTextSaveFolder)
                        Text(L10n.helpTextSendFiles)
                        Text(L10n.helpTextBackground)
                    }
                    .font(.body)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(maxHeight: 260)
                SecondaryButton(title: L10n.close, action: onClose)
            }
        }
    }
}

private struct BlockingDialog: View {
    let title: String
    let message: String
    let primaryTitle: String
    let primaryAction: () -> Void

    var body: some View {
        DialogCard {
            VStack(alignment: .leading, spacing: 16) {
                Text(title)
                    .font(.headline)
                Text(message)
                    .font(.body)
                PrimaryButton(title: primaryTitle, enabled: true, action: primaryAction)
            }
        }
    }
}

private struct ToastView: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.footnote.weight(.medium))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color.appToastBackground, in: Capsule())
            .padding(.horizontal, 24)
    }
}

private struct ModalBackdrop<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.32)
                .ignoresSafeArea()
            content
                .padding(24)
        }
    }
}

private struct OverlayModalBackdrop<Content: View>: View {
    var onDismiss: (() -> Void)? = nil
    @ViewBuilder let content: Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.32)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {
                    onDismiss?()
                }
            content
                .padding(24)
        }
    }
}

private struct SheetSurface<Content: View>: View {
    let inDialog: Bool
    @ViewBuilder let content: Content

    var body: some View {
        if inDialog {
            DialogCard {
                content
            }
        } else {
            VStack {
                content
            }
            .padding(.horizontal, 20)
            .padding(.top, 30)
            .padding(.bottom, 8)
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .background(Color.appDialogSurface.ignoresSafeArea())
        }
    }
}

private struct DialogCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack { content }
            .padding(20)
            .frame(maxWidth: 460)
            .foregroundStyle(Color.appPrimaryText)
            .background(Color.appDialogSurface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.appShadow, radius: 30, y: 10)
    }
}

private struct CardContainer<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack { content }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .foregroundStyle(Color.appPrimaryText)
            .background(Color.appCardSurface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.appShadow.opacity(0.55), radius: 16, y: 6)
    }
}

private struct PillView: View {
    let text: String
    let font: Font

    var body: some View {
        Text(text)
            .font(font)
            .foregroundStyle(Color.appAccent)
            .lineLimit(1)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.appAccentSoft, in: Capsule())
    }
}

private struct PrimaryButton: View {
    let title: String
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .foregroundStyle(.white)
                .background(
                    enabled ? Color.appAccent : Color.appAccent.opacity(0.4),
                    in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                )
                .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

private struct SecondaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .foregroundStyle(Color.appAccent)
                .background(Color.appAccentSoft, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct ModeButton: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .foregroundStyle(selected ? Color.white : Color.appAccent)
                .background(
                    selected ? Color.appAccent : Color.appAccentSoft,
                    in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                )
                .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private extension Color {
    static let appBackgroundTop = adaptive(
        light: UIColor(red: 0.98, green: 0.99, blue: 1.00, alpha: 1.0),
        dark: UIColor(red: 0.06, green: 0.09, blue: 0.14, alpha: 1.0)
    )
    static let appBackgroundBottom = adaptive(
        light: UIColor(red: 0.86, green: 0.92, blue: 0.98, alpha: 1.0),
        dark: UIColor(red: 0.10, green: 0.16, blue: 0.24, alpha: 1.0)
    )
    static let appCardSurface = adaptive(
        light: UIColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 0.96),
        dark: UIColor(red: 0.10, green: 0.14, blue: 0.20, alpha: 0.96)
    )
    static let appDialogSurface = adaptive(
        light: UIColor.white,
        dark: UIColor(red: 0.11, green: 0.15, blue: 0.22, alpha: 1.0)
    )
    static let appPrimaryText = adaptive(
        light: UIColor(red: 0.12, green: 0.16, blue: 0.24, alpha: 1.0),
        dark: UIColor(red: 0.94, green: 0.97, blue: 1.00, alpha: 1.0)
    )
    static let appSecondaryText = adaptive(
        light: UIColor(red: 0.40, green: 0.48, blue: 0.60, alpha: 1.0),
        dark: UIColor(red: 0.67, green: 0.75, blue: 0.85, alpha: 1.0)
    )
    static let appAccent = adaptive(
        light: UIColor(red: 0.12, green: 0.45, blue: 0.95, alpha: 1.0),
        dark: UIColor(red: 0.44, green: 0.72, blue: 1.0, alpha: 1.0)
    )
    static let appAccentSoft = adaptive(
        light: UIColor(red: 0.12, green: 0.45, blue: 0.95, alpha: 0.10),
        dark: UIColor(red: 0.44, green: 0.72, blue: 1.0, alpha: 0.18)
    )
    static let appToastBackground = adaptive(
        light: UIColor(white: 0.08, alpha: 0.82),
        dark: UIColor(white: 0.05, alpha: 0.90)
    )
    static let appShadow = adaptive(
        light: UIColor(white: 0.0, alpha: 0.10),
        dark: UIColor(white: 0.0, alpha: 0.38)
    )

    private static func adaptive(light: UIColor, dark: UIColor) -> Color {
        Color(uiColor: UIColor { trait in
            trait.userInterfaceStyle == .dark ? dark : light
        })
    }
}

private func fraction(_ numerator: Int64, _ denominator: Int64) -> Double {
    guard denominator > 0 else { return 0 }
    return min(max(Double(numerator) / Double(denominator), 0), 1)
}

private enum PhotoPickerTransferLoader {
    static func loadURLs(from items: [PhotosPickerItem]) async -> [URL] {
        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("PeerSendMediaImports", isDirectory: true)
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)

        var urls: [URL] = []
        for (index, item) in items.enumerated() {
            guard let data = try? await item.loadTransferable(type: Data.self), !data.isEmpty else {
                continue
            }
            let contentType = item.supportedContentTypes.first
            let ext = preferredExtension(for: contentType)
            let filename = "media_\(index + 1).\(ext)"
            let fileURL = tempDirectory.appendingPathComponent(filename)
            do {
                try data.write(to: fileURL, options: .atomic)
                urls.append(fileURL)
            } catch {
            }
        }
        return urls
    }

    private static func preferredExtension(for type: UTType?) -> String {
        if let type, let ext = type.preferredFilenameExtension {
            return ext
        }
        return "dat"
    }
}
