import Foundation

let broadcastPort: UInt16 = 37020
let dataPort: UInt16 = 37021
let bufferSize = 65_536
let localFilePort: UInt16 = 50_000
let pollInterval: TimeInterval = 3.0
let deviceTimeout: TimeInterval = 20.0

let defaultTunnelHost = "rhkr8521-tunnel.kro.kr"
let defaultTunnelToken = "public-p2p-token-8521"
let defaultTunnelSubPrefix = "ft"
let tunnelTCPName = "file_tunnel"

func buildTunnelEndpoints(hostPort: String, useSSL: Bool) -> TunnelEndpoints? {
    let cleaned = hostPort
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: "http://", with: "")
        .replacingOccurrences(of: "https://", with: "")
        .replacingOccurrences(of: "ws://", with: "")
        .replacingOccurrences(of: "wss://", with: "")
        .trimmingCharacters(in: CharacterSet(charactersIn: "/"))

    guard !cleaned.isEmpty else { return nil }
    let wsScheme = useSSL ? "wss" : "ws"
    let httpScheme = useSSL ? "https" : "http"
    let hostOnly: String
    if let slash = cleaned.firstIndex(of: "/") {
        let prefix = String(cleaned[..<slash])
        hostOnly = prefix.contains(":") ? String(prefix[..<prefix.lastIndex(of: ":")!]) : prefix
    } else {
        hostOnly = cleaned.contains(":") ? String(cleaned[..<cleaned.lastIndex(of: ":")!]) : cleaned
    }
    guard
        let wsURL = URL(string: "\(wsScheme)://\(cleaned)/_ws"),
        let adminBaseURL = URL(string: "\(httpScheme)://\(cleaned)")
    else {
        return nil
    }
    return TunnelEndpoints(wsURL: wsURL, adminBaseURL: adminBaseURL, tcpHost: hostOnly)
}

func humanReadableSize(_ sizeBytes: Int64) -> String {
    let units = ["B", "KB", "MB", "GB", "TB"]
    var value = Double(max(sizeBytes, 0))
    for unit in units {
        if value < 1024 || unit == units.last {
            return unit == "B" ? "\(Int64(value)) \(unit)" : String(format: "%.2f %@", locale: Locale(identifier: "en_US_POSIX"), value, unit)
        }
        value /= 1024
    }
    return "\(sizeBytes) B"
}

func formatRemaining(_ seconds: Double) -> String {
    let whole = max(Int(seconds.rounded()), 0)
    if whole < 60 {
        return L10n.isKorean ? "\(whole)초" : "\(whole)s"
    }
    if whole < 3600 {
        let minutes = whole / 60
        let secs = whole % 60
        return L10n.isKorean ? "\(minutes)분 \(secs)초" : "\(minutes)m \(secs)s"
    }
    let hours = whole / 3600
    let minutes = (whole % 3600) / 60
    return L10n.isKorean ? "\(hours)시간 \(minutes)분" : "\(hours)h \(minutes)m"
}

func safeName(_ filename: String) -> String {
    let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._- ()[]{}@+&,"))
    let filtered = String(filename.unicodeScalars.filter { allowed.contains($0) }).trimmingCharacters(in: .whitespacesAndNewlines)
    return filtered.isEmpty ? "downloaded_file.dat" : filtered
}

func makeSubdomain(prefix: String, seed: String) -> String {
    let normalized = seed
        .lowercased()
        .replacingOccurrences(of: "[^a-z0-9]", with: "", options: .regularExpression)
    let base = String(normalized.prefix(12)).isEmpty ? "ios" : String(normalized.prefix(12))
    let suffix = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(6)
    return "\(prefix)\(base)\(suffix)"
}

func compactJSONObject(_ input: [String: Any?]) -> [String: Any] {
    var output: [String: Any] = [:]
    for (key, value) in input {
        guard let value else { continue }
        output[key] = value
    }
    return output
}

