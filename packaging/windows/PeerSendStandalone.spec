# -*- mode: python ; coding: utf-8 -*-
import os
from pathlib import Path

from PyInstaller.utils.hooks import collect_submodules, copy_metadata


PROJECT_ROOT = Path.cwd()
ENTRY_SCRIPT = PROJECT_ROOT / "run_desktop_web.py"
WEB_DIST_DIR = PROJECT_ROOT / "web" / "dist"
ICON_PATH = PROJECT_ROOT / "icon.png"
WINDOWS_ICON = os.getenv("PEERSEND_WINDOWS_ICON") or None
datas = [
    (str(WEB_DIST_DIR), "web/dist"),
]

if ICON_PATH.exists():
    datas.append((str(ICON_PATH), "."))

metadata = []
for package in ("fastapi", "uvicorn", "aiohttp", "websockets", "python-multipart", "psutil", "certifi", "pydantic", "pydantic_core"):
    metadata += copy_metadata(package)

hiddenimports = [
    *collect_submodules("fastapi"),
    *collect_submodules("pydantic"),
    *collect_submodules("pydantic_core"),
    *collect_submodules("uvicorn"),
    *collect_submodules("aiohttp"),
    *collect_submodules("websockets"),
    *collect_submodules("engine"),
    "multipart",
    "multipart.multipart",
    "p2p",
    "tkinter",
    "tkinter.ttk",
    "tkinter.filedialog",
    "tkinter.messagebox",
]

a = Analysis(
    [str(ENTRY_SCRIPT)],
    pathex=[str(PROJECT_ROOT)],
    binaries=[],
    datas=datas + metadata,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="PeerSend",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    icon=WINDOWS_ICON,
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="PeerSend",
)
