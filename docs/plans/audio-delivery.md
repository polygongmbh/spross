# Audio delivery: one install carries only the pair's recordings

Backlog § Platform reach, "Audio ships un-thinned".
Measured 2026-09-24 off the tree, the last debug APK (2026-09-23) and the last Release `Spross.app` in DerivedData.

## 1. Measure

| pack | files | mp3 | manifest | in the APK (stored) |
|---|---|---|---|---|
| de | 1262 | 26.5 MB | 0.40 MB | 26.9 MB |
| eo | 947 | 19.8 | 0.31 | 20.1 |
| es | 888 | 19.0 | 0.30 | 19.3 |
| fr | 949 | 19.0 | 0.31 | 19.3 |
| it | 857 | 16.2 | 0.29 | 16.5 |
| sw | 821 | 14.1 | 0.26 | 14.4 |
| uk | 898 | 22.7 | 0.31 | 23.0 |
| **all** | **6622** | **137.3** | **2.2** | **139.5** |

- The format mix is 100% mp3: no wav or ogg lives under `catalog/audio/`.
  The `wav` in `noCompress` is the four cue chimes (`App/Resources/Sounds`, 336 KB).
- Android stores every mp3 uncompressed (`android/build.gradle.kts:96`),
  because `openFd` answers only for a stored entry.
  Deflating would save 5.7% (de: 26.5 → 25.0 MB) and break `openFd`, so this is not a lever.
- **Today, per install:** Android debug APK 155 MB, of which 139.5 MB is audio;
  iOS Release `.app` 170 MB on disk, of which 145 MB is `catalog/audio` (block-rounded).
  The backlog's "129 MB" is stale.
- **Per pair:** `en` ships no pack, so an en→X learner needs X alone, 14.4–26.9 MB
  (en→sw saves 125 MB).
  X→Y needs both, 30.9 MB (it+sw) to 49.9 MB (de+uk), saving 90–109 MB.
- **No cheap win exists before delivery.**
  Nothing is unplayable to strip, and deflate is ruled out above.
  A mono repack recovers 3.76% (`../archive/2026-08-22-audio-format.md`).
  AAC-LC would bring the corpus to ~40% (~56 MB) on both platforms with no delivery machinery,
  but it is our adaptation, and it obliges the following:
  the credits' "Recordings shipped unmodified" line goes,
  and every BY-SA file (2094 of 3597 per `audio-licensing.md` § 6) ships under BY-SA as ours.
  BY 4.0 also asks for the change to be indicated.
  The `sha256` gate demotes to an integrity check over the new bytes.
  Every `gain`/`gainPhone`/`cap`/`lead`/`snr` value is a measurement of the SHIPPED bytes and must be re-measured
  (`scripts/audio/catalog.py` `ANALYSIS`, `scripts/audio/measure.py`).
  AAC priming shifts `lead` by ~48 ms.
  `../archive/2026-08-22-audio-format.md` decided "mp3 stays, delivery first".
  This plan keeps that decision.
  Transcoding stays the fallback if the owner rejects networking (question 1).

## 2. Who needs which audio

- Kern resolves a form to a catalog-relative path (`audio/<lang>/…mp3`) out of `Catalog.audio`.
  That map holds one entry per language whose `manifest.json` the `CatalogSource` returned at load.
  **A language with no manifest is already a supported state** (`en` today):
  `hasRecordings` is false, `AudioCapability` is `VoiceOnly`/`None`, `audible` and `producePrompt` fall to the voice,
  `LetterDrillAvailability` (`:183`) needs a voice, and `audioCredits()` omits the language.
- **Target language:** cards, catalog list, drills, the letter drill (`task.language`) and listening.
- **Source language:** listening says both sides (`listeningOffered`, `AppModel+Queries.swift:240`).
  A de source also plays the bare `de` files.
  So a pair needs **source + target**.
- **Credits** list what `audioCredits()` derives from loaded manifests.
  If the manifest travels with its pack, the screen credits exactly what is on the device.
  That is the existing rule: "can never credit what is not bundled".
- **Language switch:** `switchSource`, `switchTarget` and `swapLanguages` (`AppModel.swift:361–392`) are instant.
  Other targets' boxes persist, so the learner can come back to a pair.
- **Couple mode** is not built (`design.md` § Not yet; `plans/sync.md`).
  Each device fetches for its own pair, so nothing here depends on it.
