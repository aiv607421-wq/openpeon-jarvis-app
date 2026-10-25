#!/usr/bin/env bash
# Fetches the Vosk models and the JARVIS voice pack into assets/ (not stored in git).
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p assets
fetch_model() {
  local dir=$1 url=$2
  if [ ! -d "assets/$dir" ]; then
    echo "==> fetching $dir..."
    curl -sL -o "/tmp/$dir.zip" "$url"
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
