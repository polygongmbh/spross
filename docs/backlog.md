# Backlog — session-discovered, out-of-scope issues
Out-of-scope issues, one pointered bullet each, filed by who moves them next: the owner, a decision (ends in its question), ready for any session, or revisit when it grows.
Neighbors: catalog content `../catalog/backlog.md`.

## Needs the owner

- The site lives in `web/` on `main` (`docs/plans/website.md`);
  what is open is `SIGNUP_ENDPOINT` in `web/site.js` being empty and spross.net having no host.
- No release has carried an IPA yet: the `ios` job needs the App Store Connect secrets
  (`docs/distribution.md` § Secrets) present to get past `App Store Connect API key from
  secret`, and iPhones are served by `scripts/deploy-devices.sh` until a run has published one.
- A live spross.net gates the iPhone install link: GitHub renders the release notes'
  `itms-services://` URL as code, not a tappable link (`.github/workflows/release.yml:263`),
  and a `web/install.html` taking `?v=` would make it a button (`docs/plans/website.md`).
- CC BY-SA §2(a)(5)(B) vs FairPlay needs a legal read before the FIRST submission
  (`docs/audio-licensing.md` § 6 item 1, mitigation on record); items 2–3
  there are the es accent and Azure S0.
- Real hardware has to time the assembled dates accepted set, an uncapped cross-product graded
  on every keystroke (worst de Sprosse 6 ≈ 128 five-word forms per `evaluate`, typical ~16),
  on the oldest supported phone before it is trusted free (`DateDrillTasks.fill`;
  `NumberReadingIndex.INDEXED_CARDINALS` states the bound precedent).
- Real hardware still has to answer three things about the Android player
  (`android/.../audio/Pronouncer.kt`): how the boost and lead skip sound, one letter-drill run
  end to end, and whether `MODIFY_AUDIO_SETTINGS` is needed for a session-scoped effect.
- Real hardware has never seen watch pairing, and complication rendering was never
  screenshot-verified (no simctl affordance).
- Real hardware once: on the emulator with a hardware keyboard, Enter after `input text` could
  walk focus onto the session top-bar mute toggle and flip it, probably an emulator artifact
  (`android/.../ui/SessionScreen.kt` top bar).

## Needs a decision

- A missed letter-name tile question opens its card although the tiles already mark the answer (`DrillRunProgress.showsAnswer`) — keep it closed there, as iOS once did?
- The repo grants nobody anything — there is no `LICENSE` file, while `docs/plans/sync.md` plans a
  paid service around a free and open app; the options and what constrains them are
  `docs/source-license.md`, and the decision is the owner's.
- Watch practice laps repeat one order within each part (unasked, misses, right) until the next snapshot, since `fb7668f4` dropped the lap jitter — should words of similar standing reorder again, and does kern or Swift decide it? (`WatchModel.practiceLap`)
- Phone recognition could ask by multiple choice after the watch's recall pause instead of self-grading, with time-based ratings moved into kern so iOS, Android and the watch grade alike (`WatchGrading`, `kern/docs/presentation.md`).
- `tools/FaceGen --seed` reads the retired `vocab-*.json` and a `../../content` that no longer exists — port it to the catalog join or delete it (`docs/facegen.md`).
- Audio ships un-thinned: both installs copy all of `catalog/audio/` (129 MB, 13–25 MB per
  language — `project.yml:55` folder reference, `android/build.gradle.kts:131` asset sync with
  mp3/wav uncompressed), so a Swahili learner carries ~116 MB they can never hear, and
  per-language delivery (on-demand resources / Play asset packs) is the fix, measured per
  platform first.
