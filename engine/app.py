from __future__ import annotations

import asyncio
import os
import shutil
import sys
import tempfile
import threading
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Literal, Optional

from fastapi import FastAPI, File, Form, HTTPException, Query, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

from .engine import PeerSendEngine
from .version import ENGINE_VERSION


def _resource_root() -> Path:
    if getattr(sys, "frozen", False):
        meipass = getattr(sys, "_MEIPASS", None)
        if meipass:
            return Path(meipass)
    return Path(__file__).resolve().parent.parent


ROOT_DIR = _resource_root()
WEB_DIST_DIR = ROOT_DIR / "web" / "dist"
ALLOWED_CORS_ORIGINS = [
    "https://send.peersend.kro.kr",
    "http://localhost:5173",
]


class ModePayload(BaseModel):
    mode: Literal["lan", "tunnel"]


class TunnelSettingsPayload(BaseModel):
    use_public_tunnel: bool = True
    host: str
    ssl: bool
    token: str


class SavePathPayload(BaseModel):
    path: str


class RespondPayload(BaseModel):
    request_id: str
    accept: bool


class SessionPayload(BaseModel):
    session_id: str


class SessionManager:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._sessions: dict[str, float] = {}
        self._had_active_session = False
        self._last_empty_at = time.time()
        self._stale_after = float(os.getenv("PEERSEND_SESSION_STALE_SECONDS", "25"))
        self._shutdown_after = float(os.getenv("PEERSEND_IDLE_EXIT_SECONDS", "15"))

    def open(self, session_id: str) -> None:
        now = time.time()
        with self._lock:
            self._sessions[session_id] = now
            self._had_active_session = True

    def heartbeat(self, session_id: str) -> None:
        with self._lock:
            if session_id in self._sessions:
                self._sessions[session_id] = time.time()

    def close(self, session_id: str) -> None:
        with self._lock:
            self._sessions.pop(session_id, None)
            if not self._sessions:
                self._last_empty_at = time.time()

    def sweep(self) -> None:
        now = time.time()
        with self._lock:
            had_sessions = bool(self._sessions)
            expired = [session_id for session_id, last_seen in self._sessions.items() if now - last_seen > self._stale_after]
            for session_id in expired:
                self._sessions.pop(session_id, None)
            if had_sessions and not self._sessions:
                self._last_empty_at = now

    def should_exit(self) -> bool:
        self.sweep()
        with self._lock:
            return self._had_active_session and not self._sessions and (time.time() - self._last_empty_at) >= self._shutdown_after


@asynccontextmanager
async def lifespan(app: FastAPI):
    engine = PeerSendEngine()
    sessions = SessionManager()
    stop_idle_watch = threading.Event()
    app.state.engine = engine
    app.state.sessions = sessions
    engine.start()

    def idle_watch() -> None:
        while not stop_idle_watch.wait(3.0):
            if sessions.should_exit():
                engine.shutdown()
                os._exit(0)

    threading.Thread(target=idle_watch, daemon=True).start()
    try:
        yield
    finally:
        stop_idle_watch.set()
        engine.shutdown()


