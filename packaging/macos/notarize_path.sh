#!/bin/zsh
set -euo pipefail

TARGET_PATH="${1:-}"
KEYCHAIN_PROFILE="${2:-${PEERSEND_NOTARY_PROFILE:-}}"

if [[ -z "$TARGET_PATH" ]]; then
  echo "usage: $(basename "$0") <app-or-dmg-path> [keychain-profile]" >&2
  exit 1
fi

if [[ ! -e "$TARGET_PATH" ]]; then
  echo "error: file not found: $TARGET_PATH" >&2
  exit 1
fi

if [[ -z "$KEYCHAIN_PROFILE" ]]; then
  echo "error: no notary keychain profile provided." >&2
  echo "Set PEERSEND_NOTARY_PROFILE or pass it as the second argument." >&2
  echo >&2
  echo "Example:" >&2
  echo "  xcrun notarytool store-credentials peersend-notary --apple-id <apple-id> --team-id <team-id> --password <app-specific-password>" >&2
  exit 1
fi

xcrun notarytool submit "$TARGET_PATH" --keychain-profile "$KEYCHAIN_PROFILE" --wait
xcrun stapler staple "$TARGET_PATH"

echo "Notarized and stapled:"
echo "  $TARGET_PATH"
