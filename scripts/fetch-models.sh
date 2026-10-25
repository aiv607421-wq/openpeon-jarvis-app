#!/usr/bin/env bash
# Fetches the Vosk models and the JARVIS voice pack into assets/ (not stored in git).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

mkdir -p assets
fetch_model() {
  local dir=$1 url=$2
  if [ ! -d "assets/$dir" ]; then
    echo "==> fetching $dir..."
    # The official server throttles single connections to a crawl; give it a
    # short window, then fall back to parallel range-chunked download.
    if ! curl -sL --fail --max-time 60 -o "/tmp/$dir.zip" "$url"; then
      echo "    server slow/failed; using parallel chunk download..."
      bash "$SCRIPT_DIR/fetch-parallel.sh" "$url" "/tmp/$dir.zip" 10
    fi
    unzip -q -o "/tmp/$dir.zip" -d assets
    rm -f "/tmp/$dir.zip"
  fi
}

fetch_model vosk-model-small-en-us-0.15 https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
fetch_model vosk-model-spk-0.4 https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip

if [ ! -d assets/sounds ] || [ -z "$(ls -A assets/sounds 2>/dev/null)" ]; then
  echo "==> fetching JARVIS voice pack..."
  rm -rf /tmp/openpeon-jarvis
  git clone --depth 1 https://github.com/FlynnCruse/openpeon-jarvis /tmp/openpeon-jarvis
  mkdir -p assets/sounds
  cp /tmp/openpeon-jarvis/sounds/*.mp3 assets/sounds/
  rm -rf /tmp/openpeon-jarvis
fi

echo "==> assets ready: $(du -sh assets | awk '{print $1}')"
