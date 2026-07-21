# Signboard

Android app that displays text full-screen, as large as it fits. Long-press anywhere to edit it,
toggle black-on-white, or pick from a recent text. The screen stays on while it's open.

Package `com.veszelovszki.signboard`. Kotlin, **no dependencies**, two source files.

## Map

- `src/main/kotlin/com/veszelovszki/signboard/MainActivity.kt` — the activity, the edit dialog, the history list. Views are built in code; there are no layout XML files.
- `src/main/kotlin/com/veszelovszki/signboard/SignboardView.kt` — the full-screen text. Sizes it to fill the screen and flows it around display cutouts.
- `src/main/res/` — strings, theme, launcher icons, backup rules.
- `build.gradle.kts` — every non-obvious build decision is commented in place. Read it before changing it.
- `scripts/check.sh` — everything that must pass before shipping.
- [`branding/CLAUDE.md`](branding/CLAUDE.md) — Play Store icon, feature graphic, screenshots, and the scripts that regenerate them.
- [`docs/play-listing.md`](docs/play-listing.md) — listing copy, category, asset inventory, version history.
- [`privacy-policy.md`](privacy-policy.md) — linked from the listing. The app collects nothing.
- `.github/workflows/build.yml` — CI.

## Working on it

**Use `./gradlew`**, never a system `gradle`. AGP 9 accepts only a narrow Gradle range.

Run `./scripts/check.sh` before committing (`--fix` auto-formats first). It runs ktlint, Android
Lint against the *release* build, builds the release APK, and fails if the APK exceeds its size
budget. Read its output in full; it's short by design.

Formatting is ktlint with `intellij_idea` style at 2-space indent (`.editorconfig`). The stricter
`ktlint_official` style was rejected on purpose: it forces every multiline initializer onto its own
line, which reads badly in a file that is mostly view construction.

### Verifying changes

Behavioural claims here were checked by measuring pixels, not by looking. `magick ... -trim` on a
screenshot gives the text's ink bounds, which is how the cutout work was validated. Do the same
rather than eyeballing: several bugs in this app's history looked fine in a screenshot.

Use the emulator for functional testing. **Don't send blind `input tap` coordinates to a real
phone** — they land on whatever is actually on screen, which during this project meant opening
system settings panels. `branding/capture-screenshots.sh` shows the safe pattern: seed state by
writing SharedPreferences with root on an emulator, rather than driving the UI.

## Size is a feature

The release APK is ~29 KB, down from 648 KB. `scripts/check.sh` enforces a budget so that doesn't
silently regress.

The dominant lever was **having no dependencies**:

- `androidx.appcompat` cost ~400 KB and ~280 resource entries, to supply an `Activity` base class the app used no feature of. `resources.arsc` alone went 162 KB → 1.5 KB when it went.
- `androidx.core` cost ~90 KB for one `WindowInsets` accessor.
- `org.json` was a dependency on something the Android framework already ships.

Weigh anything new against those numbers. If a dependency is genuinely needed, raise `MAX_APK_KB`
in `scripts/check.sh` deliberately and justify it in the commit message.

Smaller levers, all commented in `build.gradle.kts`: R8 with resource shrinking, Kotlin reflection
metadata excluded from packaging, the SDK dependency blob omitted, and launcher icons stored as
lossless 8-bit grayscale rather than 32-bit RGBA.

## Gotchas

Each of these cost real time, and every one of them fails *silently*.

### Build

- **Gradle may run on the wrong JDK.** Homebrew's Gradle launches on JDK 26, whose version string the IntelliJ code embedded in the Kotlin compiler cannot parse. It throws inside the incremental-compilation cache and surfaces only as a stray `e: Daemon compilation failed: null` under a cheerful `BUILD SUCCESSFUL`. `build.gradle.kts` pins `jvmToolchain(21)`.
- **AGP 9 has built-in Kotlin support.** Applying `org.jetbrains.kotlin.android` next to it is a hard error. Downgrading to AGP 8.13 is not an escape: it uses a Gradle internal API removed in Gradle 9.6.
- **Lint is strict on purpose.** `warningsAsErrors` is on. It has caught real bugs here, including API-level errors that were only accidentally safe at runtime. Three checks are disabled, each with a comment. Prefer fixing over extending that list.
- **`targetSdk`/`compileSdk` are held at 35 deliberately.** Raising `targetSdk` opts into new runtime restrictions and needs device testing, so it's its own task. `OldTargetApi` and `GradleDependency` are muted so this stays a decision rather than a permanently red build.

### Android

- **Never avoid a camera cutout with padding.** Padding applies to a whole edge, so clearing a punch-hole costs a full-height strip — a 149x87 hole cost 149x1008 of screen. And `safeInsetTop` and friends are *not* the hole's position; they're edge-wide bands. `SignboardView` takes `DisplayCutout.boundingRects` and fits each line against only the cutouts level with it. `boundingRects` is API 28; the per-edge `boundingRectTop` accessors are API 29, above this app's minimum.
- **A ListView row containing a focusable child never fires `OnItemClickListener`.** The history rows hold a delete button, which is exactly why tapping a row did nothing while the × worked. Rows handle their own click; the button is non-focusable.
- **Sizes written as plain numbers are pixels, not dp.** `minHeight = 200` on the edit field reserved several lines of space on a dense screen. Use the `dp()` helper.
- **Guard API-level calls inside the method that makes them.** Hoisting a `DisplayCutout` into a nullable local and reading its properties outside the version check is runtime-safe but unprovable to lint, and trips `NewApi`.

## CI

`.github/workflows/build.yml` runs `scripts/check.sh`, then builds the debug APK and the release
bundle.

- **Release signing is optional.** The keystore lives outside the repo, so CI builds an *unsigned* release APK, which is still enough to verify minification, lint, and size. Add a `SIGNBOARD_RELEASE_KEYSTORE` secret (base64 of the keystore) to get installable artifacts.
- **Unsigned builds are named `Signboard-release-unsigned.apk`.** Anything referring to the artifact must glob rather than hardcode the signed name. This broke the size check once.
- CI attaches artifacts to a GitHub Release only on a tag *and* only when signing is configured. An unsigned APK on a release page is worse than none.

## Releasing

1. Bump `versionCode` (Play rejects a reused one) and `versionName` in `build.gradle.kts`. Log it in [`docs/play-listing.md`](docs/play-listing.md).
2. `./scripts/check.sh`
3. `./gradlew bundleRelease` → `build/outputs/bundle/release/Signboard-release.aab`, which is what Play takes.
4. Upload to Play Console with any changed assets from [`branding/`](branding/CLAUDE.md).

Release builds are signed with `~/.android/signboard-release.keystore`. **That key is not in the
repo and cannot be regenerated: losing it means losing the ability to update the app on Play.**
Its passwords are currently hardcoded in `build.gradle.kts` (overridable via
`SIGNBOARD_KEYSTORE_PASSWORD` / `SIGNBOARD_KEY_PASSWORD`). The keystore file is what actually
matters, but moving the passwords out is worth doing if this repo ever goes public.
