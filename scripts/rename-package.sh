#!/usr/bin/env bash
# Rename the skeleton package and app name for a new app.
# Usage: bash scripts/rename-package.sh com.example.myapp MyApp
#
# This script must be run from the repo root after branching from skeleton:
#   git checkout -b app/myapp skeleton
#   bash scripts/rename-package.sh com.example.myapp MyApp

set -euo pipefail

if [ $# -ne 2 ]; then
    echo "Usage: $0 <new.package.name> <AppName>"
    echo "Example: $0 com.alice.tracker TrackerApp"
    exit 1
fi

NEW_PACKAGE="$1"
APP_NAME="$2"
OLD_PACKAGE="com.forge.skeleton"
OLD_JAVA_PATH="java/com/forge/skeleton"
NEW_JAVA_PATH="java/$(echo "$NEW_PACKAGE" | tr '.' '/')"
ANDROID_DIR="android/app/src/main"

echo "Renaming package: $OLD_PACKAGE → $NEW_PACKAGE"
echo "App name: $APP_NAME"

# ── 1. Move source directory ──────────────────────────────────────────────────
SRC_OLD="$ANDROID_DIR/$OLD_JAVA_PATH"
SRC_NEW="$ANDROID_DIR/$NEW_JAVA_PATH"

if [ ! -d "$SRC_OLD" ]; then
    echo "Error: source directory not found: $SRC_OLD"
    exit 1
fi

mkdir -p "$(dirname "$SRC_NEW")"
mv "$SRC_OLD" "$SRC_NEW"

# Remove now-empty parent directories
rmdir --ignore-fail-on-non-empty \
    "$ANDROID_DIR/java/com/forge/skeleton" \
    "$ANDROID_DIR/java/com/forge" \
    "$ANDROID_DIR/java/com" \
    2>/dev/null || true

# ── 2. Replace package declarations in Kotlin files ───────────────────────────
find "$ANDROID_DIR/$NEW_JAVA_PATH" -name "*.kt" -print0 | \
    xargs -0 sed -i "s|package $OLD_PACKAGE|package $NEW_PACKAGE|g"

find "$ANDROID_DIR/$NEW_JAVA_PATH" -name "*.kt" -print0 | \
    xargs -0 sed -i "s|import $OLD_PACKAGE\.|import $NEW_PACKAGE.|g"

# ── 3. Update AndroidManifest.xml ────────────────────────────────────────────
MANIFEST="$ANDROID_DIR/AndroidManifest.xml"
sed -i "s|$OLD_PACKAGE|$NEW_PACKAGE|g" "$MANIFEST"

# ── 4. Update app/build.gradle ───────────────────────────────────────────────
APP_GRADLE="android/app/build.gradle"
sed -i "s|namespace '$OLD_PACKAGE'|namespace '$NEW_PACKAGE'|g" "$APP_GRADLE"
sed -i "s|applicationId \"$OLD_PACKAGE\"|applicationId \"$NEW_PACKAGE\"|g" "$APP_GRADLE"

# ── 5. Update settings.gradle ────────────────────────────────────────────────
sed -i "s|rootProject.name = \"skeleton\"|rootProject.name = \"$APP_NAME\"|g" android/settings.gradle

# ── 6. Update strings.xml app_name ───────────────────────────────────────────
STRINGS="$ANDROID_DIR/res/values/strings.xml"
sed -i "s|<string name=\"app_name\">Skeleton</string>|<string name=\"app_name\">$APP_NAME</string>|g" "$STRINGS"

# ── 7. Update test source trees ───────────────────────────────────────────────
for TEST_DIR in android/app/src/test android/app/src/androidTest; do
    if [ -d "$TEST_DIR/$OLD_JAVA_PATH" ]; then
        TEST_NEW="$TEST_DIR/$NEW_JAVA_PATH"
        mkdir -p "$(dirname "$TEST_NEW")"
        mv "$TEST_DIR/$OLD_JAVA_PATH" "$TEST_NEW"
        find "$TEST_NEW" -name "*.kt" -print0 | \
            xargs -0 sed -i "s|package $OLD_PACKAGE|package $NEW_PACKAGE|g"
        find "$TEST_NEW" -name "*.kt" -print0 | \
            xargs -0 sed -i "s|import $OLD_PACKAGE\.|import $NEW_PACKAGE.|g"
    fi
done

echo ""
echo "Done. Next steps:"
echo "  1. Review changes: git diff"
echo "  2. Sync Gradle in Android Studio (or ./gradlew build from android/)"
echo "  3. Update res/values/strings.xml with remaining strings for your app"
echo "  4. Uncomment permissions in AndroidManifest.xml as needed"
