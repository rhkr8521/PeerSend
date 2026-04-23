import Foundation

enum TransferMode {
    case lan
    case tunnel
}

struct PeerDevice: Identifiable, Equatable {
    let id: String
    let title: String
    let addressLabel: String
    let connectHost: String
    let connectPort: UInt16
    let lastSeen: Date
    let isTunnel: Bool
}

struct TransferProgressUI {
    let title: String
    let itemLabel: String
    let totalBytes: Int64
    let transferredBytes: Int64
    let itemBytes: Int64
    let itemTransferredBytes: Int64
    let speedBytesPerSecond: Double
    let remainingSeconds: Double
    let isReceiving: Bool
    let showItemProgress: Bool
}

struct IncomingTransferRequest: Identifiable {
    let id: String
    let senderName: String
    let senderAddress: String
    let displayName: String
    let totalBytes: Int64
    let isZIP: Bool
    let fileCount: Int
}

struct SelectedDocument: Identifiable {
    let id = UUID()
    let url: URL
    let displayName: String
    let sizeBytes: Int64
    let requiresSecurityScope: Bool
}

struct TunnelEndpoints {
    let wsURL: URL
    let adminBaseURL: URL
    let tcpHost: String
}

struct AppStoreUpdateInfo {
    let version: String
    let url: URL
}

struct ReceivedFileItem: Identifiable {
    let id: UUID
    let url: URL
    let name: String
    let receivedDate: Date
    let isVideo: Bool
    let isImage: Bool
}

