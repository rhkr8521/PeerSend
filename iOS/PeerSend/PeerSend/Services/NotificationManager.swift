import Foundation
import UserNotifications

final class PeerSendNotificationManager {
    static let shared = PeerSendNotificationManager()

    private let center = UNUserNotificationCenter.current()
    private let incomingRequestID = "incoming-request"
    private let transferStatusID = "transfer-status"

    func authorizationStatus() async -> UNAuthorizationStatus {
        let settings = await center.notificationSettings()
        return settings.authorizationStatus
    }

    func authorizationGranted() async -> Bool {
        let status = await authorizationStatus()
        return status == .authorized || status == .provisional
    }

    func requestAuthorization() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .badge, .sound])
        } catch {
            return false
        }
    }

    func showIncomingRequest(_ request: IncomingTransferRequest) {
        let content = UNMutableNotificationContent()
        content.title = L10n.incomingRequestTitle
        content.body = "\(request.senderName) • \(request.displayName)"
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.2, repeats: false)
        let notification = UNNotificationRequest(identifier: incomingRequestID, content: content, trigger: trigger)
        center.add(notification)
    }

    func clearIncomingRequest() {
        center.removePendingNotificationRequests(withIdentifiers: [incomingRequestID])
        center.removeDeliveredNotifications(withIdentifiers: [incomingRequestID])
    }

    func showTransferStatus(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.2, repeats: false)
        let notification = UNNotificationRequest(identifier: transferStatusID, content: content, trigger: trigger)
        center.add(notification)
    }
}
