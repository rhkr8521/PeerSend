# -*- mode: python ; coding: utf-8 -*-
import importlib.util
import os
from pathlib import Path

from PyInstaller.utils.hooks import collect_data_files, collect_submodules, copy_metadata


PROJECT_ROOT = Path.cwd()
ENTRY_SCRIPT = PROJECT_ROOT / "run_desktop_web.py"
WEB_DIST_DIR = PROJECT_ROOT / "web" / "dist"
ICON_PATH = os.getenv("PEERSEND_MACOS_ICON") or None
VERSION_PATH = PROJECT_ROOT / "engine" / "version.py"


def load_engine_version() -> str:
    spec = importlib.util.spec_from_file_location("peersend_engine_version", VERSION_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return getattr(module, "ENGINE_VERSION", "1.0.0")


ENGINE_VERSION = load_engine_version()

hiddenimports = sorted(
    set(
        collect_submodules("uvicorn")
        + collect_submodules("fastapi")
        + collect_submodules("pydantic")
        + collect_submodules("pydantic_core")
        + collect_submodules("aiohttp")
        + collect_submodules("websockets")
        + collect_submodules("engine")
        + [
            "multipart",
            "multipart.multipart",
            "p2p",
            "tkinter",
            "tkinter.ttk",
            "tkinter.filedialog",
            "tkinter.messagebox",
        ]
    )
)

datas = [
    (str(WEB_DIST_DIR), "web/dist"),
]
datas += collect_data_files("certifi")
datas += copy_metadata("fastapi")
datas += copy_metadata("pydantic")
datas += copy_metadata("pydantic_core")
datas += copy_metadata("uvicorn")
datas += copy_metadata("aiohttp")
datas += copy_metadata("websockets")
datas += copy_metadata("python-multipart")
datas += copy_metadata("psutil")

app_info_plist = {
    "CFBundleDisplayName": "PeerSend",
    "CFBundleIdentifier": "com.rhkr8521.peersend.desktop",
    "CFBundleName": "PeerSend",
    "CFBundleShortVersionString": ENGINE_VERSION,
    "CFBundleVersion": ENGINE_VERSION,
    "NSLocalNetworkUsageDescription": "PeerSend uses your local network to discover nearby devices and transfer files directly.",
    "LSUIElement": True,
    "CFBundleURLTypes": [
        {
            "CFBundleTypeRole": "Editor",
            "CFBundleURLName": "com.rhkr8521.peersend.desktop",
            "CFBundleURLSchemes": ["peersend"],
        }
    ],
}

a = Analysis(
    [str(ENTRY_SCRIPT)],
    pathex=[str(PROJECT_ROOT)],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
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
    upx=False,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name="PeerSend",
)

app = BUNDLE(
    coll,
    name="PeerSend.app",
    icon=ICON_PATH,
    bundle_identifier="com.rhkr8521.peersend.desktop",
    info_plist=app_info_plist,
)
