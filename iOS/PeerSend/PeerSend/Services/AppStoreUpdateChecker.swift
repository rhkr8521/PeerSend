import Foundation

final class AppStoreUpdateChecker {
    func check(bundleIdentifier: String, currentVersion: String) async -> AppStoreUpdateInfo? {
        guard
            let escaped = bundleIdentifier.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
            let url = URL(string: "https://itunes.apple.com/lookup?bundleId=\(escaped)")
        else {
            return nil
        }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            guard
                let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                let results = root["results"] as? [[String: Any]],
                let first = results.first,
                let storeVersion = first["version"] as? String,
                let trackViewURLString = first["trackViewUrl"] as? String,
                let trackViewURL = URL(string: trackViewURLString)
            else {
                return nil
            }

            guard compareVersions(storeVersion, currentVersion) == .orderedDescending else {
                return nil
            }
            return AppStoreUpdateInfo(version: storeVersion, url: trackViewURL)
        } catch {
            return nil
        }
    }

    private func compareVersions(_ lhs: String, _ rhs: String) -> ComparisonResult {
        let left = lhs.split(separator: ".").map { Int($0) ?? 0 }
        let right = rhs.split(separator: ".").map { Int($0) ?? 0 }
        let count = max(left.count, right.count)
        for index in 0..<count {
            let lv = index < left.count ? left[index] : 0
            let rv = index < right.count ? right[index] : 0
            if lv > rv { return .orderedDescending }
            if lv < rv { return .orderedAscending }
        }
        return .orderedSame
    }
}
