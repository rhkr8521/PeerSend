import Foundation

enum L10n {
    static var isKorean: Bool {
        Locale.preferredLanguages.first?.hasPrefix("ko") == true
    }

    static var help: String { isKorean ? "도움말" : "Help" }
    static var tabTransfer: String { isKorean ? "전송" : "Transfer" }
    static var tabGallery: String { isKorean ? "갤러리" : "Gallery" }
    static var tabHelp: String { isKorean ? "도움말" : "Help" }
    static var tabAppInfo: String { isKorean ? "앱 정보" : "App Info" }
    static var galleryEmpty: String { isKorean ? "받은 이미지 또는 동영상이 없습니다." : "No received images or videos." }
    static var gallerySaveToPhotos: String { isKorean ? "사진 앱에 저장" : "Save to Photos" }
    static var gallerySelectAll: String { isKorean ? "전체 선택" : "Select All" }
    static var galleryClearSelection: String { isKorean ? "선택 해제" : "Deselect" }
    static func gallerySelectedCount(_ count: Int) -> String { isKorean ? "\(count)개 선택됨" : "\(count) selected" }
    static func gallerySavedCount(_ count: Int) -> String { isKorean ? "\(count)개 항목을 사진 앱에 저장했습니다." : "Saved \(count) item(s) to Photos." }
    static var galleryPhotoAccessDenied: String { isKorean ? "사진 접근 권한이 없습니다. 설정에서 허용해주세요." : "Photo access denied. Please allow it in Settings." }
    static var galleryVideoLabel: String { isKorean ? "동영상" : "Video" }
    static var appInfoAppName: String { isKorean ? "앱 이름" : "App Name" }
    static var appInfoVersion: String { isKorean ? "버전" : "Version" }
    static var appInfoDeveloper: String { isKorean ? "개발자" : "Developer" }
    static var appInfoContact: String { isKorean ? "문의하기" : "Contact" }
    static var appInfoHomepage: String { isKorean ? "홈페이지" : "Homepage" }
    static var appInfoPrivacy: String { isKorean ? "개인정보 처리방침" : "Privacy Policy" }
    static var appInfoTerms: String { isKorean ? "이용약관" : "Terms of Service" }
    static var appInfoOpenSource: String { isKorean ? "오픈소스 라이선스" : "Open Source Licenses" }
    static var galleryDeleteSelected: String { isKorean ? "삭제" : "Delete" }
    static func galleryDeletedCount(_ count: Int) -> String { isKorean ? "\(count)개 항목을 삭제했습니다." : "Deleted \(count) item(s)." }
    static var deviceNamePrefix: String { isKorean ? "기기명" : "Device" }
    static var ipPrefix: String { isKorean ? "IP" : "IP" }
    static var modeLAN: String { isKorean ? "LAN 모드" : "LAN Mode" }
    static var modeTunnel: String { isKorean ? "터널 모드" : "Tunnel Mode" }
    static var refresh: String { isKorean ? "새로고침" : "Refresh" }
    static var lanSearching: String { isKorean ? "LAN 기기 검색중" : "Searching LAN devices" }
    static var actionSectionTitle: String { isKorean ? "저장 및 전송" : "Save and Send" }
    static var saveFolder: String { isKorean ? "저장 폴더" : "Save Folder" }
    static var sendFiles: String { isKorean ? "파일 전송" : "Send Files" }
    static var sendSourceTitle: String { isKorean ? "전송할 항목 선택" : "Choose What to Send" }
    static var sendSourceBody: String { isKorean ? "파일 앱 또는 사진 보관함에서 전송할 항목을 선택하세요." : "Choose items from Files or your photo library." }
    static var chooseFromFiles: String { isKorean ? "파일 앱" : "Files" }
    static var chooseFromPhotos: String { isKorean ? "사진 및 동영상" : "Photos & Videos" }
    static var tunnelSettings: String { isKorean ? "터널 설정" : "Tunnel Settings" }
    static var publicTunnelAccess: String { isKorean ? "공개 터널 접속" : "Use Public Tunnel" }
    static var publicTunnelSummary: String { isKorean ? "공개 터널 서버를 사용합니다." : "Using the public tunnel server" }
    static var externalTunnelSummary: String { isKorean ? "외부 서버 직접 설정" : "Custom external server" }
    static var tunnelExternalRequired: String {
        isKorean
            ? "외부 서버 정보를 입력한 뒤 적용 및 재연결을 눌러주세요."
            : "Enter your external server settings and tap Apply and Reconnect."
    }
    static var tunnelServerAddress: String { isKorean ? "서버 주소 (Host:Port)" : "Server Address (Host:Port)" }
    static var tunnelToken: String { "TOKEN" }
    static var sslEnabled: String { isKorean ? "SSL 사용" : "Use SSL" }
    static var sslDisabledWarning: String {
        isKorean
            ? "SSL을 끄면 터널 토큰과 연결 정보가 네트워크에서 보호되지 않을 수 있습니다."
            : "If you turn off SSL, your tunnel token and connection details may not be protected on the network."
    }
    static var applyReconnect: String { isKorean ? "적용 및 재연결" : "Apply and Reconnect" }
    static var peerListLAN: String { isKorean ? "LAN 장치 목록" : "LAN Devices" }
    static var peerListTunnel: String { isKorean ? "터널 피어 목록" : "Tunnel Peers" }
    static var noLANDevices: String { isKorean ? "발견된 LAN 장치가 없습니다." : "No LAN devices found." }
    static var noTunnelPeers: String { isKorean ? "발견된 터널 피어가 없습니다." : "No tunnel peers found." }
    static var tunnelBadge: String { "Tunnel" }
    static var lanBadge: String { "LAN" }
    static var zipChoiceTitle: String { isKorean ? "압축 방식 선택" : "Choose Transfer Mode" }
    static func zipChoiceBody(_ count: Int) -> String {
        isKorean
            ? "선택한 \(count)개 파일을 어떻게 보낼까요?\n\n예: ZIP으로 압축해서 한 번에 전송\n아니오: 파일별로 그대로 전송"
            : "How should we send the \(count) selected files?\n\nYes: compress into one ZIP and send once\nNo: send each file separately"
    }
    static var zipSend: String { isKorean ? "예" : "Yes" }
    static var sendIndividually: String { isKorean ? "아니오" : "No" }
    static var cancel: String { isKorean ? "취소" : "Cancel" }
    static var close: String { isKorean ? "닫기" : "Close" }
    static var receive: String { isKorean ? "수신" : "Receive" }
    static var reject: String { isKorean ? "거절" : "Reject" }
    static var cancelReceive: String { isKorean ? "수신 중단" : "Cancel Receive" }
    static var cancelSend: String { isKorean ? "전송 중단" : "Cancel Send" }
    static var incomingRequestTitle: String { isKorean ? "파일 수신 요청" : "Incoming File Request" }
    static func incomingRequestSize(_ size: String) -> String {
        isKorean ? "크기 \(size)" : "Size \(size)"
    }
    static var incomingRequestSingle: String { isKorean ? "단일 파일 전송" : "Single file transfer" }
    static func totalFileCount(_ count: Int) -> String {
        isKorean ? "총 \(count)개 파일" : (count == 1 ? "Total 1 file" : "Total \(count) files")
    }
    static var helpTextLAN: String {
        isKorean ? "LAN 모드: 같은 와이파이에 있는 기기를 자동으로 찾고 바로 전송합니다." : "LAN mode: automatically finds devices on the same Wi-Fi and sends files directly."
    }
    static var helpTextTunnel: String {
        isKorean ? "터널 모드: 외부 네트워크 상에서도 P2P로 파일을 직접 주고받을 수 있습니다. 들어가면 저장된 설정으로 한 번 자동 연결을 시도하고, 서버 설정을 바꾼 뒤에는 적용 및 재연결을 눌러 다시 연결하세요." : "Tunnel mode: lets you exchange files directly over P2P even across external networks. It tries one automatic connection with the saved settings when you enter, and after editing server settings use Apply and Reconnect."
    }
    static var helpTextRefresh: String {
        isKorean ? "새로고침: LAN은 즉시 재검색하고, 터널은 피어 목록을 다시 불러오며 필요하면 연결도 다시 시도합니다." : "Refresh: LAN rescans immediately, while Tunnel reloads peers and retries the connection if needed."
    }
    static var helpTextSaveFolder: String {
        isKorean ? "저장 폴더: 기본적으로 '나의 iPhone/PeerSend'에 저장됩니다. 원하면 저장 폴더 버튼으로 다른 위치를 고를 수 있습니다." : "Save Folder: files are saved to 'On My iPhone/PeerSend' by default. You can change it with the Save Folder button."
    }
    static var helpTextSendFiles: String {
        isKorean ? "파일 전송: 기기를 먼저 선택한 뒤 파일 전송을 누르면 파일 앱이나 사진 보관함에서 여러 항목을 한 번에 보낼 수 있습니다." : "Send Files: select a device first, then tap Send Files to pick multiple items from Files or Photos."
    }
    static var helpTextGallery: String {
        isKorean ? "갤러리: 받은 파일 중 이미지와 동영상을 한눈에 관리하고 삭제할 수 있습니다. 핸드폰 갤러리에 넣고 싶은 항목을 선택한 뒤 사진 앱에 저장 버튼을 누르면 사진 앱에서 확인할 수 있습니다." : "Gallery: manage and delete received images and videos at a glance. To add items to the device gallery, select them and tap Save to Photos."
    }
    static var helpTextBackground: String {
        isKorean ? "백그라운드 전송: iOS에서는 진행 중인 전송만 제한적으로 백그라운드 시간을 연장합니다." : "Background transfer: on iOS, ongoing transfers can only be extended for limited background time."
    }
    static var initialTunnelChoiceTitle: String { isKorean ? "터널 접속 방식 선택" : "Choose Tunnel Access" }
    static var initialTunnelChoiceBody: String {
        isKorean ? "공개 터널을 바로 이용할까요, 아니면 외부 터널 서버를 직접 설정할까요?" : "Do you want to use the public tunnel first, or configure your own external tunnel server?"
    }
    static var initialTunnelChoicePublic: String { isKorean ? "공개 터널 이용" : "Use Public Tunnel" }
    static var initialTunnelChoiceExternal: String { isKorean ? "외부 서버 설정" : "Set External Server" }
    static var notificationPermissionTitle: String { isKorean ? "알림 허용" : "Allow Notifications" }
    static var notificationPermissionBody: String {
        isKorean ? "알림을 허용하면 백그라운드에서 수신 요청과 전송 상태를 더 쉽게 확인할 수 있습니다. 알림 없이도 앱을 사용할 수 있습니다." : "Allow notifications to more easily check incoming requests and transfer status in the background. You can still use the app without notifications."
    }
    static var notificationPermissionAllow: String { isKorean ? "허용" : "Allow" }
    static var notificationPermissionLater: String { isKorean ? "나중에" : "Not Now" }
    static var openSettings: String { isKorean ? "설정 열기" : "Open Settings" }
    static var updateRequiredTitle: String { isKorean ? "업데이트 필요" : "Update Required" }
    static var updateRequiredBody: String {
        isKorean ? "앱스토어에 PeerSend 최신 버전이 있습니다. 계속 사용하려면 업데이트가 필요합니다." : "A newer version of PeerSend is available on the App Store. Please update to continue."
    }
    static var updateNow: String { isKorean ? "업데이트" : "Update" }
    static var tunnelDisconnected: String { isKorean ? "터널 미접속" : "Tunnel disconnected" }
    static var tunnelStarting: String { isKorean ? "터널 연결 시작 중..." : "Starting tunnel connection..." }
    static var tunnelWSConnecting: String { isKorean ? "연결 중..." : "Connecting..." }
    static var tunnelWSConnectedRegistering: String { isKorean ? "연결됨(등록 중...)" : "Connected (registering...)" }
    static var tunnelRegistered: String { isKorean ? "등록 완료" : "Registered" }
    static func tunnelRegisteredPort(_ port: Int) -> String {
        isKorean ? "등록 완료 (내 포트: \(port))" : "Registered (my port: \(port))"
    }
    static func tunnelRegisterFailed(_ reason: String) -> String {
        isKorean ? "등록 실패: \(reason)" : "Registration failed: \(reason)"
    }
    static func tunnelWSClosedPrompt() -> String {
        isKorean ? "연결이 끊겼습니다. 새로고침 또는 적용 및 재연결로 다시 연결하세요." : "Connection closed. Use Refresh or Apply and Reconnect to connect again."
    }
    static var tunnelWSFailed: String { isKorean ? "연결 실패." : "Connection failed." }
    static var tunnelPeerFetchFailed: String { isKorean ? "피어 조회 실패." : "Failed to fetch peers." }
    static var tunnelLocalServerFailed: String { isKorean ? "터널 로컬 수신 서버 실패." : "Tunnel local receive server failed." }
    static func tunnelStreamFailed(_ reason: String) -> String {
        isKorean ? "터널 스트림 처리 실패: \(reason)" : "Tunnel stream failed: \(reason)"
    }
    static func tunnelMessageFailed(_ reason: String) -> String {
        isKorean ? "터널 메시지 처리 실패: \(reason)" : "Failed to process tunnel message: \(reason)"
    }
    static var publicTunnelUnavailableTitle: String { isKorean ? "서버 접속 불가" : "Cannot Connect to Server" }
    static var publicTunnelUnavailableBody: String {
        isKorean
            ? "현재 공개 터널 서버에 접속할 수 없습니다. 네트워크 상태를 확인해주세요."
            : "Cannot connect to the public tunnel server. Please check your network connection."
    }
    static var unknownError: String { isKorean ? "알 수 없는 오류" : "Unknown error" }
    static var genericError: String { isKorean ? "오류" : "Error" }
    static var unknownValue: String { "unknown" }
    static var errorResponseEmpty: String { isKorean ? "응답 없음" : "No response" }
    static var errorServerResponseInvalid: String { isKorean ? "서버 응답 오류" : "Invalid server response" }
    static var unknownSender: String { "Unknown" }
    static var genericFileLabel: String { isKorean ? "파일" : "File" }
    static var selectTargetFirst: String { isKorean ? "전송할 대상을 먼저 선택하세요." : "Select a device first." }
    static var busyMessage: String { isKorean ? "현재 다른 전송이 진행 중입니다." : "Another transfer is already in progress." }
    static var cannotReadSelected: String { isKorean ? "선택한 파일을 읽을 수 없습니다." : "Could not read the selected files." }
    static var cannotLoadSelectedMedia: String { isKorean ? "선택한 사진 또는 동영상을 불러올 수 없습니다." : "Could not load the selected photos or videos." }
    static var otherRejected: String { isKorean ? "상대방이 전송을 거절했습니다." : "The other device rejected the transfer." }
    static var noResponse: String { isKorean ? "상대방 응답이 없습니다." : "No response from the other device." }
    static func connectFailed(_ message: String) -> String {
        isKorean ? "연결 실패: \(message)" : "Connection failed: \(message)"
    }
    static func requestHandleFailed(_ message: String) -> String {
        isKorean ? "전송 요청 처리 실패: \(message)" : "Failed to process transfer request: \(message)"
    }
    static func receiveFailed(_ message: String) -> String {
        isKorean ? "수신 실패: \(message)" : "Receive failed: \(message)"
    }
    static func invalidSaveFolder(_ path: String) -> String {
        isKorean ? "선택한 저장 폴더를 사용할 수 없습니다: \(path)" : "The selected save folder can't be used: \(path)"
    }
    static func unavailableSaveFolder(_ path: String) -> String {
        isKorean ? "선택한 저장 폴더에 접근할 수 없습니다: \(path)" : "The selected save folder is unavailable: \(path)"
    }
    static func receiveFileLabel(_ name: String) -> String {
        isKorean ? "파일 \(name) 수신 중..." : "Receiving file \(name)..."
    }
    static func receiveFileIndexLabel(_ name: String, _ index: Int, _ total: Int) -> String {
        isKorean ? "파일 \(name) (\(index)/\(total)) 수신 중..." : "Receiving file \(name) (\(index)/\(total))..."
    }
    static func sendFileLabel(_ name: String) -> String {
        isKorean ? "파일 \(name) 전송 중..." : "Sending file \(name)..."
    }
    static func sendFileIndexLabel(_ name: String, _ index: Int, _ total: Int) -> String {
        isKorean ? "파일 \(name) (\(index)/\(total)) 전송 중..." : "Sending file \(name) (\(index)/\(total))..."
    }
    static func sendZipLabel(_ name: String) -> String {
        isKorean ? "ZIP \(name) 전송 중..." : "Sending ZIP \(name)..."
    }
    static func zippingLabel(_ name: String, _ index: Int, _ total: Int) -> String {
        isKorean ? "압축 중: \(name) (\(index)/\(total))" : "Compressing: \(name) (\(index)/\(total))"
    }
    static var preparingReceive: String { isKorean ? "수신 준비 중..." : "Preparing to receive..." }
    static var preparingSend: String { isKorean ? "전송 준비 중..." : "Preparing to send..." }
    static var transferTitleMultiFiles: String { isKorean ? "다중 파일" : "Multiple Files" }
    static var transferTitleZIP: String { isKorean ? "ZIP 압축" : "Creating ZIP" }
    static var transferCancelled: String { isKorean ? "전송이 중단되었습니다." : "Transfer canceled." }
    static var receiveCancelled: String { isKorean ? "수신을 중단했습니다." : "Receive canceled." }
    static var senderCancelled: String { isKorean ? "전송측에서 전송을 중단했습니다." : "The sender canceled the transfer." }
    static var remoteCancelled: String { isKorean ? "상대방이 전송을 중단했습니다." : "The other device canceled the transfer." }
    static func zipExtractComplete(_ count: Int) -> String {
        isKorean ? "ZIP 추출 완료: \(count)개 파일을 받았습니다." : "ZIP extracted: received \(count) files."
    }
    static func zipExtractFailed(_ reason: String, _ path: String) -> String {
        isKorean ? "ZIP 추출 실패: \(reason). 원본 ZIP은 저장했습니다. (\(path))" : "Failed to extract ZIP: \(reason). The original ZIP was saved. (\(path))"
    }
    static func receiveComplete(_ name: String) -> String {
        isKorean ? "\(name) 파일을 받았습니다." : "Received file: \(name)"
    }
    static var multiMetaFailed: String { isKorean ? "다중 파일 메타데이터 수신 실패" : "Failed to receive multi-file metadata." }
    static func multiReceiveError(_ message: String) -> String {
        isKorean ? "다중 파일 수신 오류: \(message)" : "Multi-file receive error: \(message)"
    }
    static func multiReceiveComplete(_ count: Int) -> String {
        isKorean ? "\(count)개 파일을 받았습니다." : "Received \(count) files."
    }
    static var missingZIP: String { isKorean ? "ZIP 파일이 없습니다." : "ZIP file is missing." }
    static func transferComplete(_ displayName: String) -> String {
        isKorean ? "\(displayName) 전송 완료!" : "\(displayName) transfer complete!"
    }
    static func openInputFailed(_ name: String) -> String {
        isKorean ? "입력 스트림을 열 수 없습니다: \(name)" : "Could not open input stream: \(name)"
    }
    static func progressSpeedRemaining(_ speed: String, _ remaining: String) -> String {
        isKorean ? "속도 \(speed) | 남은 시간 \(remaining)" : "Speed \(speed) | Time left \(remaining)"
    }
    static func saveFolderDefaultLabel(appName: String) -> String {
        isKorean ? "나의 iPhone/\(appName)" : "On My iPhone/\(appName)"
    }
}
