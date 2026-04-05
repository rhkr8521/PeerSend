#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
APP_PATH="${1:-$ROOT_DIR/dist/macos/dist/PeerSend.app}"
IDENTITY="${2:-${PEERSEND_CODESIGN_IDENTITY:-}}"

if [[ ! -d "$APP_PATH" ]]; then
  echo "error: app not found: $APP_PATH" >&2
  exit 1
fi

if [[ -z "$IDENTITY" ]]; then
  echo "error: no Developer ID identity provided." >&2
  echo "Set PEERSEND_CODESIGN_IDENTITY or pass it as the second argument." >&2
  echo >&2
  security find-identity -v -p codesigning || true
  exit 1
fi

xattr -cr "$APP_PATH" || true
xattr -dr com.apple.provenance "$APP_PATH" || true
codesign --remove-signature "$APP_PATH" || true

codesign --force --deep --options runtime --timestamp --sign "$IDENTITY" "$APP_PATH"
codesign --verify --deep --strict --verbose=2 "$APP_PATH"

echo "Signed app:"
echo "  $APP_PATH"
