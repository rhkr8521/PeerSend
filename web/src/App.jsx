import { useEffect, useMemo, useRef, useState } from "react";

const LOCAL_ORIGIN = import.meta.env.VITE_PEERSEND_LOCAL_ORIGIN || "http://127.0.0.1:8765";
const CUSTOM_PROTOCOL = import.meta.env.VITE_PEERSEND_PROTOCOL || "peersend://launch";
const REQUIRED_ENGINE_VERSION = import.meta.env.VITE_PEERSEND_REQUIRED_ENGINE_VERSION || "1.2.2";
const DOWNLOAD_URLS = {
  windows:
    import.meta.env.VITE_PEERSEND_WINDOWS_DOWNLOAD_URL ||
    "https://api-bucket.rhkr8521.com/peersend/download/windows/x64/PeerSend-Setup.exe",
  macosArm64:
    import.meta.env.VITE_PEERSEND_MACOS_ARM64_DOWNLOAD_URL ||
    "https://api-bucket.rhkr8521.com/peersend/download/macos/arm64/PeerSend.dmg",
  macosIntel:
    import.meta.env.VITE_PEERSEND_MACOS_INTEL_DOWNLOAD_URL ||
    "https://api-bucket.rhkr8521.com/peersend/download/macos/x64/PeerSend.dmg",
  linux: import.meta.env.VITE_PEERSEND_LINUX_DOWNLOAD_URL || "/downloads/PeerSend.AppImage",
  android:
    import.meta.env.VITE_PEERSEND_ANDROID_DOWNLOAD_URL ||
    "https://play.google.com/store/apps/details?id=com.rhkr8521.p2ptransfer",
  ios: import.meta.env.VITE_PEERSEND_IOS_DOWNLOAD_URL || "https://apps.apple.com/app/id0000000000",
};
const LOCAL_HOSTS = new Set(["127.0.0.1", "localhost"]);
const APP_ROUTE = "/";
const DOWNLOAD_ROUTE = "/download";
const MOBILE_DOWNLOAD_ROUTE = "/download/mobile";
const PROTOCOL_LAUNCH_KEY = "peersend-last-launch-at";
const ENGINE_PRELAUNCH_POLL_MS = 1500;
const ENGINE_POSTLAUNCH_POLL_MS = 5000;
const ENGINE_BOOTSTRAP_STEP_MS = 500;
const ENGINE_RELAUNCH_COOLDOWN_MS = 20000;

const EMPTY_STATE = {
  mode: "lan",
  engineVersion: "",
  myName: "Desktop",
  myIp: "-",
  savePath: "",
  language: "en",
  lanPeers: [],
  tunnelPeers: [],
  tunnelStatus: "Starting...",
  tunnelSubdomain: "",
  tunnelConnected: false,
  tunnelRegistered: false,
  tunnelPort: 0,
  tunnelSettings: {
    usePublicTunnel: true,
    host: "",
    ssl: true,
    token: "",
  },
  pendingRequest: null,
  transferProgress: null,
  isBusy: false,
};