def create_app() -> FastAPI:
    app = FastAPI(title="PeerSend", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=ALLOWED_CORS_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    def engine() -> PeerSendEngine:
        return app.state.engine

    def sessions() -> SessionManager:
        return app.state.sessions

    def resolve_session_id(payload: Optional[SessionPayload], session_id: Optional[str]) -> str:
        value = session_id or (payload.session_id if payload else None)
        if not value:
            raise HTTPException(status_code=400, detail="session_id is required")
        return value

    @app.get("/api/health")
    async def health() -> dict[str, object]:
        return {
            "ok": True,
            "product": "PeerSend Engine",
            "version": ENGINE_VERSION,
            "language": engine().language,
        }

    @app.get("/api/state")
    async def get_state() -> dict:
        return engine().serialize_state()

    @app.post("/api/mode")
    async def set_mode(payload: ModePayload) -> dict:
        return engine().set_mode(payload.mode)

    @app.post("/api/refresh")
    async def refresh() -> dict:
        engine().manual_refresh()
        return {"ok": True}

    @app.post("/api/tunnel-settings")
    async def tunnel_settings(payload: TunnelSettingsPayload) -> dict:
        return engine().update_tunnel_settings(payload.host, payload.ssl, payload.token, payload.use_public_tunnel)

    @app.post("/api/save-path")
    async def save_path(payload: SavePathPayload) -> dict:
        return engine().set_save_path(payload.path)

    @app.post("/api/save-path/dialog")
    async def save_path_dialog() -> dict:
        result = engine().choose_save_path_dialog()
        if not result.get("ok") and not result.get("cancelled") and result.get("error"):
            raise HTTPException(status_code=500, detail=str(result.get("error")))
        return result

    @app.post("/api/respond")
    async def respond(payload: RespondPayload) -> dict:
        if not engine().respond_to_request(payload.request_id, payload.accept):
            raise HTTPException(status_code=404, detail="Request not found")
        return {"ok": True}

    @app.post("/api/cancel")
    async def cancel_transfer() -> dict:
        engine().cancel_transfer()
        return {"ok": True}

    @app.post("/api/session/open")
    async def session_open(
        payload: Optional[SessionPayload] = None,
        session_id: Optional[str] = Query(default=None),
    ) -> dict:
        sessions().open(resolve_session_id(payload, session_id))
        return {"ok": True}

    @app.post("/api/session/open/{session_id}")
    async def session_open_path(session_id: str) -> dict:
        sessions().open(session_id)
        return {"ok": True}

    @app.post("/api/session/heartbeat")
    async def session_heartbeat(
        payload: Optional[SessionPayload] = None,
        session_id: Optional[str] = Query(default=None),
    ) -> dict:
        sessions().heartbeat(resolve_session_id(payload, session_id))
        return {"ok": True}

    @app.post("/api/session/heartbeat/{session_id}")
    async def session_heartbeat_path(session_id: str) -> dict:
        sessions().heartbeat(session_id)
        return {"ok": True}

    @app.post("/api/session/close")
    async def session_close(
        payload: Optional[SessionPayload] = None,
        session_id: Optional[str] = Query(default=None),
    ) -> dict:
        sessions().close(resolve_session_id(payload, session_id))
        return {"ok": True}

    @app.post("/api/session/close/{session_id}")
    async def session_close_path(session_id: str) -> dict:
        sessions().close(session_id)
        return {"ok": True}

    @app.post("/api/send-upload")
    async def send_upload(
        mode: str = Form(...),
        peer_id: str = Form(...),
        use_zip: bool = Form(False),
        files: list[UploadFile] = File(...),
    ) -> dict:
        if not files:
            raise HTTPException(status_code=400, detail="No files uploaded")

        temp_dir = tempfile.mkdtemp(prefix="peersend-upload-")
        file_paths: list[str] = []
        file_names: list[str] = []
        try:
            for upload in files:
                name = Path(upload.filename or "file.dat").name or "file.dat"
                target = Path(temp_dir) / name
                counter = 1
                while target.exists():
                    target = Path(temp_dir) / f"{target.stem}_{counter}{target.suffix}"
                    counter += 1
                with target.open("wb") as output:
                    while True:
                        chunk = await upload.read(1024 * 1024)
                        if not chunk:
                            break
                        output.write(chunk)
                await upload.close()
                file_paths.append(str(target))
                file_names.append(target.name)

            engine().queue_send_files(
                mode=mode,
                peer_id=peer_id,
                file_paths=file_paths,
                filenames=file_names,
                use_zip=use_zip,
                cleanup_dir=temp_dir,
            )
        except Exception:
            shutil.rmtree(temp_dir, ignore_errors=True)
            raise
        return {"ok": True}

    @app.websocket("/ws")
    async def websocket_endpoint(websocket: WebSocket) -> None:
        await websocket.accept()
        q = engine().events.subscribe()
        try:
            await websocket.send_json({"type": "state", "state": engine().serialize_state()})
            while True:
                event = await asyncio.to_thread(q.get)
                await websocket.send_json(event)
        except WebSocketDisconnect:
            pass
        finally:
            engine().events.unsubscribe(q)

    @app.get("/")
    async def index():
        index_file = WEB_DIST_DIR / "index.html"
        if index_file.exists():
            return FileResponse(index_file)
        return JSONResponse(
            {
                "ok": True,
                "message": "PeerSend backend is running.",
                "hint": "Build the Vite frontend in /web to serve the UI from this process.",
            }
        )

    @app.get("/{path:path}")
    async def static_or_spa(path: str):
        if path.startswith("api/"):
            raise HTTPException(status_code=404, detail="Not found")
        target = WEB_DIST_DIR / path
        if target.exists() and target.is_file():
            return FileResponse(target)
        index_file = WEB_DIST_DIR / "index.html"
        if index_file.exists():
            return FileResponse(index_file)
        return JSONResponse({"ok": False, "message": "Frontend build not found."}, status_code=404)

    return app


app = create_app()
