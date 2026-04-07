export const landingContent = {
  ko: {
    nav: {
      story: "소개",
      experience: "경험",
      download: "다운로드",
      notice: "공지사항",
      tunnelServer: "나만의 터널 서버 만들기",
      menuOpen: "메뉴 열기",
      close: "닫기",
    },
    hero: {
      tag: "Peer to Peer Transfer Platform",
      titleLines: ["파일 전송을 넘어", "기기 간 연결을 더 자연스럽게"],
      mobileTitle: "파일 전송을 넘어 기기 간 연결을 더 자연스럽게",
      description:
        "PeerSend는 같은 네트워크에 연결되어 있을 때는 LAN 모드로 주변 기기를 자동으로 찾아 직접 파일을 전송할 수 있고, 같은 네트워크가 아니어도 Tunnel 모드를 통해 외부 환경에서 연결해 파일을 주고받을 수 있습니다. 사진, 동영상, 문서, 압축 파일 등 다양한 형식의 파일을 손쉽게 전송할 수 있습니다.",
      mobileDescription:
        "PeerSend는 같은 네트워크에서는 LAN으로, 외부 환경에서는 Tunnel로 이어져 PC와 휴대폰 사이를 빠르게 연결합니다.",
      start: "시작하기",
      signals: {
        lanTitle: "Nearby devices detected",
        lanBody: "같은 공간에 있는 기기와는 즉시 연결되는 흐름",
        tunnelTitle: "Remote bridge ready",
        tunnelBody: "떨어진 환경에서도 이어지는 PeerSend 네트워크",
        iosStatus: "LAN ready",
        androidStatus: "Tunnel bridge",
      },
      console: {
        tag: "Peer To Peer File Transfer",
        title: "PeerSend Platform",
      },
    },
    platforms: [
      {
        label: "LAN",
        title: "같은 네트워크에서는 바로 찾고",
        body: "주변 PeerSend 기기를 자동으로 탐색해서 복잡한 코드 입력 없이 빠르게 보냅니다.",
      },
      {
        label: "Tunnel",
        title: "떨어진 곳에서도 자연스럽게",
        body: "같은 터널 서버에 연결된 기기를 찾아 외부 네트워크에서도 같은 감각으로 전송합니다.",
      },
      {
        label: "Control",
        title: "중앙 서버 없이 더 사적인 전송",
        body: "사용자 파일과 개인 정보를 보관하거나 수집하지 않고, 기기 간 직접 연결을 우선으로 설계했습니다.",
        note: "공개 터널 서버를 이용할 경우 사용자의 IP 정보만 수집됩니다.",
      },
    ],
    features: [
      {
        id: "direct",
        kicker: "직접 전송",
        title: "업로드 링크 대신 바로 연결되는 전송 경험",
        body:
          "PeerSend는 PC(Windows, macOS, Linux)와 휴대폰 사이, 또는 휴대폰과 휴대폰 사이에서 파일을 빠르고 간편하게 전송할 수 있는 P2P 파일 전송 서비스입니다.",
      },
      {
        id: "flexible",
        kicker: "유연한 흐름",
        title: "한 파일은 가볍게, 여러 파일은 상황에 맞게",
        body: "파일 하나는 그대로 보내고, 여러 파일은 개별 전송 또는 ZIP 묶음 전송으로 선택할 수 있습니다.",
      },
      {
        id: "tunnel",
        kicker: "터널 연결",
        title: "같은 네트워크가 아니어도 이어지는 전송",
        body:
          "터널링 서비스를 이용하면 같은 네트워크가 아니더라도 PeerSend 기기를 연결해 사진, 영상, 문서, 압축 파일을 그대로 주고받을 수 있습니다. 중앙 서버에 파일을 저장하는 형식이 아니라, 터널 서버가 기기끼리 연결만 이어 주는 P2P 방식으로 동작합니다.",
      },
    ],
    downloads: {
      tag: "Download",
      title: "바로 시작할 수 있도록 준비된 PeerSend",
      mobileTitle: "모바일에서도 바로 시작할 수 있는 PeerSend",
      body: "데스크톱에서는 설치형 엔진과 웹 UI를 함께 사용하고, 모바일에서는 각 플랫폼에 맞는 앱으로 이어집니다.",
      mobileBody: "기기와 환경에 맞는 앱 또는 웹 UI로 바로 이어집니다.",
      ctaOpen: "이용하기",
      ctaDownload: "다운로드",
      items: [
        { platform: "PC", detail: "Windows / Mac / Linux", href: "https://send.peersend.kro.kr" },
        {
          platform: "Android",
          detail: "Google Play",
          href: "https://play.google.com/store/apps/details?id=com.rhkr8521.p2ptransfer",
          store: "play",
        },
        {
          platform: "iOS",
          detail: "App Store",
          href: "https://apps.apple.com/app/id0000000000",
          store: "apple",
        },
      ],
    },
    footer: {
      brand: "PeerSend",
      body: "PC와 휴대폰, 그리고 기기와 기기 사이를 더 빠르고 자연스럽게.",
      privacy: "개인정보처리방침",
      terms: "이용약관",
      openSource: "오픈소스 라이선스",
      copyright: "All rights reserved.",
    },
  },
  en: {
    nav: {
      story: "Story",
      experience: "Experience",
      download: "Download",
      notice: "Notice",
      tunnelServer: "Build Your Own Tunnel Server",
      menuOpen: "Open menu",
      close: "Close",
    },
    hero: {
      tag: "Peer to Peer Transfer Platform",
      titleLines: ["Beyond file transfer,", "make device connections feel natural"],
      mobileTitle: "Beyond file transfer, make device connections feel natural",
      description:
        "PeerSend can automatically discover nearby devices on the same network with LAN mode and send files directly. Even when devices are not on the same network, Tunnel mode lets them connect remotely. Photos, videos, documents, archives, and many other file types can be transferred with ease.",
      mobileDescription:
        "PeerSend connects PCs and phones quickly with LAN on the same network and Tunnel in remote environments.",
      start: "Get Started",
      signals: {
        lanTitle: "Nearby devices detected",
        lanBody: "A flow that connects instantly to devices in the same place",
        tunnelTitle: "Remote bridge ready",
        tunnelBody: "A PeerSend network that continues even across distance",
        iosStatus: "LAN ready",
        androidStatus: "Tunnel bridge",
      },
      console: {
        tag: "Peer To Peer File Transfer",
        title: "PeerSend Platform",
      },
    },
    platforms: [
      {
        label: "LAN",
        title: "Find devices instantly on the same network",
        body: "PeerSend automatically discovers nearby devices so you can send files quickly without entering codes.",
      },
      {
        label: "Tunnel",
        title: "Stay connected naturally across distance",
        body: "Find devices connected to the same tunnel server and keep the same transfer flow even on external networks.",
      },
      {
        label: "Control",
        title: "More private transfer without a central server",
        body: "PeerSend does not store or collect user files and personal data, and is designed around direct device-to-device connections.",
        note: "When using the public tunnel server, only the user's IP address may be collected.",
      },
    ],
    features: [
      {
        id: "direct",
        kicker: "Direct transfer",
        title: "A transfer experience connected directly instead of through upload links",
        body:
          "PeerSend is a P2P file transfer service that makes it easy to send files quickly between PCs (Windows, macOS, Linux) and phones, or between phones.",
      },
      {
        id: "flexible",
        kicker: "Flexible flow",
        title: "One file stays light, many files stay flexible",
        body: "Send a single file as-is, or choose between individual transfer and ZIP bundling when sending multiple files.",
      },
      {
        id: "tunnel",
        kicker: "Tunnel reach",
        title: "Transfers that continue even when devices are not on the same network",
        body:
          "With tunneling, PeerSend devices can exchange photos, videos, documents, and archive files even when they are not on the same network. Files are not stored on a central server; the tunnel server only bridges the connection between devices in a P2P flow.",
      },
    ],
    downloads: {
      tag: "Download",
      title: "PeerSend ready to start right away",
      mobileTitle: "PeerSend ready to start on mobile too",
      body: "On desktop, use the installable engine together with the web UI, and on mobile move directly into the app for each platform.",
      mobileBody: "Move directly into the right app or web UI for your device and environment.",
      ctaOpen: "Open",
      ctaDownload: "Download",
      items: [
        { platform: "PC", detail: "Windows / Mac / Linux", href: "https://send.peersend.kro.kr" },
        {
          platform: "Android",
          detail: "Google Play",
          href: "https://play.google.com/store/apps/details?id=com.rhkr8521.p2ptransfer",
          store: "play",
        },
        {
          platform: "iOS",
          detail: "App Store",
          href: "https://apps.apple.com/app/id0000000000",
          store: "apple",
        },
      ],
    },
    footer: {
      brand: "PeerSend",
      body: "Faster, more natural transfer between PCs, phones, and devices.",
      privacy: "Privacy Policy",
      terms: "Terms of Service",
      openSource: "Open Source Licenses",
      copyright: "All rights reserved.",
    },
  },
};

