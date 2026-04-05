#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
rhkr8521 P2P File Transfer (LAN + Tunnel UI Toggle) v17.2

변경점:
- 터널 피어 조회를 /api/tunnels + BasicAuth 방식에서
  /_health?token=... GET 방식으로 변경
- 터널 설정 UI에서 ADMIN_USER / ADMIN_PASS 제거
- TOKEN 입력창을 비밀번호처럼 마스킹(show="*")
- 터널 서버 등록 포트 이름을 "file" -> "file-tunnel" 로 변경
- 피어 감지도 tcp["file-tunnel"] 기준으로 변경
- 그 외 기존 기능 유지 (LAN <-> Tunnel 토글, 진행바/취소/zip 등)

환경변수(터널):
  TUNNEL_HOST          예: example.com:8080   또는  example.com
  TUNNEL_SSL           "1"이면 SSL 기본 체크
  TUNNEL_TOKEN         헬스체크/터널 등록 토큰
  TUNNEL_SUB_PREFIX    ft
  CONTROL_CODEC        pickle(기본) / json
  LOCAL_FILE_PORT      50000
  PEER_POLL_INTERVAL   3.0

주의:
- 피어 목록은 /_health?token=... 응답의 tunnels 기준으로 표시합니다.
- 파일 전송 대상으로 보이려면 각 터널의 tcp 정보에 "file-tunnel" 포트가 있어야 합니다.
- 터널 데이터 릴레이는 서버가 열어준 "file-tunnel 포트"로 TCP 접속합니다.
"""

import os
import time
import json
import uuid
import base64
import socket
import struct
import pickle
import threading
import tempfile
import zipfile
import datetime
import psutil
from typing import Any, Dict, Optional

import tkinter as tk
from tkinter import ttk, messagebox, filedialog


# =========================
# LAN 설정
# =========================
BROADCAST_PORT = 37020
DATA_PORT = 37021
BUFFER_SIZE = 65536
IP_FILTER = ("lo", "docker", "veth", "br-", "vm", "tap", "virbr", "wg")


# =========================
# Tunnel 설정 (env)
# =========================
TUNNEL_HOST = os.getenv("TUNNEL_HOST", "rhkr8521-tunnel.kro.kr")  # 단일 host:port
TUNNEL_SSL = os.getenv("TUNNEL_SSL", "1").strip() in ("1", "true", "True", "yes", "Y", "y")
TUNNEL_TOKEN = os.getenv("TUNNEL_TOKEN", "public-p2p-token-8521")
TUNNEL_SUB_PREFIX = os.getenv("TUNNEL_SUB_PREFIX", "ft")
LOCAL_FILE_PORT = int(os.getenv("LOCAL_FILE_PORT", "50000"))
POLL_INTERVAL_SEC = float(os.getenv("PEER_POLL_INTERVAL", "3.0"))

TUNNEL_TCP_NAME = "file_tunnel"
CONTROL_CODEC = os.getenv("CONTROL_CODEC", "pickle").strip().lower()


# =========================
# 메시지 유틸 (공통)
# =========================
def send_msg(sock, data):
    if CONTROL_CODEC == "pickle":
        msg = pickle.dumps(data)
    else:
        try:
            msg = json.dumps(data, ensure_ascii=False).encode("utf-8")
        except Exception:
            msg = pickle.dumps(data)
    sock.sendall(struct.pack(">I", len(msg)) + msg)

def recvall(sock, n):
    data = b""
    while len(data) < n:
        packet = sock.recv(n - len(data))
        if not packet:
            return None
        data += packet
    return data

def recv_msg(sock):
    raw = recvall(sock, 4)
    if not raw:
        return None
    msglen = struct.unpack(">I", raw)[0]
    data = recvall(sock, msglen)
    if not data:
        return None
    if data[:1] in (b"{", b"[", b'"'):
        try:
            return json.loads(data.decode("utf-8", errors="replace"))
        except Exception:
            pass
    return pickle.loads(data)

def human_readable_size(size_bytes):
    size_bytes = float(size_bytes or 0)
    for unit in ["B", "KB", "MB", "GB", "TB"]:
        if size_bytes < 1024.0:
            return f"{size_bytes:.2f} {unit}" if unit != "B" else f"{int(size_bytes)} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.2f} PB"

def safe_name(filename: str) -> str:
    s = "".join(c for c in (filename or "") if c.isalnum() or c in "._- ()[]{}@+&,")
    s = s.strip()
    return s if s else "downloaded_file.dat"

def b64e(b: bytes) -> str:
    return base64.b64encode(b).decode("ascii")

def b64d(s: str) -> bytes:
    return base64.b64decode((s or "").encode("ascii")) if s else b""

def make_subdomain(prefix: str) -> str:
    host = socket.gethostname().replace("-", "").replace("_", "")
    host = "".join([c for c in host if c.isalnum()])[:12] or "pc"
    return f"{prefix}{host}{uuid.uuid4().hex[:6]}".lower()

def build_urls(hostport: str, use_ssl: bool):
    """
    hostport: "example.com:8080" 또는 "example.com"
    return (ws_url, admin_base, tcp_host)
      ws_url    : ws(s)://hostport/_ws
      admin_base: http(s)://hostport
      tcp_host  : host (포트 제외). (server에 file-tunnel port로 TCP 연결할 때 사용)
    """
    hp = (hostport or "").strip()
    hp = hp.replace("http://", "").replace("https://", "").replace("ws://", "").replace("wss://", "")
    hp = hp.strip().strip("/")
    scheme_ws = "wss" if use_ssl else "ws"
    scheme_http = "https" if use_ssl else "http"
    ws_url = f"{scheme_ws}://{hp}/_ws"
    admin_base = f"{scheme_http}://{hp}"

    host_only = hp.split("/", 1)[0]
    if ":" in host_only:
        host_only = host_only.rsplit(":", 1)[0]
    return ws_url, admin_base, host_only


# =========================
# LAN 네트워크 유틸
# =========================
def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 53))
        return s.getsockname()[0]
    except:
        for iface_name, addrs in psutil.net_if_addrs().items():
            if any(p in iface_name for p in IP_FILTER):
                continue
            for addr in addrs:
                if addr.family == socket.AF_INET and addr.address:
                    ip = addr.address
                    if not ip.startswith("127.") and ip != "0.0.0.0":
                        return ip
        return "127.0.0.1"

def get_broadcast_ip():
    try:
        for iface_name, addrs in psutil.net_if_addrs().items():
            if any(p in iface_name for p in IP_FILTER):
                continue
            for addr in addrs:
                if addr.family == socket.AF_INET and addr.broadcast:
                    ip = addr.address
                    if ip and not ip.startswith("127."):
                        return addr.broadcast
        return "255.255.255.255"
    except:
        return "255.255.255.255"


class Device:
    def __init__(self, ip, name="Unknown"):
        self.ip, self.name, self.last_seen = ip, name, time.time()

    def update_seen(self):
        self.last_seen = time.time()

    def __repr__(self):
        return f"{self.name} ({self.ip})"


# =========================
# Tunnel Bridge Client (서버 수정 0줄)
# =========================
class TunnelBridgeClient:
    def __init__(self, ws_url: str, subdomain: str, token: str, local_port: int, display_name: Optional[str] = None):
        self.ws_url = ws_url
        self.subdomain = subdomain
        self.token = token
        self.local_port = local_port
        self.display_name = (display_name or subdomain or "PC").strip()

        self._thread = None
        self._stop = threading.Event()
        self.connected = False
        self.last_assigned_port = None
        self._status_cb = None

    def set_status_callback(self, cb):
        self._status_cb = cb

    def _emit(self, kind: str, data=None):
        if self._status_cb:
            try:
                self._status_cb(kind, data)
            except Exception:
                pass

    def start(self):
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()

    def _run(self):
        import asyncio
        try:
            import aiohttp
        except Exception as e:
            self._emit("ws_error", {"error": f"aiohttp import failed: {e}"})
            return

        class suppress:
            def __init__(self, *exc): self.exc = exc or (Exception,)
            def __enter__(self): return self
            def __exit__(self, et, ev, tb): return et is not None and issubclass(et, self.exc)

        async def main_loop():
            backoff = 1.0
            while not self._stop.is_set():
                try:
                    timeout = aiohttp.ClientTimeout(total=None, sock_read=None, sock_connect=30)
                    async with aiohttp.ClientSession(timeout=timeout) as session:
                        async with session.ws_connect(self.ws_url, heartbeat=20) as ws:
                            self.connected = True
                            self._emit("ws_connected", {"ws_url": self.ws_url})

                            reg = {
                                "type": "register",
                                "subdomain": self.subdomain,
                                "auth_token": self.token,
                                "name": self.display_name,
                                "display_name": self.display_name,
                                "client_name": self.display_name,
                                "metadata": {
                                    "device_name": self.display_name,
                                    "display_name": self.display_name,
                                    "platform": "desktop",
                                },
                                "tcp_configs": [{"name": TUNNEL_TCP_NAME, "remote_port": 0}],
                                "udp_configs": [],
                            }
                            await ws.send_json(reg)

                            tcp_streams = {}  # sid -> (reader, writer)

                            async for msg in ws:
                                if self._stop.is_set():
                                    break

                                if msg.type == aiohttp.WSMsgType.TEXT:
                                    try:
                                        data = json.loads(msg.data)
                                    except Exception:
                                        continue
                                    mtype = data.get("type")

                                    if mtype == "register_result":
                                        if data.get("ok"):
                                            tcp_assigned = data.get("tcp_assigned", []) or []
                                            port = 0
                                            for e in tcp_assigned:
                                                if e.get("name") == TUNNEL_TCP_NAME:
                                                    try:
                                                        port = int(e.get("remote_port") or 0)
                                                    except Exception:
                                                        port = 0
                                                    break
                                            self.last_assigned_port = port
                                            self._emit("registered", {"subdomain": self.subdomain, "file_port": port})
                                            backoff = 1.0
                                        else:
                                            self._emit("register_failed", {"reason": data.get("reason")})
                                            await asyncio.sleep(2)
                                            break

                                    elif mtype == "tcp_open":
                                        name = data.get("name")
                                        sid = data.get("stream_id")
                                        if name != TUNNEL_TCP_NAME or not sid:
                                            await ws.send_json({"type": "tcp_close", "stream_id": sid, "who": "client"})
                                            continue

                                        try:
                                            reader, writer = await asyncio.open_connection("127.0.0.1", self.local_port)
                                        except Exception:
                                            await ws.send_json({"type": "tcp_close", "stream_id": sid, "who": "client"})
                                            continue
                                        tcp_streams[sid] = (reader, writer)

                                        async def pump_local_to_ws(_sid=sid, _reader=reader):
                                            try:
                                                while True:
                                                    chunk = await _reader.read(65536)
                                                    if not chunk:
                                                        break
                                                    await ws.send_json({"type": "tcp_data", "stream_id": _sid, "b64": b64e(chunk)})
                                            except Exception:
                                                pass
                                            finally:
                                                with suppress(Exception):
                                                    await ws.send_json({"type": "tcp_close", "stream_id": _sid, "who": "client"})

                                        asyncio.create_task(pump_local_to_ws())

                                    elif mtype == "tcp_data":
                                        sid = data.get("stream_id")
                                        payload = b64d(data.get("b64", ""))
                                        io = tcp_streams.get(sid)
                                        if io:
                                            _, writer = io
                                            try:
                                                writer.write(payload)
                                                await writer.drain()
                                            except Exception:
                                                pass

                                    elif mtype == "tcp_close":
                                        sid = data.get("stream_id")
                                        io = tcp_streams.pop(sid, None)
                                        if io:
                                            _, writer = io
                                            with suppress(Exception):
                                                writer.close()
                                                await writer.wait_closed()

                                elif msg.type in (aiohttp.WSMsgType.CLOSED, aiohttp.WSMsgType.ERROR):
                                    break

                except asyncio.CancelledError:
                    break
                except Exception as e:
                    self._emit("ws_error", {"error": str(e)})
                    await asyncio.sleep(backoff)
                    backoff = min(backoff * 2, 10.0)
                finally:
                    self.connected = False
                    self._emit("ws_disconnected", None)

        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            loop.run_until_complete(main_loop())
        finally:
            try:
                loop.stop()
            except Exception:
                pass
            loop.close()


# =========================
# /_health 폴링
# =========================
def fetch_tunnel_peers(admin_base: str, token: str):
    import urllib.request
    import urllib.parse

    qs = urllib.parse.urlencode({"token": token})
    url = admin_base.rstrip("/") + f"/_health?{qs}"

    req = urllib.request.Request(url, method="GET")

    with urllib.request.urlopen(req, timeout=8) as resp:
        data = json.loads(resp.read().decode("utf-8", errors="replace"))

    if not data.get("ok"):
        raise RuntimeError(f"서버 응답 오류: {data}")

    tunnels = data.get("tunnels", {}) or {}
    out = {}

    for sub, info in tunnels.items():
        info = info or {}
        tcp = (info.get("tcp") or {})
        fp = tcp.get(TUNNEL_TCP_NAME)
        if fp:
            try:
                meta = (info.get("metadata") or info.get("meta") or {})
                peer_sub = (
                    _find_nested_tunnel_subdomain(info)
                    or _extract_tunnel_subdomain(sub)
                    or sub
                )
                display_title = (
                    info.get("display_name")
                    or info.get("name")
                    or info.get("device_name")
                    or info.get("client_name")
                    or meta.get("display_name")
                    or meta.get("name")
                    or meta.get("device_name")
                    or meta.get("client_name")
                    or sub
                )
                out[sub] = {
                    "file_port": int(fp),
                    "title": str(peer_sub),
                    "display_title": str(display_title),
                    "subdomain": sub,
                }
            except Exception:
                pass
    return out


def _extract_tunnel_subdomain(value):
    text = str(value or "").strip()
    if not text:
        return None
    text = text.split("://", 1)[-1]
    text = text.split("/", 1)[0]
    text = text.rsplit("@", 1)[-1]
    if text.startswith("[") and "]" in text:
        text = text[1:text.index("]")]
    elif text.count(":") == 1:
        text = text.split(":", 1)[0]
    candidate = text.split(".", 1)[0].strip().strip("\"'")
    if not candidate or " " in candidate or candidate == TUNNEL_TCP_NAME:
        return None
    return candidate


def _find_nested_tunnel_subdomain(value, depth=0):
    if depth > 4:
        return None
    if isinstance(value, dict):
        for key in ("subdomain", "requested_subdomain", "assigned_subdomain", "hostname", "host", "domain", "url"):
            candidate = _extract_tunnel_subdomain(value.get(key))
            if candidate:
                return candidate
        for child in value.values():
            candidate = _find_nested_tunnel_subdomain(child, depth + 1)
            if candidate:
                return candidate
    elif isinstance(value, list):
        for child in value:
            candidate = _find_nested_tunnel_subdomain(child, depth + 1)
            if candidate:
                return candidate
    return None


# =========================
# 통합 앱
# =========================
class UnifiedApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("rhkr8521 P2P File Transfer v17.2")

        # 공통
        self.root.resizable(False, False)
        self.my_ip = get_local_ip()
        self.my_name = socket.gethostname()
        self.file_save_path = os.path.join(os.path.expanduser("~"), "Downloads")
        self.running = True
        self.root.cancel_flag = False

        self.total_size = 0
        self.total_bytes_sent = 0
        self.start_time_total = None

        self.active_receive_sock = None
        self.active_mode = None  # "send"/"receive"/None

        # ===== LAN 상태 =====
        self.broadcast_ip = get_broadcast_ip()
        self.devices: Dict[str, Device] = {}
        self.pending_client = None

        # ===== Tunnel 상태 =====
        self.mode = "LAN"

        self.tunnel_host = TUNNEL_HOST
        self.tunnel_ssl = TUNNEL_SSL
        self.tunnel_token = TUNNEL_TOKEN

        self.tunnel_subdomain = make_subdomain(TUNNEL_SUB_PREFIX)
        self.tunnel_ws_url, self.tunnel_admin_base, self.tunnel_server_host = build_urls(self.tunnel_host, self.tunnel_ssl)

        self.tunnel_client: Optional[TunnelBridgeClient] = None
        self.tunnel_peers: Dict[str, Dict[str, Any]] = {}
        self.tunnel_peer_order = []
        self._tunnel_poll_thread = None
        self._local_tunnel_recv_thread = None
        self._tunnel_started = False

        self.setup_gui()

        # LAN threads always on
        self.discovery_thread = threading.Thread(target=self.listen_incoming_discovery, daemon=True)
        self.transfer_thread = threading.Thread(target=self.listen_incoming_for_transfer_lan, daemon=True)
        self.broadcast_thread = threading.Thread(target=self.broadcast_loop, daemon=True)
        self.discovery_thread.start()
        self.transfer_thread.start()
        self.broadcast_thread.start()

        self.root.after(5000, self.cleanup_devices)

        self.set_mode("LAN")
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    # ============ UI safe ============
    def ui(self, fn, *args, **kwargs):
        self.root.after(0, lambda: fn(*args, **kwargs))

    def ui_info(self, t, m): self.ui(messagebox.showinfo, t, m)
    def ui_warn(self, t, m): self.ui(messagebox.showwarning, t, m)
    def ui_error(self, t, m): self.ui(messagebox.showerror, t, m)

    # ============ 표시 문구 ============
    def make_multi_display_name(self, first_name: str, file_count: int) -> str:
        first_name = first_name or "파일"
        if file_count and file_count > 1:
            return f"{first_name} 외 {file_count-1}개"
        return first_name

    # ================= GUI =================
    def setup_gui(self):
        frame_info = ttk.Frame(self.root, padding=10)
        frame_info.pack(fill=tk.X)

        left = ttk.Frame(frame_info)
        left.pack(side=tk.LEFT, fill=tk.X, expand=True)

        ttk.Label(left, text=f"🖥️ IP: {self.my_ip}", font=("Helvetica", 10, "bold")).pack(anchor="w")
        ttk.Label(left, text=f"💾 이름: {self.my_name}").pack(anchor="w")
        self.mode_status = tk.StringVar(value="LAN 모드")
        ttk.Label(left, textvariable=self.mode_status, foreground="gray").pack(anchor="w", pady=(2, 0))

        right = ttk.Frame(frame_info)
        right.pack(side=tk.RIGHT)

        self.mode_btn = ttk.Button(right, text="🌐 터널 접속", command=self.toggle_mode, cursor="hand2")
        self.mode_btn.pack(anchor="e")

        # ===== 터널 설정 프레임 =====
        self.tunnel_frame = ttk.LabelFrame(self.root, text="🌐 터널 설정", padding=10)

        r1 = ttk.Frame(self.tunnel_frame); r1.pack(fill=tk.X, pady=2)
        ttk.Label(r1, text="서버 주소(Host:Port)").pack(side=tk.LEFT)
        self.host_entry = ttk.Entry(r1)
        self.host_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=6)
        self.host_entry.insert(0, self.tunnel_host)

        self.ssl_var = tk.BooleanVar(value=self.tunnel_ssl)
        self.ssl_chk = ttk.Checkbutton(r1, text="SSL", variable=self.ssl_var)
        self.ssl_chk.pack(side=tk.LEFT, padx=(4, 0))

        r2 = ttk.Frame(self.tunnel_frame); r2.pack(fill=tk.X, pady=2)
        ttk.Label(r2, text="TOKEN").pack(side=tk.LEFT)
        self.token_entry = ttk.Entry(r2, show="*")
        self.token_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=6)
        self.token_entry.insert(0, self.tunnel_token)

        r3 = ttk.Frame(self.tunnel_frame); r3.pack(fill=tk.X, pady=(6, 0))
        ttk.Button(r3, text="🔄 적용/재연결", command=self.apply_tunnel_settings).pack(side=tk.LEFT)
        self.tunnel_status = tk.StringVar(value="(터널 미접속)")
        ttk.Label(r3, textvariable=self.tunnel_status, foreground="gray").pack(side=tk.RIGHT)

        # ===== 장치 목록 =====
        self.frame_devices = ttk.LabelFrame(self.root, text="📡 장치 목록(LAN)", padding=10)
        self.frame_devices.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)

        top_frame = ttk.Frame(self.frame_devices)
        top_frame.pack(fill=tk.X, pady=(0, 5))

        self.device_list = tk.Listbox(top_frame, height=8, font=("Courier New", 10))
        ttk.Scrollbar(top_frame, orient="vertical", command=self.device_list.yview).pack(side=tk.RIGHT, fill=tk.Y)
        self.device_list.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        ttk.Button(top_frame, text="🔄", command=self.manual_update, width=2, cursor="hand2").pack(side=tk.RIGHT, padx=5)

        # ===== 액션 =====
        frame_actions = ttk.Frame(self.root, padding=10)
        frame_actions.pack(fill=tk.X)
        ttk.Button(frame_actions, text="📁 저장 폴더", command=self.set_save_folder).pack(side=tk.LEFT, padx=5)
        ttk.Button(frame_actions, text="📤 전송", command=self.send_file_prompt).pack(side=tk.LEFT, padx=5)

        self.save_path_label = ttk.Label(frame_actions, text=f"💾 저장: {self.file_save_path}", foreground="gray")
        self.save_path_label.pack(side=tk.RIGHT, padx=10)

        # ===== Progress UI =====
        self.progress_frame = ttk.Frame(self.root)
        self.progress_frame.pack(fill=tk.X, padx=10, pady=5)

        self.indiv_progress_frame = ttk.Frame(self.progress_frame)
        self.indiv_progress_frame.pack(fill=tk.X)

        self.indiv_label = ttk.Label(self.indiv_progress_frame, text="파일: -", font=("Helvetica", 9))
        self.indiv_label.pack(anchor="w")

        top_row = ttk.Frame(self.indiv_progress_frame)
        top_row.pack(fill=tk.X)

        self.indiv_var = tk.DoubleVar()
        self.indiv_bar = ttk.Progressbar(top_row, variable=self.indiv_var, maximum=100)
        self.indiv_bar.pack(side=tk.LEFT, fill=tk.X, expand=True)

        self.indiv_pct_label = ttk.Label(top_row, text="0%", font=("Helvetica", 9, "bold"))
        self.indiv_pct_label.pack(side=tk.RIGHT)

        self.total_label = ttk.Label(self.progress_frame, text="전체 진행: 0 / 0", font=("Helvetica", 9, "bold"))
        self.total_label.pack(anchor="w", pady=(5, 0))

        self.bottom_row = ttk.Frame(self.progress_frame)
        self.bottom_row.pack(fill=tk.X)

        self.total_var = tk.DoubleVar()
        self.total_bar = ttk.Progressbar(self.bottom_row, variable=self.total_var, maximum=100)
        self.total_bar.pack(side=tk.LEFT, fill=tk.X, expand=True)

        self.total_pct_label = ttk.Label(self.bottom_row, text="0%", font=("Helvetica", 9, "bold"))
        self.total_pct_label.pack(side=tk.RIGHT)

        self.speed_label = ttk.Label(self.progress_frame, text="속도: 0 MB/s | 남은 시간: --:--", foreground="gray")
        self.speed_label.pack(anchor="e", pady=2)

        self.cancel_frame = ttk.Frame(self.progress_frame)
        self.cancel_button = ttk.Button(self.cancel_frame, text="❌ 전송 중단", command=self.cancel_transfer, width=15)
        self.cancel_button.pack()
        self.cancel_frame.pack(pady=5)
        self.cancel_frame.pack_forget()

        self.hide_progress()

    # ================= 모드 전환 =================
    def toggle_mode(self):
        self.set_mode("TUNNEL" if self.mode == "LAN" else "LAN")

    def set_mode(self, mode: str):
        self.mode = mode

        if mode == "LAN":
            self.mode_status.set("LAN 모드")
            self.mode_btn.config(text="🌐 터널 접속")
            self.frame_devices.config(text="📡 장치 목록(LAN)")
            self.tunnel_frame.pack_forget()
            self.refresh_device_list_lan()
        else:
            self.mode_status.set("터널 모드")
            self.mode_btn.config(text="📡 LAN 통신")
            self.frame_devices.config(text="🌐 터널 PC 목록")
            self.tunnel_frame.pack(fill=tk.X, padx=10, pady=(0, 5))
            self.start_tunnel_stack()

    # ================= Tunnel start/stop =================
    def start_tunnel_stack(self):
        if self._tunnel_started:
            return
        self._tunnel_started = True

        self._local_tunnel_recv_thread = threading.Thread(target=self._local_receive_server_tunnel, daemon=True)
        self._local_tunnel_recv_thread.start()

        self.tunnel_client = TunnelBridgeClient(
            self.tunnel_ws_url,
            self.tunnel_subdomain,
            self.tunnel_token,
            LOCAL_FILE_PORT,
            display_name=self.my_name,
        )
        self.tunnel_client.set_status_callback(self._on_tunnel_status)
        self.tunnel_client.start()

        self._tunnel_poll_thread = threading.Thread(target=self._tunnel_poll_loop, daemon=True)
        self._tunnel_poll_thread.start()

    def apply_tunnel_settings(self):
        self.tunnel_host = self.host_entry.get().strip() or self.tunnel_host
        self.tunnel_ssl = bool(self.ssl_var.get())
        self.tunnel_token = self.token_entry.get().strip() or self.tunnel_token

        self.tunnel_ws_url, self.tunnel_admin_base, self.tunnel_server_host = build_urls(self.tunnel_host, self.tunnel_ssl)

        if self.tunnel_client:
            try:
                self.tunnel_client.stop()
            except Exception:
                pass

        self.tunnel_client = TunnelBridgeClient(
            self.tunnel_ws_url,
            self.tunnel_subdomain,
            self.tunnel_token,
            LOCAL_FILE_PORT,
            display_name=self.my_name,
        )
        self.tunnel_client.set_status_callback(self._on_tunnel_status)
        self.tunnel_client.start()

        self.ui_info("적용", f"적용 완료:\nWS={self.tunnel_ws_url}\nHEALTH={self.tunnel_admin_base}/_health?token=***")

    def _on_tunnel_status(self, kind: str, data: Any):
        if kind == "ws_connected":
            self.ui(self.tunnel_status.set, "WS 연결됨(등록 중...)")
        elif kind == "registered":
            port = 0
            try:
                port = int((data or {}).get("file_port") or 0)
            except Exception:
                port = 0
            if port:
                self.ui(self.tunnel_status.set, f"등록 완료 ✅ (내 {TUNNEL_TCP_NAME} 포트: {port})")
            else:
                self.ui(self.tunnel_status.set, f"등록 완료 ✅ ({TUNNEL_TCP_NAME} 포트 없음?)")
        elif kind == "register_failed":
            self.ui(self.tunnel_status.set, f"등록 실패 ❌ ({(data or {}).get('reason')})")
        elif kind == "ws_error":
            self.ui(self.tunnel_status.set, f"오류: {(data or {}).get('error')}")
        elif kind == "ws_disconnected":
            self.ui(self.tunnel_status.set, "WS 끊김(재시도 중...)")

    def _tunnel_poll_loop(self):
        while self.running:
            try:
                peers = fetch_tunnel_peers(self.tunnel_admin_base, self.tunnel_token)
                peers.pop(self.tunnel_subdomain, None)
                self.tunnel_peers = peers
                if self.mode == "TUNNEL":
                    self.ui(self.refresh_device_list_tunnel)
            except Exception as e:
                if self.mode == "TUNNEL":
                    self.ui(self.tunnel_status.set, f"피어 조회 실패(/_health): {e}")
            time.sleep(POLL_INTERVAL_SEC)

    def refresh_device_list_tunnel(self):
        self.device_list.delete(0, tk.END)
        self.tunnel_peer_order = sorted(
            self.tunnel_peers.keys(),
            key=lambda sub: sub.lower(),
        )
        for sub in self.tunnel_peer_order:
            peer = self.tunnel_peers.get(sub, {})
            p = 0
            title = str(peer.get("title") or sub)
            display_title = str(peer.get("display_title") or "").strip()
            try:
                p = int(peer.get("file_port") or 0)
            except Exception:
                p = 0
            label = title if not display_title or display_title == title else f"{title} ({display_title})"
            self.device_list.insert(tk.END, f"{label:24}  {TUNNEL_TCP_NAME}:{p}")

    # ================== 공통 UI 기능 ==================
    def set_save_folder(self):
        path = filedialog.askdirectory(initialdir=self.file_save_path)
        if path:
            self.file_save_path = path
            self.save_path_label.config(text=f"💾 저장: {self.file_save_path}")

    def hide_progress(self):
        self.progress_frame.pack_forget()
        self.indiv_var.set(0)
        self.total_var.set(0)
        self.indiv_pct_label.config(text="0%")
        self.total_pct_label.config(text="0%")
        self.speed_label.config(text="속도: 0 MB/s | 남은 시간: --:--")
        self.indiv_label.config(text="파일: -")
        self.total_label.config(text="전체 진행: 0 / 0")
        self.cancel_frame.pack_forget()

    def show_progress(self, display_title, total_size, is_receiving=False, hide_indiv=False):
        self.progress_frame.pack(fill=tk.X, padx=10, pady=5)

        self.indiv_progress_frame.pack_forget()
        self.total_label.pack_forget()
        self.bottom_row.pack_forget()
        self.speed_label.pack_forget()
        self.cancel_frame.pack_forget()

        if not hide_indiv:
            self.indiv_progress_frame.pack(fill=tk.X)

        self.total_label.pack(anchor="w", pady=(5, 0))
        self.bottom_row.pack(fill=tk.X)
        self.speed_label.pack(anchor="e", pady=2)
        self.cancel_frame.pack(pady=5)

        self.indiv_label.config(text=f"{display_title} {'수신 중...' if is_receiving else '진행 중...'}")
        self.indiv_var.set(0)
        self.indiv_pct_label.config(text="0%")

        self.total_size = int(total_size)
        self.total_bytes_sent = 0
        self.start_time_total = time.time()
        self.total_label.config(text=f"전체 진행: 0 / {human_readable_size(self.total_size)}")
        self.total_var.set(0)
        self.total_pct_label.config(text="0%")
        self.speed_label.config(text="속도: 0 MB/s | 남은 시간: --:--")

    def format_time(self, seconds):
        seconds = max(0, seconds)
        if seconds < 60:
            return f"{int(seconds)}초"
        if seconds < 3600:
            return f"{int(seconds // 60)}분 {int(seconds % 60)}초"
        h = int(seconds // 3600)
        m = int((seconds % 3600) // 60)
        return f"{h}시간 {m}분"

    def update_progress(self, chunk_size, file_done, file_size, hide_indiv=False):
        self.total_bytes_sent += int(chunk_size)

        elapsed_total = (time.time() - self.start_time_total) if self.start_time_total else 0.000001
        bps = self.total_bytes_sent / elapsed_total if elapsed_total > 0 else 0
        speed_mb = (bps / (1024 * 1024)) if bps > 0 else 0.0
        remaining_sec = (self.total_size - self.total_bytes_sent) / bps if bps > 0 else 0

        total_percent = min(100.0, (self.total_bytes_sent / self.total_size) * 100.0) if self.total_size > 0 else 100.0
        self.total_var.set(total_percent)
        self.total_pct_label.config(text=f"{total_percent:.1f}%")
        self.total_label.config(text=f"전체 진행: {human_readable_size(self.total_bytes_sent)} / {human_readable_size(self.total_size)}")
        self.speed_label.config(text=f"속도: {speed_mb:.2f} MB/s | 남은 시간: {self.format_time(remaining_sec)}")

        if not hide_indiv:
            indiv_percent = min(100.0, (file_done / file_size) * 100.0) if file_size > 0 else 100.0
            self.indiv_var.set(indiv_percent)
            self.indiv_pct_label.config(text=f"{indiv_percent:.1f}%")

        self.root.update_idletasks()

    def cancel_transfer(self):
        self.root.cancel_flag = True
        if self.active_mode == "receive" and self.active_receive_sock:
            try:
                send_msg(self.active_receive_sock, {"type": "CANCEL"})
            except Exception:
                pass
        self.ui(self.hide_progress)

    # ================== LAN: discovery ==================
    def broadcast_loop(self):
        while self.running:
            self.broadcast_discovery()
            time.sleep(5)

    def broadcast_discovery(self, force=False):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        s.settimeout(0.5)
        try:
            s.sendto(json.dumps({
                "type": "DISCOVERY",
                "name": self.my_name,
                "ip": self.my_ip,
                "port": DATA_PORT
            }).encode(), (self.broadcast_ip, BROADCAST_PORT))
        except:
            pass
        finally:
            s.close()

    def listen_incoming_discovery(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("0.0.0.0", BROADCAST_PORT))
        except Exception:
            return
        s.settimeout(0.5)

        while self.running:
            try:
                data, _ = s.recvfrom(65535)
                if not data:
                    continue
                try:
                    msg = json.loads(data.decode())
                    if msg.get("type") == "DISCOVERY":
                        ip, name = msg.get("ip"), msg.get("name")
                        if ip and ip != self.my_ip:
                            if ip not in self.devices:
                                self.devices[ip] = Device(ip, name or "Unknown")
                            else:
                                self.devices[ip].update_seen()
                                self.devices[ip].name = name or self.devices[ip].name
                            if self.mode == "LAN":
                                self.root.after(0, self.refresh_device_list_lan)
                except json.JSONDecodeError:
                    pass
            except socket.timeout:
                continue
            except Exception:
                break

    def manual_update(self):
        if self.mode == "LAN":
            self.broadcast_discovery(force=True)
            self.root.after(500, self.refresh_device_list_lan)
        else:
            try:
                peers = fetch_tunnel_peers(self.tunnel_admin_base, self.tunnel_token)
                peers.pop(self.tunnel_subdomain, None)
                self.tunnel_peers = peers
                self.refresh_device_list_tunnel()
            except Exception as e:
                self.tunnel_status.set(f"피어 조회 실패: {e}")

    def cleanup_devices(self):
        now = time.time()
        for ip in list(self.devices.keys()):
            if now - self.devices[ip].last_seen > 20:
                del self.devices[ip]
        if self.mode == "LAN":
            self.refresh_device_list_lan()
        if self.running:
            self.root.after(5000, self.cleanup_devices)

    def refresh_device_list_lan(self):
        self.device_list.delete(0, tk.END)
        for dev in self.devices.values():
            self.device_list.insert(tk.END, str(dev))

    # ================== LAN: transfer server ==================
    def listen_incoming_for_transfer_lan(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("0.0.0.0", DATA_PORT))
            s.listen(5)
            s.settimeout(0.5)
        except Exception:
            return

        while self.running:
            try:
                client, _ = s.accept()
                threading.Thread(target=self.handle_transfer_common, args=(client,), daemon=True).start()
            except socket.timeout:
                continue
            except Exception:
                break

    # ================== Tunnel: local receive server ==================
    def _local_receive_server_tunnel(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("127.0.0.1", LOCAL_FILE_PORT))
            s.listen(10)
        except Exception as e:
            self.ui_error("오류", f"터널 로컬 수신 서버 바인드 실패: 127.0.0.1:{LOCAL_FILE_PORT}\n{e}")
            return

        while self.running:
            try:
                client, _ = s.accept()
                threading.Thread(target=self.handle_transfer_common, args=(client,), daemon=True).start()
            except Exception:
                continue

    # ================== 공통: 수신 핸들러 ==================
    def handle_transfer_common(self, client: socket.socket):
        self.root.cancel_flag = False
        try:
            client.settimeout(None)

            while True:
                msg = recv_msg(client)
                if not msg:
                    break

                mtype = msg.get("type")

                if mtype == "REQUEST_SEND":
                    display_name = msg.get("display_name", msg.get("filename", "파일"))
                    sender_name = msg.get("name", "Unknown")
                    sender_ip = msg.get("ip", "?")
                    payload_size = int(msg.get("size", 0))

                    total_size_str = human_readable_size(payload_size)
                    answer = messagebox.askyesno(
                        "파일 수신 요청",
                        f"[{sender_name}({sender_ip})]가 {display_name} ({total_size_str})를 보내려 합니다.\n수신하시겠습니까?"
                    )
                    try:
                        if answer:
                            send_msg(client, {"type": "ACCEPT"})
                        else:
                            send_msg(client, {"type": "REJECT"})
                            self.ui_info("거절됨", "수신이 거절되었습니다.")
                            return
                    except Exception:
                        return

                elif mtype == "START_TRANSFER":
                    is_zip = bool(msg.get("is_zip", False))
                    file_count = int(msg.get("file_count", 1))
                    total_size = int(msg.get("size", 0))
                    filename = msg.get("filename", "downloaded_file.dat")

                    self.active_receive_sock = client
                    self.active_mode = "receive"

                    if is_zip:
                        self.receive_single_file(client, filename, total_size, is_zip=True, file_count=file_count)
                    else:
                        if file_count <= 1:
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

    # ================== 수신 로직 ==================
    def _prepare_unique_path(self, base_dir, filename):
        base_dir = base_dir or os.getcwd()
        os.makedirs(base_dir, exist_ok=True)
        fn = safe_name(filename)
        path = os.path.join(base_dir, fn)
        base, ext = os.path.splitext(path)
        c = 1
        while os.path.exists(path):
            path = f"{base}_{c}{ext}"
            c += 1
        return path

    def _delete_paths_safely(self, paths):
        for p in paths:
            try:
                if os.path.exists(p) and os.path.isfile(p):
                    os.remove(p)
            except Exception:
                pass

    def _build_receive_done_message(self, base_path, saved_files, extracted_files=None):
        lines = [f"저장 위치: {base_path}"]
        if saved_files:
            lines += ["", "[저장된 파일]"]
            for x in saved_files[:30]:
                lines.append(f"- {os.path.basename(x)}")
            if len(saved_files) > 30:
                lines.append(f"... 외 {len(saved_files)-30}개")
        if extracted_files:
            lines += ["", "[추출된 파일]"]
            for x in extracted_files[:30]:
                lines.append(f"- {x}")
            if len(extracted_files) > 30:
                lines.append(f"... 외 {len(extracted_files)-30}개")
        return "\n".join(lines)

    def receive_single_file(self, sock, filename, file_size, is_zip=False, file_count=1):
        self.root.cancel_flag = False
        saved_paths = []
        extracted_list = None

        download_dir = self.file_save_path
        os.makedirs(download_dir, exist_ok=True)

        save_path = self._prepare_unique_path(download_dir, filename)
        saved_paths.append(save_path)

        self.ui(self.show_progress, filename, file_size, True, True)

        total = 0
        interrupted = False
        try:
            with open(save_path, "wb") as f:
                while total < file_size and not self.root.cancel_flag:
                    chunk = sock.recv(BUFFER_SIZE)
                    if not chunk:
                        interrupted = True
                        break
                    f.write(chunk)
                    total += len(chunk)
                    self.ui(self.update_progress, len(chunk), total, file_size, True)
        except Exception:
            interrupted = True

        try:
            sock.close()
        except Exception:
            pass

        if self.root.cancel_flag:
            self.ui(self.hide_progress)
            self._delete_paths_safely(saved_paths)
            self.ui_info("중단", "수신을 중단했습니다. (부분 파일 삭제 완료)")
            return

        if interrupted or total < file_size:
            self.ui(self.hide_progress)
            self._delete_paths_safely(saved_paths)
            self.ui_info("중단", "전송측에서 전송을 중단했습니다. (미완료 파일 삭제 완료)")
            return

        if is_zip:
            extract_dir = os.path.splitext(save_path)[0]
            os.makedirs(extract_dir, exist_ok=True)
            try:
                with zipfile.ZipFile(save_path, "r") as z:
                    extracted_list = z.namelist()
                    z.extractall(extract_dir)
                os.remove(save_path)
                base_path_for_msg = extract_dir
                saved_for_msg = []
            except Exception:
                base_path_for_msg = download_dir
                saved_for_msg = saved_paths
        else:
            base_path_for_msg = download_dir
            saved_for_msg = saved_paths

        self.ui(self.hide_progress)
        msg = self._build_receive_done_message(
            base_path_for_msg,
            saved_for_msg if not is_zip else [],
            extracted_list if is_zip else None
        )
        self.ui_info("완료", msg)

    def receive_multi_files(self, sock, total_size, file_count):
        self.root.cancel_flag = False
        saved_paths = []
        interrupted = False

        download_dir = self.file_save_path
        os.makedirs(download_dir, exist_ok=True)

        meta = recv_msg(sock)
        if not meta or meta.get("type") != "FILES_META":
            try:
                sock.close()
            except Exception:
                pass
            self.ui_error("오류", "다중 파일 메타데이터 수신 실패")
            return

        files = meta.get("files", [])
        if not isinstance(files, list) or len(files) != file_count:
            try:
                sock.close()
            except Exception:
                pass
            self.ui_error("오류", "다중 파일 메타데이터가 올바르지 않습니다.")
            return

        self.ui(self.show_progress, "다중 파일", total_size, True, False)

        try:
            for idx, info in enumerate(files, 1):
                if self.root.cancel_flag:
                    break

                fname = safe_name(info.get("name", f"file_{idx}.dat"))
                fsize = int(info.get("size", 0))

                save_path = self._prepare_unique_path(download_dir, fname)
                saved_paths.append(save_path)

                self.ui(self.indiv_label.config, text=f"파일 {fname} ({idx}/{file_count}) 수신 중...")
                self.ui(self.indiv_var.set, 0)
                self.ui(self.indiv_pct_label.config, text="0%")

                done = 0
                with open(save_path, "wb") as f:
                    while done < fsize and not self.root.cancel_flag:
                        chunk = sock.recv(min(BUFFER_SIZE, fsize - done))
                        if not chunk:
                            interrupted = True
                            break
                        f.write(chunk)
                        done += len(chunk)
                        self.ui(self.update_progress, len(chunk), done, fsize, False)

                if self.root.cancel_flag or interrupted or done < fsize:
                    break

        except Exception:
            interrupted = True

        try:
            sock.close()
        except Exception:
            pass

        if self.root.cancel_flag:
            self.ui(self.hide_progress)
            self._delete_paths_safely(saved_paths)
            self.ui_info("중단", "수신을 중단했습니다. (이번 전송 파일 삭제 완료)")
            return

        if interrupted:
            self.ui(self.hide_progress)
            self._delete_paths_safely(saved_paths)
            self.ui_info("중단", "전송측에서 전송을 중단했습니다. (미완료/이번 전송 파일 삭제 완료)")
            return

        self.ui(self.hide_progress)
        msg = self._build_receive_done_message(download_dir, saved_paths, None)
        self.ui_info("완료", msg)

    # ================== 송신 (LAN/Tunnel 분기) ==================
    def send_file_prompt(self):
        self.root.cancel_flag = False
        sel = self.device_list.curselection()
        if not sel:
            self.ui_warn("오류", "전송할 대상을 선택하세요.")
            return

        paths = list(filedialog.askopenfilenames(title="전송할 파일 선택 (다중 가능)"))
        if not paths:
            self.ui_warn("파일 없음", "파일을 선택해주세요.")
            return

        filenames = [os.path.basename(p) for p in paths]
        file_count = len(paths)

        if file_count > 1:
            use_zip = messagebox.askyesnocancel(
                "압축 선택",
                "파일을 ZIP으로 압축하여 전송하시겠습니까?\n\n예: ZIP 압축 (단일 ZIP 전송)\n아니오: 원본 그대로(다중 파일) 전송"
            )
            if use_zip is None:
                return
        else:
            use_zip = False

        display_name = self.make_multi_display_name(filenames[0], file_count)

        if self.mode == "LAN":
            ip = list(self.devices.keys())[sel[0]] if sel[0] < len(self.devices) else None
            if not ip:
                self.ui_error("오류", "대상 장치를 찾을 수 없습니다.")
                return
            threading.Thread(
                target=self._send_via_lan,
                args=(ip, paths, filenames, file_count, use_zip, display_name),
                daemon=True
            ).start()
        else:
            target_sub = self.tunnel_peer_order[sel[0]] if sel[0] < len(self.tunnel_peer_order) else None
            peer = self.tunnel_peers.get(target_sub)
            if not peer:
                self.ui_error("오류", "대상 터널 정보를 찾을 수 없습니다.")
                return
            try:
                target_port = int(peer.get("file_port") or 0)
            except Exception:
                target_port = 0
            if not target_port:
                self.ui_error("오류", f"대상 {TUNNEL_TCP_NAME} 포트가 없습니다.")
                return
            threading.Thread(
                target=self._send_via_tunnel,
                args=(target_port, paths, filenames, file_count, use_zip, display_name),
                daemon=True
            ).start()

    def _zip_with_progress(self, zip_path, file_paths):
        total_src = sum(os.path.getsize(p) for p in file_paths)
        self.ui(self.show_progress, "ZIP 압축", total_src, False, False)

        try:
            with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
                for idx, p in enumerate(file_paths, 1):
                    if self.root.cancel_flag:
                        break
                    arc = os.path.basename(p)
                    fsize = os.path.getsize(p)
                    done_file = 0

                    self.ui(self.indiv_label.config, text=f"압축 중: {arc} ({idx}/{len(file_paths)})")
                    self.ui(self.indiv_var.set, 0)
                    self.ui(self.indiv_pct_label.config, text="0%")

                    zi = zipfile.ZipInfo(arc)
                    mtime = os.path.getmtime(p)
                    zi.date_time = datetime.datetime.fromtimestamp(mtime).timetuple()[:6]

                    with open(p, "rb") as src, zf.open(zi, "w") as dest:
                        while not self.root.cancel_flag:
                            chunk = src.read(BUFFER_SIZE)
                            if not chunk:
                                break
                            dest.write(chunk)
                            done_file += len(chunk)
                            self.ui(self.update_progress, len(chunk), done_file, fsize, False)
        finally:
            self.ui(self.hide_progress)

        if self.root.cancel_flag:
            raise RuntimeError("CANCELED_BY_USER")

    def _cleanup_temp_zip(self, path):
        if path and os.path.exists(path):
            try:
                os.remove(path)
            except Exception:
                pass

    def _send_via_lan(self, ip, paths, filenames, file_count, use_zip, display_name):
        temp_zip_path = None
        if use_zip:
            try:
                base = os.path.splitext(filenames[0])[0]
                zip_name = safe_name(f"{base}_외_{file_count-1}개.zip")
                temp_zip_path = tempfile.NamedTemporaryFile(suffix=".zip", delete=False).name
                self._zip_with_progress(temp_zip_path, paths)
                payload_paths = [temp_zip_path]
                payload_size = os.path.getsize(temp_zip_path)
                payload_filename = zip_name
            except RuntimeError:
                self._cleanup_temp_zip(temp_zip_path)
                self.ui_info("중단", "압축을 중단했습니다.")
                return
            except Exception as e:
                self._cleanup_temp_zip(temp_zip_path)
                self.ui_error("압축 실패", f"ZIP 압축 실패: {e}")
                return
        else:
            payload_paths = paths[:]
            payload_size = sum(os.path.getsize(p) for p in payload_paths)
            payload_filename = filenames[0]

        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(15)
        try:
            sock.connect((ip, DATA_PORT))

            send_msg(sock, {
                "type": "REQUEST_SEND",
                "name": self.my_name,
                "ip": self.my_ip,
                "filename": payload_filename,
                "display_name": display_name,
                "size": payload_size,
                "is_zip": use_zip,
                "file_count": file_count
            })

            resp = recv_msg(sock)
            if resp and resp.get("type") == "ACCEPT":
                self._start_transfer(sock, payload_paths, display_name, payload_filename, payload_size, use_zip, file_count, temp_zip_path)
            elif resp and resp.get("type") == "REJECT":
                try:
                    sock.close()
                except:
                    pass
                self._cleanup_temp_zip(temp_zip_path)
                self.ui_info("거절됨", "상대방이 전송을 거절했습니다.")
            else:
                try:
                    sock.close()
                except:
                    pass
                self._cleanup_temp_zip(temp_zip_path)
                self.ui_warn("응답 없음", "서버 응답 없음")
        except Exception as e:
            try:
                sock.close()
            except:
                pass
            self._cleanup_temp_zip(temp_zip_path)
            self.ui_error("연결 오류", f"연결 실패: {e}")

    def _send_via_tunnel(self, target_port, paths, filenames, file_count, use_zip, display_name):
        self.tunnel_ws_url, self.tunnel_admin_base, self.tunnel_server_host = build_urls(self.tunnel_host, self.tunnel_ssl)

        temp_zip_path = None
        if use_zip:
            try:
                base = os.path.splitext(filenames[0])[0]
                zip_name = safe_name(f"{base}_외_{file_count-1}개.zip")
                temp_zip_path = tempfile.NamedTemporaryFile(suffix=".zip", delete=False).name
                self._zip_with_progress(temp_zip_path, paths)
                payload_paths = [temp_zip_path]
                payload_size = os.path.getsize(temp_zip_path)
                payload_filename = zip_name
            except RuntimeError:
                self._cleanup_temp_zip(temp_zip_path)
                self.ui_info("중단", "압축을 중단했습니다.")
                return
            except Exception as e:
                self._cleanup_temp_zip(temp_zip_path)
                self.ui_error("압축 실패", f"ZIP 압축 실패: {e}")
                return
        else:
            payload_paths = paths[:]
            payload_size = sum(os.path.getsize(p) for p in payload_paths)
            payload_filename = filenames[0]

        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(15)
        try:
            sock.connect((self.tunnel_server_host, int(target_port)))

            send_msg(sock, {
                "type": "REQUEST_SEND",
                "name": self.my_name,
                "ip": self.tunnel_subdomain,
                "tunnel_name": self.tunnel_subdomain,
                "filename": payload_filename,
                "display_name": display_name,
                "size": payload_size,
                "is_zip": use_zip,
                "file_count": file_count
            })

            resp = recv_msg(sock)
            if resp and resp.get("type") == "ACCEPT":
                self._start_transfer(sock, payload_paths, display_name, payload_filename, payload_size, use_zip, file_count, temp_zip_path)
            elif resp and resp.get("type") == "REJECT":
                try:
                    sock.close()
                except:
                    pass
                self._cleanup_temp_zip(temp_zip_path)
                self.ui_info("거절됨", "상대방이 전송을 거절했습니다.")
            else:
                try:
                    sock.close()
                except:
                    pass
                self._cleanup_temp_zip(temp_zip_path)
                self.ui_warn("응답 없음", "상대방 응답 없음")
        except Exception as e:
            try:
                sock.close()
            except:
                pass
            self._cleanup_temp_zip(temp_zip_path)
            self.ui_error("연결 오류", f"연결 실패: {e}")

    def _start_transfer(self, sock, paths, display_name, payload_filename, payload_size,
                        use_zip=False, file_count=1, temp_zip_path=None):
        self.active_mode = "send"
        cancel_event = threading.Event()

        def cancel_listener():
            try:
                while not cancel_event.is_set() and not self.root.cancel_flag:
                    msg = recv_msg(sock)
                    if not msg:
                        break
                    if msg.get("type") == "CANCEL":
                        self.root.cancel_flag = True
                        break
            except Exception:
                pass

        threading.Thread(target=cancel_listener, daemon=True).start()

        try:
            send_msg(sock, {
                "type": "START_TRANSFER",
                "filename": payload_filename,
                "size": payload_size,
                "is_zip": use_zip,
                "file_count": file_count
            })

            hide_indiv = (use_zip or file_count <= 1)
            self.ui(self.show_progress, display_name, payload_size, False, hide_indiv)

            if use_zip:
                zip_path = paths[0]
                zsize = os.path.getsize(zip_path)
                sent = 0
                self.ui(self.indiv_label.config, text=f"ZIP {os.path.basename(zip_path)} 전송 중...")
                with open(zip_path, "rb") as f:
                    while sent < zsize and not self.root.cancel_flag:
                        chunk = f.read(BUFFER_SIZE)
                        if not chunk:
                            break
                        sock.sendall(chunk)
                        sent += len(chunk)
                        self.ui(self.update_progress, len(chunk), sent, zsize, True)

            else:
                if file_count <= 1:
                    p = paths[0]
                    fsize = os.path.getsize(p)
                    sent = 0
                    self.ui(self.indiv_label.config, text=f"파일 {os.path.basename(p)} 전송 중...")
                    with open(p, "rb") as f:
                        while sent < fsize and not self.root.cancel_flag:
                            chunk = f.read(BUFFER_SIZE)
                            if not chunk:
                                break
                            sock.sendall(chunk)
                            sent += len(chunk)
                            self.ui(self.update_progress, len(chunk), sent, fsize, True)

                else:
                    files_meta = [{"name": os.path.basename(p), "size": os.path.getsize(p)} for p in paths]
                    send_msg(sock, {"type": "FILES_META", "files": files_meta})

                    for idx, p in enumerate(paths, 1):
                        if self.root.cancel_flag:
                            break
                        fname = os.path.basename(p)
                        fsize = os.path.getsize(p)

                        self.ui(self.indiv_label.config, text=f"파일 {fname} ({idx}/{file_count}) 전송 중...")
                        self.ui(self.indiv_var.set, 0)
                        self.ui(self.indiv_pct_label.config, text="0%")

                        sent = 0
                        with open(p, "rb") as f:
                            while sent < fsize and not self.root.cancel_flag:
                                chunk = f.read(BUFFER_SIZE)
                                if not chunk:
                                    break
                                sock.sendall(chunk)
                                sent += len(chunk)
                                self.ui(self.update_progress, len(chunk), sent, fsize, False)

        finally:
            cancel_event.set()
            try:
                sock.close()
            except Exception:
                pass
            self._cleanup_temp_zip(temp_zip_path)
            self.ui(self.hide_progress)
            self.active_mode = None

        if self.root.cancel_flag:
            self.ui_info("중단", "전송이 중단되었습니다.")
        else:
            self.ui_info("완료", f"{display_name} 전송 완료!")

    # ======= 종료 =======
    def on_close(self):
        self.running = False
        try:
            if self.tunnel_client:
                self.tunnel_client.stop()
        except Exception:
            pass
        self.root.destroy()


if __name__ == "__main__":
    root = tk.Tk()
    app = UnifiedApp(root)
    root.mainloop()
    app.running = False
