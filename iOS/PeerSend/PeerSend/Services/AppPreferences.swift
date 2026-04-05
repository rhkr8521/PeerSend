import Foundation
import UIKit

final class AppPreferences {
    static let shared = AppPreferences()

    private let defaults = UserDefaults.standard

    private enum Key {
        static let tunnelHost = "tunnel_host"
        static let tunnelSSL = "tunnel_ssl"
        static let tunnelToken = "tunnel_token"
        static let usePublicTunnel = "use_public_tunnel"
        static let tunnelSubdomain = "tunnel_subdomain"
        static let lanNameSuffix = "lan_name_suffix"
        static let saveFolderBookmark = "save_folder_bookmark"
    }

    var savedTunnelHost: String {
        defaults.string(forKey: Key.tunnelHost) ?? ""
    }

    var savedTunnelSSL: Bool {
        defaults.object(forKey: Key.tunnelSSL) as? Bool ?? true
    }

    var savedTunnelToken: String {
        defaults.string(forKey: Key.tunnelToken) ?? ""
    }

    var usePublicTunnel: Bool {
        defaults.object(forKey: Key.usePublicTunnel) as? Bool ?? true
    }

    var hasTunnelChoice: Bool {
        defaults.object(forKey: Key.usePublicTunnel) != nil
    }

    var tunnelSubdomain: String? {
        defaults.string(forKey: Key.tunnelSubdomain)
    }

    var lanNameSuffix: String? {
        defaults.string(forKey: Key.lanNameSuffix)
    }

    func saveTunnel(host: String, ssl: Bool, token: String, usePublic: Bool) {
        defaults.set(host, forKey: Key.tunnelHost)
        defaults.set(ssl, forKey: Key.tunnelSSL)
        defaults.set(token, forKey: Key.tunnelToken)
        defaults.set(usePublic, forKey: Key.usePublicTunnel)
    }

    func rememberInitialTunnelChoice(usePublic: Bool) {
        defaults.set(usePublic, forKey: Key.usePublicTunnel)
    }

    func persistTunnelSubdomain(_ value: String) {
        defaults.set(value, forKey: Key.tunnelSubdomain)
    }

    func persistLanNameSuffix(_ value: String) {
        defaults.set(value, forKey: Key.lanNameSuffix)
    }

    func persistSaveFolderBookmark(_ bookmark: Data?) {
        if let bookmark {
            defaults.set(bookmark, forKey: Key.saveFolderBookmark)
        } else {
            defaults.removeObject(forKey: Key.saveFolderBookmark)
        }
    }

    func restoreSaveFolderURL() -> URL? {
        guard let data = defaults.data(forKey: Key.saveFolderBookmark) else {
            return nil
        }
        var isStale = false
        let url = try? URL(resolvingBookmarkData: data, options: [], relativeTo: nil, bookmarkDataIsStale: &isStale)
        if isStale {
            defaults.removeObject(forKey: Key.saveFolderBookmark)
        }
        return url
    }

    func baseDeviceName() -> String {
        let currentName = UIDevice.current.name.trimmingCharacters(in: .whitespacesAndNewlines)
        if !currentName.isEmpty {
            return currentName
        }
        return UIDevice.current.model
    }
}
