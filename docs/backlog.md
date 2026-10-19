# Backlog — session-discovered, out-of-scope issues
Out-of-scope issues, one pointered bullet each, filed by who moves them next: the owner, a decision (ends in its question), ready for any session, or revisit when it grows.
Neighbors: catalog content `../catalog/backlog.md`.

## Needs the owner

- Reconcile the `website` branch (`../app-website` worktree, `docs/plans/website.md`) with `main` and decide what of it ships.
- CC BY-SA §2(a)(5)(B) vs FairPlay needs a legal read now that the app is on the App Store (`docs/audio-licensing.md` § 6 item 1: 2094 of 3597 files, mitigation on record);
  items 2–3 there are the es accent and Azure S0.
- Real hardware has to time the assembled dates accepted set, an uncapped cross-product graded on every keystroke (worst de Sprosse 6 ≈ 128 five-word forms per `evaluate`, typical ~16), on the oldest supported phone before it is trusted free (`DateDrillTasks.fill`;
  `NumberReadingIndex.INDEXED_CARDINALS` states the bound precedent).
- Real hardware still has to answer three things about the Android player (`android/.../audio/Pronouncer.kt`):
  how the boost and lead skip sound, one letter-drill run end to end,
  and whether `MODIFY_AUDIO_SETTINGS` is needed for a session-scoped effect.
- Real hardware has never seen watch pairing,
  and complication rendering was never screenshot-verified (no simctl affordance).
- Real hardware once: on the emulator with a hardware keyboard, Enter after `input text` could walk focus onto the session top-bar mute toggle and flip it, probably an emulator artifact (`android/.../ui/SessionScreen.kt` top bar).

## Needs a decision

- A missed letter-name tile question opens its card although the tiles already mark the answer (`DrillRunProgress.showsAnswer`) —
  keep it closed there, as iOS once did?
- The repo grants nobody anything —
  there is no `LICENSE` file, while `docs/plans/sync.md` plans a paid service around a free and open app;
  the options and what constrains them are `docs/source-license.md`, and the decision is the owner's.
- Watch practice laps repeat one order within each part (unasked, misses, right) until the next snapshot,
  since `fb7668f4` dropped the lap jitter —
  should words of similar standing reorder again, and does kern or Swift decide it?
  (`WatchModel.practiceLap`)
- Phone recognition could ask by multiple choice after the watch's recall pause instead of self-grading, with time-based ratings moved into kern so iOS, Android and the watch grade alike (`WatchGrading`, `kern/docs/presentation.md`).
- `tools/FaceGen --seed` reads the retired `vocab-*.json` and a `../../content` that no longer exists —
  port it to the catalog join or delete it (`docs/facegen.md`).
- Audio ships un-thinned: both installs copy all of `catalog/audio/` (129 MB, 13–25 MB per language —
  `project.yml:55` folder reference, `android/build.gradle.kts:131` asset sync with mp3/wav uncompressed), so a Swahili learner carries ~116 MB they can never hear, and per-language delivery (on-demand resources / Play asset packs) is the fix, measured per platform first.
- Compound/morpheme-boundary training for a compounding language (marking the component seams inside a German compound, the way Leichte Sprache's mediopunkt does) is a distinct unbuilt drill needing curated component-boundary data,
  and syllable data would not deliver it since a syllable split cuts through a stem rather than landing on a seam ("Fei-er-tag" buries "Feier") —
  considered for word scramble (`drills-words.md`) and left out.
- The letter drill sounds each letter in one recording or voice,
  while high-variability phonetic training (Thomson 2018, *Journal of Second Language Pronunciation* 4(2)) finds several talkers teach a sound better —
  play a second voice where one exists?
  (`LetterDrill`)
- The iOS session summary burst has geometry and timing Android does not draw —
  port it through kern `design/`, or keep it an iOS flourish?
  (`App/Sources/Design/SessionSummaryView.swift`)
- Card geometry is still written on both platforms (emoji slot, reveal rule, replay target, verdict tile, card reserves, activity strip gutter) —
  where does native spacing end and a kern `design/` value begin?
- Emoji as chrome or content:
  `Drill.emoji` and `DrillPauseReason.emoji` live in kern while the milestone KDoc calls glyphs chrome and both phones keep their own `AnswerStreakMilestone` glyph table,
  and both widgets and the watch tile write their own 🗂️ stand-in for a card with no picture —
  which side owns a glyph?
- `web/site.js` runs its own numbers ramp beside kern's `NumbersRun` —
  should the site read kern's JS export instead?
- The dated audio research docs (`docs/2026-09-28-audio-quality-*.md`, `docs/2026-10-09-audio-rating-sources.md`) and `docs/audio-verdicts.tsv` sit under `docs/` as if current —
  archive, fold into `docs/audio-format.md`, or keep?

## Ready


### Apps & tooling

- The iOS drill run (`DrillRunView`) still wraps its scaffold by hand,
  because its run state arrives through `Face.snapshot` rather than kern's run progress —
  exposing that would let `runScreen` reach it.
- The widget kind `"SprossWordWidget"` is a literal at the iOS reload sites and in `Widgets/Sources/WordWidget.swift` —
  one constant in `Shared/Sources`.
- `HomeStandingTest`, `CardDisplayTest` and `UnlockPriceTest` assert literal German/English copy and break on a copy edit (`android/src/test`).
- Over ~300 lines with a seam worth checking:
  Android `AppModel.kt` (wants holder classes for its private setters), `ListeningService.kt`, `ListeningDriver.kt`;
  iOS `AppModel.swift`, `WatchModel.swift`, `TrainerHubView.swift`, `AnswerInputView.swift`, `Theme.swift`, `BoxSettingsSection.swift`;
  Android `Chrome.kt` is a hand-declared field table,
  and the generated `ChromeDe.kt`/`ChromeEn.kt` are exempt by nature.
- `targetSdk` sits at 36: re-check the Android 17 behavior changes before raising it,
  since compiling against 37 does not opt the app into them.


## Revisit when it grows

- Watch snapshot entry cap (`WatchSnapshotBuilder.ENTRY_CAP`, 120):
  due-first ranking keeps due cards on-watch,
  but revisit the cap if the active box outgrows it (`../kern/docs/snapshots.md`).
- Android's `NumberReferenceTable` renders every band eagerly inside one `verticalScroll` —
  fine at today's ~50 rows, revisit if a band grows (`android/.../ui/NumberReference.kt`).
