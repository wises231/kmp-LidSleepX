#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
VERSION="0.2.1"
# jpackage rejects versions whose first component is zero.
JPACKAGE_VERSION="1.0.0"
APP_ID="com.wyz.lidsleepx"
APP_NAME="LidSleepX"
JAR_NAME="app-jvm-executable.jar"
JAR_DIR="$ROOT/build/tasks/_app_executableJarJvm"
ICON="$ROOT/assets/LidSleepX.icns"
APP_IMAGE="$DIST/$APP_NAME.app"
DMG_PATH="$DIST/$APP_NAME-$VERSION.dmg"

if [[ "$(uname -s)" != "Darwin" || "$(uname -m)" != "arm64" ]]; then
  printf 'This script requires an Apple Silicon Mac.\n' >&2
  exit 1
fi

cd "$ROOT"
/bin/sh ./kotlin build
/bin/sh ./kotlin test
/bin/sh ./kotlin package -m app -f executable-jar -v release

if [[ ! -f "$JAR_DIR/$JAR_NAME" ]]; then
  printf 'Missing executable JAR: %s\n' "$JAR_DIR/$JAR_NAME" >&2
  exit 1
fi

if [[ ! -f "$ICON" ]]; then
  printf 'Missing icon: %s\n' "$ICON" >&2
  exit 1
fi

find_jdk25() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jpackage" ]]; then
    local candidate_version
    candidate_version="$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n '1s/.*"\([0-9][0-9]*\).*/\1/p')"
    if [[ "$candidate_version" == "25" ]]; then
      printf '%s\n' "$JAVA_HOME"
      return 0
    fi
  fi

  local candidate
  while IFS= read -r candidate; do
    if [[ -x "$candidate/bin/jpackage" ]]; then
      local candidate_version
      candidate_version="$("$candidate/bin/java" -version 2>&1 | sed -n '1s/.*"\([0-9][0-9]*\).*/\1/p')"
      if [[ "$candidate_version" == "25" ]]; then
        printf '%s\n' "$candidate"
        return 0
      fi
    fi
  done < <(find "$HOME/Library/Caches/JetBrains/Kotlin/extract.cache" -path '*/Contents/Home' -type d 2>/dev/null | sort -r)

  printf 'JDK 25 was not found. Install JDK 25 or set JAVA_HOME.\n' >&2
  return 1
}

JDK25="$(find_jdk25)"
JPACKAGE="$JDK25/bin/jpackage"
PLIST_BUDDY="/usr/libexec/PlistBuddy"

if [[ -d "$APP_IMAGE" ]]; then
  find "$APP_IMAGE" -depth -delete
fi
if [[ -e "$DMG_PATH" ]]; then
  unlink "$DMG_PATH"
fi
mkdir -p "$DIST"

"$JPACKAGE" \
  --type app-image \
  --dest "$DIST" \
  --input "$JAR_DIR" \
  --name "$APP_NAME" \
  --main-jar "$JAR_NAME" \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --app-version "$JPACKAGE_VERSION" \
  --vendor wyz \
  --copyright "Copyright (c) 2026 wyz" \
  --description "A menu-bar app that controls macOS sleep behavior." \
  --icon "$ICON" \
  --mac-package-identifier "$APP_ID" \
  --mac-package-name "$APP_NAME" \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  --java-options "-Dapple.awt.UIElement=true" \
  --java-options "-Dapple.awt.application.name=$APP_NAME"

PLIST="$APP_IMAGE/Contents/Info.plist"
set_plist_value() {
  local key="$1"
  local type="$2"
  local value="$3"
  if "$PLIST_BUDDY" -c "Print :$key" "$PLIST" >/dev/null 2>&1; then
    "$PLIST_BUDDY" -c "Set :$key $value" "$PLIST"
  else
    "$PLIST_BUDDY" -c "Add :$key $type $value" "$PLIST"
  fi
}

set_plist_value CFBundleIdentifier string "$APP_ID"
set_plist_value CFBundleShortVersionString string "$VERSION"
set_plist_value CFBundleVersion string "$VERSION"
set_plist_value LSMinimumSystemVersion string "12.0"
set_plist_value LSUIElement bool true
set_plist_value NSHighResolutionCapable bool true

/usr/bin/codesign --force --deep --sign - "$APP_IMAGE"
/usr/bin/codesign --verify --deep --strict "$APP_IMAGE"

"$JPACKAGE" \
  --type dmg \
  --dest "$DIST" \
  --app-image "$APP_IMAGE" \
  --name "$APP_NAME" \
  --app-version "$JPACKAGE_VERSION" \
  --vendor wyz \
  --icon "$ICON"

PRODUCED_DMG="$DIST/$APP_NAME-$JPACKAGE_VERSION.dmg"
if [[ -f "$PRODUCED_DMG" && "$PRODUCED_DMG" != "$DMG_PATH" ]]; then
  mv "$PRODUCED_DMG" "$DMG_PATH"
fi

if [[ ! -d "$APP_IMAGE" || ! -f "$DMG_PATH" ]]; then
  printf 'Packaging did not create the expected artifacts.\n' >&2
  exit 1
fi

printf 'Created %s\n' "$APP_IMAGE"
printf 'Created %s\n' "$DMG_PATH"
