from __future__ import annotations

import asyncio
import json
import locale
import os
import platform
import queue
import re
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import uuid
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Optional

from p2p import (
    BROADCAST_PORT,
    BUFFER_SIZE,
    DATA_PORT,
    LOCAL_FILE_PORT,
    TUNNEL_HOST,
    TUNNEL_SSL,
    TUNNEL_SUB_PREFIX,
    TUNNEL_TCP_NAME,
    TUNNEL_TOKEN,
    TunnelBridgeClient,
    build_urls,
    fetch_tunnel_peers,
    get_broadcast_ip,
    get_local_ip,
    human_readable_size,
    make_subdomain,
    recv_msg,
    safe_name,
    send_msg,
)
from .version import ENGINE_VERSION


def _default_save_path() -> str:
    return str(Path.home() / "Downloads")


def _config_path() -> Path:
    return Path.home() / ".peersend-web.json"


def _now_ms() -> int:
    return int(time.time() * 1000)


def _detect_language() -> str:
    candidates = []
    for key in ("LC_ALL", "LANGUAGE", "LANG", "LC_MESSAGES"):
        try:
            value = os.environ.get(key)
            if value:
                candidates.append(value)
        except Exception:
            pass
    try:
        candidates.append(locale.getlocale()[0])
    except Exception:
        pass
    try:
        default_locale = locale.getdefaultlocale()[0]  # type: ignore[attr-defined]
        candidates.append(default_locale)
    except Exception:
        pass
    system_name = platform.system()
    if system_name == "Darwin":
        try:
            apple_languages = subprocess.check_output(
                ["defaults", "read", "-g", "AppleLanguages"],
                text=True,
                stderr=subprocess.DEVNULL,
            )
            candidates.append(apple_languages)
        except Exception:
            pass
    elif system_name == "Windows":
        try:
            import ctypes

            lang_id = ctypes.windll.kernel32.GetUserDefaultUILanguage()
            candidates.append(locale.windows_locale.get(lang_id))
        except Exception:
            pass
    text = " ".join(str(candidate or "") for candidate in candidates).lower()
    return "ko" if re.search(r"(^|[^a-z])ko(?:[-_][a-z0-9]+)?([^a-z]|$)", text) or "korean" in text else "en"


def _escape_applescript_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def _generate_lan_name_suffix() -> str:
    return uuid.uuid4().hex[:3].upper()


def _build_lan_device_name(base_name: str, suffix: str) -> str:
    return f"{base_name} {suffix}".strip()


TRANSLATIONS: dict[str, dict[str, str]] = {
    "ko": {
        "status_lan_mode": "LAN 모드",
        "status_searching_lan": "LAN 기기 검색중",
        "status_searching_tunnel": "터널 기기 검색중",
        "status_applying_tunnel": "적용 및 재연결 중...",
        "status_starting_tunnel": "터널 기기 검색중",
        "status_ws_connected": "연결됨 (등록 중...)",
        "status_registered": "등록 완료",
        "status_registered_port": "등록 완료 (내 포트: {port})",
        "status_register_failed": "등록 실패: {reason}",
        "status_connection_failed": "연결 실패: {error}",
        "status_ws_disconnected": "연결 끊김 (재시도 중...)",
        "status_peer_fetch_failed": "피어 조회 실패: {error}",
        "status_tunnel_local_failed": "터널 로컬 수신 서버 실패: {error}",
        "toast_tunnel_settings_applied": "터널 설정을 적용했습니다.",
        "toast_save_path_changed": "저장 경로를 변경했습니다.",
        "toast_receive_rejected": "수신이 거절되었습니다.",
        "toast_receive_cancelled": "수신을 중단했습니다.",
        "toast_sender_cancelled": "전송측에서 전송을 중단했습니다.",
        "toast_zip_received": "ZIP 추출 완료: {count}개 파일을 받았습니다.",
        "toast_zip_saved": "ZIP 추출 실패, 원본 ZIP을 저장했습니다.",
        "toast_file_received": "{filename} 파일을 받았습니다.",
        "toast_multi_meta_failed": "다중 파일 메타데이터 수신 실패",
        "toast_multi_meta_invalid": "다중 파일 메타데이터가 올바르지 않습니다.",
        "toast_files_received": "{count}개 파일을 받았습니다.",
        "toast_send_failed": "전송 실패: {error}",
        "toast_zip_cancelled": "압축을 중단했습니다.",
        "toast_peer_rejected": "상대방이 전송을 거절했습니다.",
        "toast_peer_no_response": "상대방 응답이 없습니다.",
        "toast_connect_failed": "연결 실패: {error}",
        "toast_send_cancelled": "전송이 중단되었습니다.",
        "toast_send_complete": "{name} 전송 완료!",
        "progress_prepare_receive": "수신 준비 중...",
        "progress_prepare_send": "전송 준비 중...",
        "progress_receive_file": "파일 {filename} 수신 중...",
        "progress_receive_file_indexed": "파일 {filename} ({index}/{count}) 수신 중...",
        "progress_zip_prepare": "ZIP 압축",
        "progress_zip_item": "압축 중: {filename} ({index}/{count})",
        "progress_send_zip": "ZIP {filename} 전송 중...",
        "progress_send_file": "파일 {filename} 전송 중...",
        "progress_send_file_indexed": "파일 {filename} ({index}/{count}) 전송 중...",
        "error_missing_tunnel_peer": "대상 터널 정보를 찾을 수 없습니다.",
        "error_missing_tunnel_port": "대상 터널 포트가 없습니다.",
        "error_transfer_busy": "이미 다른 전송이 진행 중입니다.",
        "request_unknown_sender": "알 수 없음",
        "request_unknown_file": "파일",
        "display_more_files": "{name} 외 {count}개",
        "zip_bundle_name": "{name}_외_{count}개.zip",
    },
    "en": {
        "status_lan_mode": "LAN mode",
        "status_searching_lan": "Searching LAN devices",
        "status_searching_tunnel": "Searching tunnel devices",
        "status_applying_tunnel": "Applying and reconnecting...",
        "status_starting_tunnel": "Searching tunnel devices",
        "status_ws_connected": "Connected (registering...)",
        "status_registered": "Registered",
        "status_registered_port": "Registered (my port: {port})",
        "status_register_failed": "Registration failed: {reason}",
        "status_connection_failed": "Connection failed: {error}",
        "status_ws_disconnected": "Disconnected (retrying...)",
        "status_peer_fetch_failed": "Peer lookup failed: {error}",
        "status_tunnel_local_failed": "Tunnel local receive server failed: {error}",
        "toast_tunnel_settings_applied": "Applied tunnel settings.",
        "toast_save_path_changed": "Changed the save path.",
        "toast_receive_rejected": "Receive request was rejected.",
        "toast_receive_cancelled": "Receive was cancelled.",
        "toast_sender_cancelled": "The sender cancelled the transfer.",
        "toast_zip_received": "Received {count} files from the ZIP.",
        "toast_zip_saved": "Failed to extract the ZIP. The original ZIP was saved.",
        "toast_file_received": "Received {filename}.",
        "toast_multi_meta_failed": "Failed to receive multi-file metadata.",
        "toast_multi_meta_invalid": "Received invalid multi-file metadata.",
        "toast_files_received": "Received {count} files.",
        "toast_send_failed": "Transfer failed: {error}",
        "toast_zip_cancelled": "ZIP creation was cancelled.",
        "toast_peer_rejected": "The receiver rejected the transfer.",
        "toast_peer_no_response": "The receiver did not respond.",
        "toast_connect_failed": "Connection failed: {error}",
        "toast_send_cancelled": "Transfer was cancelled.",
        "toast_send_complete": "Finished sending {name}.",
        "progress_prepare_receive": "Preparing to receive...",
        "progress_prepare_send": "Preparing to send...",
        "progress_receive_file": "Receiving {filename}...",
        "progress_receive_file_indexed": "Receiving {filename} ({index}/{count})...",
        "progress_zip_prepare": "Creating ZIP",
        "progress_zip_item": "Compressing {filename} ({index}/{count})",
        "progress_send_zip": "Sending ZIP {filename}...",
        "progress_send_file": "Sending {filename}...",
        "progress_send_file_indexed": "Sending {filename} ({index}/{count})...",
        "error_missing_tunnel_peer": "Missing target tunnel peer.",
        "error_missing_tunnel_port": "Missing target tunnel port.",
        "error_transfer_busy": "Another transfer is already in progress.",
        "request_unknown_sender": "Unknown",
        "request_unknown_file": "File",
        "display_more_files": "{name} and {count} more",
        "zip_bundle_name": "{name}_and_{count}_more.zip",
    },
}


