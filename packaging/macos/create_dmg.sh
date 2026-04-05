#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
APP_PATH="${1:-$ROOT_DIR/dist/macos/dist/PeerSend.app}"
OUTPUT_DMG="${2:-$ROOT_DIR/dist/macos/PeerSend.dmg}"
VOLUME_NAME="${PEERSEND_DMG_VOLUME_NAME:-PeerSend}"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/peersend-dmg.XXXXXX")"
STAGING_DIR="$TMP_ROOT/$VOLUME_NAME"
STAGED_APP="$STAGING_DIR/PeerSend.app"

cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

sanitize_bundle() {
  local target="$1"
  xattr -cr "$target" || true
  for attr in com.apple.FinderInfo 'com.apple.fileprovider.fpfs#P' com.apple.provenance; do
    find "$target" -exec xattr -d "$attr" {} \; 2>/dev/null || true
    find -P "$target" -type l -exec xattr -s -d "$attr" {} \; 2>/dev/null || true
  done
}

if [[ ! -d "$APP_PATH" ]]; then
  echo "error: app not found: $APP_PATH" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT_DMG")" "$STAGING_DIR"
cp -R "$APP_PATH" "$STAGING_DIR/"
sanitize_bundle "$STAGED_APP"
codesign --remove-signature "$STAGED_APP" || true
codesign --force --deep --sign - "$STAGED_APP" >/dev/null 2>&1 || true
ln -s /Applications "$STAGING_DIR/Applications"

rm -f "$OUTPUT_DMG"
hdiutil create \
  -volname "$VOLUME_NAME" \
  -srcfolder "$STAGING_DIR" \
  -ov \
  -format UDZO \
  "$OUTPUT_DMG"

echo "Created DMG:"
echo "  $OUTPUT_DMG"
