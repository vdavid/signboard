# Signboard

Android app that displays one line of text full-screen, as large as it fits. Long-press anywhere
to edit it, toggle black-on-white, or pick from recent texts. The screen stays on while it's open.

Package `com.veszelovszki.signboard`. Kotlin, no dependencies, two source files.

## Layout

- `src/main/kotlin/com/veszelovszki/signboard/MainActivity.kt`: the activity, the edit dialog, and
  the history list. Views are built in code; there are no layout XML files.
- `src/main/kotlin/com/veszelovszki/signboard/SignboardView.kt`: the full-screen text itself. It
  sizes the text to fill the screen and flows it around display cutouts.
- `src/main/res/`: strings, the theme, the launcher icons, and the backup rules.
- [`branding/`](branding/CLAUDE.md): Play Store icon, feature graphic, and screenshots, plus the
  scripts that regenerate them.
- `scripts/check.sh`: everything that must pass before shipping.
- `privacy-policy.md`: linked from the Play listing. The app collects nothing.

## Working on it

Use `./gradlew`, not a system `gradle`: AGP 9 accepts only a narrow range of Gradle versions.

Run `./scripts/check.sh` before committing (`--fix` to auto-format first). It runs ktlint, Android
Lint against the release build, builds the release APK, and fails if the APK exceeds its size
budget. Read its output in full; it's short on purpose.

## Size is a feature

The release APK is ~29 KB. It got there by having no dependencies at all, and `scripts/check.sh`
enforces a budget so that doesn't silently regress.

The single biggest lever was dropping `androidx.appcompat`: it was ~400 KB and about 280 resource
entries, supplying an `Activity` base class the app used no feature of. `androidx.core` was ~90 KB
for one `WindowInsets` accessor, and `org.json` was a dependency on something the Android framework
already ships. All three are gone; `MainActivity` uses the framework equivalents.

Before adding any dependency, weigh it against those numbers. If one is genuinely needed, raise
`MAX_APK_KB` in `scripts/check.sh` deliberately and say why in the commit message.

Other size decisions, all in `build.gradle.kts` with comments: R8 with resource shrinking, Kotlin
reflection metadata excluded from packaging, and the SDK dependency blob omitted.

## Gotchas

- **Gradle runs on the wrong JDK.** Homebrew's Gradle launches on JDK 26, and the IntelliJ code embedded in the Kotlin compiler can't parse a "26.0.1" version string. It throws inside the incremental-compilation cache and surfaces only as a stray `e: Daemon compilation failed: null` while the build still prints `BUILD SUCCESSFUL`. `build.gradle.kts` pins `jvmToolchain(21)`; AGP 9's built-in Kotlin avoids the rest.
- **AGP 9 has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` alongside it is a hard error. AGP 8.13 can't be used instead: it relies on a Gradle internal API removed in Gradle 9.6.
- **Lint is strict and that's deliberate.** `warningsAsErrors` is on. Three checks are disabled in `build.gradle.kts`, each with a comment explaining why. Prefer fixing a finding over extending that list.
- **Don't avoid a camera cutout with padding.** Padding applies to a whole edge, so clearing a punch-hole costs a full-height strip: a 149x87 hole cost 149x1008 of screen. Worse, `safeInsetTop` and friends aren't the hole's position at all, they're edge-wide bands. `SignboardView` takes `DisplayCutout.boundingRects` and fits each line against only the cutouts level with that line, so a line nowhere near the camera keeps the full width.
- **A ListView row containing a focusable child never fires `OnItemClickListener`.** The history rows hold a delete button, which is exactly why tapping a row did nothing while the button worked. Rows handle their own click and the button is non-focusable.
- **`targetSdk`/`compileSdk` are held at 35 on purpose.** Newer SDKs exist. Raising `targetSdk` opts into new runtime restrictions and needs testing on a real device, so it's its own task, not a drive-by. `OldTargetApi` and `GradleDependency` are muted so this stays a conscious decision rather than a permanent red build.

## Releasing

1. Bump `versionCode` (Play rejects a reused one) and `versionName` in `build.gradle.kts`.
2. `./scripts/check.sh`
3. `./gradlew bundleRelease` → `build/outputs/bundle/release/Signboard-release.aab`, which is what Play takes.
4. Upload to Play Console, along with anything in [`branding/`](branding/CLAUDE.md) that changed.

Release builds are signed with `~/.android/signboard-release.keystore`. That key is not in the repo
and cannot be regenerated: losing it means losing the ability to update the app on Play.

Builds without that keystore (CI, a fresh clone) still succeed and produce an unsigned release APK,
so CI can verify minification, lint, and size without holding the signing key. CI only attaches
artifacts to a GitHub Release when a `SIGNBOARD_RELEASE_KEYSTORE` secret is configured, since an
unsigned APK on a release page is worse than none.
