#!/usr/bin/env bash
# Captures every Play Console screenshot, plus the feature graphic, from headless emulators.
#
#   ./branding/capture-screenshots.sh                    # everything
#   ./branding/capture-screenshots.sh tablet7-portrait   # one shot, by output basename
#
# Creates the AVDs if missing, boots each one once, and takes every shot that AVD
# owns before moving on. Emulator boots dominate the runtime, so shots are grouped
# by device rather than run in listed order.
#
# Everything is regenerated from scratch, so re-running gives byte-comparable output.

set -euo pipefail

cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$ANDROID_HOME/emulator/emulator"
APK="build/outputs/apk/release/Signboard-release.apk"
SYSTEM_IMAGE="system-images;android-35;google_apis;arm64-v8a"
PKG="com.veszelovszki.signboard"

# Fraction of the screen height painted black at the bottom, to erase the gesture
# nav pill. The app's text never reaches this far down at any supported size.
NAV_PILL_FRACTION=0.07

# avd : device profile : port
DEVICES=(
  "sb_phone:pixel_8:5558"
  "sb_tablet7:Nexus 7 2013:5554"
  "sb_tablet10:Nexus 10:5556"
)

# avd : orientation : output basename : inverted : mode : displayed text
#
# `inverted` picks the app's black-on-white theme instead of white-on-black. Every
# device/orientation appears in both themes.
#
# `mode` is `plain` for a bare screenshot, or `dialog` to long-press first so the edit
# dialog is open in the shot. Dialog shots also get custom history entries, see history_for().
#
# Each shot gets its own emoji pair so the listing images don't look copy-pasted.
# Ordered to group same-orientation shots together: rotating costs a few seconds,
# re-seeding the text costs none.
SHOTS=(
  "sb_phone:landscape:phone-landscape:false:plain:🍆 Signboard 🍆"
  "sb_phone:landscape:phone-landscape-inverted:true:plain:🦄 Signboard 🦄"
  "sb_phone:portrait:phone-portrait:false:plain:🦒 Signboard 🦒"
  "sb_phone:portrait:phone-portrait-inverted:true:plain:🥑 Signboard 🥑"
  "sb_phone:portrait:phone-portrait-dialog:false:dialog:Go aunt Amalia! 🎸"
  "sb_tablet7:portrait:tablet7-portrait:false:plain:🐧 Signboard 🐧"
  "sb_tablet7:portrait:tablet7-portrait-inverted:true:plain:🫎 Signboard 🫎"
  "sb_tablet7:landscape:tablet7-landscape:false:plain:🐘 Signboard 🐘"
  "sb_tablet7:landscape:tablet7-landscape-inverted:true:plain:🍄‍🟫 Signboard 🍄‍🟫"
  "sb_tablet10:landscape:tablet10-landscape:false:plain:🥦 Signboard 🥦"
  "sb_tablet10:landscape:tablet10-landscape-inverted:true:plain:🍩 Signboard 🍩"
  "sb_tablet10:portrait:tablet10-portrait:false:plain:🦖 Signboard 🦕"
  "sb_tablet10:portrait:tablet10-portrait-inverted:true:plain:🐙 Signboard 🐙"
)

# History entries seeded for a given shot, as a JSON array. Only matters for dialog shots,
# where the history list is actually visible; everything else just echoes its own text.
history_for() {
  case "$1" in
    phone-portrait-dialog) printf '%s' '["1 457 664","Insektenvernichtungsmittel"]' ;;
    *)                     printf '%s' "[\"$2\"]" ;;
  esac
}

# The feature graphic is derived from this shot, not captured separately.
FEATURE_SOURCE="phone-landscape"

WANTED="${1:-}"

# The emulator needs a non-Play system image: `adb root` is blocked on
# google_apis_playstore images, and seeding SharedPreferences needs root.
if [ ! -x "$EMULATOR" ] || [ ! -f "$ANDROID_HOME/system-images/android-35/google_apis/arm64-v8a/system.img" ]; then
  echo "Installing emulator + system image (~4 GB, several minutes)..."
  yes | "$SDKMANAGER" --install "emulator" "$SYSTEM_IMAGE"
fi

[ -f "$APK" ] || { echo "No release APK. Run: gradle assembleRelease"; exit 1; }