const COPY = {
  ko: {
    app_name: "PeerSend",
    hero_kicker: "Desktop",
    hero_subtitle: "PC와 휴대폰, 또는 휴대폰끼리 빠르게 주고받는 P2P 파일 전송",
    hero_body:
      "같은 Wi-Fi에서는 LAN 모드로 주변 기기를 자동으로 찾고, 외부 네트워크에서는 터널 모드로 연결해 사진, 동영상, 문서, 압축 파일을 손쉽게 전송할 수 있습니다.",
    hero_engine_online: "로컬 엔진 연결됨",
    hero_web_control: "브라우저 제어 중",
    peer_section_hint_lan: "같은 네트워크의 PeerSend 기기를 찾아 바로 전송할 수 있습니다.",
    peer_section_hint_tunnel: "외부 네트워크에서도 같은 터널 서버에 연결된 기기를 선택할 수 있습니다.",
    save_path_hint: "수신 파일을 저장할 기본 폴더를 지정합니다.",
    tunnel_settings_hint: "공개 터널 또는 외부 터널 서버 연결 정보를 관리합니다.",
    tunnel_registered_hint: "연결됨 · {subdomain} / port:{port}",
    tunnel_registering_hint: "연결 중 · {subdomain}",
    my_device: "내 기기",
    my_ip: "IP",
    save_path: "저장 경로",
    lan_mode: "LAN 모드",
    tunnel_mode: "터널 모드",
    refresh: "새로고침",
    lan_status: "LAN 상태",
    tunnel_status: "터널 상태",
    lan_searching: "LAN 기기 검색중",
    tunnel_searching: "터널 기기 검색중",
    devices_found: "{count}개의 기기를 찾았습니다",
    tunnel_peers: "터널 기기 목록",
    lan_peers: "LAN 기기 목록",
    send_files: "파일 전송",
    selected: "선택됨",
    select_peer_first: "전송 대상을 먼저 선택해 주세요.",
    loading: "불러오는 중...",
    loading_body: "로컬 엔진 상태를 불러오고 있습니다.",
    no_tunnel_peers: "아직 터널 기기가 없습니다",
    no_tunnel_peers_body: "터널 네트워크에 있는 다른 PeerSend 기기를 잠시 기다려 주세요.",
    no_lan_peers: "LAN 기기를 찾지 못했습니다",
    no_lan_peers_body: "같은 네트워크에 있는 다른 PeerSend 기기를 잠시 기다려 주세요.",
    storage: "저장",
    directory: "폴더 경로",
    apply: "적용",
    choose_folder: "폴더 선택",
    tunnel: "터널",
    tunnel_settings: "터널 설정",
    show: "보기",
    hide: "숨기기",
    host: "서버 주소",
    token: "토큰",
    use_ssl: "SSL 사용",
    use_public_tunnel: "공개 터널 접속",
    apply_reconnect: "적용 및 재연결",
    my_subdomain: "내 서브도메인",
    transfer: "전송",
    cancel: "취소",
    current_item: "현재 항목",
    remaining: "남음",
    choose_transfer_mode: "전송 방식 선택",
    choose_transfer_body:
      "선택한 파일들을 어떻게 보낼까요?\n\n예: ZIP 하나로 묶어서 전송\n아니오: 파일별로 개별 전송",
    yes: "예",
    no: "아니오",
    receive_files: "파일을 받으시겠습니까?",
    preparing_transfer_title: "전송 준비중",
    preparing_transfer_body: "상대방의 수신 확인을 기다리고 있습니다.",
    files_count: "{count}개 파일",
    file_count_one: "파일 1개",
    reject: "거절",
    accept: "수락",
    file_picker_failed: "파일 선택 창을 열지 못했습니다.",
    realtime_unstable: "실시간 연결이 불안정합니다.",
    realtime_closed: "로컬 엔진 연결이 종료되었습니다. 다시 연결을 시도합니다.",
    engine_unresponsive: "로컬 엔진이 응답하지 않습니다. 다시 연결을 시도합니다.",
    folder_picker_failed: "폴더 선택 창을 열지 못했습니다.",
    launcher_checking_title: "로컬 엔진 확인 중",
    launcher_checking_body: "이 PC에 설치된 PeerSend 엔진이 있는지 확인하고 있습니다.",
    launcher_starting_title: "엔진 시작 시도 중",
    launcher_starting_body:
      "설치된 엔진이 있다면 자동으로 실행을 시도합니다, 엔진 실행에 조금 시간이 걸릴 수 있습니다 브라우저를 끄지 말고 기다려주세요.",
    launcher_install_title: "PeerSend 엔진 설치 필요",
    launcher_install_body: "이 브라우저에서 PeerSend를 사용하려면 먼저 PC용 로컬 엔진을 설치해야 합니다.",
    engine_update_required_title: "엔진 업데이트 필요",
    engine_update_required_body:
      "현재 PC에 설치된 엔진 버전은 {installed} 입니다. 안정적인 사용을 위하여 현재 최신 엔진 버전({required})으로 업데이트 해주시기 바랍니다.",
    launcher_retry: "다시 확인",
    launcher_open_engine: "엔진 실행 시도",
    launcher_downloads: "엔진 다운로드",
    mobile_title: "모바일에서는 앱을 설치해 주세요",
    mobile_body: "휴대폰과 태블릿에서는 PeerSend 모바일 앱으로 이용해 주세요.",
    download_android: "Android 다운로드",
    download_ios: "iPhone / iPad 다운로드",
    download_page_title: "PeerSend 다운로드",
    desktop_downloads: "PC 엔진 다운로드",
    desktop_downloads_body: "각 OS에 맞는 엔진을 설치 후 다시 엔진 실행 시도 버튼을 눌러주세요.",
    mobile_downloads: "모바일 앱 다운로드",
    mobile_downloads_body: "모바일에서는 Android 또는 iPhone / iPad 앱을 설치해서 사용해 주세요.",
    windows: "Windows",
    macos: "macOS",
    linux: "Linux",
    download_for_windows: "Windows 다운로드",
    download_for_macos: "macOS 다운로드",
    download_for_linux: "Linux 다운로드",
    apple_silicon: "Apple Silicon",
    intel: "Intel",
    close: "닫기",
    macos_download_guide_title: "macOS 설치 안내",
    macos_download_guide_body:
      "PeerSend.dmg 를 실행한후 PeerSend를 Applications에 복사하면 설치가 완료됩니다.",
    open_peersend: "PeerSend 열기",
    go_home: "홈으로",
    downloads_ready: "다운로드 링크가 준비되면 여기에 표시됩니다.",
    local_engine_ready: "로컬 엔진이 준비되었습니다. PeerSend 열기 버튼을 눌러주세요.",
    mobile_redirect_notice: "모바일 환경이 감지되어 앱 다운로드 화면으로 이동했습니다.",
    coming_soon: "준비 중",
  },
  en: {
    app_name: "PeerSend",
    hero_kicker: "Desktop",
    hero_subtitle: "Fast P2P file transfer between PC and phone, or phone to phone",
    hero_body:
      "Use LAN mode to discover nearby devices on the same Wi-Fi automatically, or Tunnel mode to connect across external networks and send photos, videos, documents, archives, and more.",
    hero_engine_online: "Local engine online",
    hero_web_control: "Controlled from the browser",
    peer_section_hint_lan: "Find PeerSend devices on the same network and send files right away.",
    peer_section_hint_tunnel: "Select devices connected to the same tunnel server, even across external networks.",
    save_path_hint: "Choose the default folder where received files will be stored.",
    tunnel_settings_hint: "Manage public tunnel access or your own external tunnel server settings.",
    tunnel_registered_hint: "Connected · {subdomain} / port:{port}",
    tunnel_registering_hint: "Connecting · {subdomain}",
    my_device: "My Device",
    my_ip: "IP",
    save_path: "Save Path",
    lan_mode: "LAN Mode",
    tunnel_mode: "Tunnel Mode",
    refresh: "Refresh",
    lan_status: "LAN Status",
    tunnel_status: "Tunnel Status",
    lan_searching: "Searching LAN devices",
    tunnel_searching: "Searching tunnel devices",
    devices_found: "Found {count} devices",
    tunnel_peers: "Tunnel Peers",
    lan_peers: "LAN Peers",
    send_files: "Send Files",
    selected: "Selected",
    select_peer_first: "Select a target device first.",
    loading: "Loading...",
    loading_body: "Loading the local engine state.",
    no_tunnel_peers: "No tunnel peers yet",
    no_tunnel_peers_body: "Wait a moment for another PeerSend device on the tunnel network.",
    no_lan_peers: "No LAN peers found",
    no_lan_peers_body: "Wait a moment for another PeerSend device on the same network.",
    storage: "Storage",
    directory: "Directory",
    apply: "Apply",
    choose_folder: "Choose Folder",
    tunnel: "Tunnel",
    tunnel_settings: "Tunnel Settings",
    show: "Show",
    hide: "Hide",
    host: "Host",
    token: "Token",
    use_ssl: "Use SSL",
    use_public_tunnel: "Use Public Tunnel",
    apply_reconnect: "Apply & Reconnect",
    my_subdomain: "My Subdomain",
    transfer: "Transfer",
    cancel: "Cancel",
    current_item: "Current item",
    remaining: "remaining",
    choose_transfer_mode: "Choose Transfer Mode",
    choose_transfer_body:
      "How should the selected files be sent?\n\nYes: send as one ZIP file\nNo: send each file individually",
    yes: "Yes",
    no: "No",
    receive_files: "Receive Files?",
    preparing_transfer_title: "Preparing Transfer",
    preparing_transfer_body: "Waiting for the receiver to confirm the transfer.",
    files_count: "{count} files",
    file_count_one: "1 file",
    reject: "Reject",
    accept: "Accept",
    file_picker_failed: "Failed to open the file picker.",
    realtime_unstable: "The realtime connection is unstable.",
    realtime_closed: "The local engine connection closed. Reconnecting now.",
    engine_unresponsive: "The local engine is not responding. Reconnecting now.",
    folder_picker_failed: "Failed to open the folder picker.",
    launcher_checking_title: "Checking the local engine",
    launcher_checking_body: "Looking for an installed PeerSend engine on this computer.",
    launcher_starting_title: "Starting the engine",
    launcher_starting_body:
      "If PeerSend is installed, it will try to launch automatically. Starting the engine may take a little time, so please keep your browser open and wait.",
    launcher_install_title: "PeerSend engine required",
    launcher_install_body: "Install the local PC engine first to use PeerSend from this browser.",
    engine_update_required_title: "Engine Update Required",
    engine_update_required_body:
      "The engine version currently installed on this PC is {installed}. For stable use, please update to the latest engine version ({required}).",
    launcher_retry: "Check Again",
    launcher_open_engine: "Try to Launch Engine",
    launcher_downloads: "Download Engine",
    mobile_title: "Install the mobile app",
    mobile_body: "On phones and tablets, please use the PeerSend mobile app.",
    download_android: "Download for Android",
    download_ios: "Download for iPhone / iPad",
    download_page_title: "Download PeerSend",
    desktop_downloads: "Desktop Engine Downloads",
    desktop_downloads_body: "Install the engine for your operating system, then press Try to Launch Engine again.",
    mobile_downloads: "Mobile App Downloads",
    mobile_downloads_body: "On mobile, install the Android or iPhone / iPad app.",
    windows: "Windows",
    macos: "macOS",
    linux: "Linux",
    download_for_windows: "Download for Windows",
    download_for_macos: "Download for macOS",
    download_for_linux: "Download for Linux",
    apple_silicon: "Apple Silicon",
    intel: "Intel",
    close: "Close",
    macos_download_guide_title: "macOS Installation Guide",
    macos_download_guide_body:
      "Open PeerSend.dmg and copy PeerSend to Applications to complete the installation.",
    open_peersend: "Open PeerSend",
    go_home: "Home",
    downloads_ready: "Download links will appear here when they are ready.",
    local_engine_ready: "The local engine is ready. Press the Open PeerSend button.",
    mobile_redirect_notice: "A mobile device was detected, so you were sent to the app download page.",
    coming_soon: "Coming Soon",
  },
};

