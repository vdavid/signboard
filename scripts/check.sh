#!/usr/bin/env bash
# Everything that has to pass before shipping. Run it before committing.
#
#   ./scripts/check.sh          # check only, fails on any violation
#   ./scripts/check.sh --fix    # auto-format first, then check
#
# Output is concise by design: read it in full rather than piping to tail.

set -euo pipefail

cd "$(dirname "$0")/.."

# The APK is the product here, so a size regression is a real regression. Bump this
# deliberately when the app genuinely grows; don't nudge it to make a build pass.
MAX_APK_KB=40

if [ "${1:-}" = "--fix" ]; then
  echo "==> ktlintFormat"
  gradle --quiet ktlintFormat
fi

echo "==> ktlintCheck"
gradle --quiet ktlintCheck

echo "==> lint (release)"
gradle --quiet lintRelease

echo "==> assembleRelease"
gradle --quiet assembleRelease

apk=build/outputs/apk/release/Signboard-release.apk
size_kb=$(( $(wc -c < "$apk") / 1024 ))
echo "==> APK size: ${size_kb} KB (budget ${MAX_APK_KB} KB)"
if [ "$size_kb" -gt "$MAX_APK_KB" ]; then
  echo "FAIL: APK grew past its budget. Investigate before raising MAX_APK_KB:"
  echo "  ~/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer files list $apk"
  exit 1
fi

echo
echo "All checks passed."