boot_avd() {
  local avd=$1 device=$2 port=$3 serial="emulator-$port"

  if ! "$EMULATOR" -list-avds | grep -qx "$avd"; then
    echo "Creating AVD $avd ($device)..."
    # The "Could not load devices.xml" warning it prints is harmless; the device
    # profile still applies. Verify: grep hw.lcd ~/.android/avd/$avd.avd/config.ini
    echo no | "$AVDMANAGER" create avd -n "$avd" -k "$SYSTEM_IMAGE" -d "$device" --force
  fi

  echo "Booting $avd..."
  "$EMULATOR" -avd "$avd" -no-window -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -port "$port" >"/tmp/emu-$avd.log" 2>&1 &
  # `adb emu kill` makes the emulator abort rather than exit cleanly, and bash job
  # control reports that as a scary "Abort trap: 6". Disowning drops the reporting.
  disown

  local i
  for i in $(seq 1 60); do
    [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 5
  done
  [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] \
    || { echo "$avd never finished booting, see /tmp/emu-$avd.log"; return 1; }

  adb -s "$serial" install -r "$APK" >/dev/null
  # Launch once so Android creates shared_prefs/, which set_text writes into.
  adb -s "$serial" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
  sleep 3
  adb -s "$serial" shell am force-stop "$PKG"
  adb -s "$serial" root >/dev/null 2>&1
  adb -s "$serial" wait-for-device
  # Clear any rotation lock left behind in this AVD's persisted state, then disable
  # auto-rotate so rotate_to() is the only thing steering the display.
  adb -s "$serial" shell wm user-rotation free >/dev/null
  adb -s "$serial" shell settings put system accelerometer_rotation 0
}

# Writes the displayed text straight into SharedPreferences. Driving the app's edit
# dialog with `adb shell input text` can't type emoji, which is the whole point here.
set_text() {
  local serial=$1 text=$2 inverted=$3 history=$4
  adb -s "$serial" shell am force-stop "$PKG"
  cat > /tmp/signboard-prefs.xml <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="text">${text}</string>
    <boolean name="inverted" value="${inverted}" />
    <string name="history">${history}</string>
</map>
XML
  adb -s "$serial" push /tmp/signboard-prefs.xml /data/local/tmp/signboard.xml >/dev/null
  # The chown matters: a root-owned prefs file is unreadable by the app, which then
  # silently falls back to its default text instead of erroring.
  adb -s "$serial" shell "cp /data/local/tmp/signboard.xml /data/data/$PKG/shared_prefs/signboard.xml \
    && chown \$(stat -c %u:%g /data/data/$PKG) /data/data/$PKG/shared_prefs/signboard.xml"
}

# Reports the display's current size, e.g. "1080x2400". This is the authoritative
# answer, unlike a screencap, which can hand back a stale frame mid-rotation.
current_display_size() {
  adb -s "$1" shell dumpsys window displays | grep -oE 'cur=[0-9]+x[0-9]+' | head -1 | cut -d= -f2
}

# Rotates and blocks until the window manager reports the requested aspect.
#
# Devices differ in natural orientation, so a given rotation value means different
# things per device: try both, keep whichever lands. Rotation is also applied
# asynchronously and can take several seconds, so this polls the window manager
# rather than sleeping a fixed amount and hoping.
#
# Requires an app that accepts the target orientation to already be in the
# foreground; see capture_oriented.
rotate_to() {
  local serial=$1 orientation=$2 rot i cur w h
  for rot in 0 1; do
    # `wm user-rotation lock` rather than writing the user_rotation setting: the wm
    # lock takes precedence over that setting, so if anything ever set a lock (an
    # earlier run, a manual debugging session) a settings write is silently ignored
    # and the display never moves.
    adb -s "$serial" shell wm user-rotation lock "$rot" >/dev/null
    for i in $(seq 1 20); do
      cur=$(current_display_size "$serial")
      w=${cur%x*}; h=${cur#*x}
      if [ "$orientation" = "landscape" ] && [ "$w" -gt "$h" ]; then return 0; fi
      if [ "$orientation" = "portrait" ] && [ "$h" -gt "$w" ]; then return 0; fi
      sleep 1
    done
  done
  echo "  WARNING: could not rotate to $orientation (display stuck at $cur)"
  return 1
}

capture_oriented() {
  local serial=$1 orientation=$2 out=$3 mode=$4 expected got size w h

  # The screen can blank between shots, and a screencap of a blanked display is black.
  adb -s "$serial" shell input keyevent KEYCODE_WAKEUP

  # Start the app BEFORE rotating. The display only honors a user rotation the
  # foreground activity permits, and the phone launcher is locked to portrait, so
  # rotating against the launcher silently does nothing. Signboard itself accepts any
  # orientation and handles the config change in place, so no restart is needed after.
  adb -s "$serial" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
  sleep 2

  rotate_to "$serial" "$orientation" || true
  expected=$(current_display_size "$serial")
  sleep 2

  # A long-press anywhere on the text opens the edit dialog. `input swipe` with identical
  # start and end coordinates and a long duration is a press-and-hold; there's no
  # `input longpress`. 800ms clears the framework's ~500ms long-press threshold.
  if [ "$mode" = "dialog" ]; then
    size=$(current_display_size "$serial")
    w=${size%x*}; h=${size#*x}
    adb -s "$serial" shell input swipe $((w / 2)) $((h / 2)) $((w / 2)) $((h / 2)) 800
    sleep 3
  fi

  adb -s "$serial" shell screencap -p /sdcard/s.png
  adb -s "$serial" pull /sdcard/s.png "$out" >/dev/null

  got=$(magick identify -format '%wx%h' "$out")
  [ "$got" = "$expected" ] || echo "  WARNING: $out is $got but display reports $expected"
}

# Paints over the gesture nav pill with the app's own background color. Using the
# wrong color here leaves an obvious bar along the bottom edge, so it tracks the
# shot's theme rather than always filling black.
erase_nav_pill() {
  local img=$1 fill=$2 w h cutoff
  w=$(magick identify -format '%w' "$img")
  h=$(magick identify -format '%h' "$img")
  cutoff=$(awk -v h="$h" -v f="$NAV_PILL_FRACTION" 'BEGIN { printf "%d", h - h * f }')
  magick "$img" -fill "$fill" -draw "rectangle 0,$cutoff $((w - 1)),$((h - 1))" "PNG32:$img"
}

for device_entry in "${DEVICES[@]}"; do
  IFS=: read -r avd device port <<< "$device_entry"
  serial="emulator-$port"

  # Skip booting this device entirely if it owns none of the requested shots.
  device_shots=()
  for shot in "${SHOTS[@]}"; do
    IFS=: read -r shot_avd orientation basename inverted mode text <<< "$shot"
    [ "$shot_avd" = "$avd" ] || continue
    [ -z "$WANTED" ] || [ "$WANTED" = "$basename" ] || continue
    device_shots+=("$shot")
  done
  [ ${#device_shots[@]} -gt 0 ] || continue

  boot_avd "$avd" "$device" "$port"

  for shot in "${device_shots[@]}"; do
    IFS=: read -r shot_avd orientation basename inverted mode text <<< "$shot"
    out="branding/$basename.png"
    [ "$inverted" = "true" ] && bg=white || bg=black
    set_text "$serial" "$text" "$inverted" "$(history_for "$basename" "$text")"
    capture_oriented "$serial" "$orientation" "$out" "$mode"
    # A dialog shot dims the whole background, so painting the nav pill area pure black
    # would leave a visibly darker patch. Leave those alone.
    [ "$mode" = "dialog" ] || erase_nav_pill "$out" "$bg"
    echo "  $out ($(magick identify -format '%wx%h' "$out"))  $text  [$bg bg, $mode]"
  done

  adb -s "$serial" emu kill >/dev/null 2>&1 || true
  sleep 2
done

# Feature graphic: letterbox the landscape phone shot to exactly 1024x500. Never crop
# to that size, it eats the text. The black padding blends into the app background.
if [ -z "$WANTED" ] || [ "$WANTED" = "$FEATURE_SOURCE" ]; then
  magick "branding/$FEATURE_SOURCE.png" \
    -resize 1024x460 \
    -background black -gravity center -extent 1024x500 \
    PNG32:branding/feature-graphic-1024x500.png
  echo "  branding/feature-graphic-1024x500.png (1024x500)"
fi

sleep 2
adb kill-server
echo "adb server stopped."
