# Backlog — session-discovered, out-of-scope issues
Out-of-scope issues found mid-session, pruned when fixed.
Neighbors: catalog content `../catalog/backlog.md`.

One item per bullet, with a file or context pointer, filed under the section it belongs to —
as short as that allows, longer only to carry evidence or reasoning a fixer would otherwise have to redo.
Within a section, ready work comes first, then the items that end in a question for the owner,
then open design work, then what waits on someone else, grouped by who that is.
Parked work is not an issue: its own doc says it is parked.

## Engine & scheduling

- Watch snapshot 60-entry cap: due-first ranking keeps due cards on-watch, but revisit the cap
  if the active box outgrows it (`../kern/docs/snapshots.md`).
- Real hardware has to time the assembled dates accepted set, an uncapped cross-product graded
  on every keystroke (worst de Sprosse 6 ≈ 128 five-word forms per `evaluate`, typical ~16),
  on the oldest supported phone before it is trusted free (`DateDrillTasks.fill`;
  `NumberReadingIndex.INDEXED_CARDINALS` states the bound precedent).

## App & UX

- "Move noun class, word types and the tenses further back" — filed as a suggestion
  without a surface; the three are a card's Swahili plural/class grammar, its kind badge
  and the tense phrases' seed positions, which sit in three different places. Which one
  arrives too early for the owner: the card's own lines, or the order content unlocks in?
- No automated visual-parity check exists between iOS and Android for shared, parity-bearing
  UI (cards, layout tokens), and `scripts/card-parity.py` closes 5 of the 9 historical
  divergences (numbers and primitive names, not rendering) — is a snapshot gate
  (Roborazzi/Paparazzi + swift-snapshot-testing or simctl diff, versioned goldens) worth its
  cost, or does this bullet narrow to the residual non-numeric class?
- A duplicate-`// why:` scan earns a ranked report, never a commit gate: it reads files that
  duplicate a COMMENT, so a copy whose prose drifted is invisible — it missed two scramble
  screens, a second `DrillBeat` in `TurnFlow`, a third reference sheet in `NumberReferenceTable`
  and a panel cut by hand at 15 sites — and still stands at 46 groups after six clusters shipped.
- The watch reveal carries no "also means" line because `WatchEntryDto` ships `sourceText`
  alone (`WatchSnapshotBuilder.kt:221-235`), so a merged word teaches only the meaning of the
  card that was asked; the kern side is one field plus `SCHEMA_VERSION` 5→6, so where does
  the "auch: …" line live after a recognize tap — appended to the prompt line, below the 2x2
  grid, or a reserved slot — and does the 900 ms correct-advance hold longer when it is present?
- Android's `NumberReferenceTable` renders every band eagerly inside one `verticalScroll` —
  fine at today's ~50 rows, revisit if a band grows (`android/.../ui/NumberReference.kt`).
- Compound/morpheme-boundary training for a compounding language (marking the component seams
  inside a German compound, the way Leichte Sprache's mediopunkt does) is a distinct unbuilt
  drill needing curated component-boundary data, and syllable data would not deliver it since a
  syllable split cuts through a stem rather than landing on a seam ("Fei-er-tag" buries
  "Feier") — considered for word scramble (`drills-words.md`) and left out.

## Platform reach

- `compileSdk` sits at 36 and holds androidx back — lifecycle 2.11 refuses to resolve below
  37 (`checkDebugAarMetadata`) and the next Compose BOM will follow — so the bump is one edit
  to `gradle/libs.versions.toml` once the android-37 platform is installed, plus a separate
  re-check of `targetSdk`, since compiling against 37 does not opt the app into its runtime behavior.
- Android surfaces still unported: `docs/design.md` § Not yet owns the list (couple mode,
  accounts/sync, chrome past de/en, no forest canvas or growth headline), and the `growth*`
  rows in `Chrome.kt:456-458` stand ready for a headline that needs `AreaTree`/`TreeTransition`
  lifted from `App/Sources/Design/ForestLayout.swift` into kern first — render it, or delete
  the rows and let `design.md`'s deferral stand?
- Portability move 6 (`snapshot/WatchRun` + public snapshot DTOs — the watch's own queue and
  ranking in `Watch/Sources/WatchModel.swift`, latency-to-rating in `Shared/Sources/WatchGrading.swift`,
  shortlist sampling in `Shared/Sources/WatchPracticeQuestion.swift`) was deferred 2026-08-08 —
  reopen it as a series now (kern engine + tests + both consumers + `SCHEMA_VERSION`), or keep it parked?
- Audio ships un-thinned: both installs copy all of `catalog/audio/` (129 MB, 13–25 MB per
  language — `project.yml:35` folder reference, `android/build.gradle.kts:131` asset sync with
  mp3/wav uncompressed), so a Swahili learner carries ~116 MB they can never hear, and
  per-language delivery (on-demand resources / Play asset packs) is the fix, measured per
  platform first.
- No release has carried an IPA yet: the `ios` job needs the App Store Connect secrets
  (`docs/distribution.md` § Secrets) present to get past `App Store Connect API key from
  secret`, and iPhones are served by `scripts/deploy-devices.sh` until a run has published one.
- A live spross.net gates the iPhone install link: GitHub renders the release notes'
  `itms-services://` URL as code, not a tappable link (`.github/workflows/release.yml:229`),
  and a `web/install.html` taking `?v=` would make it a button (`docs/plans/website.md`).
- The repo grants nobody anything — there is no `LICENSE` file, while `docs/sync.md` plans a
  paid service around a free and open app; the options and what constrains them are
  `docs/source-license.md`, and the decision is the owner's.

## Verification gaps

- Real hardware still has to answer three things about the Android player
  (`android/.../audio/Pronouncer.kt`): how the boost and lead skip sound, one letter-drill run
  end to end, and whether `MODIFY_AUDIO_SETTINGS` is needed for a session-scoped effect.
- Real hardware has never seen watch pairing, and complication rendering was never
  screenshot-verified (no simctl affordance).
- Real hardware once: on the emulator with a hardware keyboard, Enter after `input text` could
  walk focus onto the session top-bar mute toggle and flip it, probably an emulator artifact
  (`android/.../ui/SessionScreen.kt` top bar).

## Compliance

- CC BY-SA §2(a)(5)(B) vs FairPlay needs a legal read before the FIRST submission
  (`docs/audio-licensing.md` § 6 item 1: 2094 of 3597 files, mitigation on record); items 2–3
  there are the es accent and Azure S0.