- Compound/morpheme-boundary training for a compounding language (marking the component seams
  inside a German compound, the way Leichte Sprache's mediopunkt does) is a distinct unbuilt
  drill needing curated component-boundary data, and syllable data would not deliver it since a
  syllable split cuts through a stem rather than landing on a seam ("Fei-er-tag" buries
  "Feier") — considered for word scramble (`drills-words.md`) and left out.

## Ready

### Engine

- Only tests read `AreaStatistics.notIntroduced`, `CatalogArea.conceptsBySlug`, `WordScrambleMasking.fullyScrambled` and `Catalog.dateNames` (the last documented as API in `kern/docs/catalog.md`) — drop each with its test.
- `CountryDrillRun` and `DateDrillRun` reduce near line for line (~200 lines), and `CountryDrill`/`DateDrill` repeat `answerLanguage`, `promptLanguage`, `winsToAdvance` and `fastUnlocked`; one shared reducer changes the ObjC header, so both apps move with it.
- `TypedDrillVerdicts` is pinned only through `CountryDrillRunTest`; a direct test would let the country run's verdict tests shrink to wiring.
- Both phones mint the backup file name `Spross-<lang>-<day>` kern should name (`BackupRow.swift` `BackupFile.taken`, `ui/BackupSetting.kt:~80`, `ui/BoxSettings.kt:~146`).
- `Presentation.kt` KDoc on `emojiCue` and `producePrompt` justifies against the past ("came to disagree once already", "bit-exact v1 contract").

### Apps & tooling

- The watch speed mark (⚡) is visual only: VoiceOver hears right or wrong but not how quick (`WatchQuizView.verdict`).
- The iOS drill run (`DrillRunView`) still wraps its scaffold by hand, because its run state arrives through `Face.snapshot` rather than kern's run progress — exposing that would let `runScreen` reach it.
- The widget kind `"SprossWordWidget"` is a literal at the iOS reload sites and in `Widgets/Sources/WordWidget.swift` — one constant in `Shared/Sources`.
- `HomeStandingTest`, `CardDisplayTest` and `UnlockPriceTest` assert literal German/English copy and break on a copy edit (`android/src/test`).
- Still over ~300 lines: Android `AppModel.kt` (453, wants holder classes for its private setters), `ListeningService.kt`, `ListeningDriver.kt`;
  iOS `AppModel.swift` (397), `WatchModel.swift`, `TrainerHubView.swift`, `AnswerInputView.swift`, `Theme.swift`, `BoxSettingsSection.swift`;
  kern `AnswerNormalizer.kt` (398), `NumbersRun.kt`, `SessionRun.kt`, `BoxEngine.kt`, `Catalog.kt`, `LetterDrillRun.kt`;
  Android `Chrome.kt` (570) is a hand-declared field table the owner may exempt, and the generated `ChromeDe.kt`/`ChromeEn.kt` are exempt by nature.
- A duplicate-`// why:` scan earns a ranked report, never a commit gate: it reads files that
  duplicate a COMMENT, so a copy whose prose drifted is invisible — it missed two scramble
  screens, a second `DrillBeat` in `TurnFlow`, a third reference sheet in `NumberReferenceTable`
  and a panel cut by hand at 15 sites — and stands at 7 groups.
- `kern/docs` `grading.md`, `catalog.md`, `audio.md`, `reports.md`, the head of `snapshots.md` and all of `turns.md`,
  plus `catalog/README.md`, `catalog/audio/README.md`, `catalog/alphabet/README.md`, `docs/date-readings.md`, `RUNBOOK-android.md` and `docs/backlog.md`,
  still hard-wrap at a column instead of semantic linebreaks.
- `compileSdk` sits at 36 and holds androidx back — lifecycle 2.11 refuses to resolve below
  37 (`checkDebugAarMetadata`) and the next Compose BOM will follow — so the bump is one edit
  to `gradle/libs.versions.toml` once the android-37 platform is installed, plus a separate
  re-check of `targetSdk`, since compiling against 37 does not opt the app into its runtime behavior.

- Pronouncer parity: a held or unsayable word still calls `onFinish` on Android but not on iOS, where the reader polls to a ceiling; one end-of-reading signal would let kern rule it (`App/Sources/Audio/Pronouncer.swift`, `android/.../audio/Pronouncer.kt`).

## Revisit when it grows

- Watch snapshot entry cap (`WatchSnapshotBuilder.ENTRY_CAP`, 120): due-first ranking keeps due cards on-watch, but revisit the cap
  if the active box outgrows it (`../kern/docs/snapshots.md`).
- Android's `NumberReferenceTable` renders every band eagerly inside one `verticalScroll` —
  fine at today's ~50 rows, revisit if a band grows (`android/.../ui/NumberReference.kt`).