function detectLanguage(value) {
  if (Array.isArray(value)) {
    return value.some((item) => String(item || "").toLowerCase().startsWith("ko")) ? "ko" : "en";
  }
  return String(value || "").toLowerCase().startsWith("ko") ? "ko" : "en";
}

function detectBrowserLanguage() {
  const candidates = [];
  if (Array.isArray(navigator.languages) && navigator.languages.length) {
    candidates.push(...navigator.languages);
  }
  candidates.push(navigator.language);
  candidates.push(document.documentElement.lang);
  try {
    candidates.push(Intl.DateTimeFormat().resolvedOptions().locale);
  } catch {
    // ignore locale probing failures
  }
  return detectLanguage(candidates);
}

function detectDesktopPlatform(userAgent, platform) {
  const text = `${userAgent || ""} ${platform || ""}`.toLowerCase();
  if (text.includes("win")) {
    return "windows";
  }
  if (text.includes("mac")) {
    return "macos";
  }
  if (text.includes("linux") || text.includes("x11")) {
    return "linux";
  }
  return "windows";
}

function normalizeRoute(pathname, isMobile) {
  if (pathname === MOBILE_DOWNLOAD_ROUTE) {
    return MOBILE_DOWNLOAD_ROUTE;
  }
  if (pathname === DOWNLOAD_ROUTE) {
    return DOWNLOAD_ROUTE;
  }
  if (isMobile) {
    return MOBILE_DOWNLOAD_ROUTE;
  }
  return APP_ROUTE;
}

function createTranslator(language) {
  const catalog = COPY[language] || COPY.en;
  return (key, params = {}) => {
    const template = catalog[key] || COPY.en[key] || key;
    return template.replace(/\{(\w+)\}/g, (_, name) => String(params[name] ?? ""));
  };
}

function buildUrl(origin, path) {
  return `${String(origin || "").replace(/\/$/, "")}${path}`;
}

function buildWsUrl(origin, path) {
  const url = new URL(buildUrl(origin, path));
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  return url.toString();
}

function formatBytes(value) {
  const size = Number(value || 0);
  if (!Number.isFinite(size) || size <= 0) {
    return "0 B";
  }
  const units = ["B", "KB", "MB", "GB", "TB"];
  let current = size;
  let unitIndex = 0;
  while (current >= 1024 && unitIndex < units.length - 1) {
    current /= 1024;
    unitIndex += 1;
  }
  return `${current.toFixed(unitIndex === 0 ? 0 : 2)} ${units[unitIndex]}`;
}

function formatSpeed(value) {
  const speed = Number(value || 0);
  if (!Number.isFinite(speed) || speed <= 0) {
    return "-";
  }
  return `${formatBytes(speed)}/s`;
}

function formatDuration(value) {
  const seconds = Math.max(0, Math.round(Number(value || 0)));
  if (seconds <= 0) {
    return "-";
  }
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  if (mins <= 0) {
    return `${secs}s`;
  }
  const hours = Math.floor(mins / 60);
  if (hours <= 0) {
    return `${mins}m ${secs}s`;
  }
  return `${hours}h ${mins % 60}m`;
}

function percent(done, total) {
  if (!total) {
    return 0;
  }
  return Math.max(0, Math.min(100, Math.round((done / total) * 100)));
}