- **At first launch no pack is required** except where no voice exists:
  Swahili on iOS has no system voice, so an en→sw iPhone is silent until its pack lands.

## 3. iOS options

- **On-Demand Resources.** Rejected.
  - Apple deprecates ODR from iOS 27 in favor of Background Assets ([WWDC26 App Store guide][wwdc26]).
  - Tags per language would need `catalog/audio/<lang>` as separate tagged folder references.
  - Access through `NSBundleResourceRequest` holds only while the request object lives.
    Packs are purgeable once no request holds them.
    The flat `catalogDirectory.appending(path:)` join would stop working.
  - Hosting is App Store/TestFlight only.
    The ad-hoc IPA would need `onDemandResourcesAssetPacksBaseURL` pointing at our own server.
    Without it, the export defaults to `embedOnDemandResourcesAssetPacksInBundle`, which re-embeds everything (`xcodebuild -help`).
    `deploy-devices.sh` installs a bare `.app` through `devicectl` and gets no packs.
- **Background Assets.**
  The Apple-hosted packs need iOS 26 ([WWDC25 325][ba]) against our deployment target of 17.0,
  and are served through App Store Connect, so the ad-hoc IPA cannot use them.
  The self-hosted downloader extension (iOS 16.1+) adds an extension target to fetch before first launch.
  That is a real win only for the sw-on-iOS silence.
- **Self-hosted download into Application Support.** Recommended.
  - A background `URLSession` fetches the file.
  - It behaves the same for ad-hoc, `devicectl`, TestFlight and a future App Store build.
  - It needs no kern change: an overlay `CatalogSource` reads `audio/<lang>/…` from the pack directory, else from the bundle.
    `audioURL(_:)` resolves the same way.
  - App Review 4.2.3(iii) asks a download needed to function at first launch to state its size.
    Recordings are optional wherever a voice exists.

## 4. Android options

- **Play Asset Delivery** (install-time, fast-follow and on-demand packs) needs an AAB published through Play.
  Android ships a sideloaded APK through Obtainium today (`distribution.md` § Android; `release.yml:56`),
  and no pack reaches a sideloaded APK: bundletool local testing is the only non-Play route ([PAD test][pad]).
  PAD is out until there is a Play listing.
  On-demand packs land as files (`AssetPackLocation.assetsPath()`),
  so the file resolver below is what a later PAD swap would reuse.
- App Bundle language splits key on the DEVICE locale, not the learning target. That rules them out too.
- **Self-hosted download into `filesDir`.** Recommended.
  - It needs `android.permission.INTERNET`, which the app pointedly does not declare today.
  - `openRecording` gains a file branch (`ParcelFileDescriptor` → `AssetFileDescriptor`) ahead of `assets.openFd`.
  - `AssetCatalogSource` gains the same overlay as iOS.

## 5. Offline and first run

- **The manifest travels with its pack**, installed last, after every mp3 verifies against the manifest's own `sha256`.
  Until then the language has no manifest, which is exactly `en`'s state today.
  Every surface already degrades to the live voice, or to the source prompt where there is none.
  No per-file "does it exist" check spreads across callers.
- On install: reload the catalog. Audio is outside the fingerprint (`kern/docs/audio.md`), so nothing restamps.
  Then refresh `anyWordAudible`, the trainer content and letter-drill availability, as `switchSource` already does.
- **Letter drill dictation:** without a pack, availability needs a voice.
  uk, eo, es, fr, it and de letters on a device with a voice dictate by voice. sw ships no letters section.
  A pack arriving mid-drill takes effect at the next composition, not in the running task.
- **Existing gap to close first:** iOS `PronunciationPlayer.play` returns silently when `AVAudioFile` cannot open the URL.
  It neither speaks the voice nor calls `onFinish`, so a listening run stalls.
  Android's `openRecording` falls through to the voice.
  A purged or half-written pack would hit this.
- Fetch triggers: onboarding completion, `activate` (every language change), and foreground.
  A failed fetch retries on the next trigger, with no UI beyond an optional status row (question 3).
- The `release.yml` IPA/APK stay self-contained only if the owner picks bundled release variants (question 2).

## 6. Recommendation

