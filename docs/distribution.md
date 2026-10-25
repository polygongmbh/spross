# Distribution — getting builds onto other people's phones
How builds reach other people's phones: the pipeline, signing, and each platform's install route.
Neighbors: cutting a release and the changelog `release.md`; the install steps a learner follows `../README.md` § Install.

One GitHub Actions run, `.github/workflows/release.yml`, builds every route from the tag.
Android is in no store:
the APK is attached to the GitHub release, and Obtainium installs and updates it.
iPhone reaches the App Store and TestFlight from one App Store Connect upload,
and registered devices also get an ad-hoc build over the air.

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
| `APPLE_DISTRIBUTION_P12` | the team's Apple Distribution certificate with its private key, `.p12`, base64 |
| `APPLE_DISTRIBUTION_P12_PASSWORD` | the password the `.p12` was exported with |

The first three are enough to ship Android:
the release job waits for the iPhone build but does not depend on it, so an Apple credential that is missing or expired costs the IPA and its install manifest, not the release.

`scripts/release-keystore.sh <dir>` creates the Android key wherever you keep key files and writes the first three into `<dir>/github-secrets.txt` ready to paste.
The App Store Connect key is generated in App Store Connect › Users and Access › Integrations with the **App Manager** role, downloadable exactly once;
`base64 -i AuthKey_XXX.p8 | pbcopy` turns it into the secret.

The distribution certificate is created once by an Admin or the Account Holder:
Xcode › Settings › Accounts › the team › Manage Certificates › `+` › Apple Distribution.
In Keychain Access, under My Certificates, the "Apple Distribution: …" entry with its key exports as `.p12`;
`base64 -i dist.p12 | pbcopy` turns it into the secret.
Without it in the runner's keychain, the export falls back to Apple's cloud-managed certificate, which an App Manager key is refused with "Cloud signing permission error".
It expires after a year; a renewed one replaces both secrets.

**The Android key is unrepeatable.**
Android pins an app's signature:
a differently-signed APK is a different app to every device that already has this one, with uninstall as the only path across.
Back the keystore up off the machine.

## Signing a release APK locally

The environment names the key —
the same three variables CI sets, so there is one route in and a locally cut APK installs over a released one:

```sh
. ~/keys/spross/release.env      # what release-keystore.sh wrote
./gradlew :android:assembleRelease
```

Without `SPROSS_KEYSTORE` the packaging task fails and names what is missing, rather than leaving an `android-release-unsigned.apk` behind:
Android's installer rejects an unsigned APK outright —
there is no unknown-sources toggle for it, and every APK on every device, Play Store ones included, carries a self-signed key.
Debug builds and the test gates are unaffected; the demand lands on `packageRelease` alone.

## The app id, and when it stops being free

`net.spross.app`, the same string on both platforms —
the reversed domain plus the app-name slot, which is what every cross-platform toolchain seeds into both anyway.
What binds it, on each side:

- **Android** — the first installed release APK.
  The `applicationId` *is* the app's identity there, so a renamed one is a second, unrelated app:
  installed alongside the first, unable to reach its data directory, and invisible to Obtainium until every user re-adds it by hand.
  No store publication is needed for this to bind.
- **iOS** — the App Store Connect record, which exists, so the id is bound.
  Moving that one alone would also break the match and orphan `group.net.spross.data`, the container the widget and watch share in a release build (`project.yml` `APP_GROUP_ID`; debug uses its own group, `group.net.spross.dev`, under the same polygon team).

## Challenge links — the site's half

A shared numbers challenge is `https://spross.net/c?<code>`, and it opens the app only where spross.net vouches for it.
`scripts/build-web.sh` ships `web/.well-known/` with the site:
`apple-app-site-association` names both iOS app ids under the team,
and `assetlinks.json` has to name `net.spross.app` with the release key's SHA-256 (`keytool -list -v -keystore "$SPROSS_KEYSTORE"`).
Until a file answers at `https://spross.net/.well-known/…`, the link opens `web/c.html`, whose button opens the app through `spross://challenge/<code>`.

## Impressum and privacy policy — the gate before anyone outside sees a build

Both live in the app itself, on the About screen each phone reaches from Box settings:
the provider identification § 5 DDG asks a German company for, and a link to `https://spross.net/privacy`, which App Review wants reachable in-app as well as in App Store Connect.
Neither is optional once a build leaves the team.

The Impressum carries the registry facts themselves —
company, address, managing director, register entry, VAT id —
in `App/Sources/Resources/Localizable.xcstrings` under `legal.*`.
They are the same values the DSA trader declaration publishes on the App Store product page, so the two say one thing or they contradict each other.

The page is `web/privacy.html`, served at `https://spross.net/privacy`;
App Store Connect demands the same URL for the listing and for external TestFlight testers.

