import Foundation
import zlib

enum ZIPArchiveUtility {
    struct Entry {
        let displayName: String
        let fileURL: URL
    }

    private struct CentralDirectoryEntry {
        let nameData: Data
        let crc32: UInt32
        let compressedSize: UInt32
        let uncompressedSize: UInt32
        let localHeaderOffset: UInt32
        let modificationTime: UInt16
        let modificationDate: UInt16
        let compressionMethod: UInt16
    }

    private struct ParsedEntry {
        let name: String
        let compressedSize: UInt32
        let uncompressedSize: UInt32
        let localHeaderOffset: UInt32
        let compressionMethod: UInt16
    }

    private static let localFileHeaderSignature: UInt32 = 0x04034b50
    private static let centralDirectoryHeaderSignature: UInt32 = 0x02014b50
    private static let endOfCentralDirectorySignature: UInt32 = 0x06054b50
    private static let utf8Flag: UInt16 = 0x0800
    private static let maxChunkSize = 65_536

    static func createArchive(
        at archiveURL: URL,
        entries: [Entry],
        onProgress: ((Entry, Int, Int, Int64, Int64, Int64) -> Void)? = nil
    ) throws {
        FileManager.default.createFile(atPath: archiveURL.path, contents: nil)
        let handle = try FileHandle(forWritingTo: archiveURL)
        defer { try? handle.close() }

        var centralDirectory: [CentralDirectoryEntry] = []

        for (index, entry) in entries.enumerated() {
            let nameData = Data(safeName(entry.displayName).utf8)
            guard !nameData.isEmpty else { continue }
            let modification = dosDateTime(from: (try? entry.fileURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date())

            let localHeaderOffset = try currentOffset(for: handle)
            try handle.write(contentsOf: localFileHeader(
                filenameLength: UInt16(nameData.count),
                modificationTime: modification.time,
                modificationDate: modification.date
            ))
            try handle.write(contentsOf: nameData)

            guard let stream = InputStream(url: entry.fileURL) else {
                throw NSError(domain: "ZIPArchiveUtility", code: 12, userInfo: [NSLocalizedDescriptionKey: "Failed to open \(entry.displayName)"])
            }
            stream.open()
            defer { stream.close() }

            var crc: UInt32 = 0
            var uncompressedSize: UInt64 = 0
            var buffer = [UInt8](repeating: 0, count: maxChunkSize)
            let totalSize = Int64((try? entry.fileURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)

            while true {
                let read = stream.read(&buffer, maxLength: buffer.count)
                if read < 0 {
                    throw stream.streamError ?? NSError(domain: "ZIPArchiveUtility", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to read \(entry.displayName)"])
                }
                if read == 0 {
                    break
                }
                let chunk = Data(buffer.prefix(read))
                try handle.write(contentsOf: chunk)
                crc = updateCRC32(crc, data: chunk)
                uncompressedSize += UInt64(read)
                onProgress?(entry, index, entries.count, Int64(read), Int64(uncompressedSize), totalSize)
            }

            guard uncompressedSize <= UInt64(UInt32.max) else {
                throw NSError(domain: "ZIPArchiveUtility", code: 2, userInfo: [NSLocalizedDescriptionKey: "\(entry.displayName) is too large for ZIP32"])
            }

            let endOffset = try currentOffset(for: handle)
            try patchLocalHeader(
                handle: handle,
                headerOffset: localHeaderOffset,
                crc32: crc,
                compressedSize: UInt32(uncompressedSize),
                uncompressedSize: UInt32(uncompressedSize)
            )
            try handle.seek(toOffset: endOffset)

            centralDirectory.append(
                CentralDirectoryEntry(
                    nameData: nameData,
                    crc32: crc,
                    compressedSize: UInt32(uncompressedSize),
                    uncompressedSize: UInt32(uncompressedSize),
                    localHeaderOffset: UInt32(localHeaderOffset),
                    modificationTime: modification.time,
                    modificationDate: modification.date,
                    compressionMethod: 0
                )
            )
        }

        let centralDirectoryOffset = try currentOffset(for: handle)
        var centralDirectorySize: UInt64 = 0
        for entry in centralDirectory {
            let record = centralDirectoryHeader(for: entry)
            try handle.write(contentsOf: record)
            centralDirectorySize += UInt64(record.count)
        }

        guard
            centralDirectory.count <= Int(UInt16.max),
            centralDirectoryOffset <= UInt64(UInt32.max),
            centralDirectorySize <= UInt64(UInt32.max)
        else {
            throw NSError(domain: "ZIPArchiveUtility", code: 3, userInfo: [NSLocalizedDescriptionKey: "ZIP archive is too large"])
        }

        try handle.write(contentsOf: endOfCentralDirectoryRecord(
            entryCount: UInt16(centralDirectory.count),
            centralDirectorySize: UInt32(centralDirectorySize),
            centralDirectoryOffset: UInt32(centralDirectoryOffset)
        ))
    }

    static func extractArchive(at archiveURL: URL, to destinationURL: URL) throws -> Int {
        let archiveData = try Data(contentsOf: archiveURL)
        let entries = try parseCentralDirectory(in: archiveData)
        let reader = try FileHandle(forReadingFrom: archiveURL)
        defer { try? reader.close() }
        try FileManager.default.createDirectory(at: destinationURL, withIntermediateDirectories: true)

        var extracted = 0
        for entry in entries {
            let sanitizedName = sanitizeArchivePath(entry.name)
            guard !sanitizedName.isEmpty else { continue }

            let outputURL = destinationURL.appendingPathComponent(sanitizedName)
            if entry.name.hasSuffix("/") {
                try FileManager.default.createDirectory(at: outputURL, withIntermediateDirectories: true)
                continue
            }

            try FileManager.default.createDirectory(at: outputURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            FileManager.default.createFile(atPath: outputURL.path, contents: nil)
            let writer = try FileHandle(forWritingTo: outputURL)

            do {
                defer { try? writer.close() }
                let dataOffset = try localFileDataOffset(for: entry, in: archiveData)
                try reader.seek(toOffset: dataOffset)
                switch entry.compressionMethod {
                case 0:
                    try copyStoredFile(from: reader, to: writer, size: Int(entry.compressedSize))
                case 8:
                    do {
                        try inflateFile(from: reader, to: writer, compressedSize: Int(entry.compressedSize), windowBits: -MAX_WBITS)
                    } catch {
                        try writer.truncate(atOffset: 0)
                        try writer.seek(toOffset: 0)
                        try reader.seek(toOffset: dataOffset)
                        try inflateFile(from: reader, to: writer, compressedSize: Int(entry.compressedSize), windowBits: MAX_WBITS)
                    }
                default:
                    throw NSError(domain: "ZIPArchiveUtility", code: 4, userInfo: [NSLocalizedDescriptionKey: "Unsupported ZIP compression method: \(entry.compressionMethod)"])
                }
            } catch {
                try? writer.close()
                try? FileManager.default.removeItem(at: outputURL)
                throw error
            }
            extracted += 1
        }

        return extracted
    }

    private static func copyStoredFile(from reader: FileHandle, to writer: FileHandle, size: Int) throws {
        var remaining = size
        while remaining > 0 {
            let chunkSize = min(maxChunkSize, remaining)
            let chunk = try reader.read(upToCount: chunkSize) ?? Data()
            if chunk.isEmpty {
                throw NSError(domain: "ZIPArchiveUtility", code: 5, userInfo: [NSLocalizedDescriptionKey: "Unexpected EOF in ZIP file"])
            }
            try writer.write(contentsOf: chunk)
            remaining -= chunk.count
        }
    }

    private static func inflateFile(from reader: FileHandle, to writer: FileHandle, compressedSize: Int, windowBits: Int32) throws {
        var inflater = try RawInflater(windowBits: windowBits)
        var remaining = compressedSize
        while remaining > 0 {
            let chunkSize = min(maxChunkSize, remaining)
            let chunk = try reader.read(upToCount: chunkSize) ?? Data()
            if chunk.isEmpty {
                throw NSError(domain: "ZIPArchiveUtility", code: 6, userInfo: [NSLocalizedDescriptionKey: "Unexpected EOF while inflating ZIP file"])
            }
            let output = try inflater.process(chunk)
            if !output.isEmpty {
                try writer.write(contentsOf: output)
            }
            remaining -= chunk.count
        }

        let finalData = try inflater.finish()
        if !finalData.isEmpty {
            try writer.write(contentsOf: finalData)
        }
    }

    private static func parseCentralDirectory(in data: Data) throws -> [ParsedEntry] {
        guard let eocdOffset = findEndOfCentralDirectory(in: data) else {
            throw NSError(domain: "ZIPArchiveUtility", code: 7, userInfo: [NSLocalizedDescriptionKey: "ZIP end record not found"])
        }

        let entryCount = Int(try readUInt16LE(from: data, offset: eocdOffset + 10))
        let centralDirectoryOffset = Int(try readUInt32LE(from: data, offset: eocdOffset + 16))
        guard centralDirectoryOffset >= 0, centralDirectoryOffset < data.count else {
            throw NSError(domain: "ZIPArchiveUtility", code: 13, userInfo: [NSLocalizedDescriptionKey: "Invalid central directory offset"])
        }
        var cursor = centralDirectoryOffset
        var entries: [ParsedEntry] = []

        for _ in 0..<entryCount {
            guard try readUInt32LE(from: data, offset: cursor) == centralDirectoryHeaderSignature else {
                throw NSError(domain: "ZIPArchiveUtility", code: 8, userInfo: [NSLocalizedDescriptionKey: "Invalid central directory entry"])
            }

            let compressionMethod = try readUInt16LE(from: data, offset: cursor + 10)
            let compressedSize = try readUInt32LE(from: data, offset: cursor + 20)
            let uncompressedSize = try readUInt32LE(from: data, offset: cursor + 24)
            let filenameLength = Int(try readUInt16LE(from: data, offset: cursor + 28))
            let extraLength = Int(try readUInt16LE(from: data, offset: cursor + 30))
            let commentLength = Int(try readUInt16LE(from: data, offset: cursor + 32))
            let localHeaderOffset = try readUInt32LE(from: data, offset: cursor + 42)

            let nameStart = cursor + 46
            let nameEnd = nameStart + filenameLength
            guard nameStart >= 0, nameEnd <= data.count else {
                throw NSError(domain: "ZIPArchiveUtility", code: 14, userInfo: [NSLocalizedDescriptionKey: "Invalid ZIP filename range"])
            }
            let name = String(data: data.subdata(in: nameStart..<nameEnd), encoding: .utf8) ?? ""

            entries.append(
                ParsedEntry(
                    name: name,
                    compressedSize: compressedSize,
                    uncompressedSize: uncompressedSize,
                    localHeaderOffset: localHeaderOffset,
                    compressionMethod: compressionMethod
                )
            )

            cursor = nameEnd + extraLength + commentLength
            guard cursor <= data.count else {
                throw NSError(domain: "ZIPArchiveUtility", code: 15, userInfo: [NSLocalizedDescriptionKey: "Invalid central directory cursor"])
            }
        }

        return entries
    }

    private static func localFileDataOffset(for entry: ParsedEntry, in data: Data) throws -> UInt64 {
        let offset = Int(entry.localHeaderOffset)
        guard try readUInt32LE(from: data, offset: offset) == localFileHeaderSignature else {
            throw NSError(domain: "ZIPArchiveUtility", code: 9, userInfo: [NSLocalizedDescriptionKey: "Invalid local ZIP header"])
        }
        let filenameLength = Int(try readUInt16LE(from: data, offset: offset + 26))
        let extraLength = Int(try readUInt16LE(from: data, offset: offset + 28))
        return UInt64(offset + 30 + filenameLength + extraLength)
    }

    private static func patchLocalHeader(
        handle: FileHandle,
        headerOffset: UInt64,
        crc32: UInt32,
        compressedSize: UInt32,
        uncompressedSize: UInt32
    ) throws {
        let current = try currentOffset(for: handle)
        try handle.seek(toOffset: headerOffset + 14)
        try handle.write(contentsOf: dataLE(crc32))
        try handle.write(contentsOf: dataLE(compressedSize))
        try handle.write(contentsOf: dataLE(uncompressedSize))
        try handle.seek(toOffset: current)
    }

    private static func localFileHeader(filenameLength: UInt16, modificationTime: UInt16, modificationDate: UInt16) -> Data {
        var data = Data()
        data.append(dataLE(localFileHeaderSignature))
        data.append(dataLE(UInt16(20)))
        data.append(dataLE(utf8Flag))
        data.append(dataLE(UInt16(0)))
        data.append(dataLE(modificationTime))
        data.append(dataLE(modificationDate))
        data.append(dataLE(UInt32(0)))
        data.append(dataLE(UInt32(0)))
        data.append(dataLE(UInt32(0)))
        data.append(dataLE(filenameLength))
        data.append(dataLE(UInt16(0)))
        return data
    }

    private static func centralDirectoryHeader(for entry: CentralDirectoryEntry) -> Data {
        var data = Data()
        data.append(dataLE(centralDirectoryHeaderSignature))
        data.append(dataLE(UInt16(20)))
        data.append(dataLE(UInt16(20)))
        data.append(dataLE(utf8Flag))
        data.append(dataLE(entry.compressionMethod))
        data.append(dataLE(entry.modificationTime))
        data.append(dataLE(entry.modificationDate))
        data.append(dataLE(entry.crc32))
        data.append(dataLE(entry.compressedSize))
        data.append(dataLE(entry.uncompressedSize))
        data.append(dataLE(UInt16(entry.nameData.count)))
        data.append(dataLE(UInt16(0)))
        data.append(dataLE(UInt16(0)))
        data.append(dataLE(UInt16(0)))
        data.append(dataLE(UInt16(0)))
        data.append(dataLE(UInt32(0)))
        data.append(dataLE(entry.localHeaderOffset))
        data.append(entry.nameData)
        return data
    }

    private static func endOfCentralDirectoryRecord(entryCount: UInt16, centralDirectorySize: UInt32, centralDirectoryOffset: UInt32) -> Data {
        var data = Data()
        data.append(dataLE(endOfCentralDirectorySignature))
        data.append(dataLE(UInt16(0)))
        data.append(dataLE(UInt16(0)))
        data.append(dataLE(entryCount))
        data.append(dataLE(entryCount))
        data.append(dataLE(centralDirectorySize))
        data.append(dataLE(centralDirectoryOffset))
        data.append(dataLE(UInt16(0)))
        return data
    }

    private static func findEndOfCentralDirectory(in data: Data) -> Int? {
        let minRecordLength = 22
        guard data.count >= minRecordLength else { return nil }
        let lowerBound = max(0, data.count - (65_535 + minRecordLength))
        let signature = dataLE(endOfCentralDirectorySignature)
        if data.count < signature.count { return nil }

        for offset in stride(from: data.count - minRecordLength, through: lowerBound, by: -1) {
            if data[offset..<(offset + 4)] == signature[0..<4] {
                return offset
            }
        }
        return nil
    }

    private static func sanitizeArchivePath(_ path: String) -> String {
        let components = path
            .split(separator: "/")
            .map(String.init)
            .filter { !$0.isEmpty && $0 != "." && $0 != ".." }
            .map(safeName)
            .filter { !$0.isEmpty }
        return components.joined(separator: "/")
    }

    private static func readUInt16LE(from data: Data, offset: Int) throws -> UInt16 {
        guard offset >= 0, offset + 2 <= data.count else {
            throw NSError(domain: "ZIPArchiveUtility", code: 16, userInfo: [NSLocalizedDescriptionKey: "Unexpected end of ZIP data"])
        }
        let low = UInt16(data[data.index(data.startIndex, offsetBy: offset)])
        let high = UInt16(data[data.index(data.startIndex, offsetBy: offset + 1)])
        return low | (high << 8)
    }

    private static func readUInt32LE(from data: Data, offset: Int) throws -> UInt32 {
        guard offset >= 0, offset + 4 <= data.count else {
            throw NSError(domain: "ZIPArchiveUtility", code: 17, userInfo: [NSLocalizedDescriptionKey: "Unexpected end of ZIP data"])
        }
        let b0 = UInt32(data[data.index(data.startIndex, offsetBy: offset)])
        let b1 = UInt32(data[data.index(data.startIndex, offsetBy: offset + 1)])
        let b2 = UInt32(data[data.index(data.startIndex, offsetBy: offset + 2)])
        let b3 = UInt32(data[data.index(data.startIndex, offsetBy: offset + 3)])
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24)
    }

    private static func updateCRC32(_ current: UInt32, data: Data) -> UInt32 {
        data.withUnsafeBytes { rawBuffer in
            let buffer = rawBuffer.bindMemory(to: Bytef.self)
            return UInt32(crc32_z(uLong(current), buffer.baseAddress, z_size_t(buffer.count)))
        }
    }

    private static func dataLE<T: FixedWidthInteger>(_ value: T) -> Data {
        var littleEndian = value.littleEndian
        return withUnsafeBytes(of: &littleEndian) { Data($0) }
    }

    private static func dosDateTime(from date: Date) -> (date: UInt16, time: UInt16) {
        let calendar = Calendar(identifier: .gregorian)
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        let year = max((components.year ?? 1980) - 1980, 0)
        let month = max(components.month ?? 1, 1)
        let day = max(components.day ?? 1, 1)
        let hour = max(components.hour ?? 0, 0)
        let minute = max(components.minute ?? 0, 0)
        let second = max((components.second ?? 0) / 2, 0)

        let dosDate = UInt16((year << 9) | (month << 5) | day)
        let dosTime = UInt16((hour << 11) | (minute << 5) | second)
        return (dosDate, dosTime)
    }

    private static func currentOffset(for handle: FileHandle) throws -> UInt64 {
        if #available(iOS 13.4, *) {
            return try handle.offset()
        }
        return handle.offsetInFile
    }

    private struct RawInflater {
        private var stream = z_stream()

        init(windowBits: Int32) throws {
            let result = inflateInit2_(&stream, windowBits, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
            guard result == Z_OK else {
                throw NSError(domain: "ZIPArchiveUtility", code: 10, userInfo: [NSLocalizedDescriptionKey: "Failed to initialize inflater"])
            }
        }

        mutating func process(_ chunk: Data) throws -> Data {
            try chunk.withUnsafeBytes { rawBuffer in
                let buffer = rawBuffer.bindMemory(to: Bytef.self)
                stream.next_in = UnsafeMutablePointer(mutating: buffer.baseAddress)
                stream.avail_in = uInt(buffer.count)
                return try drainInflateLoop()
            }
        }

        mutating func finish() throws -> Data {
            stream.next_in = nil
            stream.avail_in = 0
            let output = try drainInflateLoop()
            inflateEnd(&stream)
            return output
        }

        private mutating func drainInflateLoop() throws -> Data {
            var output = Data()
            var status: Int32 = Z_OK
            repeat {
                var buffer = [UInt8](repeating: 0, count: maxChunkSize)
                status = buffer.withUnsafeMutableBytes { rawBuffer -> Int32 in
                    let pointer = rawBuffer.bindMemory(to: Bytef.self)
                    stream.next_out = pointer.baseAddress
                    stream.avail_out = uInt(pointer.count)
                    return inflate(&stream, Z_NO_FLUSH)
                }

                let produced = buffer.count - Int(stream.avail_out)
                if produced > 0 {
                    output.append(contentsOf: buffer.prefix(produced))
                }

                if status == Z_STREAM_END {
                    break
                }

                guard status == Z_OK || status == Z_BUF_ERROR else {
                    throw NSError(domain: "ZIPArchiveUtility", code: 11, userInfo: [NSLocalizedDescriptionKey: "ZIP inflate failed with status \(status)"])
                }
            } while stream.avail_in > 0 || Int(stream.avail_out) == 0

            return output
        }
    }
}
