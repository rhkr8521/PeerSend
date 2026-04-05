import Foundation
import Network
import Darwin

enum TCPChannelError: Error {
    case invalidPort
    case endOfStream
}

final class TCPChannel {
    private let connection: NWConnection
    private let queue = DispatchQueue(label: "com.rhkr8521.peersend.tcpchannel.\(UUID().uuidString)")
    private var started = false

    private final class ResumeState {
        let lock = NSLock()
        var resumed = false
    }

    init(existing connection: NWConnection) {
        self.connection = connection
    }

    init(host: String, port: UInt16) throws {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            throw TCPChannelError.invalidPort
        }
        self.connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .tcp)
    }

    func start() async throws {
        guard !started else { return }
        started = true
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let resumeState = ResumeState()
            connection.stateUpdateHandler = { connectionState in
                func resumeOnce(_ result: Result<Void, Error>) {
                    resumeState.lock.lock()
                    defer { resumeState.lock.unlock() }
                    guard !resumeState.resumed else { return }
                    resumeState.resumed = true
                    switch result {
                    case .success:
                        continuation.resume()
                    case .failure(let error):
                        continuation.resume(throwing: error)
                    }
                }

                switch connectionState {
                case .ready:
                    resumeOnce(.success(()))
                case .failed(let error):
                    resumeOnce(.failure(error))
                case .cancelled:
                    resumeOnce(.failure(TCPChannelError.endOfStream))
                default:
                    break
                }
            }
            connection.start(queue: queue)
        }
    }

    func send(_ data: Data) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.send(content: data, completion: .contentProcessed { error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            })
        }
    }

    func receiveExactly(_ length: Int) async throws -> Data? {
        var output = Data()
        while output.count < length {
            guard let chunk = try await receive(minimum: 1, maximum: length - output.count) else {
                return output.isEmpty ? nil : output
            }
            if chunk.isEmpty {
                continue
            }
            output.append(chunk)
        }
        return output
    }

    func receiveChunk(maximumLength: Int) async throws -> Data? {
        try await receive(minimum: 1, maximum: maximumLength)
    }

    private func receive(minimum: Int, maximum: Int) async throws -> Data? {
        try await withCheckedThrowingContinuation { continuation in
            connection.receive(minimumIncompleteLength: minimum, maximumLength: maximum) { data, _, isComplete, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data, !data.isEmpty {
                    continuation.resume(returning: data)
                } else if isComplete {
                    continuation.resume(returning: nil)
                } else {
                    continuation.resume(returning: Data())
                }
            }
        }
    }

    func cancel() {
        connection.cancel()
    }
}

final class TCPListenerService {
    private let listener: NWListener
    private let queue: DispatchQueue

    init(port: UInt16, label: String) throws {
        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            throw TCPChannelError.invalidPort
        }
        self.listener = try NWListener(using: .tcp, on: nwPort)
        self.queue = DispatchQueue(label: label)
    }

    func start(onConnection: @escaping (NWConnection) -> Void) {
        listener.newConnectionHandler = onConnection
        listener.start(queue: queue)
    }

    func stop() {
        listener.cancel()
    }
}

final class UDPDiscoveryService {
    private let listenQueue = DispatchQueue(label: "com.rhkr8521.peersend.udp.discovery.listen")
    private let sendQueue = DispatchQueue(label: "com.rhkr8521.peersend.udp.discovery.send")
    private var socketFD: Int32 = -1
    private var running = false

    func startListening(onMessage: @escaping (Data) -> Void) {
        guard !running else { return }
        running = true
        listenQueue.async { [weak self] in
            guard let self else { return }
            let fd = Darwin.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
            guard fd >= 0 else { return }
            self.socketFD = fd

            var reuse: Int32 = 1
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, socklen_t(MemoryLayout<Int32>.size))
            setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &reuse, socklen_t(MemoryLayout<Int32>.size))