Self-hosted, content-addressed per-language packs on both platforms,
published as assets of one long-lived GitHub release (`audio-packs`) and never overwritten.
Each pack is `audio-<lang>-<manifest sha8>.tar`: uncompressed, because mp3 does not deflate.
Old app versions keep resolving their packs, and a pack that did not change is not downloaded again.
The public per-language files also carry out the BY-SA/FairPlay mitigation in `audio-licensing.md` § 6.1.
Debug builds keep bundling everything, so simulator, emulator and UI tests need no network.

Atomic series, each green on its own:

1. `fix(audio): iOS speaks the live voice when a recording will not open`:
   the `PronunciationPlayer`/`Pronouncer` failure path falls to `say` and fires `onFinish`.
   Gate: xcodebuild. Android already behaves this way.
2. `feat(kern): which recording packs a pair needs`:
   a pure rule in `catalog/`, covering source + target, minus languages with no pack in the build's index,
   plus the languages of the other persisted boxes if the owner keeps them (question 4).
   Gate: `:kern:jvmTest`. Platforms read it and never mint it (`LayerBoundaryTest`).
3. `build(audio): per-language pack archives and their index`:
   a committed `scripts/audio-pack.py` with `--check`.
   It writes the tars and `audio-packs.json` (lang → sha, bytes, URL), which both builds bundle.
   `release.yml` uploads any pack missing from `audio-packs`.
   Gate: `--check`, `release.sh --check`. Docs: `distribution.md` gets a new § "Recording packs".
4. `refactor(app,android): recordings resolve through a pack-aware catalog source`:
   the overlay `CatalogSource` plus the file resolver (`audioURL`, `openRecording`), still bundle-only, so no behavior changes.
   Gates: xcodebuild, `:android:testDebugUnitTest`.
5. `feat(app,android): download the pair's recording packs`:
   fetch, verify, then an atomic rename into place with the manifest last, followed by a catalog reload.
   Adds INTERNET on Android and the first network code on iOS.
   `web/privacy.html` loses "contains no networking code at all" (`:37`, `:69–72`) and gains the GitHub fetch (it sends no identifiers).
   Gates: both builds and unit tests, plus a sim/emulator run with the pack removed from the bundle.
6. `build(release): release builds ship without recordings`:
   iOS drops `catalog/audio` from the Release product (a script phase after resources, or a split folder reference).
   Android's `SyncAssetsTask` excludes `audio/**` for `release`.
   Docs: `audio-licensing.md` § 1 ("what ships" becomes "what the pair downloads") and § 6.1 (mitigation in place),
   `distribution.md`,
   a CHANGELOG line, and the backlog line pruned.
   Gate: size check on the release APK/IPA (expected ~16 MB APK before minify).
7. Optional: `feat(app,android): recordings status in the audio setting`: size, downloaded/pending, remove.
   iOS and Android land together.

Owner questions:

1. **Networking at all?** It rewrites the privacy page's strongest claim and adds INTERNET on Android.
   If no: AAC-LC transcoding (~56 MB, § 1 obligations) is the only lever until Play/App Store hosting exists.
2. Release IPA/APK without recordings, or ship a "full" variant beside a thin one
   for offline sideloading (Obtainium users, the itms link)?
3. Show pack status and a manual download in settings, or keep it silent?
4. Keep the packs of pairs the learner left (their boxes persist), or only the active pair's?
5. Is sw-on-iOS silent until the pack lands acceptable, or does sw stay bundled (14.4 MB)?
   A Background Assets extension could fetch it before first launch.
6. Is `github.com/polygongmbh/spross` releases the host, or `spross.net`
   (which also has to answer for `/privacy` before any external build)?

[wwdc26]: https://developer.apple.com/wwdc26/guides/app-store/
[ba]: https://developer.apple.com/videos/play/wwdc2025/325/
[pad]: https://developer.android.com/guide/playcore/asset-delivery/test

## 7. Owner rulings (2026-09-24)

- Android keeps a full offline APK for sideloading; the thin build is the CI's Play AAB with Play Asset Delivery.
- iOS has no sideloading worth serving: the thin build is the only store build, delivering packs through Apple-hosted Background Assets.
- The iOS 17.0 deployment target was the project generator's default (2f0c02ba), never a decision;
  no `#available` check in the app depends on it.
  Raising it to 26 is open — the App Store's "last compatible version" keeps older devices on the final 17-target build.
- The iOS stall on an unopenable recording shipped separately (4d545bce).
