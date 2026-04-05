#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SPEC_PATH="$SCRIPT_DIR/PeerSendStandalone.spec"
OUTPUT_DIR="${1:-$ROOT_DIR/dist/linux}"
BUILD_DIR="$OUTPUT_DIR/build"
DIST_DIR="$OUTPUT_DIR/dist"
APP_DIR="$DIST_DIR/PeerSend"
APPIMAGE_ROOT="$OUTPUT_DIR/AppDir"
APPIMAGE_PATH="$OUTPUT_DIR/PeerSend.AppImage"

if ! command -v pyinstaller >/dev/null 2>&1; then
  echo "error: pyinstaller is not installed. Run: python3 -m pip install pyinstaller" >&2
  exit 1
fi

cd "$ROOT_DIR"

if [[ ! -f "$ROOT_DIR/web/dist/index.html" ]]; then
  echo "web/dist is missing. Running npm run build..."
  (cd "$ROOT_DIR/web" && npm run build)
fi

rm -rf "$BUILD_DIR" "$DIST_DIR" "$APPIMAGE_ROOT"
mkdir -p "$BUILD_DIR" "$DIST_DIR"

pyinstaller --noconfirm --clean \
  --distpath "$DIST_DIR" \
  --workpath "$BUILD_DIR" \
  "$SPEC_PATH"

mkdir -p "$APPIMAGE_ROOT/usr/lib/peersend" "$APPIMAGE_ROOT/usr/share/applications" "$APPIMAGE_ROOT/usr/share/icons/hicolor/512x512/apps"
cp -R "$APP_DIR/." "$APPIMAGE_ROOT/usr/lib/peersend/"
cp "$SCRIPT_DIR/AppRun" "$APPIMAGE_ROOT/AppRun"
cp "$SCRIPT_DIR/PeerSend.desktop" "$APPIMAGE_ROOT/usr/share/applications/PeerSend.desktop"
cp "$ROOT_DIR/icon.png" "$APPIMAGE_ROOT/usr/share/icons/hicolor/512x512/apps/peersend.png"
cp "$ROOT_DIR/icon.png" "$APPIMAGE_ROOT/peersend.png"
chmod +x "$APPIMAGE_ROOT/AppRun"

echo "Created standalone Linux app directory:"
echo "  $APP_DIR"

if command -v appimagetool >/dev/null 2>&1; then
  appimagetool "$APPIMAGE_ROOT" "$APPIMAGE_PATH"
  echo "Created AppImage:"
  echo "  $APPIMAGE_PATH"
else
  echo "warning: appimagetool was not found. AppDir is ready at:" >&2
  echo "  $APPIMAGE_ROOT" >&2
fi
