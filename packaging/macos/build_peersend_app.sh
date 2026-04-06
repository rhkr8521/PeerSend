#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ICON_PNG="$ROOT_DIR/icon.png"
ENGINE_VERSION="$(python3 -c "from pathlib import Path; import re, sys; text = Path(sys.argv[1]).read_text(); match = re.search(r'ENGINE_VERSION\\s*=\\s*\\\"([^\\\"]+)\\\"', text); print(match.group(1) if match else '1.0.0')" "$ROOT_DIR/engine/version.py")"

print_usage() {
  cat <<'EOF'
Usage:
  ./packaging/macos/build_peersend_app.sh <output-dir>
  ./packaging/macos/build_peersend_app.sh <launcher-binary> <output-dir>

Modes:
  1. Python bundle mode
     - pass only <output-dir>
     - bundles the current project files into PeerSend.app
     - launches with the current python3 interpreter

  2. Binary wrapper mode
     - pass <launcher-binary> and <output-dir>
     - wraps an existing launcher binary inside PeerSend.app
EOF
}

resolve_path() {
  local value="$1"
  if [[ "$value" = /* ]]; then
    printf '%s\n' "$value"
  else
    printf '%s\n' "$(pwd)/$value"
  fi
}

generate_macos_icon() {
  local resource_dir="$1"
  local icon_path="$resource_dir/PeerSend.icns"

  if [[ ! -f "$ICON_PNG" ]] || ! command -v sips >/dev/null 2>&1; then
    return
  fi

  sips -s format icns "$ICON_PNG" --out "$icon_path" >/dev/null
}

create_common_layout() {
  local output_dir="$1"
  APP_DIR="$output_dir/PeerSend.app"
  CONTENTS_DIR="$APP_DIR/Contents"
  MACOS_DIR="$CONTENTS_DIR/MacOS"
  RESOURCES_DIR="$CONTENTS_DIR/Resources"

  rm -rf "$APP_DIR"
  mkdir -p "$MACOS_DIR" "$RESOURCES_DIR"
  cp "$SCRIPT_DIR/Info.plist.template" "$CONTENTS_DIR/Info.plist"
  python3 - <<EOF
from pathlib import Path
plist_path = Path(r"$CONTENTS_DIR/Info.plist")
plist_path.write_text(plist_path.read_text().replace("__PEERSEND_VERSION__", "$ENGINE_VERSION"))
EOF
  generate_macos_icon "$RESOURCES_DIR"
}

create_python_bundle() {
  local output_dir="$1"
  local payload_dir launcher_path python_executable

  create_common_layout "$output_dir"
  payload_dir="$RESOURCES_DIR/app"
  launcher_path="$MACOS_DIR/PeerSend"
  python_executable="$(python3 -c 'import sys; print(sys.executable)')"

  if [[ -z "$python_executable" || ! -x "$python_executable" ]]; then
    echo "error: unable to resolve the current python3 executable" >&2
    exit 1
  fi

  python3 -c 'import fastapi, uvicorn, aiohttp, psutil, websockets, multipart' >/dev/null

  mkdir -p "$payload_dir" "$payload_dir/web"

  cp "$ROOT_DIR/run_desktop_web.py" "$payload_dir/run_desktop_web.py"
  cp "$ROOT_DIR/p2p.py" "$payload_dir/p2p.py"
  cp "$ROOT_DIR/requirements.txt" "$payload_dir/requirements.txt"
  cp -R "$ROOT_DIR/engine" "$payload_dir/engine"
  cp -R "$ROOT_DIR/web/dist" "$payload_dir/web/dist"
  rm -rf "$payload_dir/engine/__pycache__"

  cat >"$launcher_path" <<EOF
#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="\$(cd "\$(dirname "\$0")" && pwd)"
APP_DIR="\$(cd "\$SCRIPT_DIR/.." && pwd)"
PAYLOAD_DIR="\$APP_DIR/Resources/app"
PYTHON_EXECUTABLE="$python_executable"

cd "\$PAYLOAD_DIR"
export PEERSEND_OPEN_BROWSER_ON_PROTOCOL=0
export PEERSEND_OPEN_BROWSER=1
exec "\$PYTHON_EXECUTABLE" "\$PAYLOAD_DIR/run_desktop_web.py" "\$@"
EOF
  chmod +x "$launcher_path"
}

create_binary_wrapper() {
  local binary_path="$1"
  local output_dir="$2"

  if [[ ! -f "$binary_path" ]]; then
    echo "error: launcher binary not found: $binary_path" >&2
    exit 1
  fi

  create_common_layout "$output_dir"
  cp "$binary_path" "$MACOS_DIR/PeerSend"
  chmod +x "$MACOS_DIR/PeerSend"
}

main() {
  local output_dir launcher_binary

  if [[ $# -eq 0 || $# -gt 2 ]]; then
    print_usage
    exit 1
  fi

  if [[ $# -eq 1 ]]; then
    output_dir="$(resolve_path "$1")"
    mkdir -p "$output_dir"
    create_python_bundle "$output_dir"
    echo "Created Python-backed app bundle: $output_dir/PeerSend.app"
    echo "Open it once so macOS registers the peersend:// URL scheme."
    echo "This bundle uses the current python3 interpreter and its installed dependencies."
    exit 0
  fi

  launcher_binary="$(resolve_path "$1")"
  output_dir="$(resolve_path "$2")"
  mkdir -p "$output_dir"
  create_binary_wrapper "$launcher_binary" "$output_dir"
  echo "Created binary-wrapped app bundle: $output_dir/PeerSend.app"
  echo "Open it once so macOS registers the peersend:// URL scheme."
}

main "$@"
