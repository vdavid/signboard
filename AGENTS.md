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
- `scripts/play-status.py` — what's live on each Play track, via the Play Developer API. `scripts/play_token.py` mints the OAuth token it uses.
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

**After any `targetSdk` bump, re-run the cutout check.** It's the part most exposed to platform
behavior changes, and it fails silently and looks plausible. On an emulator whose API level matches
the new `targetSdk`, in landscape (where the cutout sits on a long edge):

```bash
adb shell dumpsys window | grep -oE 'boundingRect=\{Bounds=\[[^]]*\]\}' | head -1   # where the hole is
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png /tmp/s.png
magick /tmp/s.png -crop <W>x1000+0+0 +repage -bordercolor black -border 1 -fuzz 25% -trim \
  -format 'ink x %X..%[fx:page.x+w]\n' info:
```

Two cases, both required: text long enough to reach the hole must stop clear of it, and text that
doesn't reach it must stay centred on the screen (ink midpoint ≈ screen midpoint). The second is
the one that regresses.

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
- **Lint is strict on purpose.** `warningsAsErrors` is on. It has caught real bugs here, including API-level errors that were only accidentally safe at runtime, and it flags a stale AGP or SDK before Google emails about it. Only `IconLauncherShape` is disabled, with a comment. Prefer fixing over extending that list.
- **Keep `targetSdk` current.** `OldTargetApi` and `GradleDependency` are lint-enabled so a stale SDK or plugin breaks the build instead of arriving as mail later. Bumping `targetSdk` opts into that release's behavior changes, so it needs the cutout checks below re-run, not just a number edit. The Play-side deadline and how to verify what's live: [Play requirements and deadlines](#play-requirements-and-deadlines).

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

## Play Developer API

`./scripts/play-status.py` reports each track without opening the Console.

- GCP project `signboard-play-store`, with `androidpublisher.googleapis.com` enabled.
- Service account `signboard-release@signboard-play-store.iam.gserviceaccount.com`, granted access under Play Console → Users and permissions.
- Its key is in the sops store as `SIGNBOARD_PLAY_SA_KEY`, base64-encoded JSON, matching the convention of the other entries there. **Never put it in the repo.** Read it with `secret SIGNBOARD_PLAY_SA_KEY`.

Things to know before scripting against it:

- **`gcloud auth print-access-token` does not work here.** It mints a token with gcloud's own scopes and the API rejects it with `ACCESS_TOKEN_SCOPE_INSUFFICIENT`. `play_token.py` signs a JWT assertion scoped to `androidpublisher` instead, using `openssl` so it needs no third-party libraries.
- **Everything goes through an "edit"**, the API's unit of work. For a read, open one and always discard it; abandoned edits linger. For a write, an edit's uploads are discarded unless it is committed, so a failed commit silently loses the upload and leaves the track untouched.
- **Brace the edit id: `${EDIT}:commit`, never `$EDIT:commit`.** zsh treats `:c` after a bare parameter as its "resolve command to an absolute path" modifier, so `$EDIT:commit` expands to the id with `:c` swallowed and `ommit` tacked on. The call then 404s with Google's generic HTML error page rather than a JSON API error, which looks nothing like a quoting bug. `:validate` is immune because `:v` isn't a modifier, so validate succeeding tells you nothing about whether commit will.

The service account can publish, not just read: uploading a bundle and rolling it out to production
works end to end over the API.

## Play requirements and deadlines

Google enforces a handful of dated requirements against this app. All of them are met today; these
notes exist so a future session doesn't re-derive the answer from a nag email.

**Notifications lag reality, and Google never retracts them.** The Aug 31, 2026 target-API notice
was sent hours before the fix was uploaded and still sat in the Console weeks later, red and
unread, echoed inline on the app dashboard. Check the artifact, not the notification.

- **Target API level, Aug 31, 2026.** Met: `targetSdk` 37 (Android 17) ships in versionCode 6. Play enforces this by refusing *updates*; a live app keeps working. To prove what Play actually holds, `edits.bundles.list` returns a sha1 per uploaded bundle, so matching one against the local `.aab` identifies the build, and `unzip -p <aab> base/manifest/AndroidManifest.xml` shows `targetSdkVersion` as readable text inside the proto manifest.
- **Android developer verification, Sep 30, 2026.** Met, for both distribution paths. Google auto-registered `com.veszelovszki.signboard` against its own Play App Signing key on 2026-07-21; the upload key was added by hand on 2026-08-27, because the APK attached to GitHub Releases is signed with that one and an unregistered certificate stops installing in the enforcing regions. Two keys are registered, both `Verified`: `57:0B:E5:…` is Google's, `43:BD:1F:9B:…` is `~/.android/signboard-release.keystore`. **Sign release APKs with that keystore and nothing else.** A new signing key needs registering under Play Console → Android developer verification before anything signed with it goes out.
- **Memory and DEX thresholds, Feb 2027.** Met by construction. The app holds no bitmaps beyond the launcher icon and its heap is one activity's worth, so the memory thresholds aren't reachable. The DEX rule wants at least 25% coverage from a shrinker, which R8 already exceeds. Keeping `isMinifyEnabled` on is now a publishing requirement, not only a size lever.
- **Zero-Tap Sign-In, Apr 2027.** Not applicable: it binds apps that support user sign-in, and this one has no accounts.

Nothing here is visible through the Play Developer API. Verification status, policy warnings, and
review outcomes are Console-only, so the API can confirm *what* is published but never *whether
Google is happy about it*. The `androidpublisher` v3 discovery document has no verification
resource at all; `appsigning` is Play App Signing, a different mechanism.

Driving the Console with browser automation went badly enough to be worth warning about. Its SPA
takes 20+ seconds to hydrate, screenshots came back stale and disagreed with the live DOM, clicks
by coordinate and by element reference both did nothing silently, and `Runtime.evaluate` hit
45-second renderer timeouts. Worse, setting a form field programmatically updates the visible text
and even enables the submit button while Angular's form model stays empty, so the submit fails
validation and **looks like it worked**. Check the resulting state, never the button click.

## Releasing

1. Bump `versionCode` (Play rejects a reused one) and `versionName` in `build.gradle.kts`. Log it in [`docs/play-listing.md`](docs/play-listing.md).
2. `./scripts/check.sh`
3. `./gradlew bundleRelease` → `build/outputs/bundle/release/Signboard-release.aab`, which is what Play takes.
4. Publish, either way round. In Play Console: upload the `.aab` along with any changed assets from [`branding/`](branding/CLAUDE.md). Over the API: open an edit, `POST` the bundle to the `/upload/` host, `PUT` the track with the new `versionCode` and release notes, `:validate`, then `:commit`. Straight to Production; no testing-track sequence is required. See [`docs/play-listing.md`](docs/play-listing.md).
5. `./scripts/play-status.py` to confirm the track picked it up.

A track showing `completed` means its *rollout* is complete, not that review passed. The API
exposes no review status; the public listing returning 200 instead of 404 is the real signal.

Release builds are signed with `~/.android/signboard-release.keystore`. **That key is not in the
repo and cannot be regenerated: losing it means losing the ability to update the app on Play.**

Its passwords come from `SIGNBOARD_KEYSTORE_PASSWORD` and `SIGNBOARD_KEY_PASSWORD`, with no
in-repo fallback, so export them before `assembleRelease` or `bundleRelease`. Without them the
build succeeds and logs a warning, but the APK is **unsigned**, so check the signer rather than
assuming:

```bash
apksigner verify --print-certs build/outputs/apk/release/Signboard-release.apk
```

A correct release build reports `CN=David Veszelovszki` and SHA-256 `43:BD:1F:9B:…`. They belong
in the sops store next to `SIGNBOARD_PLAY_SA_KEY`, which doesn't hold them yet.

This repo is public and the passwords used to be literals in `build.gradle.kts`, so they're in git
history and should be treated as burned. `keytool -storepasswd` and `keytool -keypasswd` rotate
them without touching the key itself, leaving the certificate, its fingerprint, and the Play
registration intact.
