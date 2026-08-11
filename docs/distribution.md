# Distribution — getting builds onto other people's phones

Neither store is involved.
Android installs the APK directly and tracks updates through Obtainium;
iPhone installs an ad-hoc build over the air, which reaches registered devices only.
Both come out of one GitHub Actions run, `.github/workflows/release.yml`.

## Cutting a release

```sh
# CHANGELOG.md: rename '## Unreleased' to '## <version> — <date>', open a fresh Unreleased
# project.yml:  MARKETING_VERSION to the same version
git commit -am "chore: release 4.1.0"
git tag v4.1.0 && git push origin main v4.1.0
```

The tag is the trigger and the version:
the workflow strips the leading `v` and hands the rest to both surfaces,
so nothing is bumped twice.
`MARKETING_VERSION` in `project.yml` is what a local build shows —
Gradle reads it too rather than keeping a second number,
and `scripts/release-notes.sh` fails the run when the changelog has no section
under that heading.

Android's `versionCode` is derived from the name — `4.1.2` → `40102` —
so it rises on its own as long as minor and patch stay under 100.

## Secrets

Set once, in Settings › Secrets and variables › Actions.

| Secret | What it is |
| --- | --- |
| `SPROSS_KEYSTORE_BASE64` | the release keystore, base64 |
| `SPROSS_KEYSTORE_PASSWORD` | its password (key and store share one) |
| `SPROSS_KEY_ALIAS` | `spross` |
| `APPSTORE_API_PRIVATE_KEY` | App Store Connect API key `.p8`, base64 |
| `APPSTORE_API_KEY_ID` | that key's ID |
| `APPSTORE_API_ISSUER_ID` | the issuer UUID, one per Apple team |

`scripts/release-keystore.sh <dir>` creates the Android key wherever you keep key
files and writes the first three into `<dir>/github-secrets.txt` ready to paste.
The App Store Connect key is generated in App Store Connect › Users and Access › Integrations
with the **App Manager** role, downloadable exactly once;
`base64 -i AuthKey_XXX.p8 | pbcopy` turns it into the secret.

**The Android key is unrepeatable.** Android pins an app's signature: a differently-signed
APK is a different app to every device that already has this one, with uninstall as the only
path across. Back the keystore up off the machine.

## Signing a release APK locally

The environment names the key — the same three variables CI sets, so there is one route
in and a locally cut APK installs over a released one:

```sh
. ~/keys/spross/release.env      # what release-keystore.sh wrote
./gradlew :android:assembleRelease
```

Without `SPROSS_KEYSTORE` the packaging task fails and names what is missing, rather than
leaving an `android-release-unsigned.apk` behind: Android's installer rejects an unsigned
APK outright — there is no unknown-sources toggle for it, and every APK on every device,
Play Store ones included, carries a self-signed key. Debug builds and the test gates are
unaffected; the demand lands on `packageRelease` alone.

## Android — Obtainium

Add `https://github.com/polygongmbh/spross` as a GitHub app in
[Obtainium](https://github.com/ImranR98/Obtainium); it picks `spross-<version>.apk`
off each release and offers the update. The repo being public is what makes this
work without a token. Direct download from the release page installs the same file.

## iPhone — ad-hoc

Ad-hoc builds run only on devices registered in the Apple Developer portal
**before the build**, and the pipeline needs the paid Developer Program:
a free personal team cannot issue the distribution certificate, and the export step
is where that shows up.

Signing is automatic — `-allowProvisioningUpdates` with the API key lets Xcode mint
the profile covering the app, the widget and both watch targets, so no certificate or
profile is kept as a secret. `scripts/ExportOptions.plist` holds the export settings.

Adding a tester: register the device UDID in the portal, then re-run the tag's workflow.
The profile is minted per build, so a device added afterwards is not in the released IPA.

Install goes through the `itms-services://` link in the release notes, opened in Safari
on the device. It points at `manifest.plist`, an asset of the same release naming the IPA's
URL. GitHub strips the scheme from rendered links, so the link is code to copy, not tap.

The build expires when its profile does — a year at most, and immediately if the
certificate is revoked. TestFlight is the way out of that, and out of the UDID list;
it costs App Store Connect review of the first build.

## What the runners do

`ubuntu-latest` builds the APK behind `:kern:jvmTest` and `:android:testDebugUnitTest`.
`macos-15` pins Xcode 16.4 — the image's default moves — generates the project with
`xcodegen` (`.xcodeproj` is never committed), caches `~/.konan` against the Kotlin
version, then archives and exports. macOS minutes bill at ten times the Linux rate,
which is why only a tag starts one.