            var timeout = timeval(tv_sec: 1, tv_usec: 0)
            setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, socklen_t(MemoryLayout<timeval>.size))

            var address = sockaddr_in()
            address.sin_len = UInt8(MemoryLayout<sockaddr_in>.stride)
            address.sin_family = sa_family_t(AF_INET)
            address.sin_port = in_port_t(broadcastPort.bigEndian)
            address.sin_addr = in_addr(s_addr: INADDR_ANY)

            withUnsafePointer(to: &address) { pointer in
                pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockaddrPointer in
                    _ = Darwin.bind(fd, sockaddrPointer, socklen_t(MemoryLayout<sockaddr_in>.stride))
                }
            }

            var buffer = [UInt8](repeating: 0, count: 8192)
            while self.running {
                var remote = sockaddr_storage()
                var remoteLength = socklen_t(MemoryLayout<sockaddr_storage>.stride)
                let received = withUnsafeMutablePointer(to: &remote) { remotePointer in
                    remotePointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockaddrPointer in
                        Darwin.recvfrom(fd, &buffer, buffer.count, 0, sockaddrPointer, &remoteLength)
                    }
                }
                if received > 0 {
                    onMessage(Data(buffer[0..<received]))
                }
            }

            Darwin.close(fd)
            self.socketFD = -1
        }
    }

    func stop() {
        running = false
        if socketFD >= 0 {
            Darwin.close(socketFD)
            socketFD = -1
        }
    }

    func broadcast(_ payload: Data) {
        sendQueue.async {
            let fd = Darwin.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
            guard fd >= 0 else { return }
            defer { Darwin.close(fd) }

            var broadcastFlag: Int32 = 1
            setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &broadcastFlag, socklen_t(MemoryLayout<Int32>.size))

            let targets = NetworkInterfaceInfo.broadcastAddresses()
            for host in targets {
                var address = sockaddr_in()
                address.sin_len = UInt8(MemoryLayout<sockaddr_in>.stride)
                address.sin_family = sa_family_t(AF_INET)
                address.sin_port = in_port_t(broadcastPort.bigEndian)
                inet_pton(AF_INET, host, &address.sin_addr)
                withUnsafePointer(to: &address) { pointer in
                    pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockaddrPointer in
                        payload.withUnsafeBytes { buffer in
                            _ = Darwin.sendto(fd, buffer.baseAddress, buffer.count, 0, sockaddrPointer, socklen_t(MemoryLayout<sockaddr_in>.stride))
                        }
                    }
                }
            }
        }
    }
}

enum NetworkInterfaceInfo {
    private struct IPv4InterfaceInfo {
        let name: String
        let address: String
        let broadcast: String?
    }

    private static let ignoredPrefixes = ["lo", "docker", "veth", "br-", "vm", "tap", "virbr", "wg", "awdl", "llw", "utun"]

    static func currentIPv4Address() -> String {
        ipv4Interfaces().first?.address ?? "127.0.0.1"
    }

    static func broadcastAddresses() -> [String] {
        var ordered: [String] = ipv4Interfaces().compactMap(\.broadcast)
        if !ordered.contains("255.255.255.255") {
            ordered.append("255.255.255.255")
        }

        var seen = Set<String>()
        return ordered.filter { seen.insert($0).inserted }
    }

    private static func ipv4Interfaces() -> [IPv4InterfaceInfo] {
        var pointer: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&pointer) == 0, let first = pointer else {
            return []
        }
        defer { freeifaddrs(pointer) }

        var results: [IPv4InterfaceInfo] = []
        for interface in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let flags = Int32(interface.pointee.ifa_flags)
            guard flags & IFF_UP != 0, flags & IFF_LOOPBACK == 0 else { continue }
            guard let address = interface.pointee.ifa_addr, address.pointee.sa_family == UInt8(AF_INET) else { continue }

            let name = String(cString: interface.pointee.ifa_name)
            guard !ignoredPrefixes.contains(where: { name.hasPrefix($0) }) else { continue }

            let addressString = numericHost(for: address)
            guard isUsableIPv4(addressString) else { continue }

            var broadcastString: String?
            if flags & IFF_BROADCAST != 0, let netmask = interface.pointee.ifa_netmask {
                let addrIn = address.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee }
                let maskIn = netmask.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee }
                let addr = UInt32(bigEndian: addrIn.sin_addr.s_addr)
                let mask = UInt32(bigEndian: maskIn.sin_addr.s_addr)
                let broadcast = (addr & mask) | ~mask
                var outAddress = in_addr(s_addr: UInt32(bigEndian: broadcast))
                var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                if inet_ntop(AF_INET, &outAddress, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil {
                    let candidate = String(cString: buffer)
                    if isUsableIPv4(candidate) {
                        broadcastString = candidate
                    }
                }
            }

            results.append(
                IPv4InterfaceInfo(
                    name: name,
                    address: addressString,
                    broadcast: broadcastString
                )
            )
        }

        return results.sorted { lhs, rhs in
            let leftPriority = interfacePriority(lhs.name)
            let rightPriority = interfacePriority(rhs.name)
            if leftPriority == rightPriority {
                return lhs.name < rhs.name
            }
            return leftPriority < rightPriority
        }
    }

    private static func interfacePriority(_ name: String) -> Int {
        if name == "en0" { return 0 }
        if name.hasPrefix("en") { return 1 }
        if name.hasPrefix("bridge") || name.hasPrefix("eth") { return 2 }
        return 10
    }

    private static func isUsableIPv4(_ address: String) -> Bool {
        !address.isEmpty &&
        address != "0.0.0.0" &&
        !address.hasPrefix("127.") &&
        !address.hasPrefix("169.254.")
    }

    private static func numericHost(for address: UnsafeMutablePointer<sockaddr>) -> String {
        var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
        var copy = address.pointee
        let length = socklen_t(copy.sa_len)
        let result = getnameinfo(&copy, length, &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST)
        guard result == 0 else { return "127.0.0.1" }
        return String(cString: host)
    }
}