func makeMultiDisplayName(_ firstName: String, fileCount: Int) -> String {
    guard fileCount > 1 else { return firstName }
    return L10n.isKorean ? "\(firstName) 외 \(fileCount - 1)개" : "\(firstName) + \(fileCount - 1) more"
}

func buildZIPFileName(baseName: String, extraFileCount: Int) -> String {
    let raw = L10n.isKorean ? "\(baseName)_외_\(extraFileCount)개.zip" : "\(baseName)_plus_\(extraFileCount)_files.zip"
    return safeName(raw)
}

enum ControlWire {
    static func writeMessage(channel: TCPChannel, payload: [String: Any]) async throws {
        let body = PickleCompat.serialize(payload)
        var header = withUnsafeBytes(of: UInt32(body.count).bigEndian) { Data($0) }
        header.append(body)
        try await channel.send(header)
    }

    static func readMessage(channel: TCPChannel) async throws -> [String: Any]? {
        guard let header = try await channel.receiveExactly(4) else {
            return nil
        }
        let size = header.withUnsafeBytes { rawBuffer in
            rawBuffer.load(as: UInt32.self).bigEndian
        }
        guard size > 0 else {
            throw NSError(domain: "ControlWire", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid control frame size: \(size)"])
        }
        guard let payload = try await channel.receiveExactly(Int(size)) else {
            return nil
        }
        return try decodePayload(payload)
    }

    private static func decodePayload(_ payload: Data) throws -> [String: Any] {
        if
            let object = try? JSONSerialization.jsonObject(with: payload),
            let dictionary = object as? [String: Any]
        {
            return dictionary
        }

        let decoded = try PickleCompat.deserialize(payload)
        guard let dictionary = decoded as? [String: Any] else {
            throw NSError(domain: "ControlWire", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unsupported control payload"])
        }
        return dictionary
    }
}

private enum PickleCompat {
    private final class Mark: NSObject {}

    static func serialize(_ value: Any) -> Data {
        var output = Data()
        output.append(0x80)
        output.append(0x02)
        writeValue(value, into: &output)
        output.append(ascii("."))
        return output
    }

    static func deserialize(_ data: Data) throws -> Any {
        let bytes = Array(data)
        var stack: [Any] = []
        var memo: [Int: Any] = [:]
        var nextMemoIndex = 0
        var index = 0

        func readByte() throws -> UInt8 {
            guard index < bytes.count else {
                throw NSError(domain: "PickleCompat", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unexpected EOF while reading pickle"])
            }
            defer { index += 1 }
            return bytes[index]
        }

        func readLittleInt(_ length: Int) throws -> Int64 {
            var result: Int64 = 0
            for shift in 0..<length {
                result |= Int64(try readByte()) << (shift * 8)
            }
            return result
        }

        func popMarkIndex() throws -> Int {
            for offset in stride(from: stack.count - 1, through: 0, by: -1) {
                if stack[offset] is Mark {
                    return offset
                }
            }
            throw NSError(domain: "PickleCompat", code: 2, userInfo: [NSLocalizedDescriptionKey: "Pickle MARK not found"])
        }

        func popStackValue() throws -> Any {
            guard !stack.isEmpty else {
                throw NSError(domain: "PickleCompat", code: 3, userInfo: [NSLocalizedDescriptionKey: "Pickle stack is empty"])
            }
            return stack.removeLast()
        }

        while index < bytes.count {
            let op = try readByte()
            switch op {
                case 0x80:
                _ = try readByte()
                case 0x95:
                    index += 8
            case ascii("("):
                stack.append(Mark())
            case ascii(")"):
                stack.append([Any]())
            case ascii("]"):
                stack.append([Any]())
            case ascii("}"):
                stack.append([String: Any]())
            case 0x8c:
                let length = Int(try readByte())
                let value = String(data: Data(bytes[index..<(index + length)]), encoding: .utf8) ?? ""
                index += length
                stack.append(value)
            case ascii("X"):
                let length = Int(try readLittleInt(4))
                let value = String(data: Data(bytes[index..<(index + length)]), encoding: .utf8) ?? ""
                index += length
                stack.append(value)
            case 0x8d:
                let length = Int(try readLittleInt(8))
                let value = String(data: Data(bytes[index..<(index + length)]), encoding: .utf8) ?? ""
                index += length
                stack.append(value)
            case 0x88:
                stack.append(true)
            case 0x89:
                stack.append(false)
            case ascii("N"):
                stack.append(NSNull())
            case ascii("K"):
                stack.append(Int(try readByte()))
            case ascii("M"):
                stack.append(Int(try readLittleInt(2)))
            case ascii("J"):
                stack.append(Int(try readLittleInt(4)))
            case 0x8a:
                let length = Int(try readByte())
                stack.append(Int(try readLittleInt(length)))
            case ascii("I"):
                let end = bytes[index...].firstIndex(of: ascii("\n")) ?? bytes.endIndex
                let text = String(data: Data(bytes[index..<end]), encoding: .utf8) ?? ""
                index = min(end + 1, bytes.count)
                switch text {
                case "00": stack.append(false)
                case "01": stack.append(true)
                default: stack.append(Int64(text) ?? text)
                }
            case ascii("V"):
                let end = bytes[index...].firstIndex(of: ascii("\n")) ?? bytes.endIndex
                let text = String(data: Data(bytes[index..<end]), encoding: .utf8) ?? ""
                index = min(end + 1, bytes.count)
                stack.append(text)
            case ascii("q"):
                memo[Int(try readByte())] = stack.last
            case ascii("r"):
                memo[Int(try readLittleInt(4))] = stack.last
            case 0x94:
                memo[nextMemoIndex] = stack.last
                nextMemoIndex += 1
            case ascii("h"):
                if let value = memo[Int(try readByte())] { stack.append(value) }
            case ascii("j"):
                if let value = memo[Int(try readLittleInt(4))] { stack.append(value) }
            case ascii("s"):
                let value = try popStackValue()
                let key = String(describing: try popStackValue())
                guard var map = stack.removeLast() as? [String: Any] else {
                    throw NSError(domain: "PickleCompat", code: 4, userInfo: [NSLocalizedDescriptionKey: "Expected map on pickle stack"])
                }
                map[key] = sanitizePickleValue(value)
                stack.append(map)
            case ascii("u"):
                let markIndex = try popMarkIndex()
                guard var map = stack[markIndex - 1] as? [String: Any] else {
                    throw NSError(domain: "PickleCompat", code: 5, userInfo: [NSLocalizedDescriptionKey: "Expected map before MARK"])
                }
                var cursor = markIndex + 1
                while cursor < stack.count {
                    let key = String(describing: stack[cursor])
                    cursor += 1
                    let value = cursor < stack.count ? stack[cursor] : NSNull()
                    cursor += 1
                    map[key] = sanitizePickleValue(value)
                }
                stack.removeSubrange(markIndex..<stack.count)
                stack[markIndex - 1] = map
            case ascii("a"):
                let value = sanitizePickleValue(try popStackValue())
                guard var list = stack.removeLast() as? [Any] else {
                    throw NSError(domain: "PickleCompat", code: 6, userInfo: [NSLocalizedDescriptionKey: "Expected list on pickle stack"])
                }
                list.append(value)
                stack.append(list)
            case ascii("e"):
                let markIndex = try popMarkIndex()
                guard var list = stack[markIndex - 1] as? [Any] else {
                    throw NSError(domain: "PickleCompat", code: 7, userInfo: [NSLocalizedDescriptionKey: "Expected list before MARK"])
                }
                list.append(contentsOf: stack[(markIndex + 1)...].map(sanitizePickleValue))
                stack.removeSubrange(markIndex..<stack.count)
                stack[markIndex - 1] = list
            case ascii("l"):
                let markIndex = try popMarkIndex()
                let list = stack[(markIndex + 1)...].map(sanitizePickleValue)
                stack.removeSubrange(markIndex..<stack.count)
                stack.append(list)
            case ascii("d"):
                let markIndex = try popMarkIndex()
                var dictionary: [String: Any] = [:]
                var cursor = markIndex + 1
                while cursor < stack.count {
                    let key = String(describing: stack[cursor])
                    cursor += 1
                    let value = cursor < stack.count ? stack[cursor] : NSNull()
                    cursor += 1
                    dictionary[key] = sanitizePickleValue(value)
                }
                stack.removeSubrange(markIndex..<stack.count)
                stack.append(dictionary)
            case ascii("."):
                return sanitizePickleValue(stack.last ?? NSNull())
            default:
                throw NSError(domain: "PickleCompat", code: 8, userInfo: [NSLocalizedDescriptionKey: String(format: "Unsupported pickle opcode: 0x%02x", op)])
            }
        }

        return sanitizePickleValue(stack.last ?? NSNull())
    }

    private static func writeValue(_ value: Any, into output: inout Data) {
        switch value {
        case is NSNull:
            output.append(ascii("N"))
        case let bool as Bool:
            output.append(bool ? 0x88 : 0x89)
        case let int as Int:
            writeInteger(Int64(int), into: &output)
        case let int64 as Int64:
            writeInteger(int64, into: &output)
        case let int32 as Int32:
            writeInteger(Int64(int32), into: &output)
        case let uint as UInt:
            writeInteger(Int64(clamping: uint), into: &output)
        case let uint64 as UInt64:
            writeInteger(Int64(clamping: uint64), into: &output)
        case let number as NSNumber:
            if CFGetTypeID(number) == CFBooleanGetTypeID() {
                output.append(number.boolValue ? 0x88 : 0x89)
            } else {
                writeInteger(number.int64Value, into: &output)
            }
        case let string as String:
            writeString(string, into: &output)
        case let dictionary as [String: Any]:
            output.append(ascii("}"))
            output.append(ascii("("))
            for (key, entryValue) in dictionary {
                writeString(key, into: &output)
                writeValue(entryValue, into: &output)
            }
            output.append(ascii("u"))
        case let array as [Any]:
            output.append(ascii("]"))
            output.append(ascii("("))
            for item in array {
                writeValue(item, into: &output)
            }
            output.append(ascii("e"))
        case let iterable as any Sequence:
            output.append(ascii("]"))
            output.append(ascii("("))
            for item in iterable {
                writeValue(item, into: &output)
            }
            output.append(ascii("e"))
        default:
            writeString(String(describing: value), into: &output)
        }
    }

    private static func writeString(_ value: String, into output: inout Data) {
        let bytes = Data(value.utf8)
        output.append(ascii("X"))
        appendLittleEndian(Int64(bytes.count), length: 4, into: &output)
        output.append(bytes)
    }

    private static func writeInteger(_ value: Int64, into output: inout Data) {
        switch value {
        case 0...0xFF:
            output.append(ascii("K"))
            output.append(UInt8(value))
        case 0...0xFFFF:
            output.append(ascii("M"))
            appendLittleEndian(value, length: 2, into: &output)
        case Int64(Int32.min)...Int64(Int32.max):
            output.append(ascii("J"))
            appendLittleEndian(value, length: 4, into: &output)
        default:
            var current = UInt64(bitPattern: value)
            var bytes: [UInt8] = []
            while current != 0 {
                bytes.append(UInt8(current & 0xFF))
                current >>= 8
            }
            if bytes.isEmpty {
                bytes = [0]
            }
            output.append(0x8a)
            output.append(UInt8(bytes.count))
            output.append(contentsOf: bytes)
        }
    }

    private static func appendLittleEndian(_ value: Int64, length: Int, into output: inout Data) {
        let raw = UInt64(bitPattern: value)
        for shift in 0..<length {
            output.append(UInt8((raw >> (shift * 8)) & 0xFF))
        }
    }

    private static func sanitizePickleValue(_ value: Any) -> Any {
        if value is NSNull {
            return NSNull()
        }
        if let dictionary = value as? [String: Any] {
            return dictionary.mapValues(sanitizePickleValue)
        }
        if let array = value as? [Any] {
            return array.map(sanitizePickleValue)
        }
        return value
    }
}

extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String? {
        guard let value = self[key] else { return nil }
        if value is NSNull { return nil }
        if let string = value as? String { return string }
        if let number = value as? NSNumber { return number.stringValue }
        return String(describing: value)
    }

    func int(_ key: String) -> Int? {
        guard let value = self[key] else { return nil }
        if let int = value as? Int { return int }
        if let int64 = value as? Int64 { return Int(int64) }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String { return Int(string) }
        return nil
    }

    func int64(_ key: String) -> Int64? {
        guard let value = self[key] else { return nil }
        if let int64 = value as? Int64 { return int64 }
        if let int = value as? Int { return Int64(int) }
        if let number = value as? NSNumber { return number.int64Value }
        if let string = value as? String { return Int64(string) }
        return nil
    }

    func bool(_ key: String) -> Bool? {
        guard let value = self[key] else { return nil }
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.boolValue }
        if let string = value as? String { return Bool(string) }
        return nil
    }

    func array(_ key: String) -> [[String: Any]]? {
        self[key] as? [[String: Any]]
    }

    func dictionary(_ key: String) -> [String: Any]? {
        self[key] as? [String: Any]
    }
}

func extractTunnelSubdomainCandidate(_ raw: String?) -> String? {
    guard var text = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else {
        return nil
    }
    if let range = text.range(of: "://") {
        text = String(text[range.upperBound...])
    }
    if let slashIndex = text.firstIndex(of: "/") {
        text = String(text[..<slashIndex])
    }
    if let atIndex = text.lastIndex(of: "@") {
        text = String(text[text.index(after: atIndex)...])
    }
    if text.hasPrefix("["),
       let end = text.firstIndex(of: "]") {
        text = String(text[text.index(after: text.startIndex)..<end])
    } else if text.filter({ $0 == ":" }).count == 1,
              let colon = text.lastIndex(of: ":") {
        text = String(text[..<colon])
    }
    let candidate = text.split(separator: ".").first.map(String.init)?.trimmingCharacters(in: CharacterSet(charactersIn: "\"' ")) ?? ""
    guard !candidate.isEmpty, candidate != tunnelTCPName, !candidate.contains(" ") else {
        return nil
    }
    return candidate
}

func findNestedTunnelSubdomain(_ element: Any, depth: Int = 0) -> String? {
    guard depth <= 4 else { return nil }
    if let object = element as? [String: Any] {
        let preferred = ["subdomain", "requested_subdomain", "assigned_subdomain", "hostname", "host", "domain", "url"]
        for key in preferred {
            if let value = object[key] as? String, let subdomain = extractTunnelSubdomainCandidate(value) {
                return subdomain
            }
        }
        for value in object.values {
            if let found = findNestedTunnelSubdomain(value, depth: depth + 1) {
                return found
            }
        }
    } else if let array = element as? [Any] {
        for child in array {
            if let found = findNestedTunnelSubdomain(child, depth: depth + 1) {
                return found
            }
        }
    }
    return nil
}

func findNestedTunnelTitle(_ element: Any, depth: Int = 0) -> String? {
    guard depth <= 4 else { return nil }
    if let object = element as? [String: Any] {
        let preferred = ["display_name", "device_name", "client_name", "name", "title", "hostname"]
        for key in preferred {
            if let value = object[key] as? String, !value.isEmpty, value != tunnelTCPName {
                return value
            }
        }
        for (key, value) in object where key != "tcp" && key != "udp" {
            if let found = findNestedTunnelTitle(value, depth: depth + 1) {
                return found
            }
        }
    } else if let array = element as? [Any] {
        for child in array {
            if let found = findNestedTunnelTitle(child, depth: depth + 1) {
                return found
            }
        }
    }
    return nil
}

private func ascii(_ character: Character) -> UInt8 {
    character.asciiValue ?? 0
}
