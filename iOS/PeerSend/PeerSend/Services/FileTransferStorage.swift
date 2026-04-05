import Foundation

struct ScopedDirectory {
    let url: URL
    let stopAccessing: () -> Void
}

enum StorageAccessError: LocalizedError {
    case invalidSaveFolder(String)
    case unavailableSaveFolder(String)

    var errorDescription: String? {
        switch self {
        case .invalidSaveFolder(let path):
            return L10n.invalidSaveFolder(path)
        case .unavailableSaveFolder(let path):
            return L10n.unavailableSaveFolder(path)
        }
    }
}

final class FileTransferStorage {
    private let preferences: AppPreferences
    private let defaultVisibleFolderName = "Received Files"
    private let defaultVisibilityMarker = ".peersend-placeholder"
    private var sessionSaveDirectoryURL: URL?

    init(preferences: AppPreferences = .shared) {
        self.preferences = preferences
        ensureDefaultRootExists()
    }

    func rememberSaveDirectory(_ url: URL) throws {
        let started = url.startAccessingSecurityScopedResource()
        defer {
            if started {
                url.stopAccessingSecurityScopedResource()
            }
        }
        guard isDirectoryURL(url) else {
            throw StorageAccessError.invalidSaveFolder(url.path)
        }
        sessionSaveDirectoryURL = url
        let bookmark = try url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil)
        preferences.persistSaveFolderBookmark(bookmark)
    }

    func saveLocationLabel() -> String {
        let current = currentRootURL()
        if isDefaultRoot(current) {
            return L10n.saveFolderDefaultLabel(appName: appDisplayName())
        }
        return current.path
    }

    func currentRootURL() -> URL {
        if let sessionSaveDirectoryURL {
            return sessionSaveDirectoryURL
        }
        if let restored = preferences.restoreSaveFolderURL() {
            sessionSaveDirectoryURL = restored
            return restored
        }
        return defaultRootURL()
    }

    func accessCurrentRoot() throws -> ScopedDirectory {
        let url = currentRootURL()
        if isDefaultRoot(url) {
            _ = ensureWritableDirectory(url)
            return ScopedDirectory(url: url) {
            }
        }

        let started = url.startAccessingSecurityScopedResource()
        guard isDirectoryURL(url) else {
            if started {
                url.stopAccessingSecurityScopedResource()
            }
            clearSavedDirectory()
            throw StorageAccessError.invalidSaveFolder(url.path)
        }
        return ScopedDirectory(url: url) {
            if started {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }

    func createUniqueFileURL(named displayName: String, in directory: URL) -> URL {
        let cleanName = safeName(displayName)
        let ext = URL(fileURLWithPath: cleanName).pathExtension
        let stem = ext.isEmpty ? cleanName : String(cleanName.dropLast(ext.count + 1))
        var candidate = directory.appendingPathComponent(cleanName)
        var index = 1
        while FileManager.default.fileExists(atPath: candidate.path) {
            let nextName = ext.isEmpty ? "\(stem) (\(index))" : "\(stem) (\(index)).\(ext)"
            candidate = directory.appendingPathComponent(nextName)
            index += 1
        }
        return candidate
    }

    func createUniqueDirectoryURL(named displayName: String, in directory: URL) -> URL {
        let cleanName = safeName(displayName)
        var candidate = directory.appendingPathComponent(cleanName, isDirectory: true)
        var index = 1
        while FileManager.default.fileExists(atPath: candidate.path) {
            candidate = directory.appendingPathComponent("\(cleanName) (\(index))", isDirectory: true)
            index += 1
        }
        try? FileManager.default.createDirectory(at: candidate, withIntermediateDirectories: true)
        return candidate
    }

    func deleteItem(at url: URL) {
        try? FileManager.default.removeItem(at: url)
    }

    private func defaultRootURL() -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    private func ensureDefaultRootExists() {
        let root = defaultRootURL()
        _ = ensureWritableDirectory(root)
        let visibleFolder = root.appendingPathComponent(defaultVisibleFolderName, isDirectory: true)
        try? FileManager.default.createDirectory(at: visibleFolder, withIntermediateDirectories: true)
        let marker = root.appendingPathComponent(defaultVisibilityMarker)
        if !FileManager.default.fileExists(atPath: marker.path) {
            FileManager.default.createFile(atPath: marker.path, contents: Data())
        }
    }

    private func isDefaultRoot(_ url: URL) -> Bool {
        url.standardizedFileURL == defaultRootURL().standardizedFileURL
    }

    private func clearSavedDirectory() {
        sessionSaveDirectoryURL = nil
        preferences.persistSaveFolderBookmark(nil)
    }

    private func appDisplayName() -> String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String)
            ?? (Bundle.main.object(forInfoDictionaryKey: "CFBundleName") as? String)
            ?? "PeerSend"
    }

    private func isDirectoryURL(_ url: URL) -> Bool {
        if url.hasDirectoryPath {
            return true
        }
        let values = try? url.resourceValues(forKeys: [.isDirectoryKey])
        return values?.isDirectory == true
    }

    private func ensureWritableDirectory(_ url: URL) -> Bool {
        do {
            try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
            let probe = url.appendingPathComponent("PeerSend.write-test-\(UUID().uuidString).tmp")
            try Data().write(to: probe, options: .atomic)
            try? FileManager.default.removeItem(at: probe)
            return true
        } catch {
            return false
        }
    }
}
