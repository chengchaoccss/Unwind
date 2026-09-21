#!/usr/bin/env bash
# Build, install, launch, record the headset view and extract one frame.
# Usage: tools/look.sh <out-prefix> [seconds-before-recording]
set -euo pipefail
cd "$(dirname "$0")/.."
PREFIX="${1:?out prefix}"
SETTLE="${2:-7}"
DEVICE="${PICO_CLI_DEVICE:-PB3310PGL7200057B}"
export JAVA_HOME="${JAVA_HOME:-/Users/bytedance/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home}"
export NO_COLOR=1
if ! ./gradlew :app:assembleDebug --console=plain -q > /tmp/armilla_build.log 2>&1; then
  grep -E "^e: |error:|FAILED" /tmp/armilla_build.log | head -20
  exit 1
fi
# Stop first: installing over a running app leaves the new process on the old stage (all black).
pico-cli app stop com.armilla.neckcare -d "$DEVICE" >/dev/null 2>&1 || true
pico-cli app install app/build/outputs/apk/debug/app-debug.apk -d "$DEVICE" -r | tail -1
pico-cli app stop com.armilla.neckcare -d "$DEVICE" >/dev/null 2>&1 || true
pico-cli app launch com.armilla.neckcare --activity .platform.LaunchActivity -d "$DEVICE" >/dev/null
sleep "$SETTLE"
tools/record.sh 2 "$PREFIX.mp4" "$DEVICE" >/dev/null
ffmpeg -v error -y -ss 2 -i "$PREFIX.mp4" -frames:v 1 -vf "scale=1296:-1" "$PREFIX.jpg"
echo "frame: $PREFIX.jpg"
