#!/usr/bin/env bash
# Stream logcat from the local device via loopback ADB.
# Run in Termux (outside proot) or inside proot with android-tools installed.
#
# Usage:
#   bash scripts/logcat.sh                    # all app output
#   bash scripts/logcat.sh com.forge.myapp    # specific package
#   bash scripts/logcat.sh com.forge.myapp E  # errors only

set -euo pipefail

PACKAGE="${1:-}"
LEVEL="${2:-V}"

# connect to local adbd if not already connected
if ! adb devices | grep -q "127.0.0.1:5555"; then
    echo "Connecting to local adbd..."
    adb connect 127.0.0.1:5555
fi

if [[ -n "$PACKAGE" ]]; then
    PID=$(adb shell pidof "$PACKAGE" 2>/dev/null || true)
    if [[ -z "$PID" ]]; then
        echo "App not running: $PACKAGE — showing by tag filter instead"
        TAG=$(echo "$PACKAGE" | awk -F. '{print $NF}')
        adb logcat "$TAG:$LEVEL" "*:S"
    else
        adb logcat --pid="$PID" "*:$LEVEL"
    fi
else
    adb logcat "*:$LEVEL"
fi
