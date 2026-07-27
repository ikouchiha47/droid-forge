#!/usr/bin/env bash
# Take a screenshot via the bridge and print the file path.
# Run inside proot-distro.
#
# Usage:
#   bash scripts/screenshot.sh
#   # returns: /root/bridge/screenshot.png

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$SCRIPT_DIR/bridge-client.sh" screenshot