@dataclass
class RequestDecision:
    event: threading.Event
    accept: bool = False


class EventHub:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._subscribers: set[queue.Queue[dict[str, Any]]] = set()

    def subscribe(self) -> queue.Queue[dict[str, Any]]:
        q: queue.Queue[dict[str, Any]] = queue.Queue()
        with self._lock:
            self._subscribers.add(q)
        return q

    def unsubscribe(self, q: queue.Queue[dict[str, Any]]) -> None:
        with self._lock:
            self._subscribers.discard(q)

    def publish(self, payload: dict[str, Any]) -> None:
        with self._lock:
            subscribers = list(self._subscribers)
        for q in subscribers:
            try:
                q.put_nowait(payload)
            except Exception:
                pass


class PeerSendEngine:
    def __init__(self) -> None:
        self._lock = threading.RLock()
        self.events = EventHub()
        self.running = False
        self.language = _detect_language()

        self.my_ip = get_local_ip()
        self.base_name = socket.gethostname() or "Desktop"
        self.lan_name_suffix = _generate_lan_name_suffix()
        self.my_name = _build_lan_device_name(self.base_name, self.lan_name_suffix)
        self.save_path = _default_save_path()

        self.mode = "lan"
        self.devices: dict[str, dict[str, Any]] = {}
        self.broadcast_ip = get_broadcast_ip()

        self.use_public_tunnel = True
        self.custom_tunnel_host = ""
        self.custom_tunnel_ssl = TUNNEL_SSL
        self.custom_tunnel_token = ""
        self.tunnel_host = TUNNEL_HOST
        self.tunnel_ssl = TUNNEL_SSL
        self.tunnel_token = TUNNEL_TOKEN
        self.tunnel_subdomain = make_subdomain(TUNNEL_SUB_PREFIX)
        self.tunnel_ws_url, self.tunnel_admin_base, self.tunnel_server_host = build_urls(
            self.tunnel_host,
            self.tunnel_ssl,
        )
        self.tunnel_status = self.tr("status_lan_mode")
        self.tunnel_connected = False
        self.tunnel_registered = False
        self.tunnel_port = 0
        self.tunnel_peers: dict[str, dict[str, Any]] = {}
        self._tunnel_started = False
        self._local_tunnel_recv_thread: Optional[threading.Thread] = None
        self._tunnel_poll_thread: Optional[threading.Thread] = None
        self.tunnel_client: Optional[TunnelBridgeClient] = None

        self.pending_request: Optional[dict[str, Any]] = None
        self._incoming_decisions: dict[str, RequestDecision] = {}

        self.transfer_progress: Optional[dict[str, Any]] = None
        self._busy_reserved = False
        self._last_progress_emit = 0.0
        self.total_size = 0
        self.total_bytes_done = 0
        self.start_time_total = 0.0
        self.cancel_flag = False
        self.active_receive_sock: Optional[socket.socket] = None
        self.active_mode: Optional[str] = None

        self.discovery_thread: Optional[threading.Thread] = None
        self.transfer_thread: Optional[threading.Thread] = None
        self.broadcast_thread: Optional[threading.Thread] = None
        self.cleanup_thread: Optional[threading.Thread] = None

        self._load_config()
        self.my_name = _build_lan_device_name(self.base_name, self.lan_name_suffix)

    def _apply_tunnel_runtime_settings(self) -> None:
        self.tunnel_host = TUNNEL_HOST if self.use_public_tunnel else (self.custom_tunnel_host.strip() or TUNNEL_HOST)
        self.tunnel_ssl = TUNNEL_SSL if self.use_public_tunnel else bool(self.custom_tunnel_ssl)
        self.tunnel_token = TUNNEL_TOKEN if self.use_public_tunnel else (self.custom_tunnel_token.strip() or TUNNEL_TOKEN)
        self.tunnel_ws_url, self.tunnel_admin_base, self.tunnel_server_host = build_urls(
            self.tunnel_host,
            self.tunnel_ssl,
        )

    def tr(self, key: str, **kwargs: Any) -> str:
        catalog = TRANSLATIONS.get(self.language, TRANSLATIONS["en"])
        template = catalog.get(key) or TRANSLATIONS["en"].get(key) or key
        try:
            return template.format(**kwargs)
        except Exception:
            return template

    def refresh_network_info(self) -> None:
        try:
            self.my_ip = get_local_ip()
        except Exception:
            pass
        try:
            self.broadcast_ip = get_broadcast_ip()
        except Exception:
            pass

    # ------------------------
    # lifecycle
    # ------------------------
    def start(self) -> None:
        with self._lock:
            if self.running:
                return
            self.running = True
        self.refresh_network_info()

        self.discovery_thread = threading.Thread(target=self.listen_incoming_discovery, daemon=True)
        self.transfer_thread = threading.Thread(target=self.listen_incoming_for_transfer_lan, daemon=True)
        self.broadcast_thread = threading.Thread(target=self.broadcast_loop, daemon=True)
        self.cleanup_thread = threading.Thread(target=self.cleanup_loop, daemon=True)
        self.discovery_thread.start()
        self.transfer_thread.start()
        self.broadcast_thread.start()
        self.cleanup_thread.start()
        if self.mode == "tunnel":
            self.start_tunnel_stack()
        self.emit_state()

    def shutdown(self) -> None:
        with self._lock:
            self.running = False
        try:
            if self.tunnel_client:
                self.tunnel_client.stop()
        except Exception:
            pass
        try:
            if self.active_receive_sock:
                self.active_receive_sock.close()
        except Exception:
            pass

    # ------------------------
    # config
    # ------------------------
    def _load_config(self) -> None:
        path = _config_path()
        if not path.exists():
            return
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            return
        self.save_path = str(data.get("save_path") or self.save_path)
        self.lan_name_suffix = str(data.get("lan_name_suffix") or self.lan_name_suffix).strip() or self.lan_name_suffix
        legacy_host = str(data.get("tunnel_host") or "").strip()
        legacy_ssl = bool(data.get("tunnel_ssl", TUNNEL_SSL))
        legacy_token = str(data.get("tunnel_token") or "").strip()
        self.custom_tunnel_host = str(data.get("custom_tunnel_host") or "").strip()
        self.custom_tunnel_ssl = bool(data.get("custom_tunnel_ssl", legacy_ssl))
        self.custom_tunnel_token = str(data.get("custom_tunnel_token") or "").strip()
        if self.custom_tunnel_host == TUNNEL_HOST:
            self.custom_tunnel_host = ""
        if self.custom_tunnel_token == TUNNEL_TOKEN:
            self.custom_tunnel_token = ""
        if "use_public_tunnel" in data:
            self.use_public_tunnel = bool(data.get("use_public_tunnel"))
        else:
            if not self.custom_tunnel_host and legacy_host and legacy_host != TUNNEL_HOST:
                self.custom_tunnel_host = legacy_host
            if not self.custom_tunnel_token and legacy_token and legacy_token != TUNNEL_TOKEN:
                self.custom_tunnel_token = legacy_token
            self.use_public_tunnel = not bool(self.custom_tunnel_host or self.custom_tunnel_token)
        if not self.custom_tunnel_host and not self.use_public_tunnel and legacy_host and legacy_host != TUNNEL_HOST:
            self.custom_tunnel_host = legacy_host
        if not self.custom_tunnel_token and not self.use_public_tunnel and legacy_token and legacy_token != TUNNEL_TOKEN:
            self.custom_tunnel_token = legacy_token
        if not self.custom_tunnel_host and not self.custom_tunnel_token:
            self.use_public_tunnel = True
        self.mode = "lan"
        self._apply_tunnel_runtime_settings()
        self.tunnel_status = self.tr("status_lan_mode")

    def _save_config(self) -> None:
        payload = {
            "save_path": self.save_path,
            "lan_name_suffix": self.lan_name_suffix,
            "use_public_tunnel": self.use_public_tunnel,
            "custom_tunnel_host": self.custom_tunnel_host,
            "custom_tunnel_ssl": self.custom_tunnel_ssl,
            "custom_tunnel_token": self.custom_tunnel_token,
            "tunnel_host": self.tunnel_host,
            "tunnel_ssl": self.tunnel_ssl,
            "tunnel_token": self.tunnel_token,
            "language": self.language,
        }
        try:
            _config_path().write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        except Exception:
            pass

    # ------------------------
    # state/events
    # ------------------------
    def serialize_state(self) -> dict[str, Any]:
        with self._lock:
            display_my_name = self.tunnel_subdomain if self.mode == "tunnel" else self.my_name
            active_tunnel_port = self.tunnel_port or int(getattr(self.tunnel_client, "last_assigned_port", 0) or 0)
            tunnel_connected = bool(self.tunnel_connected or getattr(self.tunnel_client, "connected", False))
            tunnel_registered = bool(self.tunnel_registered or active_tunnel_port > 0)
            lan_peers = [
                {
                    "id": ip,
                    "title": str(info.get("name") or "Unknown"),
                    "addressLabel": ip,
                    "isTunnel": False,
                }
                for ip, info in sorted(self.devices.items(), key=lambda item: (str(item[1].get("name") or "").lower(), item[0]))
            ]
            tunnel_peers = []
            for subdomain, peer in sorted(self.tunnel_peers.items(), key=lambda item: str(item[1].get("title") or item[0]).lower()):
                title = str(peer.get("title") or subdomain)
                display_title = str(peer.get("display_title") or "").strip()
                try:
                    file_port = int(peer.get("file_port") or 0)
                except Exception:
                    file_port = 0
                address = title if not display_title or display_title == title else f"{title} ({display_title})"
                tunnel_peers.append(
                    {
                        "id": subdomain,
                        "title": title,
                        "addressLabel": f"{address} / port:{file_port}" if file_port else address,
                        "port": file_port,
                        "isTunnel": True,
                    }
                )

            return {
                "mode": self.mode,
                "engineVersion": ENGINE_VERSION,
                "myName": display_my_name,
                "myIp": self.my_ip,
                "savePath": self.save_path,
                "language": self.language,
                "lanPeers": lan_peers,
                "tunnelPeers": tunnel_peers,
                "tunnelStatus": self.tunnel_status,
                "tunnelSubdomain": self.tunnel_subdomain,
                "tunnelConnected": tunnel_connected,
                "tunnelRegistered": tunnel_registered,
                "tunnelPort": active_tunnel_port,
                "tunnelSettings": {
                    "usePublicTunnel": self.use_public_tunnel,
                    "host": "" if self.use_public_tunnel else self.custom_tunnel_host,
                    "ssl": self.custom_tunnel_ssl,
                    "token": "" if self.use_public_tunnel else self.custom_tunnel_token,
                },
                "pendingRequest": self.pending_request,
                "transferProgress": self.transfer_progress,
                "isBusy": bool(self.pending_request or self.transfer_progress or self._busy_reserved),
            }

    def emit_state(self) -> None:
        self.events.publish({"type": "state", "state": self.serialize_state()})

    def emit_toast(self, message: str) -> None:
        self.events.publish({"type": "toast", "message": message})

    # ------------------------
    # ui actions
    # ------------------------
    def set_mode(self, mode: str) -> dict[str, Any]:
        normalized = mode.lower().strip()
        if normalized not in {"lan", "tunnel"}:
            raise ValueError("Unsupported mode")
        self.refresh_network_info()
        with self._lock:
            self.mode = normalized
            if normalized == "lan":
                self.tunnel_status = self.tr("status_searching_lan")
            else:
                active_port = self.tunnel_port or int(getattr(self.tunnel_client, "last_assigned_port", 0) or 0)
                active_connected = bool(self.tunnel_connected or getattr(self.tunnel_client, "connected", False))
                if active_port > 0:
                    self.tunnel_registered = True
                    self.tunnel_connected = True
                    self.tunnel_port = active_port
                    self.tunnel_status = self.tr("status_registered_port", port=active_port)
                elif active_connected:
                    self.tunnel_connected = True
                    self.tunnel_status = self.tr("status_ws_connected")
                else:
                    self.tunnel_status = self.tr("status_searching_tunnel")
            self._save_config()
        if normalized == "tunnel":
            self.start_tunnel_stack()
        self.emit_state()
        return self.serialize_state()

    def manual_refresh(self) -> None:
        self.refresh_network_info()
        if self.mode == "lan":
            self.broadcast_discovery()
        else:
            self.refresh_tunnel_peers_once()
        self.emit_state()

    def update_tunnel_settings(self, host: str, ssl: bool, token: str, use_public_tunnel: bool) -> dict[str, Any]:
        with self._lock:
            self.use_public_tunnel = bool(use_public_tunnel)
            if not self.use_public_tunnel:
                cleaned_host = host.strip()
                cleaned_token = token.strip()
                if not cleaned_host:
                    raise ValueError("Tunnel host is required.")
                if not cleaned_token:
                    raise ValueError("Tunnel token is required.")
                self.custom_tunnel_host = cleaned_host
                self.custom_tunnel_ssl = bool(ssl)
                self.custom_tunnel_token = cleaned_token
            self._apply_tunnel_runtime_settings()
            self._save_config()
        self.restart_tunnel_client()
        self.tunnel_status = self.tr("status_applying_tunnel")
        self.emit_state()
        self.emit_toast(self.tr("toast_tunnel_settings_applied"))
        return self.serialize_state()

    def set_save_path(self, path: str) -> dict[str, Any]:
        cleaned = os.path.abspath(os.path.expanduser(path.strip()))
        os.makedirs(cleaned, exist_ok=True)
        with self._lock:
            self.save_path = cleaned
            self._save_config()
        self.emit_state()
        self.emit_toast(self.tr("toast_save_path_changed"))
        return self.serialize_state()

    def choose_save_path_dialog(self) -> dict[str, Any]:
        if platform.system() == "Darwin":
            default_dir = self.save_path or _default_save_path()
            script = f'''
try
    set defaultFolder to POSIX file "{_escape_applescript_string(default_dir)}"
    set chosenFolder to POSIX path of (choose folder with prompt "Choose a folder for PeerSend downloads" default location defaultFolder)
    return chosenFolder
on error number -128
    return ""
end try
'''
            try:
                result = subprocess.run(
                    ["osascript", "-e", script],
                    capture_output=True,
                    text=True,
                    check=False,
                )
                if result.returncode == 0:
                    path = result.stdout.strip()
                    if path:
                        self.set_save_path(path)
                        return {"ok": True, "path": path, "cancelled": False}
                    return {"ok": False, "path": None, "cancelled": True}
                stderr = (result.stderr or "").strip()
                if "User canceled" in stderr:
                    return {"ok": False, "path": None, "cancelled": True}
                return {"ok": False, "path": None, "cancelled": False, "error": stderr or "Failed to open folder dialog."}
            except Exception as error:
                return {"ok": False, "path": None, "cancelled": False, "error": str(error)}
        try:
            import tkinter as tk
            from tkinter import filedialog

            root = tk.Tk()
            root.withdraw()
            root.attributes("-topmost", True)
            root.update()
            path = filedialog.askdirectory(initialdir=self.save_path or _default_save_path())
            root.destroy()
            if path:
                self.set_save_path(path)
                return {"ok": True, "path": path, "cancelled": False}
            return {"ok": False, "path": None, "cancelled": True}
        except Exception as error:
            return {"ok": False, "path": None, "cancelled": False, "error": str(error)}

    def choose_send_files_dialog(self) -> dict[str, Any]:
        if platform.system() == "Darwin":
            default_dir = self.save_path or _default_save_path()
            script = f'''
try
    set defaultFolder to POSIX file "{_escape_applescript_string(default_dir)}"
    set chosenFiles to choose file with prompt "Choose files to send with PeerSend" default location defaultFolder with multiple selections allowed
    set outputText to ""
    repeat with chosenFile in chosenFiles
        set outputText to outputText & POSIX path of chosenFile & linefeed
    end repeat
    return outputText
on error number -128
    return ""
end try
'''
            try:
                result = subprocess.run(
                    ["osascript", "-e", script],
                    capture_output=True,
                    text=True,
                    check=False,
                )
                if result.returncode == 0:
                    raw_paths = [line.strip() for line in (result.stdout or "").splitlines() if line.strip()]
                    files = [
                        {"path": path, "name": Path(path).name}
                        for path in raw_paths
                        if os.path.isfile(path)
                    ]
                    if files:
                        return {"ok": True, "files": files, "cancelled": False}
                    return {"ok": False, "files": [], "cancelled": True}
                stderr = (result.stderr or "").strip()
                if "User canceled" in stderr:
                    return {"ok": False, "files": [], "cancelled": True}
                return {"ok": False, "files": [], "cancelled": False, "error": stderr or "Failed to open file dialog."}
            except Exception as error:
                return {"ok": False, "files": [], "cancelled": False, "error": str(error)}
        try:
            import tkinter as tk
            from tkinter import filedialog

            root = tk.Tk()
            root.withdraw()
            root.attributes("-topmost", True)
            root.update()
            paths = list(
                filedialog.askopenfilenames(
                    title="Choose files to send with PeerSend",
                    initialdir=self.save_path or _default_save_path(),
                )
            )
            root.destroy()
            files = [{"path": path, "name": Path(path).name} for path in paths if os.path.isfile(path)]
            if files:
                return {"ok": True, "files": files, "cancelled": False}
            return {"ok": False, "files": [], "cancelled": True}
        except Exception as error:
            return {"ok": False, "files": [], "cancelled": False, "error": str(error)}

    def cancel_transfer(self) -> None:
        self.cancel_flag = True
        sock = self.active_receive_sock
        if self.active_mode == "receive" and sock:
            try:
                send_msg(sock, {"type": "CANCEL"})
            except Exception:
                pass

    def respond_to_request(self, request_id: str, accept: bool) -> bool:
        with self._lock:
            decision = self._incoming_decisions.get(request_id)
        if not decision:
            return False
        decision.accept = bool(accept)
        decision.event.set()
        return True

    # ------------------------
    # tunnel
    # ------------------------
    def start_tunnel_stack(self) -> None:
        with self._lock:
            if self._tunnel_started:
                return
            self._tunnel_started = True
            self.tunnel_status = self.tr("status_starting_tunnel")
        self._local_tunnel_recv_thread = threading.Thread(target=self._local_receive_server_tunnel, daemon=True)
        self._local_tunnel_recv_thread.start()
        self.restart_tunnel_client()
        self._tunnel_poll_thread = threading.Thread(target=self._tunnel_poll_loop, daemon=True)
        self._tunnel_poll_thread.start()
        self.emit_state()

    def restart_tunnel_client(self) -> None:
        try:
            if self.tunnel_client:
                self.tunnel_client.stop()
        except Exception:
            pass
        self.tunnel_connected = False
        self.tunnel_registered = False
        self.tunnel_port = 0
        client = TunnelBridgeClient(
            self.tunnel_ws_url,
            self.tunnel_subdomain,
            self.tunnel_token,
            LOCAL_FILE_PORT,
            display_name=self.my_name,
        )
        client.set_status_callback(self._on_tunnel_status)
        client.start()
        self.tunnel_client = client

    def _on_tunnel_status(self, kind: str, data: Any) -> None:
        if kind == "ws_connected":
            self.tunnel_connected = True
            self.tunnel_registered = False
            self.tunnel_port = 0
            self.tunnel_status = self.tr("status_ws_connected")
        elif kind == "registered":
            try:
                port = int((data or {}).get("file_port") or 0)
            except Exception:
                port = 0
            subdomain = str((data or {}).get("subdomain") or "").strip()
            if subdomain:
                self.tunnel_subdomain = subdomain
            self.tunnel_connected = True
            self.tunnel_registered = True
            self.tunnel_port = port
            self.tunnel_status = self.tr("status_registered_port", port=port) if port else self.tr("status_registered")
        elif kind == "register_failed":
            self.tunnel_registered = False
            self.tunnel_port = 0
            self.tunnel_status = self.tr("status_register_failed", reason=(data or {}).get("reason") or "unknown")
        elif kind == "ws_error":
            self.tunnel_connected = False
            self.tunnel_registered = False
            self.tunnel_port = 0
            self.tunnel_status = self.tr("status_connection_failed", error=(data or {}).get("error") or "unknown")
        elif kind == "ws_disconnected":
            self.tunnel_connected = False
            self.tunnel_registered = False
            self.tunnel_port = 0
            self.tunnel_status = self.tr("status_ws_disconnected")
        self.emit_state()

    def refresh_tunnel_peers_once(self) -> None:
        try:
            peers = fetch_tunnel_peers(self.tunnel_admin_base, self.tunnel_token)
            my_peer = peers.get(self.tunnel_subdomain)
            if my_peer:
                try:
                    port = int(my_peer.get("file_port") or 0)
                except Exception:
                    port = 0
                if port:
                    self.tunnel_connected = True
                    self.tunnel_registered = True
                    self.tunnel_port = port
                    self.tunnel_status = self.tr("status_registered_port", port=port)
            peers.pop(self.tunnel_subdomain, None)
            with self._lock:
                self.tunnel_peers = peers
        except Exception as exc:
            self.tunnel_status = self.tr("status_peer_fetch_failed", error=exc)
        self.emit_state()

    def _tunnel_poll_loop(self) -> None:
        while self.running:
            self.refresh_tunnel_peers_once()
            time.sleep(3.0)

    # ------------------------
    # lan discovery
    # ------------------------
    def broadcast_loop(self) -> None:
        while self.running:
            self.broadcast_discovery()
            time.sleep(5)

    def broadcast_discovery(self) -> None:
        self.refresh_network_info()
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.settimeout(0.5)
        try:
            payload = json.dumps(
                {
                    "type": "DISCOVERY",
                    "name": self.my_name,
                    "ip": self.my_ip,
                    "port": DATA_PORT,
                }
            ).encode()
            sock.sendto(payload, (self.broadcast_ip, BROADCAST_PORT))
        except Exception:
            pass
        finally:
            sock.close()

    def listen_incoming_discovery(self) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("0.0.0.0", BROADCAST_PORT))
        except Exception:
            return
        sock.settimeout(0.5)

        while self.running:
            try:
                data, _ = sock.recvfrom(65535)
                if not data:
                    continue
                message = json.loads(data.decode())
                if message.get("type") != "DISCOVERY":
                    continue
                ip = str(message.get("ip") or "")
                if not ip or ip == self.my_ip:
                    continue
                with self._lock:
                    self.devices[ip] = {
                        "ip": ip,
                        "name": str(message.get("name") or self.devices.get(ip, {}).get("name") or "Unknown"),
                        "last_seen": time.time(),
                    }
                if self.mode == "lan":
                    self.emit_state()
            except socket.timeout:
                continue
            except Exception:
                break

    def cleanup_loop(self) -> None:
        while self.running:
            cutoff = time.time() - 20
            changed = False
            with self._lock:
                for ip in list(self.devices.keys()):
                    if float(self.devices[ip].get("last_seen") or 0) < cutoff:
                        del self.devices[ip]
                        changed = True
            if changed and self.mode == "lan":
                self.emit_state()
            time.sleep(5)

    # ------------------------
    # local receive servers
    # ------------------------
    def listen_incoming_for_transfer_lan(self) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("0.0.0.0", DATA_PORT))
            sock.listen(5)
            sock.settimeout(0.5)
        except Exception:
            return

        while self.running:
            try:
                client, _ = sock.accept()
                threading.Thread(target=self.handle_transfer_common, args=(client,), daemon=True).start()
            except socket.timeout:
                continue
            except Exception:
                break

    def _local_receive_server_tunnel(self) -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("127.0.0.1", LOCAL_FILE_PORT))
            sock.listen(10)
            sock.settimeout(0.5)
        except Exception as exc:
            self.tunnel_status = self.tr("status_tunnel_local_failed", error=exc)
            self.emit_state()
            return

        while self.running:
            try:
                client, _ = sock.accept()
                threading.Thread(target=self.handle_transfer_common, args=(client,), daemon=True).start()
            except socket.timeout:
                continue
            except Exception:
                continue

    # ------------------------
    # receive flow
    # ------------------------
    def handle_transfer_common(self, client: socket.socket) -> None:
        self.cancel_flag = False
        try:
            client.settimeout(None)
            while True:
                message = recv_msg(client)
                if not message:
                    break

                msg_type = message.get("type")
                if msg_type == "REQUEST_SEND":
                    if self.serialize_state()["isBusy"]:
                        try:
                            send_msg(client, {"type": "REJECT"})
                        except Exception:
                            pass
                        return
                    accept = self._handle_incoming_request(message)
                    try:
                        send_msg(client, {"type": "ACCEPT" if accept else "REJECT"})
                    except Exception:
                        return
                    if not accept:
                        self.emit_toast(self.tr("toast_receive_rejected"))
                        return
                elif msg_type == "START_TRANSFER":
                    is_zip = bool(message.get("is_zip", False))
                    file_count = int(message.get("file_count", 1))
                    total_size = int(message.get("size", 0))
                    filename = str(message.get("filename") or "downloaded_file.dat")

                    self.active_receive_sock = client
                    self.active_mode = "receive"
                    if is_zip:
                        self.receive_single_file(client, filename, total_size, is_zip=True, file_count=file_count)
                    elif file_count <= 1:
                        self.receive_single_file(client, filename, total_size, is_zip=False, file_count=1)
                    else:
                        self.receive_multi_files(client, total_size, file_count)
                    return
        finally:
            if self.active_receive_sock is client:
                self.active_receive_sock = None
                self.active_mode = None
            try:
                client.close()
            except Exception:
                pass

    def _handle_incoming_request(self, message: dict[str, Any]) -> bool:
        request_id = uuid.uuid4().hex
        request = {
            "requestId": request_id,
            "senderName": str(message.get("tunnel_name") or message.get("name") or self.tr("request_unknown_sender")),
            "senderAddress": str(message.get("tunnel_name") or message.get("ip") or "?"),
            "displayName": str(message.get("display_name") or message.get("filename") or self.tr("request_unknown_file")),
            "totalBytes": int(message.get("size", 0) or 0),
            "fileCount": int(message.get("file_count", 1) or 1),
            "isZip": bool(message.get("is_zip", False)),
        }
        decision = RequestDecision(event=threading.Event())
        with self._lock:
            self.pending_request = request
            self._incoming_decisions[request_id] = decision
        self.emit_state()

        accepted = decision.event.wait(120.0) and decision.accept
        with self._lock:
            self._incoming_decisions.pop(request_id, None)
            if self.pending_request and self.pending_request.get("requestId") == request_id:
                self.pending_request = None
        self.emit_state()
        return accepted

    def _prepare_unique_path(self, base_dir: str, filename: str) -> str:
        os.makedirs(base_dir, exist_ok=True)
        path = os.path.join(base_dir, safe_name(filename))
        stem, ext = os.path.splitext(path)
        counter = 1
        while os.path.exists(path):
            path = f"{stem}_{counter}{ext}"
            counter += 1
        return path

    def _delete_paths_safely(self, paths: list[str]) -> None:
        for path in paths:
            try:
                if os.path.isfile(path):
                    os.remove(path)
            except Exception:
                pass

    def receive_single_file(self, sock: socket.socket, filename: str, file_size: int, is_zip: bool = False, file_count: int = 1) -> None:
        self.cancel_flag = False
        saved_paths: list[str] = []
        extracted_names: list[str] | None = None
        os.makedirs(self.save_path, exist_ok=True)
        save_path = self._prepare_unique_path(self.save_path, filename)
        saved_paths.append(save_path)
        self.begin_progress(filename, file_size, is_receiving=True, show_item_progress=False)

        total = 0
        interrupted = False
        try:
            with open(save_path, "wb") as handle:
                while total < file_size and not self.cancel_flag:
                    chunk = sock.recv(BUFFER_SIZE)
                    if not chunk:
                        interrupted = True
                        break
                    handle.write(chunk)
                    total += len(chunk)
                    self.update_progress(len(chunk), total, file_size, self.tr("progress_receive_file", filename=filename))
        except Exception:
            interrupted = True

        try:
            sock.close()
        except Exception:
            pass

        if self.cancel_flag:
            self.clear_progress()
            self._delete_paths_safely(saved_paths)
            self.emit_toast(self.tr("toast_receive_cancelled"))
            return

        if interrupted or total < file_size:
            self.clear_progress()
            self._delete_paths_safely(saved_paths)
            self.emit_toast(self.tr("toast_sender_cancelled"))
            return

        if is_zip:
            extract_dir = os.path.splitext(save_path)[0]
            os.makedirs(extract_dir, exist_ok=True)
            try:
                with zipfile.ZipFile(save_path, "r") as archive:
                    extracted_names = archive.namelist()
                    archive.extractall(extract_dir)
                os.remove(save_path)
                self.emit_toast(self.tr("toast_zip_received", count=len(extracted_names)))
            except Exception:
                self.emit_toast(self.tr("toast_zip_saved"))
        else:
            self.emit_toast(self.tr("toast_file_received", filename=os.path.basename(save_path)))

        self.clear_progress()

    def receive_multi_files(self, sock: socket.socket, total_size: int, file_count: int) -> None:
        self.cancel_flag = False
        saved_paths: list[str] = []
        interrupted = False
        os.makedirs(self.save_path, exist_ok=True)

        meta = recv_msg(sock)
        if not meta or meta.get("type") != "FILES_META":
            try:
                sock.close()
            except Exception:
                pass
            self.emit_toast(self.tr("toast_multi_meta_failed"))
            return

        files = meta.get("files", [])
        if not isinstance(files, list) or len(files) != file_count:
            try:
                sock.close()
            except Exception:
                pass
            self.emit_toast(self.tr("toast_multi_meta_invalid"))
            return

        self.begin_progress("다중 파일", total_size, is_receiving=True, show_item_progress=True)
        try:
            for index, info in enumerate(files, 1):
                if self.cancel_flag:
                    break
                name = safe_name(str(info.get("name") or f"file_{index}.dat"))
                file_size = int(info.get("size") or 0)
                save_path = self._prepare_unique_path(self.save_path, name)
                saved_paths.append(save_path)
                done = 0
                with open(save_path, "wb") as handle:
                    while done < file_size and not self.cancel_flag:
                        chunk = sock.recv(min(BUFFER_SIZE, file_size - done))
                        if not chunk:
                            interrupted = True
                            break
                        handle.write(chunk)
                        done += len(chunk)
                        self.update_progress(
                            len(chunk),
                            done,
                            file_size,
                            self.tr("progress_receive_file_indexed", filename=name, index=index, count=file_count),
                        )
                if interrupted or done < file_size:
                    interrupted = True
                    break
        except Exception:
            interrupted = True

        try:
            sock.close()
        except Exception:
            pass

        if self.cancel_flag:
            self.clear_progress()
            self._delete_paths_safely(saved_paths)
            self.emit_toast(self.tr("toast_receive_cancelled"))
            return

        if interrupted:
            self.clear_progress()
            self._delete_paths_safely(saved_paths)
            self.emit_toast(self.tr("toast_sender_cancelled"))
            return

        self.clear_progress()
        self.emit_toast(self.tr("toast_files_received", count=len(saved_paths)))

    # ------------------------
    # send flow
    # ------------------------
    def queue_send_native_files(self, *, mode: str, peer_id: str, file_paths: list[str], use_zip: bool) -> None:
        normalized_paths: list[str] = []
        normalized_names: list[str] = []
        for path in file_paths:
            cleaned = os.path.abspath(os.path.expanduser(str(path).strip()))
            if not cleaned or not os.path.isfile(cleaned):
                continue
            normalized_paths.append(cleaned)
            normalized_names.append(Path(cleaned).name)
        self.queue_send_files(
            mode=mode,
            peer_id=peer_id,
            file_paths=normalized_paths,
            filenames=normalized_names,
            use_zip=use_zip,
            cleanup_dir=None,
        )

    def queue_send_files(self, *, mode: str, peer_id: str, file_paths: list[str], filenames: list[str], use_zip: bool, cleanup_dir: Optional[str] = None) -> None:
        if not peer_id:
            raise ValueError("peer_id is required")
        if not file_paths:
            raise ValueError("No files selected")
        with self._lock:
            if self.pending_request or self.transfer_progress or self._busy_reserved:
                raise RuntimeError(self.tr("error_transfer_busy"))
            self._busy_reserved = True
        self.emit_state()
        thread = threading.Thread(
            target=self._send_worker,
            args=(mode.lower().strip(), peer_id, file_paths[:], filenames[:], bool(use_zip), cleanup_dir),
            daemon=True,
        )
        thread.start()

    def _send_worker(self, mode: str, peer_id: str, file_paths: list[str], filenames: list[str], use_zip: bool, cleanup_dir: Optional[str]) -> None:
        try:
            file_count = len(file_paths)
            display_name = filenames[0] if file_count <= 1 else self.tr(
                "display_more_files",
                name=filenames[0],
                count=file_count - 1,
            )
            if mode == "lan":
                self._send_via_lan(peer_id, file_paths, filenames, file_count, use_zip, display_name)
            else:
                with self._lock:
                    peer = self.tunnel_peers.get(peer_id)
                if not peer:
                    raise RuntimeError(self.tr("error_missing_tunnel_peer"))
                target_port = int(peer.get("file_port") or 0)
                if not target_port:
                    raise RuntimeError(self.tr("error_missing_tunnel_port"))
                self._send_via_tunnel(target_port, file_paths, filenames, file_count, use_zip, display_name)
        except Exception as exc:
            self._busy_reserved = False
            self.emit_state()
            self.emit_toast(self.tr("toast_send_failed", error=exc))
        finally:
            if cleanup_dir:
                shutil.rmtree(cleanup_dir, ignore_errors=True)

    def _zip_with_progress(self, zip_path: str, file_paths: list[str]) -> None:
        total_src = sum(os.path.getsize(path) for path in file_paths)
        self.begin_progress(self.tr("progress_zip_prepare"), total_src, is_receiving=False, show_item_progress=True)
        try:
            with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for index, path in enumerate(file_paths, 1):
                    if self.cancel_flag:
                        break
                    arc = os.path.basename(path)
                    file_size = os.path.getsize(path)
                    done = 0
                    self.update_progress(0, 0, file_size, self.tr("progress_zip_item", filename=arc, index=index, count=len(file_paths)))
                    with open(path, "rb") as source, archive.open(arc, "w") as destination:
                        while not self.cancel_flag:
                            chunk = source.read(BUFFER_SIZE)
                            if not chunk:
                                break
                            destination.write(chunk)
                            done += len(chunk)
                            self.update_progress(
                                len(chunk),
                                done,
                                file_size,
                                self.tr("progress_zip_item", filename=arc, index=index, count=len(file_paths)),
                            )
        finally:
            self.clear_progress()
        if self.cancel_flag:
            raise RuntimeError("CANCELED_BY_USER")

    def _cleanup_temp_zip(self, path: Optional[str]) -> None:
        if path and os.path.exists(path):
            try:
                os.remove(path)
            except Exception:
                pass

    def _send_via_lan(self, ip: str, paths: list[str], filenames: list[str], file_count: int, use_zip: bool, display_name: str) -> None:
        temp_zip_path: Optional[str] = None
        self.cancel_flag = False
        if use_zip and file_count > 1:
            base = os.path.splitext(filenames[0])[0]
            temp_zip_path = tempfile.NamedTemporaryFile(suffix=".zip", delete=False).name
            try:
                self._zip_with_progress(temp_zip_path, paths)
            except RuntimeError:
                self._cleanup_temp_zip(temp_zip_path)
                self._busy_reserved = False
                self.emit_state()
                self.emit_toast(self.tr("toast_zip_cancelled"))
                return
            payload_paths = [temp_zip_path]
            payload_size = os.path.getsize(temp_zip_path)
            payload_filename = safe_name(self.tr("zip_bundle_name", name=base, count=file_count - 1))
        else:
            payload_paths = paths[:]
            payload_size = sum(os.path.getsize(path) for path in payload_paths)
            payload_filename = filenames[0]

        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(15)
        try:
            sock.connect((ip, DATA_PORT))
            send_msg(
                sock,
                {
                    "type": "REQUEST_SEND",
                    "name": self.my_name,
                    "ip": self.my_ip,
                    "filename": payload_filename,
                    "display_name": display_name,
                    "size": payload_size,
                    "is_zip": use_zip and file_count > 1,
                    "file_count": file_count,
                },
            )
            response = recv_msg(sock)
            if response and response.get("type") == "ACCEPT":
                self._start_transfer(
                    sock,
                    payload_paths,
                    display_name,
                    payload_filename,
                    payload_size,
                    use_zip=use_zip and file_count > 1,
                    file_count=file_count,
                    temp_zip_path=temp_zip_path,
                )
            elif response and response.get("type") == "REJECT":
                self._cleanup_temp_zip(temp_zip_path)
                self._busy_reserved = False
                self.emit_state()
                self.emit_toast(self.tr("toast_peer_rejected"))
            else:
                self._cleanup_temp_zip(temp_zip_path)
                self._busy_reserved = False
                self.emit_state()
                self.emit_toast(self.tr("toast_peer_no_response"))
        except Exception as exc:
            try:
                sock.close()
            except Exception:
                pass
            self._cleanup_temp_zip(temp_zip_path)
            self._busy_reserved = False
            self.emit_state()
            self.emit_toast(self.tr("toast_connect_failed", error=exc))

    def _send_via_tunnel(self, target_port: int, paths: list[str], filenames: list[str], file_count: int, use_zip: bool, display_name: str) -> None:
        self.tunnel_ws_url, self.tunnel_admin_base, self.tunnel_server_host = build_urls(self.tunnel_host, self.tunnel_ssl)
        temp_zip_path: Optional[str] = None
        self.cancel_flag = False
        if use_zip and file_count > 1:
            base = os.path.splitext(filenames[0])[0]
            temp_zip_path = tempfile.NamedTemporaryFile(suffix=".zip", delete=False).name
            try:
                self._zip_with_progress(temp_zip_path, paths)
            except RuntimeError:
                self._cleanup_temp_zip(temp_zip_path)
                self._busy_reserved = False
                self.emit_state()
                self.emit_toast(self.tr("toast_zip_cancelled"))
                return
            payload_paths = [temp_zip_path]
            payload_size = os.path.getsize(temp_zip_path)
            payload_filename = safe_name(self.tr("zip_bundle_name", name=base, count=file_count - 1))
        else:
            payload_paths = paths[:]
            payload_size = sum(os.path.getsize(path) for path in payload_paths)
            payload_filename = filenames[0]

        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(15)
        try:
            sock.connect((self.tunnel_server_host, int(target_port)))
            send_msg(
                sock,
                {
                    "type": "REQUEST_SEND",
                    "name": self.my_name,
                    "ip": self.tunnel_subdomain,
                    "tunnel_name": self.tunnel_subdomain,
                    "filename": payload_filename,
                    "display_name": display_name,
                    "size": payload_size,
                    "is_zip": use_zip and file_count > 1,
                    "file_count": file_count,
                },
            )
            response = recv_msg(sock)
            if response and response.get("type") == "ACCEPT":
                self._start_transfer(
                    sock,
                    payload_paths,
                    display_name,
                    payload_filename,
                    payload_size,
                    use_zip=use_zip and file_count > 1,
                    file_count=file_count,
                    temp_zip_path=temp_zip_path,
                )
            elif response and response.get("type") == "REJECT":
                self._cleanup_temp_zip(temp_zip_path)
                self._busy_reserved = False
                self.emit_state()
                self.emit_toast(self.tr("toast_peer_rejected"))
            else:
                self._cleanup_temp_zip(temp_zip_path)
                self._busy_reserved = False
                self.emit_state()
                self.emit_toast(self.tr("toast_peer_no_response"))
        except Exception as exc:
            try:
                sock.close()
            except Exception:
                pass
            self._cleanup_temp_zip(temp_zip_path)
            self._busy_reserved = False
            self.emit_state()
            self.emit_toast(self.tr("toast_connect_failed", error=exc))

    def _start_transfer(
        self,
        sock: socket.socket,
        paths: list[str],
        display_name: str,
        payload_filename: str,
        payload_size: int,
        *,
        use_zip: bool,
        file_count: int,
        temp_zip_path: Optional[str],
    ) -> None:
        self.active_mode = "send"
        cancel_event = threading.Event()

        def cancel_listener() -> None:
            try:
                while not cancel_event.is_set() and not self.cancel_flag:
                    msg = recv_msg(sock)
                    if not msg:
                        break
                    if msg.get("type") == "CANCEL":
                        self.cancel_flag = True
                        break
            except Exception:
                pass

        threading.Thread(target=cancel_listener, daemon=True).start()

        try:
            send_msg(
                sock,
                {
                    "type": "START_TRANSFER",
                    "filename": payload_filename,
                    "size": payload_size,
                    "is_zip": use_zip,
                    "file_count": file_count,
                },
            )
            self.begin_progress(display_name, payload_size, is_receiving=False, show_item_progress=not (use_zip or file_count <= 1))

            if use_zip:
                zip_path = paths[0]
                zip_size = os.path.getsize(zip_path)
                sent = 0
                with open(zip_path, "rb") as handle:
                    while sent < zip_size and not self.cancel_flag:
                        chunk = handle.read(BUFFER_SIZE)
                        if not chunk:
                            break
                        sock.sendall(chunk)
                        sent += len(chunk)
                        self.update_progress(
                            len(chunk),
                            sent,
                            zip_size,
                            self.tr("progress_send_zip", filename=os.path.basename(zip_path)),
                        )
            elif file_count <= 1:
                path = paths[0]
                file_size = os.path.getsize(path)
                sent = 0
                with open(path, "rb") as handle:
                    while sent < file_size and not self.cancel_flag:
                        chunk = handle.read(BUFFER_SIZE)
                        if not chunk:
                            break
                        sock.sendall(chunk)
                        sent += len(chunk)
                        self.update_progress(
                            len(chunk),
                            sent,
                            file_size,
                            self.tr("progress_send_file", filename=os.path.basename(path)),
                        )
            else:
                files_meta = [{"name": os.path.basename(path), "size": os.path.getsize(path)} for path in paths]
                send_msg(sock, {"type": "FILES_META", "files": files_meta})
                for index, path in enumerate(paths, 1):
                    if self.cancel_flag:
                        break
                    file_name = os.path.basename(path)
                    file_size = os.path.getsize(path)
                    sent = 0
                    with open(path, "rb") as handle:
                        while sent < file_size and not self.cancel_flag:
                            chunk = handle.read(BUFFER_SIZE)
                            if not chunk:
                                break
                            sock.sendall(chunk)
                            sent += len(chunk)
                            self.update_progress(
                                len(chunk),
                                sent,
                                file_size,
                                self.tr("progress_send_file_indexed", filename=file_name, index=index, count=file_count),
                            )
        finally:
            cancel_event.set()
            try:
                sock.close()
            except Exception:
                pass
            self._cleanup_temp_zip(temp_zip_path)
            self.clear_progress()
            self.active_mode = None
            self._busy_reserved = False
            self.emit_state()

        if self.cancel_flag:
            self.emit_toast(self.tr("toast_send_cancelled"))
        else:
            self.emit_toast(self.tr("toast_send_complete", name=display_name))

    # ------------------------
    # progress
    # ------------------------
    def begin_progress(self, title: str, total_size: int, *, is_receiving: bool, show_item_progress: bool) -> None:
        with self._lock:
            self.start_time_total = time.time()
            self.total_size = int(total_size)
            self.total_bytes_done = 0
            self._busy_reserved = False
            self.transfer_progress = {
                "title": title,
                "itemLabel": self.tr("progress_prepare_receive") if is_receiving else self.tr("progress_prepare_send"),
                "totalBytes": int(total_size),
                "transferredBytes": 0,
                "itemBytes": 0,
                "itemTransferredBytes": 0,
                "speedBytesPerSecond": 0.0,
                "remainingSeconds": 0.0,
                "isReceiving": is_receiving,
                "showItemProgress": show_item_progress,
            }
            self._last_progress_emit = 0.0
        self.emit_state()

    def update_progress(self, chunk_size: int, item_done: int, item_total: int, item_label: str) -> None:
        with self._lock:
            if not self.transfer_progress:
                return
            self.total_bytes_done += int(chunk_size)
            elapsed = max(time.time() - self.start_time_total, 0.001)
            bps = self.total_bytes_done / elapsed
            remaining = (self.total_size - self.total_bytes_done) / bps if bps > 0 and self.total_size > 0 else 0.0
            self.transfer_progress.update(
                {
                    "itemLabel": item_label,
                    "transferredBytes": self.total_bytes_done,
                    "itemBytes": int(item_total),
                    "itemTransferredBytes": int(item_done),
                    "speedBytesPerSecond": bps,
                    "remainingSeconds": remaining,
                }
            )
        now = time.time()
        if now - self._last_progress_emit >= 0.1 or self.total_bytes_done >= self.total_size:
            self._last_progress_emit = now
            self.emit_state()

    def clear_progress(self) -> None:
        with self._lock:
            self.transfer_progress = None
            self.total_size = 0
            self.total_bytes_done = 0
            self.start_time_total = 0.0
            self._last_progress_emit = 0.0
        self.emit_state()
