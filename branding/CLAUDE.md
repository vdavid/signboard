# Branding assets

Everything Play Console asks for, plus the launcher icons. Nothing here ships inside the APK
except the generated `src/main/res/mipmap-*/ic_launcher.png` files.

## Files

Both scripts cd to the repo root themselves, so run them from anywhere. Every PNG here is
generated; don't hand-edit any of them.

- `generate-icons.sh`: the Play Store icon **and** all launcher mipmaps.
- `capture-screenshots.sh`: every screenshot, plus the feature graphic derived from one of them.
- `icon-512.png`: Play Store listing icon.
- `feature-graphic-1024x500.png`: Play Store feature graphic.
- `<device>-<orientation>.png`: white-on-black screenshots.
- `<device>-<orientation>-inverted.png`: the same shots in the app's black-on-white theme.

Devices and their native sizes: `phone` 1080x2400 (Pixel 8), `tablet7` 1200x1920 (Nexus 7 2013),
`tablet10` 1600x2560 (Nexus 10). Landscape variants are those transposed.

Everything is checked in, so updating the Play listing never requires re-running anything.

## Icons

Run `./branding/generate-icons.sh`. It writes `branding/icon-512.png` and every
`src/main/res/mipmap-*/ic_launcher.png`. One script, one design, all sizes: there's no
separate "old icon" recipe to keep around.

Design: black top half with "abc" in white, white bottom half with "ABC" in black. Each word's
ink box is centered on the 25% / 75% horizontal line, so each word sits centered inside its own half.

Two things in the script are load-bearing, and both are easy to break:

- **Center by ink bounds, not by `-annotate` offsets.** ImageMagick positions text by the font's baseline and em box, which for "abc" (no descenders, one ascender) sits nowhere near the visual middle. The script renders each word onto a transparent canvas, `-trim`s to the actual ink, measures it, and composites at a computed offset. Going back to plain `-gravity North -annotate +0+N` produces text that looks bottom- or top-aligned within its half.
- **Text width is capped at 60% of the icon.** Google Play masks the icon with a corner radius equal to 30% of the icon size. Wider text starts to graze the mask. `TEXT_WIDTH_RATIO` at the top of the script controls this.

Play Store icon spec (`developer.android.com/distribute/google-play/resources/icon-design-specifications`):
512x512, 32-bit PNG, sRGB, full square, **no** rounded corners, **no** drop shadow. Play adds the
radius and shadow itself, so baking either one in double-applies it. The script emits `PNG32:` in
sRGB with a flattened opaque background to satisfy this.

To preview what Play actually renders:

```bash
magick -size 512x512 xc:black -fill white -draw "roundrectangle 0,0,511,511,154,154" -alpha off /tmp/mask.png
magick branding/icon-512.png /tmp/mask.png -alpha off -compose CopyOpacity -composite PNG32:/tmp/icon-rounded.png
magick -size 560x560 xc:'#9e9e9e' /tmp/icon-rounded.png -gravity center -composite PNG32:/tmp/preview.png
```

Always look at the output before shipping. The failure modes here are all visual; nothing errors out.

## Screenshots and feature graphic

`./branding/capture-screenshots.sh` regenerates all of it: creates the AVDs if missing, boots each
one headless, installs the current release APK, sets the displayed text, captures every orientation
that device owns, paints out the nav pill, and derives the feature graphic. It ends with
`adb kill-server` so a physical device is left free afterwards.

Pass an output basename to redo just one, e.g. `./branding/capture-screenshots.sh tablet7-landscape`.

Needs a current `build/outputs/apk/release/Signboard-release.apk`; run `gradle assembleRelease` first
if the app changed.

Everything comes off emulators, including the phone shots. Using a real phone works but drags in
that device's notch, status bar, and rotation state, and leaves the result unreproducible.

### What gets captured

The `SHOTS` table at the top of the script is the source of truth: one row per output file, giving
the device, orientation, theme, mode, and displayed text. Thirteen shots: every device and
orientation in both themes, plus one with the edit dialog open.

Each shot uses a different emoji pair so the listing images don't look copy-pasted. White-on-black
gets 🍆 🦒 🐧 🐘 🥦 🦖🦕, black-on-white gets 🦄 🥑 🫎 🍄‍🟫 🍩 🐙.

One shot has `mode` set to `dialog`: the script long-presses the screen first, so the edit dialog is
open and the history list is visible. Dialog shots take their history entries from `history_for()`
rather than echoing their own text, and they skip nav-pill erasure (the dialog dims the whole
background, so painting that strip pure black would leave a visibly darker patch).

To change an emoji, add a shot, or flip a theme, edit that table and nothing else. Rows are grouped
by device at runtime, so adding a shot to a device already in the list costs seconds rather than
another ~30s boot. Within a device, keep same-orientation rows adjacent: re-seeding the text is
free, rotating is not.

### Feature graphic

Play wants exactly 1024x500. The script letterboxes `phone-landscape` (the white-on-black one, set
by `FEATURE_SOURCE`) into that frame: `-resize 1024x460` fits the whole screenshot, `-extent
1024x500` pads the rest with black, which blends into the app's own background. **Don't crop to
1024x500**: cropping eats the text.

Pointing `FEATURE_SOURCE` at an `-inverted` shot would need the padding color flipped to white too.

### Things that will bite you

- **Use `google_apis`, not `google_apis_playstore`.** `adb root` is blocked on Play-enabled images, and setting the text needs root.
- **Set the text by writing SharedPreferences, not by driving the UI.** `adb shell input text` can't type emoji, and the app's text is only settable through its edit dialog. The script launches the app once so Android creates `shared_prefs/`, force-stops it, then copies a prepared `signboard.xml` in. The `chown` after the copy is required: a root-owned prefs file is unreadable by the app, which then silently falls back to its default text rather than failing loudly.
- **`avdmanager create` prints a "Could not load devices.xml" error that is harmless.** The device profile still applies. Confirm with `grep hw.lcd ~/.android/avd/<name>.avd/config.ini` rather than trusting the error.
- **Rotate only while the app is in the foreground.** The display honors a user rotation only if the foreground activity permits that orientation, and the phone launcher is locked to portrait. Rotating from the home screen therefore does nothing at all on the phone, with no error anywhere. Tablet launchers allow both orientations, so this failure looks like "only the phone is broken." `capture_oriented` starts Signboard first, then rotates.
- **Use `wm user-rotation lock`, not the `user_rotation` setting.** The `wm` lock takes precedence, so if anything ever set one (an earlier run, a debugging session) writes to the setting are silently ignored forever after. `boot_avd` clears any stale lock with `wm user-rotation free` before starting.
- **Rotation is asynchronous and natural orientation is per-device.** It can take several seconds, and a fixed sleep followed by a screencap captures the pre-rotation frame and fails *silently*: you get a plausible file in the wrong orientation. The script polls `dumpsys window displays` until the window manager reports the requested aspect, tries both rotation values since devices differ in what's "natural", and cross-checks the captured PNG's dimensions against the display's.
- **The nav pill fill has to match the theme.** `erase_nav_pill` paints the bottom edge with the shot's own background color. Filling black on a black-on-white shot leaves an obvious bar.
- **Look at the output.** Pill erasure is a blind `NAV_PILL_FRACTION` slice of the bottom edge, and nothing errors if it misses the pill or eats into the text.

Emulator boots are ~30s each on Apple silicon. A first run also downloads ~4 GB of emulator and
system image.
