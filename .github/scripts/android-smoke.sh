#!/usr/bin/env bash
# 在模拟器里装上 APK、启动、把崩溃证据全部落盘，供日志分析。
set -uo pipefail

api="${1:?usage: android-smoke.sh <api-level>}"
dir="smoke/api-$api"
mkdir -p "$dir"

adb wait-for-device

adb install -r CintyezOS.apk 2>&1 | tee "$dir/install.txt"
adb shell am start -W -n os.cintyez.launcher/.MainActivity 2>&1 | tee "$dir/am-start.txt"
sleep 12

adb shell pidof os.cintyez.launcher 2>/dev/null | tr -d '\r' > "$dir/pid.txt"
adb logcat -b crash -d > "$dir/crash.txt" 2>&1
adb logcat -d > "$dir/logcat.txt" 2>&1
adb shell dumpsys activity exit-info os.cintyez.launcher > "$dir/exit-info.txt" 2>&1
adb shell dumpsys window displays > "$dir/displays.txt" 2>&1
adb exec-out screencap -p > "$dir/screen.png" 2>&1

echo "=== pid ==="
cat "$dir/pid.txt"
echo "=== crash buffer ==="
head -n 120 "$dir/crash.txt"
echo "=== app logcat ==="
grep -E "FATAL EXCEPTION|AndroidRuntime|CintyezOS|cintyez" -A 60 "$dir/logcat.txt" | head -n 300 || true

if [ ! -s "$dir/pid.txt" ]; then
  echo "::error::os.cintyez.launcher is not running after launch (API $api)"
  exit 1
fi
