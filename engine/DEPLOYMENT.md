# PeerSend Desktop Web Deployment

## Public site + local engine model

The intended production model is:

- public web UI: `https://send.peersend.kro.kr`
- local engine on each PC: `http://127.0.0.1:8765`

The browser stays on the public website. It does **not** redirect to localhost. Instead, it:

1. checks whether the local engine is running
2. tries to launch it through `peersend://launch` if needed
3. talks to the local FastAPI engine directly over `127.0.0.1`

The local engine should only expose its API on `127.0.0.1:8765`. It should not open its own browser UI unless you explicitly set `PEERSEND_OPEN_BROWSER=1`.

## Public web environment

The public web launcher reads these Vite environment variables:

- `VITE_PEERSEND_LOCAL_ORIGIN`
- `VITE_PEERSEND_PROTOCOL`
- `VITE_PEERSEND_WINDOWS_DOWNLOAD_URL`
- `VITE_PEERSEND_MACOS_DOWNLOAD_URL`
- `VITE_PEERSEND_LINUX_DOWNLOAD_URL`
- `VITE_PEERSEND_ANDROID_DOWNLOAD_URL`
- `VITE_PEERSEND_IOS_DOWNLOAD_URL`

Start from [`web/.env.example`](/Users/rhkr8521/Desktop/p2p-file-transfer/web/.env.example) and create a real `.env.production`.

Recommended production download URLs:

- `https://send.peersend.kro.kr/downloads/PeerSend-Setup.exe`
- `https://send.peersend.kro.kr/downloads/PeerSend.dmg`
- `https://send.peersend.kro.kr/downloads/PeerSend.AppImage`

## Development mode

When running the Vite dev server on `127.0.0.1:5173`, the local Python engine must also be running on `127.0.0.1:8765`.

Use two terminals:

```bash
cd /Users/rhkr8521/Desktop/p2p-file-transfer
python3 run_desktop_web.py
```

```bash
cd /Users/rhkr8521/Desktop/p2p-file-transfer/web
npm run dev
```

The Vite app now talks directly to `127.0.0.1:8765`, so it behaves the same way as the public site.

If you want the engine launcher to open a browser during development, set:

```bash
PEERSEND_OPEN_BROWSER=1 PEERSEND_UI_URL=http://localhost:5173 python3 run_desktop_web.py
```

## Public download page

The frontend includes a public download view for:

- desktop engine installers
- Android app
- iPhone / iPad app

If a phone or tablet visits the public site, it is routed to the mobile app download page automatically.

## Custom protocol registration

The public launcher can attempt to start the installed engine through `peersend://launch`.

Packaging assets:

- Windows: [`packaging/windows/PeerSendProtocol.reg`](/Users/rhkr8521/Desktop/p2p-file-transfer/packaging/windows/PeerSendProtocol.reg)
- Windows standalone build: [`packaging/windows/build_standalone_windows.ps1`](/Users/rhkr8521/Desktop/p2p-file-transfer/packaging/windows/build_standalone_windows.ps1)
- Windows installer: [`packaging/windows/PeerSendSetup.iss`](/Users/rhkr8521/Desktop/p2p-file-transfer/packaging/windows/PeerSendSetup.iss)
- macOS bundle template: [`packaging/macos/Info.plist.template`](/Users/rhkr8521/Desktop/p2p-file-transfer/packaging/macos/Info.plist.template)
- macOS standalone build: [`packaging/macos/build_standalone_app.sh`](/Users/rhkr8521/Desktop/p2p-file-transfer/packaging/macos/build_standalone_app.sh)
- macOS release pipeline: [`packaging/macos/release_macos.sh`](/Users/rhkr8521/Desktop/p2p-file-transfer/packaging/macos/release_macos.sh)
- Linux standalone build: [`packaging/linux/build_standalone_linux.sh`](/Users/rhkr8521/Desktop/p2p-file-transfer/packaging/linux/build_standalone_linux.sh)

The installer or packaged app must register the custom URL scheme with the operating system for automatic launch to work.
