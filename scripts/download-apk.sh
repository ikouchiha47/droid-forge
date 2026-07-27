#!/usr/bin/env bash
# Download the latest APK artifact from GitHub Actions and install it via the bridge.
# Run inside proot-distro.
#
# Usage:
#   bash scripts/download-apk.sh [branch]
#   bash scripts/download-apk.sh app/myapp

set -euo pipefail

BRANCH="${1:-$(git rev-parse --abbrev-ref HEAD)}"
ARTIFACT_NAME="${BRANCH//\//-}-release"
OUT_DIR="$HOME/apk"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mkdir -p "$OUT_DIR"

echo "Downloading artifact: $ARTIFACT_NAME from branch: $BRANCH"
gh run download \
    --repo "$(gh repo view --json nameWithOwner -q .nameWithOwner)" \
    --name "$ARTIFACT_NAME" \
    --dir "$OUT_DIR" \
    2>&1 | tail -5

APK=$(find "$OUT_DIR" -name "*.apk" | head -1)
if [[ -z "$APK" ]]; then
    echo "No APK found in $OUT_DIR" >&2
    exit 1
fi

echo "APK: $APK"
echo "Sending to Android via bridge..."
"$SCRIPT_DIR/bridge-client.sh" install-apk "$APK"
echo "Android package installer opened."
