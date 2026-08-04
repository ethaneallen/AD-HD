#!/usr/bin/env bash
# Build the debug APK, install it on the running emulator, and (re)launch TavernTask.
# Used as the default inner-loop for testing changes without sideloading to a phone.
set -e
SDK="$HOME/AppData/Local/Android/Sdk"
ADB="$SDK/platform-tools/adb"
PKG="com.focuspet.app"
cd "$(dirname "$0")"

echo "== building =="
./gradlew :app:assembleDebug -q

echo "== installing =="
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk

echo "== launching =="
"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
echo "== done: TavernTask relaunched on emulator =="