export const openSourceContent = {
  ko: {
    metaTitle: "PeerSend 오픈소스 라이선스",
    metaDescription: "PeerSend에 사용된 오픈소스 라이선스 안내",
    home: "홈으로",
    tag: "Open Source Licenses",
    title: "PeerSend 오픈소스 라이선스",
    intro:
      "이 페이지는 PeerSend 데스크톱 엔진, Android 앱, iOS 앱에 직접 사용되거나 배포에 포함되는 주요 오픈소스 구성 요소와 라이선스를 정리한 문서입니다.",
    sections: [
      {
        title: "Desktop Engine",
        description: "Windows/macOS/Linux용 로컬 엔진과 엔진 API에 사용되는 주요 오픈소스입니다.",
        items: [
          { name: "CPython", version: "3.x", license: "PSF-2.0", href: "https://www.python.org/" },
          { name: "FastAPI", version: "0.135.3", license: "MIT", href: "https://fastapi.tiangolo.com/" },
          { name: "Uvicorn", version: "0.43.0", license: "BSD-3-Clause", href: "https://www.uvicorn.org/" },
          { name: "aiohttp", version: "3.13.5", license: "Apache-2.0 / MIT", href: "https://github.com/aio-libs/aiohttp" },
          { name: "psutil", version: "7.2.2", license: "BSD-3-Clause", href: "https://github.com/giampaolo/psutil" },
          { name: "python-multipart", version: "0.0.22", license: "Apache-2.0", href: "https://github.com/Kludex/python-multipart" },
          { name: "websockets", version: "15.0.1", license: "BSD-3-Clause", href: "https://websockets.readthedocs.io/" },
          { name: "keyring", version: "25.x", license: "MIT", href: "https://github.com/jaraco/keyring" },
        ],
        notes: [
          "오프라인 모드 UI는 CPython에 포함된 Tkinter/Tcl/Tk 환경을 사용합니다.",
        ],
      },
      {
        title: "Android App",
        description: "Android 앱에 직접 사용되는 주요 오픈소스 및 라이브러리입니다.",
        items: [
          { name: "Kotlin / Kotlin Standard Library", version: "2.2.10", license: "Apache-2.0", href: "https://kotlinlang.org/" },
          { name: "AndroidX Core KTX", version: "1.16.0", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx" },
          { name: "AndroidX Lifecycle Runtime / Compose / ViewModel Compose", version: "2.9.2", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx" },
          { name: "AndroidX Activity Compose", version: "1.10.1", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx" },
          { name: "Jetpack Compose UI / Graphics / Tooling Preview / Material3 / Material Icons", version: "Compose BOM 2024.09.00", license: "Apache-2.0", href: "https://developer.android.com/jetpack/compose" },
          { name: "AndroidX DocumentFile", version: "1.0.1", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx" },
          { name: "AndroidX Security Crypto", version: "1.1.0-alpha06", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx/releases/security" },
          { name: "Kotlinx Coroutines Android", version: "1.9.0", license: "Apache-2.0", href: "https://github.com/Kotlin/kotlinx.coroutines" },
          { name: "OkHttp", version: "4.12.0", license: "Apache-2.0", href: "https://square.github.io/okhttp/" },
          { name: "Gson", version: "2.11.0", license: "Apache-2.0", href: "https://github.com/google/gson" },
        ],
      },
      {
        title: "iOS App",
        description: "iOS 앱에 직접 포함되는 오픈소스 구성 요소입니다.",
        items: [
          { name: "zlib", version: "system", license: "zlib License", href: "https://zlib.net/" },
        ],
      },
    ],
  },
  en: {
    metaTitle: "PeerSend Open Source Licenses",
    metaDescription: "Open source licenses used by PeerSend",
    home: "Home",
    tag: "Open Source Licenses",
    title: "PeerSend Open Source Licenses",
    intro:
      "This page lists the major open-source components directly used by, or bundled with, the PeerSend desktop engine, Android app, and iOS app.",
    sections: [
      {
        title: "Desktop Engine",
        description: "Major open-source components used by the local engine and engine API for Windows, macOS, and Linux.",
        items: [
          { name: "CPython", version: "3.x", license: "PSF-2.0", href: "https://www.python.org/" },
          { name: "FastAPI", version: "0.135.3", license: "MIT", href: "https://fastapi.tiangolo.com/" },
          { name: "Uvicorn", version: "0.43.0", license: "BSD-3-Clause", href: "https://www.uvicorn.org/" },
          { name: "aiohttp", version: "3.13.5", license: "Apache-2.0 / MIT", href: "https://github.com/aio-libs/aiohttp" },
          { name: "psutil", version: "7.2.2", license: "BSD-3-Clause", href: "https://github.com/giampaolo/psutil" },
          { name: "python-multipart", version: "0.0.22", license: "Apache-2.0", href: "https://github.com/Kludex/python-multipart" },
          { name: "websockets", version: "15.0.1", license: "BSD-3-Clause", href: "https://websockets.readthedocs.io/" },
          { name: "keyring", version: "25.x", license: "MIT", href: "https://github.com/jaraco/keyring" },
        ],
        notes: [
          "The offline mode UI uses the Tkinter/Tcl/Tk environment included with CPython.",
        ],
      },
      {
        title: "Android App",
        description: "Major open-source libraries directly used by the Android app.",
        items: [
          { name: "Kotlin / Kotlin Standard Library", version: "2.2.10", license: "Apache-2.0", href: "https://kotlinlang.org/" },
          { name: "AndroidX Core KTX", version: "1.16.0", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx" },
          { name: "AndroidX Lifecycle Runtime / Compose / ViewModel Compose", version: "2.9.2", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx" },
          { name: "AndroidX Activity Compose", version: "1.10.1", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx" },
          { name: "Jetpack Compose UI / Graphics / Tooling Preview / Material3 / Material Icons", version: "Compose BOM 2024.09.00", license: "Apache-2.0", href: "https://developer.android.com/jetpack/compose" },
          { name: "AndroidX DocumentFile", version: "1.0.1", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx" },
          { name: "AndroidX Security Crypto", version: "1.1.0-alpha06", license: "Apache-2.0", href: "https://developer.android.com/jetpack/androidx/releases/security" },
          { name: "Kotlinx Coroutines Android", version: "1.9.0", license: "Apache-2.0", href: "https://github.com/Kotlin/kotlinx.coroutines" },
          { name: "OkHttp", version: "4.12.0", license: "Apache-2.0", href: "https://square.github.io/okhttp/" },
          { name: "Gson", version: "2.11.0", license: "Apache-2.0", href: "https://github.com/google/gson" },
        ],
      },
      {
        title: "iOS App",
        description: "Open-source components directly bundled in the iOS app.",
        items: [
          { name: "zlib", version: "system", license: "zlib License", href: "https://zlib.net/" },
        ],
      },
    ],
  },
};

export const tunnelServerContent = {
  ko: {
    metaTitle: "PeerSend Tunnel Server Guide",
    metaDescription: "PeerSend 나만의 터널 서버 구축 안내",
    home: "홈으로",
    tag: "Tunnel Server",
    title: "나만의 터널 서버 만들기",
    intro:
      "공개 터널 서버 대신 직접 운영하는 터널 서버를 사용하면, 같은 네트워크가 아니더라도 PeerSend 기기끼리 연결을 이어 파일을 주고받을 수 있습니다. 아래 가이드는 Ubuntu 서버 기준 설치 방법입니다.",
    installGuide: "설치 방법",
    requirements: "요구사항",
    server: "서버",
    points: "핵심 포인트",
    requirementsItems: [
      "Ubuntu 20.04+ (24.04 권장)",
      "공개 포트: 80, 443, 앱 포트(기본 8080), 터널링에 사용할 TCP/UDP 포트 범위",
      "Python 3.10+, Nginx (스크립트에서 자동 설치)",
      "도메인 및 DNS 설정 (와일드카드 서브도메인 사용 시 *.example.com 필요)",
    ],
    pointItems: [
      "터널 서버는 파일을 저장하지 않고, 기기 간 연결만 이어주는 역할을 합니다.",
      "도메인과 와일드카드 DNS를 준비하면 여러 기기를 더 안정적으로 연결할 수 있습니다.",
      "토큰 화이트리스트와 대시보드 계정으로 서버 접근 범위를 제어할 수 있습니다.",
    ],
    sections: {
      install: "1. 서버 설치 (Ubuntu/Debian)",
      installBody:
        "Tunneler 전용 APT 저장소를 통해 패키지 관리 및 자동 업데이트를 지원합니다. 저장소를 신뢰하기 위해 인증 키를 등록하고 리스트를 추가합니다. 이 작업은 한 번만 수행하면 됩니다.",
      package: "2. 서버 패키지 설치",
      packagePrompt: "주요 입력 항목",
      packageItems: [
        "도메인",
        "와일드카드 사용 여부",
        "TCP/UDP 포트 범위",
        "토큰 화이트리스트",
        "대시보드 ID / 비밀번호",
        "Let's Encrypt 사용 여부",
      ],
      verify: "3. 설치 확인",
      dashboard: "대시보드 접속",
      status: "서비스 상태",
      logs: "실시간 로그",
      health: "헬스 체크",
    },
  },
  en: {
    metaTitle: "PeerSend Tunnel Server Guide",
    metaDescription: "Guide to building your own PeerSend tunnel server",
    home: "Home",
    tag: "Tunnel Server",
    title: "Build Your Own Tunnel Server",
    intro:
      "If you run your own tunnel server instead of using the public one, PeerSend devices can stay connected and exchange files even when they are not on the same network. The guide below is based on an Ubuntu server setup.",
    installGuide: "Installation",
    requirements: "Requirements",
    server: "Server",
    points: "Key points",
    requirementsItems: [
      "Ubuntu 20.04+ (24.04 recommended)",
      "Public ports: 80, 443, app port (default 8080), and TCP/UDP port ranges used for tunneling",
      "Python 3.10+, Nginx (installed automatically by the script)",
      "Domain and DNS configuration (wildcard subdomain support requires *.example.com)",
    ],
    pointItems: [
      "The tunnel server does not store files; it only bridges connections between devices.",
      "Preparing your domain and wildcard DNS helps connect multiple devices more reliably.",
      "Token whitelists and dashboard accounts let you control who can access the server.",
    ],
    sections: {
      install: "1. Server installation (Ubuntu/Debian)",
      installBody:
        "The Tunneler APT repository supports package management and automatic updates. Register the signing key and add the repository list to trust the source. This only needs to be done once.",
      package: "2. Install the server package",
      packagePrompt: "Main configuration inputs",
      packageItems: [
        "Domain",
        "Whether to use wildcard subdomains",
        "TCP/UDP port ranges",
        "Token whitelist",
        "Dashboard ID / password",
        "Whether to use Let's Encrypt",
      ],
      verify: "3. Verify installation",
      dashboard: "Dashboard access",
      status: "Service status",
      logs: "Live logs",
      health: "Health check",
    },
  },
};

export const privacyContent = {
  ko: {
    metaTitle: "PeerSend 개인정보처리방침",
    metaDescription: "PeerSend 개인정보처리방침",
    home: "홈으로",
    tag: "Privacy Policy",
    title: "PeerSend 개인정보처리방침",
    intro:
      'PeerSend(이하 "회사")은 이용자의 개인정보를 중요하게 생각하며, 관련 법령을 준수합니다. 회사는 기본적으로 이용자의 개인정보를 수집하거나 저장하지 않으며, 공개 터널 서버를 이용하는 경우에 한해 제한적인 범위에서 IP 정보가 처리될 수 있습니다.',
    preface: "본 개인정보처리방침은 회사가 제공하는 PeerSend 앱 및 관련 서비스에 적용됩니다.",
    sections: [
      {
        title: "1. 수집하는 개인정보",
        paragraphs: [
          "회사는 현재 이용자로부터 다음과 같은 정보를 수집하지 않습니다.",
          "다만, 공개 터널 서버를 이용하는 경우에는 기기 연결을 중계하고 보안 및 운영 상태를 확인하기 위해 IP 정보가 처리될 수 있습니다.",
        ],
        list: [
          "이름",
          "이메일 주소",
          "전화번호",
          "생년월일",
          "프로필 정보",
          "위치정보",
          "기기 식별정보",
          "쿠키",
          "서비스 이용기록",
          "기타 이용자를 식별할 수 있는 개인정보",
        ],
      },
      {
        title: "2. 개인정보의 처리 목적",
        paragraphs: [
          "회사는 일반적인 서비스 이용 과정에서 이용자의 개인정보를 수집하지 않습니다. 다만, 공개 터널 서버를 이용하는 경우에는 기기 간 연결 중계, 서비스 보안 유지, 비정상 접근 대응, 운영 상태 확인을 위해 IP 정보가 제한적으로 처리될 수 있습니다.",
        ],
      },
      {
        title: "3. 개인정보의 보유 및 이용기간",
        paragraphs: [
          "회사는 이용자의 개인정보를 별도로 수집하거나 장기 보관하지 않습니다. 다만, 공개 터널 서버 운영 과정에서 처리되는 IP 정보는 연결 안정성, 보안 점검, 장애 대응에 필요한 최소한의 범위와 기간 내에서만 취급되며, 관련 법령상 보관 의무가 없는 한 필요 목적이 달성된 후 지체 없이 삭제됩니다.",
        ],
      },
      {
        title: "4. 개인정보의 제3자 제공",
        paragraphs: [
          "회사는 이용자의 개인정보를 제3자에게 판매하거나 제공하지 않습니다. 다만, 법령에 따른 요구가 있는 경우에는 관련 법령이 정한 절차와 범위 내에서 제공될 수 있습니다.",
        ],
      },
      {
        title: "5. 개인정보의 처리 위탁",
        paragraphs: [
          "회사는 이용자의 개인정보를 처리업무 위탁 형태로 외부 업체에 제공하지 않습니다. 다만, 서비스 운영에 필요한 인프라 환경을 이용하는 과정에서 네트워크 수준의 정보가 해당 환경에서 처리될 수 있으며, 이 경우 관련 사업자의 정책이 적용될 수 있습니다.",
        ],
      },
      {
        title: "6. 개인정보의 파기",
        paragraphs: [
          "회사는 이용자의 개인정보를 별도로 수집·보관하지 않습니다. 다만, 공개 터널 서버 운영 과정에서 일시적으로 처리될 수 있는 IP 정보 또는 보안 로그는 목적 달성 후 복구 불가능한 방식으로 삭제하거나 관련 법령에 따라 안전하게 관리 후 파기합니다.",
        ],
      },
      {
        title: "7. 개인정보의 안전성 확보 조치",
        paragraphs: [
          "회사는 이용자의 개인정보를 별도로 수집하지 않지만, 공개 터널 서버 운영 중 처리될 수 있는 IP 정보 및 관련 로그에 대해서는 접근 통제, 최소 권한 원칙, 운영 환경 점검 등 필요한 보안 조치를 유지하기 위해 노력합니다.",
        ],
      },
      {
        title: "8. 자동 수집 장치의 설치·운영",
        paragraphs: [
          "회사는 현재 쿠키, 광고식별자, 분석도구 등 이용자를 추적하거나 식별할 수 있는 기술을 사용하지 않습니다.",
          "다만, 앱 마켓 또는 운영체제 제공 사업자(예: Apple, Google) 측에서 서비스 제공 과정상 별도의 정보를 처리할 수 있으며, 이에 대해서는 각 사업자의 정책이 적용될 수 있습니다.",
        ],
      },
      {
        title: "9. 이용자의 권리",
        paragraphs: [
          "회사는 일반적인 서비스 이용 과정에서 이용자의 개인정보를 수집하지 않으므로, 열람·정정·삭제·처리정지 요청의 대상이 되는 정보가 제한적일 수 있습니다. 다만, 공개 터널 서버 이용과 관련한 문의가 있는 경우 아래 연락처를 통해 문의하실 수 있습니다.",
          "개인정보처리방침과 관련하여 문의가 있는 경우 아래 연락처로 문의하실 수 있습니다.",
        ],
      },
      {
        title: "10. 문의처",
        list: ["담당자: 곽태근", "이메일: rhkr8521@rhkr8521.com", "전화번호: +82 10-6576-8521"],
      },
      {
        title: "11. 개인정보처리방침의 변경",
        paragraphs: [
          "회사는 관련 법령, 서비스 내용 또는 정책 변경에 따라 본 개인정보처리방침을 수정할 수 있습니다. 변경 시 앱 내 공지 또는 별도 안내를 통해 고지합니다.",
          "시행일자: 2026-04-03",
        ],
        emphasisLast: true,
      },
    ],
  },
  en: {
    metaTitle: "PeerSend Privacy Policy",
    metaDescription: "PeerSend Privacy Policy",
    home: "Home",
    tag: "Privacy Policy",
    title: "PeerSend Privacy Policy",
    intro:
      'PeerSend ("Company") values users\' privacy and complies with applicable laws. The Company does not normally collect or store personal data, though IP information may be processed in a limited scope when the public tunnel server is used.',
    preface: "This Privacy Policy applies to the PeerSend app and related services provided by the Company.",
    sections: [
      {
        title: "1. Personal information we collect",
        paragraphs: [
          "The Company does not currently collect the following information from users.",
          "However, when the public tunnel server is used, IP information may be processed in order to relay device connections and verify security and operational status.",
        ],
        list: [
          "Name",
          "Email address",
          "Phone number",
          "Date of birth",
          "Profile information",
          "Location information",
          "Device identifiers",
          "Cookies",
          "Service usage records",
          "Other personal data that can identify a user",
        ],
      },
      {
        title: "2. Purpose of processing personal information",
        paragraphs: [
          "The Company does not collect personal information during ordinary use of the service. However, when the public tunnel server is used, IP information may be processed in a limited scope for device connection relay, service security, abnormal access response, and operational monitoring.",
        ],
      },
      {
        title: "3. Retention and use period",
        paragraphs: [
          "The Company does not separately collect or retain personal information long term. However, IP information processed during public tunnel server operation is handled only within the minimum scope and period needed for connection stability, security review, and incident response, and is deleted without delay once the purpose has been fulfilled unless retention is required by law.",
        ],
      },
      {
        title: "4. Provision to third parties",
        paragraphs: [
          "The Company does not sell or provide users' personal information to third parties. However, information may be provided within the scope required by applicable law when legally requested.",
        ],
      },
      {
        title: "5. Outsourcing of processing",
        paragraphs: [
          "The Company does not entrust users' personal information to external processors. However, network-level information may be handled by infrastructure used for service operation, and in such cases the policies of the relevant provider apply.",
        ],
      },
      {
        title: "6. Deletion of personal information",
        paragraphs: [
          "The Company does not separately collect or store personal information. However, IP information or security logs that may be processed temporarily during public tunnel server operation are deleted in an irrecoverable manner once the purpose is fulfilled, or are securely managed and destroyed in accordance with applicable law.",
        ],
      },
      {
        title: "7. Security measures",
        paragraphs: [
          "Although the Company does not separately collect personal information, it strives to maintain necessary security measures such as access control, least-privilege operation, and environment review for IP information and related logs that may be processed during public tunnel server operation.",
        ],
      },
      {
        title: "8. Automatic collection technologies",
        paragraphs: [
          "The Company does not currently use cookies, advertising identifiers, analytics tools, or similar technologies that track or identify users.",
          "However, app stores or platform providers such as Apple and Google may process separate information in the course of providing their own services, and their policies apply in those cases.",
        ],
      },
      {
        title: "9. User rights",
        paragraphs: [
          "Because the Company does not collect personal information during ordinary use of the service, the information subject to requests for access, correction, deletion, or suspension of processing may be limited. However, if you have questions related to the public tunnel server, you may contact us through the information below.",
          "If you have any questions regarding this Privacy Policy, please contact us using the details below.",
        ],
      },
      {
        title: "10. Contact",
        list: ["Manager: Taegeun Gwak", "Email: rhkr8521@rhkr8521.com", "Phone: +82 10-6576-8521"],
      },
      {
        title: "11. Changes to this Privacy Policy",
        paragraphs: [
          "The Company may update this Privacy Policy in accordance with changes in applicable law, service content, or policy. If changes are made, notice will be provided through the app or by another appropriate method.",
          "Effective date: 2026-04-03",
        ],
        emphasisLast: true,
      },
    ],
  },
};

export const termsContent = {
  ko: {
    metaTitle: "PeerSend 이용약관",
    metaDescription: "PeerSend 이용약관",
    home: "홈으로",
    tag: "Terms of Service",
    title: "PeerSend 이용약관",
    intro:
      "본 약관은 PeerSend가 제공하는 앱, 웹사이트, 로컬 엔진, 공개 터널 서버 및 관련 서비스의 이용 조건과 책임 범위를 정합니다. 서비스를 이용하는 경우 본 약관에 동의한 것으로 봅니다.",
    sections: [
      {
        title: "1. 서비스의 내용",
        paragraphs: [
          "PeerSend는 PC와 휴대폰, 또는 기기와 기기 사이에서 파일을 전송할 수 있도록 지원하는 P2P 기반 서비스입니다. 서비스는 LAN 기반 직접 연결, 터널 서버를 통한 연결, 로컬 엔진, 웹 UI, 모바일 앱 등으로 구성될 수 있습니다.",
          "회사는 서비스의 기능, 구성, 제공 방식을 운영상 필요에 따라 변경하거나 일부를 중단할 수 있습니다.",
        ],
      },
      {
        title: "2. 이용자의 책임",
        paragraphs: [
          "이용자는 관련 법령과 본 약관을 준수하여 서비스를 이용해야 하며, 자신의 기기, 계정, 네트워크 환경, 저장된 파일 및 전송 대상에 대한 책임을 부담합니다.",
        ],
        list: [
          "전송하는 파일의 적법성 및 권한 보유 여부를 스스로 확인해야 합니다.",
          "기기 보안, 저장 공간, 네트워크 환경, 방화벽 설정 등은 이용자가 관리해야 합니다.",
          "공개 터널 서버 또는 직접 구축한 터널 서버의 접근 정보는 안전하게 관리해야 합니다.",
        ],
      },
      {
        title: "3. 금지행위",
        paragraphs: ["이용자는 다음 행위를 해서는 안 됩니다."],
        list: [
          "불법 파일, 악성코드, 저작권 침해 자료, 타인의 권리를 침해하는 자료의 전송",
          "서비스 또는 터널 서버를 무단 스캔, 공격, 우회, 과도한 요청 전송에 이용하는 행위",
          "타인의 기기나 네트워크를 무단으로 이용하거나 연결을 방해하는 행위",
          "서비스를 이용해 법령, 공공질서 또는 선량한 풍속에 반하는 행위를 하는 경우",
        ],
      },
      {
        title: "4. 공개 터널 서버 이용 시 책임 범위",
        paragraphs: [
          "공개 터널 서버는 기기 간 연결을 중계하기 위한 용도로 제공되며, 파일을 중앙 서버에 저장하거나 보관하는 방식으로 동작하지 않습니다.",
          "다만, 네트워크 구조와 운영 특성상 연결을 중계하고 서비스 안정성 및 보안을 유지하기 위해 IP 정보 등 제한적인 네트워크 정보가 처리될 수 있습니다.",
          "이용자는 공개 터널 서버를 사용함으로써 발생할 수 있는 네트워크 지연, 연결 실패, 외부 환경 변수, 제3자 네트워크 문제에 대해 이해하고 이를 감수해야 합니다.",
        ],
      },
      {
        title: "5. 서비스 중단 및 변경",
        paragraphs: [
          "회사는 시스템 점검, 보안 대응, 서비스 개선, 정책 변경, 외부 인프라 문제, 불가항력적 사유 등이 있는 경우 서비스의 전부 또는 일부를 일시적으로 제한하거나 중단할 수 있습니다.",
          "회사는 서비스 안정성과 운영상 필요에 따라 기능, 디자인, 접속 방식, 지원 플랫폼, 제공 범위를 변경할 수 있습니다.",
        ],
      },
      {
        title: "6. 보증 제한 및 면책",
        paragraphs: [
          "회사는 서비스를 현 상태 그대로 제공합니다. 회사는 서비스가 항상 중단 없이 제공되거나, 특정 목적에 적합하거나, 모든 환경에서 오류 없이 동작함을 보증하지 않습니다.",
          "위와 같은 사유로 발생한 손해에 대하여 회사는 관련 법령상 허용되는 범위 내에서 책임을 제한할 수 있습니다.",
        ],
        list: [
          "이용자의 기기 문제, 네트워크 환경, 방화벽, 저장 공간 부족으로 인한 문제",
          "제3자 서비스, 앱 마켓, 운영체제, 인터넷 회선 문제로 인한 장애",
          "이용자의 설정 오류, 부주의, 금지행위로 인해 발생한 손해",
          "직접 구축한 터널 서버의 운영, 설정, 보안 미비로 인해 발생한 문제",
        ],
      },
      {
        title: "7. 지식재산권",
        paragraphs: [
          "서비스, 앱, 웹사이트, 로고, 디자인, 문서, 소프트웨어 및 관련 자료에 대한 권리는 회사 또는 정당한 권리자에게 귀속됩니다. 이용자는 회사의 사전 허락 없이 이를 무단 복제, 배포, 수정, 판매할 수 없습니다.",
        ],
      },
      {
        title: "8. 문의처",
        list: ["담당자: 곽태근", "이메일: rhkr8521@rhkr8521.com", "전화번호: +82 10-6576-8521"],
      },
      {
        title: "9. 약관의 변경 및 고지",
        paragraphs: [
          "회사는 관련 법령, 서비스 구조, 정책 변경에 따라 본 약관을 수정할 수 있습니다. 중요한 변경이 있는 경우 웹사이트, 앱 또는 기타 적절한 수단을 통해 사전에 안내합니다.",
          "변경된 약관 시행 이후에도 서비스를 계속 이용하는 경우, 변경 사항에 동의한 것으로 봅니다.",
          "시행일자: 2026-04-06",
        ],
        emphasisLast: true,
      },
    ],
  },
  en: {
    metaTitle: "PeerSend Terms of Service",
    metaDescription: "PeerSend Terms of Service",
    home: "Home",
    tag: "Terms of Service",
    title: "PeerSend Terms of Service",
    intro:
      "These Terms govern the conditions of use and scope of responsibility for the app, website, local engine, public tunnel server, and related services provided by PeerSend. By using the service, you are deemed to have agreed to these Terms.",
    sections: [
      {
        title: "1. Description of the service",
        paragraphs: [
          "PeerSend is a P2P-based service that supports file transfer between PCs and phones, or between devices. The service may include direct LAN connections, tunnel-server-based connections, a local engine, a web UI, and mobile apps.",
          "The Company may change service functions, structure, and delivery methods, or discontinue parts of the service as needed for operation.",
        ],
      },
      {
        title: "2. User responsibilities",
        paragraphs: [
          "Users must use the service in compliance with applicable law and these Terms, and are responsible for their own devices, accounts, network environment, stored files, and transfer targets.",
        ],
        list: [
          "Users must verify the legality of transferred files and whether they hold the necessary rights.",
          "Users are responsible for device security, available storage, network environment, and firewall settings.",
          "Access information for the public tunnel server or a self-hosted tunnel server must be managed securely.",
        ],
      },
      {
        title: "3. Prohibited conduct",
        paragraphs: ["Users must not engage in the following acts."],
        list: [
          "Transferring illegal files, malware, infringing material, or content that violates the rights of others",
          "Using the service or tunnel server for unauthorized scanning, attacks, bypass attempts, or excessive requests",
          "Using another person's device or network without permission or interfering with connections",
          "Using the service for acts that violate law, public order, or good morals",
        ],
      },
      {
        title: "4. Scope of responsibility when using the public tunnel server",
        paragraphs: [
          "The public tunnel server is provided for bridging connections between devices and does not operate by centrally storing or retaining files.",
          "However, due to the nature of network operation, limited network information such as IP addresses may be processed in order to relay connections and maintain service stability and security.",
          "Users must understand and accept the possibility of network delay, connection failure, external environment variables, and third-party network issues when using the public tunnel server.",
        ],
      },
      {
        title: "5. Service interruption and changes",
        paragraphs: [
          "The Company may temporarily limit or suspend all or part of the service in the event of system maintenance, security response, service improvements, policy changes, external infrastructure problems, or force majeure.",
          "The Company may change functions, design, connection methods, supported platforms, and scope of service as needed for stable operation.",
        ],
      },
      {
        title: "6. Disclaimer and limitation of warranty",
        paragraphs: [
          "The service is provided as-is. The Company does not guarantee uninterrupted availability, fitness for a particular purpose, or error-free operation in every environment.",
          "The Company's liability may be limited to the extent permitted by applicable law for damage caused by the circumstances described below.",
        ],
        list: [
          "Problems caused by a user's device, network environment, firewall, or lack of storage",
          "Failures caused by third-party services, app stores, operating systems, or internet connectivity",
          "Damage caused by user misconfiguration, negligence, or prohibited conduct",
          "Problems caused by operation, configuration, or security issues of a self-hosted tunnel server",
        ],
      },
      {
        title: "7. Intellectual property",
        paragraphs: [
          "Rights to the service, app, website, logo, design, documentation, software, and related materials belong to the Company or the rightful owner. Users may not copy, distribute, modify, or sell them without prior permission.",
        ],
      },
      {
        title: "8. Contact",
        list: ["Manager: Taegeun Gwak", "Email: rhkr8521@rhkr8521.com", "Phone: +82 10-6576-8521"],
      },
      {
        title: "9. Changes to the Terms and notice",
        paragraphs: [
          "The Company may revise these Terms in accordance with changes in applicable law, service structure, or policy. Important changes will be announced in advance through the website, app, or another appropriate means.",
          "If you continue to use the service after the revised Terms take effect, you are deemed to have agreed to the changes.",
          "Effective date: 2026-04-06",
        ],
        emphasisLast: true,
      },
    ],
  },
};

export const noticeContent = {
  ko: {
    metaTitle: "PeerSend 공지사항",
    metaDescription: "PeerSend 공지사항과 서비스 안내",
    home: "홈으로",
    tag: "Notice",
    title: "공지사항",
    intro: "PeerSend 서비스와 앱, 웹, 엔진 관련 주요 공지와 변경 사항을 안내합니다.",
    columns: {
      number: "번호",
      title: "제목",
      date: "등록일",
    },
    empty: "등록된 공지사항이 없습니다.",
    pagination: {
      prev: "이전",
      next: "다음",
    },
    posts: [],
  },
  en: {
    metaTitle: "PeerSend Notice",
    metaDescription: "PeerSend notices and service updates",
    home: "Home",
    tag: "Notice",
    title: "Notice",
    intro: "This page shares major notices and updates about the PeerSend service, apps, web UI, and desktop engine.",
    columns: {
      number: "No.",
      title: "Title",
      date: "Date",
    },
    empty: "There are no notices yet.",
    pagination: {
      prev: "Prev",
      next: "Next",
    },
    posts: [],
  },
};

export const notFoundContent = {
  ko: {
    metaTitle: "페이지를 찾을 수 없습니다",
    metaDescription: "요청한 페이지를 찾을 수 없습니다.",
    tag: "404 Not Found",
    title: "페이지를 찾을 수 없습니다",
    intro: "입력한 주소가 변경되었거나 삭제되었을 수 있습니다.",
    home: "홈으로",
    download: "다운로드로 이동",
  },
  en: {
    metaTitle: "Page not found",
    metaDescription: "The page you requested could not be found.",
    tag: "404 Not Found",
    title: "Page not found",
    intro: "The address may have changed or the page may no longer exist.",
    home: "Back to Home",
    download: "Go to Download",
  },
};