function nextId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random()}`;
}

function parseTunnelPort(statusText, fallbackPort) {
  const numericPort = Number(fallbackPort || 0);
  if (Number.isFinite(numericPort) && numericPort > 0) {
    return numericPort;
  }
  const text = String(statusText || "");
  const match = text.match(/(?:port|포트)[^0-9]*(\d+)/i);
  if (!match) {
    return 0;
  }
  const parsed = Number(match[1] || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

async function apiRequest(origin, path, options = {}) {
  const response = await fetch(buildUrl(origin, path), {
    mode: "cors",
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...(options.headers || {}),
    },
  });

  const text = await response.text();
  let data = {};
  if (text) {
    try {
      data = JSON.parse(text);
    } catch (error) {
      throw new Error(`Unexpected response from ${path}`);
    }
  }
  if (!response.ok) {
    throw new Error(data.detail || data.message || "Request failed");
  }
  return data;
}

async function probeLocalEngine() {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 1200);
  try {
    const response = await fetch(buildUrl(LOCAL_ORIGIN, "/api/health"), {
      method: "GET",
      mode: "cors",
      signal: controller.signal,
    });
    if (!response.ok) {
      return null;
    }
    const data = await response.json();
    return data?.ok ? data : null;
  } catch (error) {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

function wait(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function getLastProtocolLaunchAt() {
  try {
    return Number(window.sessionStorage.getItem(PROTOCOL_LAUNCH_KEY) || "0");
  } catch {
    return 0;
  }
}

function markProtocolLaunch() {
  try {
    window.sessionStorage.setItem(PROTOCOL_LAUNCH_KEY, String(Date.now()));
  } catch {
    // ignore sessionStorage failures
  }
}

function hasRecentProtocolLaunch() {
  const lastAttempt = getLastProtocolLaunchAt();
  return lastAttempt > 0 && Date.now() - lastAttempt < ENGINE_RELAUNCH_COOLDOWN_MS;
}

function attemptProtocolLaunch({ direct = false } = {}) {
  markProtocolLaunch();
  if (direct) {
    try {
      window.location.href = CUSTOM_PROTOCOL;
      return;
    } catch {
      // fall through to iframe launch
    }
  }
  const iframe = document.createElement("iframe");
  iframe.style.display = "none";
  iframe.src = CUSTOM_PROTOCOL;
  document.body.appendChild(iframe);
  window.setTimeout(() => iframe.remove(), 2500);
}

function downloadLink(url) {
  return url && url !== "#" ? url : undefined;
}

export default function App() {
  const isMobile = /android|iphone|ipad|ipod|mobile/i.test(navigator.userAgent || "");
  const browserLanguage = detectBrowserLanguage();
  const desktopPlatform = detectDesktopPlatform(navigator.userAgent, navigator.platform);
  const isDirectEngineOrigin = LOCAL_HOSTS.has(window.location.hostname) && window.location.port === "8765";

  const [route, setRoute] = useState(() => normalizeRoute(window.location.pathname, isMobile));
  const [engineConnected, setEngineConnected] = useState(isDirectEngineOrigin);
  const [launchAttempt, setLaunchAttempt] = useState(0);
  const [launcherStage, setLauncherStage] = useState(
    isDirectEngineOrigin ? "ready" : isMobile ? "mobile" : "checking",
  );
  const [state, setState] = useState(EMPTY_STATE);
  const [selectedPeerId, setSelectedPeerId] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [savePathInput, setSavePathInput] = useState("");
  const [savePathDirty, setSavePathDirty] = useState(false);
  const [showTunnelSettings, setShowTunnelSettings] = useState(false);
  const [tunnelForm, setTunnelForm] = useState(EMPTY_STATE.tunnelSettings);
  const [tunnelDirty, setTunnelDirty] = useState(false);
  const [toasts, setToasts] = useState([]);
  const [pendingFiles, setPendingFiles] = useState([]);
  const [showZipChoice, setShowZipChoice] = useState(false);
  const [showTransferPreparing, setShowTransferPreparing] = useState(false);
  const [transferPreparingSettled, setTransferPreparingSettled] = useState(false);
  const sessionIdRef = useRef(nextId());
  const reconnectTimerRef = useRef(null);
  const launcherProbeTimerRef = useRef(null);
  const healthFailureCountRef = useRef(0);
  const [detectedEngineVersion, setDetectedEngineVersion] = useState("");

  const backendOrigin = isDirectEngineOrigin ? window.location.origin : LOCAL_ORIGIN;
  const engineLanguage = detectLanguage(state.language);
  const language =
    browserLanguage === "ko" || engineLanguage === "ko"
      ? "ko"
      : engineConnected
        ? state.language || browserLanguage
        : browserLanguage;
  const t = useMemo(() => createTranslator(language), [language]);
  const peers = state.mode === "tunnel" ? state.tunnelPeers : state.lanPeers;
  const selectedPeer = peers.find((peer) => peer.id === selectedPeerId) || null;
  const showApp = engineConnected && route === APP_ROUTE;

  useEffect(() => {
    document.title = t("app_name");
  }, [t]);

  useEffect(() => {
    const onPopState = () => {
      setRoute(normalizeRoute(window.location.pathname, isMobile));
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [isMobile]);

  useEffect(() => {
    if (!isMobile || route === MOBILE_DOWNLOAD_ROUTE) {
      return;
    }
    window.history.replaceState({}, "", MOBILE_DOWNLOAD_ROUTE);
    setRoute(MOBILE_DOWNLOAD_ROUTE);
  }, [isMobile, route]);

  useEffect(() => {
    return () => {
      if (reconnectTimerRef.current) {
        window.clearTimeout(reconnectTimerRef.current);
      }
      if (launcherProbeTimerRef.current) {
        window.clearTimeout(launcherProbeTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (isDirectEngineOrigin) {
      setEngineConnected(true);
      setLauncherStage("ready");
      return undefined;
    }
    if (isMobile) {
      setLauncherStage("mobile");
      return undefined;
    }
    if (route === MOBILE_DOWNLOAD_ROUTE) {
      return undefined;
    }
    if (engineConnected) {
      return undefined;
    }

    let cancelled = false;

    async function waitForEngine(timeoutMs) {
      const deadline = Date.now() + timeoutMs;
      while (!cancelled && Date.now() < deadline) {
        const engineInfo = await probeLocalEngine();
        if (engineInfo?.ok) {
          return engineInfo;
        }
        await wait(ENGINE_BOOTSTRAP_STEP_MS);
      }
      return null;
    }

    async function bootstrap() {
      setLauncherStage("checking");
      const prelaunchInfo = await waitForEngine(ENGINE_PRELAUNCH_POLL_MS);
      if (prelaunchInfo?.ok) {
        if (!cancelled) {
          setDetectedEngineVersion(String(prelaunchInfo.version || ""));
          setEngineConnected(true);
          setLauncherStage("ready");
        }
        return;
      }

      setLauncherStage("starting");
      if (!hasRecentProtocolLaunch()) {
        attemptProtocolLaunch();
      }

      const postlaunchInfo = await waitForEngine(ENGINE_POSTLAUNCH_POLL_MS);
      if (postlaunchInfo?.ok) {
        if (!cancelled) {
          setDetectedEngineVersion(String(postlaunchInfo.version || ""));
          setEngineConnected(true);
          setLauncherStage("ready");
        }
        return;
      }

      if (!cancelled) {
        setLauncherStage("install");
      }
    }

    void bootstrap();

    return () => {
      cancelled = true;
    };
  }, [engineConnected, isDirectEngineOrigin, isMobile, launchAttempt, route]);

  useEffect(() => {
    if (isDirectEngineOrigin || isMobile || engineConnected || route === MOBILE_DOWNLOAD_ROUTE) {
      return undefined;
    }

    let cancelled = false;

    const checkEngineNow = async () => {
      if (cancelled || engineConnected) {
        return;
      }
      const engineInfo = await probeLocalEngine();
      if (engineInfo?.ok) {
        if (!cancelled) {
          setDetectedEngineVersion(String(engineInfo.version || ""));
          setEngineConnected(true);
          setLauncherStage("ready");
        }
      }
    };

    const scheduleNextProbe = () => {
      if (launcherProbeTimerRef.current) {
        window.clearTimeout(launcherProbeTimerRef.current);
      }
      launcherProbeTimerRef.current = window.setTimeout(async () => {
        await checkEngineNow();
        if (!cancelled && !engineConnected) {
          scheduleNextProbe();
        }
      }, 2000);
    };

    const onFocus = () => {
      void checkEngineNow();
    };

    const onVisibility = () => {
      if (!document.hidden) {
        void checkEngineNow();
      }
    };

    void checkEngineNow();
    scheduleNextProbe();
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      cancelled = true;
      if (launcherProbeTimerRef.current) {
        window.clearTimeout(launcherProbeTimerRef.current);
        launcherProbeTimerRef.current = null;
      }
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [engineConnected, isDirectEngineOrigin, isMobile, launchAttempt, route]);

  useEffect(() => {
    if (!showApp) {
      setIsLoading(false);
      return undefined;
    }

    let mounted = true;
    let closedByCleanup = false;
    setIsLoading(true);

    const loadState = async () => {
      try {
        const nextState = await apiRequest(backendOrigin, "/api/state");
        if (mounted) {
          setDetectedEngineVersion(String(nextState.engineVersion || ""));
          setState(nextState);
        }
      } catch (error) {
        if (!mounted) {
          return;
        }
        setDetectedEngineVersion("");
        setEngineConnected(false);
        setLauncherStage("checking");
        setLaunchAttempt((current) => current + 1);
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    };

    void loadState();

    const socket = new WebSocket(buildWsUrl(backendOrigin, "/ws"));

    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (payload.type === "state") {
          setState(payload.state);
        } else if (payload.type === "toast") {
          pushToast(payload.message);
        }
      } catch (error) {
        pushToast(`WebSocket parse failed: ${error.message}`);
      }
    };

    socket.onerror = () => {
      pushToast(t("realtime_unstable"));
    };

    socket.onclose = () => {
      if (closedByCleanup) {
        return;
      }
      pushToast(t("realtime_closed"));
      setDetectedEngineVersion("");
      setEngineConnected(false);
      setLauncherStage("checking");
      reconnectTimerRef.current = window.setTimeout(() => {
        setLaunchAttempt((current) => current + 1);
      }, 200);
    };

    return () => {
      mounted = false;
      closedByCleanup = true;
      socket.close();
    };
  }, [backendOrigin, showApp, t]);

  useEffect(() => {
    if (!showApp) {
      return undefined;
    }

    const sessionId = sessionIdRef.current;

    const postSession = (path) =>
      fetch(buildUrl(backendOrigin, `${path}/${encodeURIComponent(sessionId)}`), {
        method: "POST",
        mode: "cors",
        keepalive: true,
      }).catch(() => {});

    void postSession("/api/session/open");
    const intervalId = window.setInterval(() => {
      void postSession("/api/session/heartbeat");
    }, 10000);

    const onVisibility = () => {
      if (!document.hidden) {
        void postSession("/api/session/heartbeat");
      }
    };

    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      window.clearInterval(intervalId);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [backendOrigin, showApp]);

  useEffect(() => {
    if (!showApp) {
      healthFailureCountRef.current = 0;
      return undefined;
    }

    let cancelled = false;

    const checkHealth = async () => {
      try {
        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), 2500);
        const response = await fetch(buildUrl(backendOrigin, "/api/health"), {
          method: "GET",
          mode: "cors",
          signal: controller.signal,
        });
        window.clearTimeout(timeoutId);
        if (!response.ok) {
          throw new Error("Health check failed");
        }
        healthFailureCountRef.current = 0;
      } catch (error) {
        healthFailureCountRef.current += 1;
        if (!cancelled && healthFailureCountRef.current >= 2) {
          pushToast(t("engine_unresponsive"));
          setDetectedEngineVersion("");
          setEngineConnected(false);
          setLauncherStage("checking");
          setLaunchAttempt((current) => current + 1);
        }
      }
    };

    const intervalId = window.setInterval(() => {
      void checkHealth();
    }, 5000);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
      healthFailureCountRef.current = 0;
    };
  }, [backendOrigin, showApp, t]);

  useEffect(() => {
    if (!savePathDirty) {
      setSavePathInput(state.savePath || "");
    }
  }, [state.savePath, savePathDirty]);

  useEffect(() => {
    if (!tunnelDirty) {
      setTunnelForm(state.tunnelSettings || EMPTY_STATE.tunnelSettings);
    }
  }, [state.tunnelSettings, tunnelDirty]);

  useEffect(() => {
    if (!peers.some((peer) => peer.id === selectedPeerId)) {
      setSelectedPeerId("");
    }
  }, [peers, selectedPeerId]);

  useEffect(() => {
    if (!showTransferPreparing || !transferPreparingSettled) {
      return;
    }
    if (state.transferProgress || !state.isBusy) {
      setShowTransferPreparing(false);
      setTransferPreparingSettled(false);
    }
  }, [showTransferPreparing, transferPreparingSettled, state.isBusy, state.transferProgress]);

  function navigate(path, replace = false) {
    if (path === route) {
      return;
    }
    if (replace) {
      window.history.replaceState({}, "", path);
    } else {
      window.history.pushState({}, "", path);
    }
    setRoute(path);
  }

  function pushToast(message) {
    const id = nextId();
    setToasts((current) => [...current, { id, message }]);
    window.setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id));
    }, 4500);
  }

  async function handleModeChange(mode) {
    try {
      const nextState = await apiRequest(backendOrigin, "/api/mode", {
        method: "POST",
        body: JSON.stringify({ mode }),
      });
      setState(nextState);
    } catch (error) {
      pushToast(error.message);
    }
  }

  async function handleRefresh() {
    try {
      await apiRequest(backendOrigin, "/api/refresh", { method: "POST" });
    } catch (error) {
      pushToast(error.message);
    }
  }

  async function handleSavePathApply() {
    try {
      const nextState = await apiRequest(backendOrigin, "/api/save-path", {
        method: "POST",
        body: JSON.stringify({ path: savePathInput }),
      });
      setState(nextState);
      setSavePathDirty(false);
    } catch (error) {
      pushToast(error.message);
    }
  }

  async function handleSavePathDialog() {
    try {
      const result = await apiRequest(backendOrigin, "/api/save-path/dialog", { method: "POST" });
      if (result.path) {
        setSavePathInput(result.path);
        setSavePathDirty(false);
      } else if (!result.cancelled) {
        pushToast(t("folder_picker_failed"));
      }
    } catch (error) {
      pushToast(error.message);
    }
  }

  async function handleTunnelApply() {
    try {
      const nextState = await apiRequest(backendOrigin, "/api/tunnel-settings", {
        method: "POST",
        body: JSON.stringify({
          use_public_tunnel: Boolean(tunnelForm.usePublicTunnel),
          host: tunnelForm.host,
          ssl: tunnelForm.ssl,
          token: tunnelForm.token,
        }),
      });
      setState(nextState);
      setTunnelDirty(false);
    } catch (error) {
      pushToast(error.message);
    }
  }

  async function openFileChooser() {
    if (!selectedPeerId || state.isBusy) {
      return;
    }
    try {
      const result = await apiRequest(backendOrigin, "/api/send-files/dialog", { method: "POST" });
      const files = Array.isArray(result.files) ? result.files : [];
      if (!files.length) {
        if (!result.cancelled) {
          pushToast(t("file_picker_failed"));
        }
        return;
      }
      if (files.length > 1) {
        setPendingFiles(files);
        setShowZipChoice(true);
        return;
      }
      void sendSelectedFiles(files, false);
    } catch (error) {
      pushToast(error.message);
    }
  }

  async function sendSelectedFiles(files, useZip) {
    if (!files.length || !selectedPeerId) {
      return;
    }

    setShowTransferPreparing(true);
    setTransferPreparingSettled(false);
    try {
      await apiRequest(backendOrigin, "/api/send-files", {
        method: "POST",
        body: JSON.stringify({
          mode: state.mode,
          peer_id: selectedPeerId,
          use_zip: useZip,
          file_paths: files.map((file) => file.path),
        }),
      });
      setPendingFiles([]);
      setShowZipChoice(false);
      window.setTimeout(() => {
        setTransferPreparingSettled(true);
      }, 1200);
    } catch (error) {
      setShowTransferPreparing(false);
      setTransferPreparingSettled(false);
      pushToast(error.message);
    }
  }

  async function handleIncomingDecision(accept) {
    if (!state.pendingRequest?.requestId) {
      return;
    }
    try {
      await apiRequest(backendOrigin, "/api/respond", {
        method: "POST",
        body: JSON.stringify({
          request_id: state.pendingRequest.requestId,
          accept,
        }),
      });
    } catch (error) {
      pushToast(error.message);
    }
  }

  async function handleCancelTransfer() {
    try {
      await apiRequest(backendOrigin, "/api/cancel", { method: "POST" });
    } catch (error) {
      pushToast(error.message);
    }
  }

  async function handleRetryLaunch() {
    if (!isMobile && !isDirectEngineOrigin && !engineConnected) {
      attemptProtocolLaunch({ direct: true });
    }
    setEngineConnected(false);
    setLauncherStage("starting");
    setLaunchAttempt((current) => current + 1);
  }

  const desktopDownloadLink = downloadLink(DOWNLOAD_URLS[desktopPlatform]);
  const currentEngineVersion = String(state.engineVersion || detectedEngineVersion || "").trim();
  const requiresEngineUpdate = Boolean(
    engineConnected &&
      currentEngineVersion &&
      currentEngineVersion !== REQUIRED_ENGINE_VERSION &&
      route !== MOBILE_DOWNLOAD_ROUTE,
  );
  const tunnelStatusText =
    typeof state.tunnelStatus === "string" && /lan/i.test(state.tunnelStatus)
      ? t("tunnel_searching")
      : state.tunnelStatus || t("tunnel_searching");
  const tunnelSubdomain = String(state.tunnelSubdomain || "").trim();
  const tunnelPort = parseTunnelPort(tunnelStatusText, state.tunnelPort);
  const tunnelIsRegistered = Boolean(state.tunnelRegistered || tunnelPort > 0);
  const tunnelIsConnected = Boolean(state.tunnelConnected || tunnelIsRegistered);
  const displayMyName =
    state.mode === "tunnel" ? tunnelSubdomain || state.myName || "Desktop" : state.myName || "Desktop";
  const tunnelConnectionHint =
    tunnelIsRegistered && tunnelSubdomain
      ? t("tunnel_registered_hint", { subdomain: tunnelSubdomain, port: tunnelPort })
      : tunnelIsConnected && tunnelSubdomain
        ? t("tunnel_registering_hint", { subdomain: tunnelSubdomain })
        : tunnelStatusText;
  const tunnelStatusValue =
    peers.length > 0
      ? t("devices_found", { count: peers.length })
      : t("tunnel_searching");

  if (!showApp) {
    if (route === DOWNLOAD_ROUTE || route === MOBILE_DOWNLOAD_ROUTE) {
      return (
        <DownloadPage
          language={language}
          route={route}
          engineConnected={engineConnected}
          onOpen={() => navigate(APP_ROUTE)}
          onRetry={handleRetryLaunch}
          onHome={() => navigate(APP_ROUTE)}
        />
      );
    }

    return (
      <LauncherScreen
        language={language}
        stage={launcherStage}
        desktopDownloadLink={desktopDownloadLink}
        desktopPlatform={desktopPlatform}
        onRetry={handleRetryLaunch}
        onDownloads={() => navigate(isMobile ? MOBILE_DOWNLOAD_ROUTE : DOWNLOAD_ROUTE)}
      />
    );
  }

  return (
    <div className="app-shell">
      <header className="hero">
        <section className="hero-panel hero-intro">
          <div className="hero-copy">
            <p className="eyebrow">{t("hero_kicker")}</p>
            <h1>{t("app_name")}</h1>
            <p className="hero-subtitle">{t("hero_subtitle")}</p>
            <p className="hero-body">{t("hero_body")}</p>
          </div>
          <div className="hero-badges">
            <span className="hero-badge hero-badge-strong">{t("hero_engine_online")}</span>
            <span className="hero-badge">
              {state.mode === "tunnel" ? t("tunnel_mode") : t("lan_mode")}
            </span>
          </div>
        </section>
        <section className="hero-panel hero-summary">
          <div className="hero-cards">
            <InfoPill label={t("my_device")} value={displayMyName} />
            <InfoPill label={t("my_ip")} value={state.myIp || "-"} />
            <InfoPill label={t("save_path")} value={state.savePath || "-"} wide />
          </div>
        </section>
      </header>

      <section className="mode-bar panel">
        <div className="mode-bar-top">
          <div>
            <p className="eyebrow">{t("transfer")}</p>
            <h2>{state.mode === "tunnel" ? t("tunnel_peers") : t("lan_peers")}</h2>
          </div>
          <div className="mode-actions">
            <ToggleButton active={state.mode === "lan"} onClick={() => void handleModeChange("lan")}>
              {t("lan_mode")}
            </ToggleButton>
            <ToggleButton active={state.mode === "tunnel"} onClick={() => void handleModeChange("tunnel")}>
              {t("tunnel_mode")}
            </ToggleButton>
            <button className="secondary-button" onClick={() => void handleRefresh()}>
              {t("refresh")}
            </button>
          </div>
        </div>
        <div className="status-line">
          <span className="status-label">{state.mode === "tunnel" ? t("tunnel_status") : t("lan_status")}</span>
          <span className="status-value">
            {state.mode === "tunnel"
              ? tunnelStatusValue
              : peers.length > 0
                ? t("devices_found", { count: peers.length })
                : t("lan_searching")}
          </span>
        </div>
        {state.mode === "tunnel" ? <p className="section-hint">{tunnelConnectionHint}</p> : null}
      </section>

      <main className="content-grid">
        <section className="panel peers-panel">
            <div className="panel-header">
              <div>
                <h2>{state.mode === "tunnel" ? t("tunnel_peers") : t("lan_peers")}</h2>
              </div>
              <button
              className="primary-button"
              disabled={!selectedPeerId || state.isBusy}
              onClick={openFileChooser}
            >
              {t("send_files")}
            </button>
          </div>

          {selectedPeer ? (
            <div className="selected-peer">
              <span className="selected-peer-label">{t("selected")}</span>
              <div>
                <strong>{selectedPeer.title}</strong>
                <p>{selectedPeer.addressLabel}</p>
              </div>
            </div>
          ) : (
            <div className="selected-peer muted">{t("select_peer_first")}</div>
          )}

          <div className="peer-list">
            {isLoading ? (
              <EmptyState title={t("loading")} body={t("loading_body")} />
            ) : peers.length === 0 ? (
              <EmptyState
                title={state.mode === "tunnel" ? t("no_tunnel_peers") : t("no_lan_peers")}
                body={state.mode === "tunnel" ? t("no_tunnel_peers_body") : t("no_lan_peers_body")}
              />
            ) : (
              peers.map((peer) => (
                <button
                  key={peer.id}
                  className={`peer-card ${selectedPeerId === peer.id ? "selected" : ""}`}
                  onClick={() => setSelectedPeerId((current) => (current === peer.id ? "" : peer.id))}
                >
                  <div className="peer-card-title-row">
                    <strong>{peer.title}</strong>
                    <span className="peer-mode-chip">{peer.isTunnel ? "Tunnel" : "LAN"}</span>
                  </div>
                  <p>{peer.addressLabel}</p>
                </button>
              ))
            )}
          </div>
        </section>

        <section className="stack-column">
          <section className="panel">
            <div className="panel-header">
              <div>
                <p className="eyebrow">{t("storage")}</p>
                <h2>{t("save_path")}</h2>
                <p className="section-hint">{t("save_path_hint")}</p>
              </div>
            </div>
            <div className="field-grid">
              <label className="field">
                <span>{t("directory")}</span>
                <input
                  type="text"
                  value={savePathInput}
                  onChange={(event) => {
                    setSavePathDirty(true);
                    setSavePathInput(event.target.value);
                  }}
                />
              </label>
              <div className="button-row">
                <button className="primary-button" onClick={() => void handleSavePathApply()}>
                  {t("apply")}
                </button>
                <button className="secondary-button" onClick={() => void handleSavePathDialog()}>
                  {t("choose_folder")}
                </button>
              </div>
            </div>
          </section>

          {state.mode === "tunnel" ? (
            <section className="panel">
              <button className="panel-toggle" onClick={() => setShowTunnelSettings((current) => !current)}>
                <div>
                  <p className="eyebrow">{t("tunnel")}</p>
                  <h2>{t("tunnel_settings")}</h2>
                  <p className="section-hint">{t("tunnel_settings_hint")}</p>
                </div>
                <span>{showTunnelSettings ? t("hide") : t("show")}</span>
              </button>
              {showTunnelSettings && (
                <div className="field-grid">
                  <label className="checkbox-row">
                    <input
                      type="checkbox"
                      checked={Boolean(tunnelForm.usePublicTunnel)}
                      onChange={(event) => {
                        setTunnelDirty(true);
                        setTunnelForm((current) => ({ ...current, usePublicTunnel: event.target.checked }));
                      }}
                    />
                    <span>{t("use_public_tunnel")}</span>
                  </label>
                  {!tunnelForm.usePublicTunnel ? (
                    <>
                  <label className="field">
                    <span>{t("host")}</span>
                    <input
                      type="text"
                      value={tunnelForm.host}
                      onChange={(event) => {
                        setTunnelDirty(true);
                        setTunnelForm((current) => ({ ...current, host: event.target.value }));
                      }}
                    />
                  </label>
                    <label className="field">
                      <span>{t("token")}</span>
                      <input
                        type="password"
                        value={tunnelForm.token}
                        onChange={(event) => {
                          setTunnelDirty(true);
                          setTunnelForm((current) => ({ ...current, token: event.target.value }));
                        }}
                      />
                    </label>
                    <label className="checkbox-row">
                      <input
                        type="checkbox"
                        checked={Boolean(tunnelForm.ssl)}
                        onChange={(event) => {
                          setTunnelDirty(true);
                          setTunnelForm((current) => ({ ...current, ssl: event.target.checked }));
                        }}
                      />
                      <span>{t("use_ssl")}</span>
                    </label>
                    </>
                  ) : null}
                  <div className="button-row">
                    <button className="primary-button" onClick={() => void handleTunnelApply()}>
                      {t("apply_reconnect")}
                    </button>
                  </div>
                </div>
              )}
            </section>
          ) : null}
        </section>
      </main>

      {state.transferProgress && (
        <section className="progress-dock panel">
          <div className="panel-header compact">
            <div className="progress-header-copy">
              <p className="eyebrow">{t("transfer")}</p>
              <h2>{state.transferProgress.title}</h2>
            </div>
            <button className="secondary-button danger progress-cancel-button" onClick={() => void handleCancelTransfer()}>
              {t("cancel")}
            </button>
          </div>
            <div className="progress-block">
              <div className="progress-meta">
                <span>{state.transferProgress.itemLabel}</span>
              </div>
            <div className="progress-track-row">
              <div className="progress-bar">
                <div
                  className="progress-fill"
                  style={{
                    width: `${percent(
                      state.transferProgress.transferredBytes,
                      state.transferProgress.totalBytes,
                    )}%`,
                  }}
                />
              </div>
              <strong className="progress-percent">
                {percent(state.transferProgress.transferredBytes, state.transferProgress.totalBytes)}%
              </strong>
            </div>
            <div className="progress-stats">
              <span>
                {formatBytes(state.transferProgress.transferredBytes)} /{" "}
                {formatBytes(state.transferProgress.totalBytes)}
              </span>
              <span>{formatSpeed(state.transferProgress.speedBytesPerSecond)}</span>
              <span>
                {formatDuration(state.transferProgress.remainingSeconds)} {t("remaining")}
              </span>
            </div>
          </div>

          {state.transferProgress.showItemProgress && (
            <div className="progress-block item-progress">
              <div className="progress-meta">
                <span>{t("current_item")}</span>
              </div>
              <div className="progress-track-row">
                <div className="progress-bar slim">
                  <div
                    className="progress-fill alt"
                    style={{
                      width: `${percent(
                        state.transferProgress.itemTransferredBytes,
                        state.transferProgress.itemBytes,
                      )}%`,
                    }}
                  />
                </div>
                <strong className="progress-percent">
                  {percent(
                    state.transferProgress.itemTransferredBytes,
                    state.transferProgress.itemBytes,
                  )}%
                </strong>
              </div>
            </div>
          )}
        </section>
      )}

      {showZipChoice && (
        <Overlay>
          <ModalCard
            title={t("choose_transfer_mode")}
            description={t("choose_transfer_body")}
            actions={
              <>
                <button
                  className="secondary-button"
                  onClick={() => {
                    setShowZipChoice(false);
                    void sendSelectedFiles(pendingFiles, false);
                  }}
                >
                  {t("no")}
                </button>
                <button
                  className="primary-button"
                  onClick={() => {
                    setShowZipChoice(false);
                    void sendSelectedFiles(pendingFiles, true);
                  }}
                >
                  {t("yes")}
                </button>
              </>
            }
          />
        </Overlay>
      )}

      {showTransferPreparing && (
        <Overlay>
          <LoadingModal title={t("preparing_transfer_title")} description={t("preparing_transfer_body")} />
        </Overlay>
      )}

      {state.pendingRequest && (
        <Overlay>
          <ModalCard
            title={t("receive_files")}
            description={`${state.pendingRequest.senderName} → ${state.pendingRequest.displayName}\n${state.pendingRequest.fileCount > 1 ? t("files_count", { count: state.pendingRequest.fileCount }) : t("file_count_one")} · ${formatBytes(state.pendingRequest.totalBytes)}`}
            actions={
              <>
                <button className="secondary-button" onClick={() => void handleIncomingDecision(false)}>
                  {t("reject")}
                </button>
                <button className="primary-button" onClick={() => void handleIncomingDecision(true)}>
                  {t("accept")}
                </button>
              </>
            }
          />
        </Overlay>
      )}

      {requiresEngineUpdate && (
        <Overlay>
          <ModalCard
            title={t("engine_update_required_title")}
            description={t("engine_update_required_body", {
              installed: currentEngineVersion,
              required: REQUIRED_ENGINE_VERSION,
            })}
            actions={
              <button
                className="primary-button"
                onClick={() => navigate(DOWNLOAD_ROUTE)}
              >
                {t("launcher_downloads")}
              </button>
            }
          />
        </Overlay>
      )}

      <div className="toast-stack">
        {toasts.map((toast) => (
          <div key={toast.id} className="toast">
            {toast.message}
          </div>
        ))}
      </div>
      <Footer />
    </div>
  );
}

function LauncherScreen({ language, onRetry, onDownloads, stage }) {
  const t = useMemo(() => createTranslator(language), [language]);

  let title = t("launcher_checking_title");
  let body = t("launcher_checking_body");
  if (stage === "starting") {
    title = t("launcher_starting_title");
    body = t("launcher_starting_body");
  } else if (stage === "install") {
    title = t("launcher_install_title");
    body = t("launcher_install_body");
  } else if (stage === "mobile") {
    title = t("mobile_title");
    body = t("mobile_body");
  }

  return (
    <div className="launcher-shell">
      <div className="launcher-page">
        <div className="launcher-card">
          <h1>{t("app_name")}</h1>
          <h2>{title}</h2>
          <p>{body}</p>
          <div className="button-row launcher-actions">
            {!stage || stage !== "mobile" ? (
              <>
                <button className="secondary-button" onClick={onRetry}>
                  {stage === "install" ? t("launcher_retry") : t("launcher_open_engine")}
                </button>
                <button className="primary-button" onClick={onDownloads}>
                  {t("launcher_downloads")}
                </button>
              </>
            ) : (
              <>
                <button className="secondary-button" onClick={onDownloads}>
                  {t("download_android")}
                </button>
                <AnchorButton href={downloadLink(DOWNLOAD_URLS.ios)} disabled={!downloadLink(DOWNLOAD_URLS.ios)}>
                  {t("download_ios")}
                </AnchorButton>
              </>
            )}
          </div>
        </div>
        <Footer />
      </div>
    </div>
  );
}

function DownloadPage({ language, route, engineConnected, onOpen, onRetry }) {
  const t = useMemo(() => createTranslator(language), [language]);
  const isMobilePage = route === MOBILE_DOWNLOAD_ROUTE;
  const [macDownloadHref, setMacDownloadHref] = useState("");

  function openMacDownloadGuide(href) {
    if (!href) {
      return;
    }
    window.location.assign(href);
    setMacDownloadHref(href);
  }

  const desktopCards = [
    {
      key: "windows",
      label: t("windows"),
      body: "x86 / x64",
      actions: [{ label: "x64", href: downloadLink(DOWNLOAD_URLS.windows) }],
    },
    {
      key: "macos",
      label: t("macos"),
      body: "Apple Silicon / Intel",
      actions: [
        {
          label: t("apple_silicon"),
          href: downloadLink(DOWNLOAD_URLS.macosArm64),
          onClick: () => openMacDownloadGuide(downloadLink(DOWNLOAD_URLS.macosArm64)),
        },
        {
          label: t("intel"),
          href: downloadLink(DOWNLOAD_URLS.macosIntel),
          onClick: () => openMacDownloadGuide(downloadLink(DOWNLOAD_URLS.macosIntel)),
        },
      ],
    },
    {
      key: "linux",
      label: t("linux"),
      body: t("coming_soon"),
      actions: [{ label: t("coming_soon"), href: undefined, disabled: true }],
    },
  ];
  const mobileCards = [
    {
      key: "android",
      label: "Android",
      body: "",
      actions: [{ label: t("download_android"), href: downloadLink(DOWNLOAD_URLS.android) }],
    },
    {
      key: "ios",
      label: "iPhone / iPad",
      body: "",
      actions: [{ label: t("download_ios"), href: downloadLink(DOWNLOAD_URLS.ios) }],
    },
  ];
  return (
    <div className="launcher-shell">
      <div className="launcher-page">
        <div className="launcher-card download-card-shell">
          <h1>{t("download_page_title")}</h1>
          {isMobilePage ? <p>{t("mobile_downloads_body")}</p> : null}

          {engineConnected && !isMobilePage ? (
            <div className="download-banner">
              <strong>{t("local_engine_ready")}</strong>
              <div className="button-row launcher-actions">
                <button className="primary-button" onClick={onOpen}>
                  {t("open_peersend")}
                </button>
              </div>
            </div>
          ) : null}

          {!isMobilePage ? (
            <>
              <section className="download-section">
                <p>{t("desktop_downloads_body")}</p>
                <div className="download-grid">
                  {desktopCards.map((item) => (
                    <DownloadCard
                      key={item.key}
                      title={item.label}
                      body={item.body}
                      actions={item.actions}
                      fallbackLabel={t("coming_soon")}
                    />
                  ))}
                </div>
                <div className="button-row launcher-actions">
                  <button className="secondary-button" onClick={onRetry}>
                    {t("launcher_open_engine")}
                  </button>
                </div>
              </section>
            </>
        ) : (
          <section className="download-section">
            <div className="download-grid compact">
              {mobileCards.map((item) => (
                <DownloadCard
                  key={item.key}
                  title={item.label}
                  body={item.body}
                  actions={item.actions}
                  fallbackLabel={t("coming_soon")}
                  />
                ))}
              </div>
            </section>
          )}
        </div>
        <Footer />
      </div>
      {macDownloadHref ? (
        <Overlay>
          <ModalCard
            title={t("macos_download_guide_title")}
            description={t("macos_download_guide_body")}
            actions={
              <button className="primary-button" onClick={() => setMacDownloadHref("")}>
                {t("close")}
              </button>
            }
          />
        </Overlay>
      ) : null}
    </div>
  );
}

function DownloadCard({ title, body, actions = [], fallbackLabel }) {
  return (
    <article className="panel download-tile">
      <div>
        <h4>{title}</h4>
        {body ? <p>{body}</p> : null}
      </div>
      <div className={`download-actions ${actions.length > 1 ? "split" : ""}`}>
        {actions.map((action, index) => (
          <AnchorButton
            key={`${title}-${index}-${action.label}`}
            href={action.href}
            disabled={action.disabled || !action.href}
            onClick={action.onClick}
          >
            {action.disabled || !action.href ? action.label || fallbackLabel : action.label}
          </AnchorButton>
        ))}
      </div>
    </article>
  );
}

function AnchorButton({ href, disabled, onClick, children }) {
  if (disabled || !href) {
    return (
      <span className="primary-button disabled-anchor" aria-disabled="true">
        {children}
      </span>
    );
  }
  if (onClick) {
    return (
      <button
        type="button"
        className="primary-button anchor-button"
        onClick={(event) => {
          event.preventDefault();
          onClick();
        }}
      >
        {children}
      </button>
    );
  }
  return (
    <a className="primary-button anchor-button" href={href}>
      {children}
    </a>
  );
}

function InfoPill({ label, value, wide = false }) {
  return (
    <div className={`info-pill ${wide ? "wide" : ""}`}>
      <span>{label}</span>
      <strong title={value}>{value}</strong>
    </div>
  );
}

function ToggleButton({ active, children, onClick }) {
  return (
    <button className={`toggle-button ${active ? "active" : ""}`} onClick={onClick}>
      {children}
    </button>
  );
}

function EmptyState({ title, body }) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <p>{body}</p>
    </div>
  );
}

function Overlay({ children }) {
  return (
    <div className="overlay">
      <div className="overlay-scrim" />
      <div className="overlay-content">{children}</div>
    </div>
  );
}

function ModalCard({ title, description, actions }) {
  const lines = String(description || "").split("\n");
  return (
    <div className="modal-card">
      <div className="modal-copy">
        <h2>{title}</h2>
        <p>
          {lines.map((line, index) => (
            <span key={`${line}-${index}`}>
              {line}
              {index < lines.length - 1 ? <br /> : null}
            </span>
          ))}
        </p>
      </div>
      <div className="modal-actions">{actions}</div>
    </div>
  );
}

function LoadingModal({ title, description }) {
  return (
    <div className="modal-card loading-modal">
      <div className="loading-spinner" aria-hidden="true" />
      <div className="modal-copy">
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
    </div>
  );
}

function Footer() {
  const year = new Date().getFullYear();
  return <footer className="app-footer">© {year} rhkr8521. All rights reserved.</footer>;
}
