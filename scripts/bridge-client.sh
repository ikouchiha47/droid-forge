#!/usr/bin/env bash
# Run inside proot-distro. Sends a command to the bridge daemon and prints the result.
# The Termux home is mounted at /root inside proot, so ~/bridge/ is /root/bridge/.
#
# Usage:
#   bridge-client.sh screenshot
#   bridge-client.sh install-apk ~/apk/app-release.apk
#   bridge-client.sh copy-to-sdcard ~/apk/app-release.apk
#   bridge-client.sh clipboard-get
#   bridge-client.sh clipboard-set "hello"

set -euo pipefail

BRIDGE_DIR="/root/bridge"
CMD_FILE="$BRIDGE_DIR/cmd"
DONE_FILE="$BRIDGE_DIR/done"
ERR_FILE="$BRIDGE_DIR/err"
TIMEOUT=15

if [[ $# -eq 0 ]]; then
    echo "Usage: $(basename "$0") <command> [arg]" >&2
    echo "Commands: screenshot | install-apk <file> | copy-to-sdcard <file> | open <file> | clipboard-get | clipboard-set <text>" >&2
    exit 1
fi

mkdir -p "$BRIDGE_DIR"
rm -f "$DONE_FILE" "$ERR_FILE"

# write command (verb + optional arg joined)
echo "$*" > "$CMD_FILE"

# wait for daemon to signal completion
ELAPSED=0
while [[ ! -f "$DONE_FILE" ]]; do
    sleep 0.5
    ELAPSED=$((ELAPSED + 1))
    if [[ $ELAPSED -ge $((TIMEOUT * 2)) ]]; then
        echo "bridge timeout: daemon did not respond within ${TIMEOUT}s" >&2
        echo "Is bridge-daemon.sh running in a Termux session?" >&2
        exit 1
    fi
done

if [[ -s "$ERR_FILE" ]]; then
    echo "bridge error: $(cat "$ERR_FILE")" >&2
    exit 1
fi

cat "$DONE_FILE"
