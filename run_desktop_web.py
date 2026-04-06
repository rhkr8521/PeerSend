#!/usr/bin/env python3
from __future__ import annotations

import os
import socket
import sys
import threading
import traceback
import urllib.error
import urllib.request
import webbrowser
from datetime import datetime
from pathlib import Path
import logging

import uvicorn

from engine.app import app


def _is_running(host: str, port: int) -> bool:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(0.35)
    try:
        return sock.connect_ex((host, port)) == 0
    finally:
        sock.close()


def _startup_log_path() -> Path:
    if sys.platform.startswith("win"):
        base_dir = Path(os.getenv("LOCALAPPDATA") or Path.home() / "AppData" / "Local")
        log_dir = base_dir / "PeerSend" / "logs"
    else:
        log_dir = Path.home() / ".peersend" / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    return log_dir / "startup-error.log"


def _record_startup_error() -> Path:
    log_path = _startup_log_path()
    timestamp = datetime.now().isoformat(timespec="seconds")
    payload = f"[{timestamp}] PeerSend startup failed\n{traceback.format_exc()}\n"
    log_path.write_text(payload, encoding="utf-8")
    return log_path


def _notify_startup_error(log_path: Path) -> None:
    message = f"PeerSend failed to start.\n\nSee the log for details:\n{log_path}"
    if getattr(sys, "frozen", False) and sys.platform.startswith("win"):
        try:
            import ctypes

            ctypes.windll.user32.MessageBoxW(None, message, "PeerSend", 0x10)
            return
        except Exception:
            pass
    print(message, file=sys.stderr)


def _configure_embedded_logging() -> None:
    if getattr(sys, "frozen", False):
        logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


def _public_ui_available(browser_url: str, timeout: float = 2.5) -> bool:
    try:
        request = urllib.request.Request(browser_url, method="HEAD")
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return 200 <= int(getattr(response, "status", 200)) < 400
    except urllib.error.HTTPError as error:
        if error.code in (401, 403, 405):
            return True
    except Exception:
        pass
    try:
        request = urllib.request.Request(browser_url, method="GET")
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return 200 <= int(getattr(response, "status", 200)) < 400
    except Exception:
        return False


def _launch_lan_only_fallback() -> None:
    import tkinter as tk

    from p2p import UnifiedApp

    root = tk.Tk()
    fallback_app = UnifiedApp(root, lan_only=True)
    root.mainloop()
    fallback_app.running = False


def main() -> None:
    host = os.getenv("PEERSEND_WEB_HOST", "127.0.0.1")
    port = int(os.getenv("PEERSEND_WEB_PORT", "8765"))
    browser_url = os.getenv("PEERSEND_UI_URL", "https://send.peersend.kro.kr")
    launch_args = [arg for arg in sys.argv[1:] if not str(arg).startswith("-psn_")]
    invoked_from_protocol = any(arg.startswith("peersend://") for arg in launch_args)
    default_open_browser = "1"
    should_open_browser = os.getenv("PEERSEND_OPEN_BROWSER", default_open_browser) == "1"
    if invoked_from_protocol:
        should_open_browser = os.getenv("PEERSEND_OPEN_BROWSER_ON_PROTOCOL", "0") == "1"

    if not invoked_from_protocol and should_open_browser and not _public_ui_available(browser_url):
        if _is_running(host, port):
            return
        _launch_lan_only_fallback()
        return

    if _is_running(host, port):
        if should_open_browser:
            webbrowser.open(browser_url)
        return

    if should_open_browser:
        threading.Timer(1.0, lambda: webbrowser.open(browser_url)).start()
    _configure_embedded_logging()
    uvicorn.run(
        app,
        host=host,
        port=port,
        log_config=None,
        access_log=False,
    )


if __name__ == "__main__":
    try:
        main()
    except Exception:
        log_path = _record_startup_error()
        _notify_startup_error(log_path)
        raise SystemExit(1)
