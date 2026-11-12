# Signboard

A minimal Android app that displays text on your screen. Long-press to edit, toggle dark/light mode, and keep recent entries for quick access.

## Features

- **Full-screen display**: Text scales automatically to fit your screen, both axes, in portrait and landscape
- **Invert mode**: Toggle between black text on white and white text on black
- **Edit dialog**: Long-press the screen to open a text editor with multiline support
- **Recent texts**: Quick access to your last 5 entries, tap × to delete any
- **Screen on**: The display stays on while the app is open (like video playback)
- **Notch aware**: Text flows around your camera hole in both orientations
- **Persistent**: Your current text and all settings save automatically

## Install

Get it from [Google Play](https://play.google.com/store/apps/details?id=com.veszelovszki.signboard), or grab the APK from the [latest release](https://github.com/vdavid/signboard/releases/latest).

Or build it yourself:

```bash
./gradlew assembleDebug
adb install build/outputs/apk/debug/Signboard-debug.apk
```

## Usage

1. Open the app to see your saved text centered on screen
2. Long-press anywhere to edit
3. In the editor: toggle "Invert" for dark/light mode, type your text, select from recent entries to populate
4. Press OK to save and close

Text respects newlines: they're preserved and displayed, but the app won't add extra line breaks. If text doesn't fit, the font shrinks to make room.

## Tech

- Kotlin + Android framework
- No dependencies at all
- ~30 KB release APK
- Runs on Android 8.0+ (API 26+)
- No permissions, no internet, no tracking

## License

[MIT](LICENSE)
