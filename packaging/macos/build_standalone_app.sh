#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SPEC_PATH="$SCRIPT_DIR/PeerSendStandalone.spec"
OUTPUT_DIR="${1:-$ROOT_DIR/dist/macos}"
BUILD_DIR="$OUTPUT_DIR/build"
DIST_DIR="$OUTPUT_DIR/dist"
PYI_CACHE_DIR="$OUTPUT_DIR/.pyinstaller-cache"
APP_PATH="$DIST_DIR/PeerSend.app"
ICON_PNG="$ROOT_DIR/icon.png"
ICNS_PATH="$OUTPUT_DIR/PeerSend.icns"

generate_icns() {
  sips -s format icns "$ICON_PNG" --out "$ICNS_PATH" >/dev/null
}

sanitize_bundle() {
  local target="$1"
  xattr -cr "$target" || true
  for attr in com.apple.FinderInfo 'com.apple.fileprovider.fpfs#P' com.apple.provenance; do
    find "$target" -exec xattr -d "$attr" {} \; 2>/dev/null || true
    find -P "$target" -type l -exec xattr -s -d "$attr" {} \; 2>/dev/null || true
  done
}

adhoc_sign() {
  local target="$1"
  codesign --remove-signature "$target" || true
  codesign --force --deep --sign - "$target"
}

if ! command -v pyinstaller >/dev/null 2>&1; then
  echo "error: pyinstaller is not installed. Run: python3 -m pip install pyinstaller" >&2
  exit 1
fi

cd "$ROOT_DIR"

if [[ ! -f "$ROOT_DIR/web/dist/index.html" ]]; then
  echo "web/dist is missing. Running npm run build..."
  (cd "$ROOT_DIR/web" && npm run build)
fi

rm -rf "$BUILD_DIR" "$DIST_DIR"
mkdir -p "$BUILD_DIR" "$DIST_DIR" "$PYI_CACHE_DIR"

if [[ -f "$ICON_PNG" ]] && command -v sips >/dev/null 2>&1; then
  generate_icns
else
  rm -f "$ICNS_PATH"
fi

PYINSTALLER_CONFIG_DIR="$PYI_CACHE_DIR" PEERSEND_MACOS_ICON="$ICNS_PATH" pyinstaller --noconfirm --clean \
  --distpath "$DIST_DIR" \
  --workpath "$BUILD_DIR" \
  "$SPEC_PATH"

if [[ -d "$APP_PATH" ]]; then
  sanitize_bundle "$APP_PATH"
  if ! adhoc_sign "$APP_PATH"; then
    echo "warning: codesign failed. The standalone app was still created." >&2
    echo "warning: you may need to clear extended attributes and sign it manually on this Mac." >&2
  fi
fi

echo "Created standalone app:"
echo "  $APP_PATH"
echo
echo "Open the app once so macOS registers the peersend:// URL scheme."
