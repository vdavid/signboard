# Play Store listing

What's been submitted, so a future update doesn't have to reinvent it. Copy here is the
source of truth; Play Console is downstream of it.

- **Package**: `com.veszelovszki.signboard`
- **Category**: Utilities
- **Privacy policy URL**: `https://github.com/vdavid/signboard/blob/main/privacy-policy.md`
  (rendered from [`privacy-policy.md`](../privacy-policy.md); the app collects nothing, so the
  policy says exactly that)
- **Content rating**: answered as an ordinary app, no objectionable content

## Copy

Short description, max 80 characters:

> Distraction-free fullscreen text display. Long press to edit.

Full description, max 4000 characters:

> Display text fullscreen. That's it.
>
> Perfect for:
> - **Displaying notes** or prompts on a big screen
> - **Teleprompter usage** for presentations
> - **Quick reference** text you need visible
> - **Text-only focus** when you need zero distractions
>
> How it works:
> - **Display**: Text fills the screen with auto-sizing. Really fills it—handles portrait and landscape, notches, all that.
> - **Edit**: Long press anywhere to open the editor. Change text, toggle dark/light mode, access recent edits.
> - **Stays on**: Screen won't sleep while the app is open.
>
> Features:
> - Full-screen auto-sized text
> - White-on-black and black-on-white modes
> - Quick access to your last 5 edits
> - Handles notches and camera holes gracefully
> - Screen stays on while viewing
> - All text stored locally on your device (zero internet, zero tracking)
>
> That's all. No fluff, no permissions beyond what's needed, no ads. Just fullscreen text.

Release notes per version go in Play Console directly; keep them to what a user would notice.

## Assets

All generated, all checked in. See [`branding/CLAUDE.md`](../branding/CLAUDE.md) for how to
regenerate any of them.

- App icon: `branding/icon-512.png` (512x512, 32-bit PNG, full-bleed, no rounded corners — Play applies its own mask)
- Feature graphic: `branding/feature-graphic-1024x500.png`
- Phone screenshots: `branding/phone-*.png`, including `phone-portrait-dialog.png` showing the edit dialog
- 7" tablet: `branding/tablet7-*.png`
- 10" tablet: `branding/tablet10-*.png`

Each device/orientation exists in both themes (`-inverted` is black-on-white).

Known gap: Play's promotional placements want phone screenshots at 9:16, and these are 20:9
because that's a real phone's aspect. Play accepts them, they just aren't eligible for some
promo slots. Letterboxing to 1080x1920 would fix it if that ever matters.

## Version history

Play rejects a reused `versionCode`, and codes only ever go up.

- 3 — uploaded to Internal testing
- 5 — current; 1.2. No dependencies, ~29 KB APK, per-line cutout handling

Version 4 was built but never uploaded.
