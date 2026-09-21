#!/usr/bin/env bash
# Records the headset's single-eye view through the system UI capture service, then pulls the
# newest file. adb screencap cannot see spatial compositor output; this can.
# Usage: tools/record.sh <seconds> <output.mp4> [device-id]
set -euo pipefail
SECONDS_TO_RECORD="${1:-5}"
OUT="${2:-record.mp4}"
DEVICE="${3:-${PICO_CLI_DEVICE:-}}"
DEV_ARGS=()
[ -n "$DEVICE" ] && DEV_ARGS=(--device "$DEVICE")
DIR=/sdcard/DCIM/ScreenRecording
toggle() {
  pico-cli shell "${DEV_ARGS[@]}" "am startservice -p com.picoxr.systemui -a systemui.intent.action.SCREEN_CAPTURE --ei type 1 --es from adb" >/dev/null
}
toggle
sleep 3
sleep "$SECONDS_TO_RECORD"
toggle
sleep 3
LATEST=$(pico-cli shell "${DEV_ARGS[@]}" "ls -t $DIR | head -1" | tr -d '\r' | tail -1)
pico-cli files pull "$DIR/$LATEST" "$OUT" "${DEV_ARGS[@]/--device/-d}" >/dev/null
echo "$OUT <- $DIR/$LATEST"
