#!/usr/bin/env bash
# Run this in a Termux session (outside proot-distro) before entering proot.
# It watches ~/bridge/cmd and executes Android API calls on behalf of proot.
#
# Setup (once):
#   pkg install termux-api
#   termux-setup-storage      # grants ~/storage/shared/ → /sdcard/
#   mkdir -p ~/bridge
#
# Usage:
#   bash scripts/bridge-daemon.sh &    # start in background
#   proot-distro login ubuntu

set -euo pipefail

BRIDGE_DIR="$HOME/bridge"
CMD_FILE="$BRIDGE_DIR/cmd"
DONE_FILE="$BRIDGE_DIR/done"
ERR_FILE="$BRIDGE_DIR/err"
DOWNLOADS="$HOME/storage/shared/Downloads"

mkdir -p "$BRIDGE_DIR" "$DOWNLOADS"

echo "[bridge] daemon started. watching $CMD_FILE"

while true; do
    if [[ -f "$CMD_FILE" ]]; then
        CMD=$(cat "$CMD_FILE")
        rm -f "$CMD_FILE" "$DONE_FILE" "$ERR_FILE"

        echo "[bridge] received: $CMD"

        # parse verb and optional argument
        VERB="${CMD%% *}"
        ARG="${CMD#* }"
        [[ "$ARG" == "$VERB" ]] && ARG=""

        case "$VERB" in
            screenshot)
                OUT="$BRIDGE_DIR/screenshot.png"
                if termux-screenshot -f "$OUT" 2>"$ERR_FILE"; then
                    echo "$OUT" > "$DONE_FILE"
                else
                    echo "termux-screenshot failed" > "$ERR_FILE"
                fi
                ;;

            copy-to-sdcard)
                # ARG = source path (relative to Termux home or absolute)
                SRC="${ARG/#\~/$HOME}"
                DEST="$DOWNLOADS/$(basename "$SRC")"
                if cp "$SRC" "$DEST" 2>"$ERR_FILE"; then
                    echo "$DEST" > "$DONE_FILE"
                fi
                ;;

            install-apk)
                SRC="${ARG/#\~/$HOME}"
                DEST="$DOWNLOADS/$(basename "$SRC")"
                if cp "$SRC" "$DEST" 2>"$ERR_FILE"; then
                    termux-open "$DEST" 2>>"$ERR_FILE"
                    echo "$DEST" > "$DONE_FILE"
                fi
                ;;

            open)
                TARGET="${ARG/#\~/$HOME}"
                if termux-open "$TARGET" 2>"$ERR_FILE"; then
                    echo "opened" > "$DONE_FILE"
                fi
                ;;

            clipboard-get)
                termux-clipboard-get > "$DONE_FILE" 2>"$ERR_FILE"
                ;;

            clipboard-set)
                if echo "$ARG" | termux-clipboard-set 2>"$ERR_FILE"; then
                    echo "ok" > "$DONE_FILE"
                fi
                ;;

            *)
                echo "unknown command: $VERB" > "$ERR_FILE"
                ;;
        esac

        # signal completion even on error so client doesn't hang
        [[ -f "$DONE_FILE" ]] || touch "$DONE_FILE"
    fi
    sleep 0.5
done
