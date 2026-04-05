#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="${1:-$ROOT_DIR/dist/macos}"
APP_PATH="$OUTPUT_DIR/dist/PeerSend.app"
DMG_PATH="$OUTPUT_DIR/PeerSend.dmg"

"$SCRIPT_DIR/build_standalone_app.sh" "$OUTPUT_DIR"
"$SCRIPT_DIR/sign_standalone_app.sh" "$APP_PATH"

if [[ -n "${PEERSEND_NOTARY_PROFILE:-}" ]]; then
  "$SCRIPT_DIR/notarize_path.sh" "$APP_PATH"
else
  echo "warning: PEERSEND_NOTARY_PROFILE is not set. Skipping app notarization." >&2
fi

"$SCRIPT_DIR/create_dmg.sh" "$APP_PATH" "$DMG_PATH"

if [[ -n "${PEERSEND_NOTARY_PROFILE:-}" ]]; then
  "$SCRIPT_DIR/notarize_path.sh" "$DMG_PATH"
else
  echo "warning: PEERSEND_NOTARY_PROFILE is not set. Skipping DMG notarization." >&2
fi

echo
echo "macOS release artifacts:"
echo "  App: $APP_PATH"
echo "  DMG: $DMG_PATH"
