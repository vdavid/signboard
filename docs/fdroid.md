# Getting Signboard onto F-Droid

Not submitted yet. This is the plan, the build recipe, and the merge request text, so the
submission is a paste job once the open items are settled.

F-Droid takes new apps as a merge request against [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata),
adding one file: `metadata/com.veszelovszki.signboard.yml`. F-Droid then builds the app from
source on its own server. Guides: [quick start](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
[`CONTRIBUTING.md`](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md),
[inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/).

## Where it stands

Done:

- `LICENSE` (MIT) is in the repo.
- Listing metadata is in `fastlane/metadata/android/` (`en-US` plus seven translations). F-Droid requires `en-US` and reads all of it from the built commit.
- The app has no dependencies, no network use, and no proprietary build tools, so nothing in the inclusion policy needs a workaround. It's older than the three months the policy asks for (first commit 2026-07-21) and has users on Play.

Open, in order:

1. **Release v1.4 (versionCode 7).** The `v1.3` tag predates the license and the metadata, and F-Droid reads both from the commit it builds. A reproducible build also needs the GitHub Release APK to come from the exact commit F-Droid builds. Play gets the same version, as always.
2. Fork `fdroiddata`, add the recipe on a branch named `com.veszelovszki.signboard`, wait for the fork's pipeline to pass, then open the merge request titled `New app: Signboard`.

## Reproducible builds

F-Droid's merge request template asks for them, and asks for a reason if they're off.

- **On**: F-Droid builds the app, checks that the result is byte-identical to the APK on GitHub Releases, and then ships *that* APK, with David's signature (`43:BD:1F:9B:…`). F-Droid and GitHub installs can update into each other. If a build ever fails to reproduce, F-Droid skips that version until it's fixed.
- **Off**: F-Droid signs with its own key. Less to go wrong, but it's a third signature next to Play's and GitHub's, and it can't be changed later.

David chose on (2026-11-12). Signboard is a good candidate: Kotlin only, no native code, no
dependencies. The fork's pipeline is where we find out whether the build reproduces.

## The recipe

`metadata/com.veszelovszki.signboard.yml`, for v1.4. Fill in `commit` with the full hash of the
`v1.4` tag.

```yaml
Categories:
  - Writing
License: MIT
AuthorName: David Veszelovszki
AuthorEmail: david@veszelovszki.com
AuthorWebSite: https://veszelovszki.com
SourceCode: https://github.com/vdavid/signboard
IssueTracker: https://github.com/vdavid/signboard/issues
Changelog: https://github.com/vdavid/signboard/releases

AutoName: Signboard

RepoType: git
Repo: https://github.com/vdavid/signboard.git
Binaries: https://github.com/vdavid/signboard/releases/download/v%v/Signboard-release.apk

Builds:
  - versionName: '1.4'
    versionCode: 7
    commit: <full hash of the v1.4 tag>
    gradle:
      - yes

AllowedAPKSigningKeys: <SHA-256 of the upload certificate, lowercase hex, no colons>

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '1.4'
CurrentVersionCode: 7
```

Notes on it:

- No `subdir`: the app module is the repo root.
- `AllowedAPKSigningKeys` is the fingerprint `apksigner verify --print-certs` prints for the GitHub Release APK (it starts `43bd1f9b`).
- `Writing` is the nearest of F-Droid's categories; none fits a big-text display well. Reviewers may pick another.
- The build pins `jvmToolchain(21)`. If F-Droid's build server doesn't have JDK 21 on hand, the pipeline fails with a toolchain error, and the usual fix is a `sudo:` block that installs `openjdk-21-jdk-headless`.
- With no keystore present, `build.gradle.kts` produces `Signboard-release-unsigned.apk`, which is what F-Droid expects to find.
- For reproducible builds off, drop `Binaries` and `AllowedAPKSigningKeys`.

## Merge request text

David's text, as he edited it on 2026-11-13. Use it as is. Title: `New app: Signboard`. The
rest of F-Droid's template (the checklist) stays as it is, with the boxes ticked.

> ### Please introduce the functions and usage of your app in summary, and also attach some screenshots.
>
> Signboard shows the text you type in full screen. You can long-press anywhere to edit it. That's the whole app. It's 30 kB.
>
> It also handles rotation, flows text around camera holes, keeps the screen on while it's open, has a white-on-black / black-on-white toggle, and remembers your last 5 texts.
>
> Screenshots are in the repo: https://github.com/vdavid/signboard/tree/main/fastlane/metadata/android/en-US/images/phoneScreenshots
>
> ### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.
>
> I kept needing to show something to someone: my address to old post office worker ladies, my 4yo's name to show her how to spell it, my phone number to girls at disco. All similar apps have ads and LED effects and all that crap. So I made this simple one with no deps, no perms, no network, etc.
>
> I hope this helps others, too.

## After it's merged

- New versions arrive by themselves: F-Droid watches the tags (`UpdateCheckMode: Tags`). Each one needs its `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
- It takes a day or two from merge to showing up in the F-Droid client.
- IzzyOnDroid is a faster, lighter alternative repo that takes the GitHub Release APK as is. Not pursued; F-Droid's main repo reaches more people.
