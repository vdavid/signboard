# Play Store listing

What's been submitted, so a future update doesn't have to reinvent it. Copy here is the
source of truth; Play Console is downstream of it.

- **Package**: `com.veszelovszki.signboard`
- **Category**: Tools
- **Privacy policy URL**: `https://github.com/vdavid/signboard/blob/main/privacy-policy.md`
  (rendered from [`privacy-policy.md`](../privacy-policy.md); the app collects nothing, so the
  policy says exactly that)
- **Content rating**: answered as an ordinary app, no objectionable content

## Copy

The copy is in [`fastlane/metadata/android/<locale>/`](../fastlane/metadata/android): `title.txt`,
`short_description.txt`, and `full_description.txt`, in English plus Hungarian, Swedish, German,
Spanish, French, Brazilian Portuguese, and Japanese. Those files are the source of truth. Play
Console is downstream of them and gets edited by hand; F-Droid reads them at build time.

- Play's short descriptions end with a period; the files don't, because F-Droid's lint rejects a trailing one.
- David wrote the English full description and approved the title and short description. Claude wrote the translations; David can check the Hungarian and Swedish.
- **Keep "no ads", "free", and similar out of the short description.** Play Console flags "keywords that indicate price or promotion" there, and a flagged listing isn't eligible for promotion on Play. The full description can say it.
- This copy went to review on 2026-11-13 together with versionCode 7, replacing the listing David wrote at launch (title "Signboard", short description "Displays a full screen text on your phone. Long-press to edit.").
- Play Console's listing editor has an "AI asset declaration" step. It covers images and videos, and all of Signboard's are real emulator captures or script-drawn, so the answer is "Don't label assets".

Release notes per version go in Play Console directly; keep them to what a user would notice.

## Assets

All generated, all checked in. See [`branding/CLAUDE.md`](../branding/CLAUDE.md) for how to
regenerate any of them.

`fastlane/metadata/android/en-US/images/` holds copies of the icon, the feature graphic, and five
phone screenshots for F-Droid. After regenerating anything in `branding/`, copy it over again.

- App icon: `branding/icon-512.png` (512x512, 32-bit PNG, full-bleed, no rounded corners — Play applies its own mask)
- Feature graphic: `branding/feature-graphic-1024x500.png`
- Phone screenshots: `branding/phone-*.png`, including `phone-portrait-dialog.png` showing the edit dialog
- 7" tablet: `branding/tablet7-*.png`
- 10" tablet: `branding/tablet10-*.png`

Each device/orientation exists in both themes (`-inverted` is black-on-white).

Known gap: Play's promotional placements want phone screenshots at 9:16, and these are 20:9
because that's a real phone's aspect. Play accepts them, they just aren't eligible for some
promo slots. Letterboxing to 1080x1920 would fix it if that ever matters.

## Checking status

`./scripts/play-status.py` prints what's on each track, straight from the Play Developer API.
No need to open the Console for "what's live where".

## Version history

Play rejects a reused `versionCode`, and codes only ever go up. Uploaded bundles: 2, 3, 4, 5, 6, 7.

- 3 — Internal testing, `completed`
- 4 — Production, `completed`
- 5 — Production, `completed`; 1.2. No dependencies, ~29 KB APK, per-line cutout handling. First version to actually reach the public listing: approved and live 2026-07-30, nine days after submission.
- 6 — Production, `completed`; 1.3. `targetSdk` 37 (Android 17), to meet Play's Aug 31, 2026 requirement. Uploaded and rolled out entirely over the API on 2026-07-30, live on the listing 2026-08-02. Review took three days, against nine for the account's first app. Also published as GitHub Release `v1.3` on 2026-08-27, the first release-signed APK on that channel; v1.0 and v1.1 carry debug-signed ones.
- 7 — Production and internal testing, `completed`; 1.4. No app changes: MIT license, F-Droid listing metadata, AGP 9.5.1 / Gradle 9.9.1. Uploaded with `scripts/play-release.py` on 2026-11-13 from the bundle CI attached to GitHub Release `v1.4`, so both channels carry the same build. Replaced a stale 1.1 draft (versionCode 3) that was still sitting on the production track.

## Publishing

No testing-track sequence is required. The "20 testers for 14 days" rule Google applies to new
personal developer accounts is a one-time gate before *first* production access, and this app is
past it: releases go straight to the Production track.

Each production release still goes through review, typically hours to a couple of days.

A track release showing `completed` means its **rollout** is complete, not that review has passed.
The API has no review-status field at all, and rejection reasons and policy warnings are
Console-only. The usable public signal is the store listing itself:

```bash
curl -sL -o /dev/null -w '%{http_code}\n' \
  'https://play.google.com/store/apps/details?id=com.veszelovszki.signboard&hl=en&gl=US'
```

404 while the app has never been published publicly, 200 once it is.

The first publication took **nine days** (submitted 2026-07-21, live 2026-07-30), with no approval
email — the listing simply started resolving. A brand-new developer account's first app is the
slowest path Google has, since they review the developer alongside the app. Later updates are much
quicker.

## Target API level

Play enforces a target API floor that rises annually, and it does so by refusing *updates*: a live
app keeps working and installed users are unaffected, but you can't ship anything new. Falling
behind earns an "[Action required] Your app is affected by Google Play's target API level
requirements" email.

As of the Aug 31, 2026 deadline the floor is **API 36** for new apps and updates (API 35 for an
existing app to stay available to new users on newer OS). Extensions to Nov 1, 2026 can be requested
in Play Console. The email advises "target the latest", which is stronger than the actual policy.

This app targets **37** rather than the required 36: the behavior change that actually affects it,
mandatory edge-to-edge, lands at 36 either way, so the testing effort is identical and 37 buys a
year instead of weeks. `targetSdk` doesn't affect device compatibility, only `minSdk` does.
