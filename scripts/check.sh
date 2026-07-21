#!/usr/bin/env bash
# Everything that has to pass before shipping. Run it before committing.
#
#   ./scripts/check.sh          # check only, fails on any violation
#   ./scripts/check.sh --fix    # auto-format first, then check
#
# Output is concise by design: read it in full rather than piping to tail.

set -euo pipefail

cd "$(dirname "$0")/.."

# Prefer the wrapper so CI and local runs use the same Gradle. AGP 9 accepts only a narrow
# range of Gradle versions, so "whatever gradle is on PATH" is not good enough.
GRADLE=./gradlew
[ -x "$GRADLE" ] || GRADLE=gradle

# The APK is the product here, so a size regression is a real regression. Bump this
# deliberately when the app genuinely grows; don't nudge it to make a build pass.
MAX_APK_KB=40

if [ "${1:-}" = "--fix" ]; then
  echo "==> ktlintFormat"
  "$GRADLE" --quiet ktlintFormat
fi

echo "==> ktlintCheck"
"$GRADLE" --quiet ktlintCheck

echo "==> lint (release)"
"$GRADLE" --quiet lintRelease

echo "==> assembleRelease"
"$GRADLE" --quiet assembleRelease

# Builds without the release keystore (CI, any fresh clone) emit "-release-unsigned.apk",
# so find the artifact rather than assuming its name.
apk=$(find build/outputs/apk/release -maxdepth 1 -name '*.apk' | head -1)
[ -n "$apk" ] || { echo "FAIL: assembleRelease produced no APK"; exit 1; }
size_kb=$(( $(wc -c < "$apk") / 1024 ))
echo "==> APK size: ${size_kb} KB (budget ${MAX_APK_KB} KB)  [$(basename "$apk")]"
if [ "$size_kb" -gt "$MAX_APK_KB" ]; then
  echo "FAIL: APK grew past its budget. Investigate before raising MAX_APK_KB:"
  echo "  ~/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer files list $apk"
  exit 1
fi

echo
echo "All checks passed."