## The privacy manifest

Each shipped bundle carries its own `PrivacyInfo.xcprivacy` at its root —
app, widget, watch app, watch complication —
because App Store Connect scans every binary in the package and the app's manifest does not speak for the ones beside it.
Only the app reaches a required-reason API, `UserDefaults`.
Reaching for another one —
a file timestamp, free disk space, the boot time, the active keyboards —
obliges the manifest of the bundle that calls it to name the category and a reason code, or the upload comes back as ITMS-91053.

## Android — Obtainium

Add `https://github.com/polygongmbh/spross` as a GitHub app in [Obtainium](https://github.com/ImranR98/Obtainium);
it picks `spross-<version>.apk` off each release and offers the update.
The repo being public is what makes this work without a token.
Direct download from the release page installs the same file.

The version at the foot of the box carries that link —
the build a learner is running is what an update is about, so the version is the control rather than a button beside it.
It fires `obtainium://add/<repo url>`, which lands on Obtainium's prefilled Add-App screen, and offers the choice between Obtainium and a direct download when nothing answers the scheme.
The app checks for nothing itself —
it declares no `INTERNET` permission and hands every URL to another app.

Obtainium reads the tag as the version and reconciles it against the APK's `versionName`, which is why the workflow derives one from the other.
A tag whose shape stops matching the name would leave it unable to tell the two apart, and it disables update detection for the app rather than guessing.

## iPhone — ad-hoc

Ad-hoc builds run only on devices registered in the Apple Developer portal **before the build**, and the pipeline needs the paid Developer Program:
a free personal team cannot issue the distribution certificate, and the export step is where that shows up.

Signing is automatic —
`-allowProvisioningUpdates` with the API key lets Xcode mint the profile covering the app, the widget and both watch targets against the distribution certificate the workflow imports, so no profile is kept as a secret.
`scripts/ExportOptions.plist` holds the export settings.

Adding a tester means registering their device, not signing them up for anything —
they never need an Apple developer account.
Three steps:

1. **Get the UDID.**
   Settings does not show it.
   On a Mac with the phone plugged in:
   Finder's sidebar, then click the line under the device name until it reads UDID, right-click to copy —
   or Xcode › Window › Devices and Simulators, field "Identifier".
   For a tester you cannot plug in, a UDID-capture page installs a configuration profile and reads it back, at the cost of handing a stranger the device identity.
2. **Register it** at developer.apple.com › Devices › `+`.
   A device your own Mac builds to is registered by Xcode on the spot.
   The cap is 100 iPhones per membership year, and the list can only be pruned at renewal.
3. **Re-run the tag's workflow.**
   The profile is minted per build, so a device added after a build is not in that build's IPA.

Install goes through the `itms-services://` link in the release notes, opened in Safari on the device.
It points at `manifest.plist`, an asset of the same release naming the IPA's URL.
GitHub strips the scheme from rendered links, so the link is code to copy, not tap.

The build expires when its profile does —
a year at most, and immediately if the certificate is revoked.

## iPhone — TestFlight

The same tag also re-signs the same archive and hands it to App Store Connect, which is the way past both the UDID list and the 100-device cap.
The two audiences run on separate clocks:

- **Internal testers** — up to 100 people holding a role on the App Store Connect team.
  No Beta App Review: the build is installable minutes after processing.
- **External testers** — up to 10 000, reachable by a public link, no UDID and no team role.
  The first build of a version needs Beta App Review, historically about a day and lately often several.
  Submitting is a step in the App Store Connect UI, not something the tag does.

A build queued for external review is already live for internal testers, so there is no reason to wait on the review before testing.
Builds expire after 90 days.

The upload needs an app record to exist in App Store Connect, and it is the step that freezes the bundle id.
It is also the one step allowed to fail without taking the release down —
the GitHub release is the delivery that must not depend on Apple being up, so a failed upload shows as a warning on the run and the APK and IPA publish anyway.
`CFBundleVersion` is the workflow's run number, which never repeats, because App Store Connect rejects a build number it has already seen for a version.

## iPhone — App Store

Spross is listed at `https://apps.apple.com/app/id6810577722`, Apple Watch app included.
A release reaches it from the same App Store Connect build TestFlight serves, submitted for App Review in the App Store Connect UI.

## What the runners do

`ubuntu-latest` builds the APK behind the text lints, `:kern:jvmTest` and `:android:testDebugUnitTest`.
`macos-26` pins Xcode 26.6 — the image's default moves —
generates the project with `xcodegen` (`.xcodeproj` is never committed), caches `~/.konan` against the Kotlin version, then archives and exports.
macOS minutes bill at ten times the Linux rate, which is why only a tag starts one.
