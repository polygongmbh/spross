# Review E — documentation and gates

Read-only review of every standing doc, the backlogs, the dated/plan files and the gate scripts against the code on `claude/app-review-kern-unification-cpznrg` (HEAD 734509b, 8.7.0).
Every pointer in backticks across `README.md`, `kern/README.md`, `RUNBOOK-android.md`, `CLAUDE.md`, `docs/*.md`, `kern/docs/*.md`, `catalog/README.md` and `catalog/*/README.md` was resolved by script (path existence, then every dotted identifier part looked up with `rg -w` under the source trees); only the misses listed below were found, so the pointer hygiene is good overall.
Findings are ranked by value within each package. Sizes: S = one edit, M = an hour, L = a session.

## Package 1 — doc ↔ code truth

### 1.1 `kern/README.md` links five sibling docs with the wrong relative path
- Evidence: `kern/README.md:169` "(the watch alone folds enough misses back into a round, `docs/surfaces.md`)"; `:233` and `:256` "(`docs/growth-evidence.md`)"; `:67` "(`catalog/README.md`)"; `:440` "(`catalog/phrases/README.md`)". From `kern/`, those resolve to `kern/docs/surfaces.md`, `kern/docs/growth-evidence.md`, `kern/catalog/…`, none of which exist (`ls kern/docs` has no `surfaces.md`/`growth-evidence.md`; `kern/catalog` does not exist). The same file's neighbors line uses the correct form `../docs/design.md`.
- Why: the engine contract is the doc every session reads first; a reader following the link lands on nothing, and the file is inconsistent with itself about how it links.
- Fix: prefix `../` on the five pointers. Size S. Confidence: verified.

### 1.2 `kern/docs/reports.md` points at a backlog entry that no longer exists
- Evidence: `kern/docs/reports.md:89-91` "Missing components are the one thing it does not count: queuing a phrase also prepends the components it lacks, and where those live on another shelf, queuing it takes in more than the count said (`../../docs/backlog.md`)." `rg -n component docs/backlog.md` hits only the compound-boundary drill entry (lines 44-48); nothing about `queueableCount`.
- Why: the backlog was pruned (ce930be re-filed every item) and the standing doc still promises an entry; a "known gap" with no ticket is either accepted behavior (then say so here) or lost work.
- Fix: either re-file the gap in `docs/backlog.md § Ready › Engine` ("`BoxBrowser.queueableCount` omits cross-shelf components `queue` prepends") or drop the parenthetical and state it as the rule. Size S. Confidence: verified.

### 1.3 `docs/read-aloud.md` says only German and Italian ship article recordings; French does too
- Evidence: `docs/read-aloud.md:29-30` "Where a pack recorded the article too, the recording says it: German and Italian carry an `articles{}` section." Manifest counts: de 312, it 73, **fr 26** article entries (`catalog/audio/fr/manifest.json`); `CHANGELOG.md` 8.6.0 "with their article too in German, Italian and French". `docs/audio-format.md:40` already names "fr … it articles".
- Why: the one doc that owns "how the app speaks a word" names the wrong set; a future pass adding articles to another pack will edit this line and still be wrong about French.
- Fix: "German, Italian and French carry …", or better, drop the language list and point at `scripts/audio-coverage.py --credits` as the doc already does for counts. Size S. Confidence: verified.

### 1.4 Stale BY-SA file counts, stated in two homes
- Evidence: `docs/audio-licensing.md:170-171` "2094 of the 3597 files are BY-SA"; repeated in `docs/backlog.md:15` "(2094 of 3597 files …)". Counting manifest entries today (words/letters/texts/articles/calendar/countries across every pack): 8157 entries, 5435 under CC BY-SA (5188 BY-SA 4.0 + 247 BY-SA 3.0).
- Why: the legal record understates the share-alike exposure by more than half, and the backlog restates a number that the licensing doc owns ("one fact, one home").
- Fix: in `audio-licensing.md` say "the majority of the files (`scripts/audio-coverage.py --credits` prints the split)"; in the backlog drop the figures and keep the pointer. Size S. Confidence: verified (entry count; the doc may have counted files rather than entries, but the ratio has moved either way).

### 1.5 Opposites ladder bands are stated in two docs and disagree
- Evidence: `kern/docs/turns.md:255-256` "adjectives, then verbs and nouns, then every prompt with more than one opposite"; `docs/drills-words.md:112` "adjectives and adverbs, then verbs, then the prompts with several opposites". Code `OppositesAvailability.kt:31-35,58-59`: `ADJECTIVES` = `CardKind.Adjective` (the catch-all that holds adverbs), `VERBS` = everything else (verbs and nouns).
- Why: drills-words omits nouns; two homes for one ladder is exactly the drift CLAUDE.md's "one fact, one home" forbids.
- Fix: keep the bands in `turns.md` (kern owns the ladder), have `drills-words.md` say "three bands, `../kern/docs/turns.md`". Size S. Confidence: verified.

### 1.6 Area size: the README says "roughly forty", the lint enforces 50, the backlog treats 47 as over the line
- Evidence: `catalog/areas/README.md:183-187` "An area holds a few dozen cards … growing past roughly forty asks to be cut"; `CatalogLintTest.kt:44` `areaDrawerLine = 50` with `noAreaGrowsIntoADrawer` (`:101-109`); `catalog/backlog.md:22-25` "`food` holds 47 concepts and `desk` and `admin` 41 each, all past the ~40 line … accept the three past the line?" (today: food 47, desk 41, admin 40, all green under the gate).
- Why: a rule with a gate has its number in the gate; the doc and the backlog carry a second number, so a contributor cannot tell whether 45 is allowed.
- Fix: `areas/README.md` says "past the line `CatalogLintTest.noAreaGrowsIntoADrawer` holds" and the backlog entry becomes "is 50 the line, or 40 with three waivers?" or is pruned. Size S. Confidence: verified.

### 1.7 Pointers into a `data/` workspace that is not in the repo
- Evidence: `catalog/README.md:9-10` "live in `../../data/` (the parent repo's content workspace) … inventoried in `reference/README.md`"; `docs/audio-licensing.md:5-6` "`data/reference/audio/README.md`"; `kern/docs/audio.md:57` "`../../../data/reference/audio/README.md`, outside the repo"; `docs/number-forms.md:420` "`../../data/reference/grammar-sw.md`"; `catalog/backlog.md:7,92,96-97,107,136` (`../data/reference/`, `../../data/review/…`, `data/orchestration/…`, `../../docs/sprachposter-learnings.md`). `ls data` → no such directory; no `sprachposter-learnings.md` anywhere.
- Why: a cloud session or a fresh clone cannot follow any of them, and `../../docs/sprachposter-learnings.md` from `catalog/` resolves outside the repo — it reads like a repo doc and is not one.
- Fix: one sentence in `catalog/README.md` naming the workspace as external (it already half does) and every other pointer written as "`data/…` (external workspace)"; the two `sprachposter-learnings.md` pointers fixed to wherever that file lives or dropped. Size S. Confidence: verified.

### 1.8 `catalog/README.md` still frames the catalog as German-authored, three pairs
- Evidence: `catalog/README.md:18-19` "A **pair** (de↔sw, de↔uk, later sw↔uk) is a **runtime join** on slug — never stored. The German side is authored once and shared across every pair that includes German." `catalog/languages.json` declares 8 languages; `README.md:6` says so.
- Why: the format spec's first example teaches the pre-2026-08 product; a contributor reads "German side authored once" as a rule.
- Fix: "A pair (any two of the eight declared languages) is a runtime join … each language file is authored once and serves every pair it joins." Size S. Confidence: verified.

### 1.9 `kern/docs/build.md` claims a Release-archive gate that nothing runs
- Evidence: `kern/docs/build.md:21` "`scripts/bootstrap.sh` for fresh clones; a Release archive smoke check joins the gates." `rg -i archive scripts/` → nothing; `scripts/release.sh:94-98` runs an unsigned simulator `build`, which `docs/distribution.md:20` describes correctly ("an unsigned simulator build"); only `.github/workflows/release.yml:128-142` archives, after the tag.
- Why: a reader expecting the archive to be gated locally will believe a signing/entitlement break is caught before the tag; it is caught after, when the release is already cut (`distribution.md:28-30` explains that cost).
- Fix: delete the clause or say "the archive runs in CI after the tag (`../../docs/distribution.md`)". Size S. Confidence: likely (depends on whether "the gates" meant CI).

### 1.10 `catalog/README.md` names the wrong test for the pinned collision set
- Evidence: `catalog/README.md:31` "Tolerated cross-area collisions are pinned by `CatalogLintTest`"; the pin is `CatalogCollisionLintTest.crossAreaPromptCollisionsAreKnown` (`kern/src/jvmTest/.../CatalogCollisionLintTest.kt:105`), as `kern/docs/catalog.md:37-47` and `catalog/areas/README.md:178` say.
- Fix: name `CatalogCollisionLintTest`. Size S. Confidence: verified.

### 1.11 Past-justifying prose and a documented absence
- Evidence: `docs/growth-evidence.md:137-139` "**The health gate cost fell on the returning learner:** coming back after two weeks away was exactly when it shut" — no health gate exists (`rg -i "health gate|healthGate"` hits only this line); the paragraph explains a removal. `kern/docs/turns.md:20-22` "Every rule about what that text is worth is here, because it lived twice before and drifted both ways — a pickable Easy on one platform, no retype after a miss on the other." `scripts/hooks/pre-commit:62-64` "nine of these divergences shipped and were corrected again, eight of them across a session boundary" and `:76` "only while the pair is new"; `scripts/arch-status.py:4-6` "the atlas drill reached 386 lines of Kotlin and 264 of Swift that way".
- Why: CLAUDE.md "Do not document a removal or absence … In code comments, always only explain the current state". The growth-evidence paragraph is the clearest case: it argues against a gate nobody will reintroduce without reading `kern/README.md §6`, which already states the rule ("Nothing throttles … on how far behind the box has fallen").
- Fix: cut the health-gate paragraph (the §6 rule stands); shorten the turns.md clause to "Every rule about what that text is worth is here"; the hook/script comments can keep one clause each. Size S. Confidence: verified (growth-evidence, turns.md); likely for the hook comments being worth touching.

### 1.12 Hard-wrapped docs beyond the ones the backlog lists
- Evidence: a scan for lines that end mid-clause (last word an article/preposition/conjunction) with a lowercase continuation: `catalog/audio/README.md` 44, `kern/docs/catalog.md` 31, the two 2026-09-28 docs 23 each, `catalog/README.md` 21, `kern/docs/turns.md` 20 (the vocabulary-turn section too, lines 25, 41-42, not only the drill-runs section), `catalog/alphabet/README.md` 17, `kern/docs/grading.md` 12, `docs/date-readings.md` 12, `kern/docs/reports.md` 11, `RUNBOOK-android.md` 11 (e.g. 90-95), `kern/docs/snapshots.md` 10, `docs/backlog.md` 10, `kern/docs/audio.md` 7. Spot-checked by eye: `catalog/audio/README.md:5-8`, `catalog/alphabet/README.md:4-7`, `docs/date-readings.md:36-39`, `RUNBOOK-android.md:90-95` are column-wrapped. `docs/backlog.md:72` lists only `grading.md`, `catalog.md`, `audio.md`, `reports.md`, the head of `snapshots.md` and the drill-runs section of `turns.md`.
- Why: CLAUDE.md "ALWAYS use semantic linebreaks"; the backlog line is the only tracker and it undercounts by half, so a sweep that works the list will declare the rule met.
- Fix: extend `docs/backlog.md:72` to name `catalog/README.md`, `catalog/audio/README.md`, `catalog/alphabet/README.md`, `docs/date-readings.md`, `RUNBOOK-android.md`, `docs/backlog.md`, all of `turns.md`; reflow is M per file. Confidence: verified (heuristic plus eye on four files).

### 1.13 Minor truth and pointer slips (all S, verified)
- `docs/backlog.md:33` "`docs/sync.md`" → the file is `docs/plans/sync.md`.
- `docs/backlog.md:40` "`project.yml:35` folder reference" → line 35 is a comment about entitlements; the catalog folder reference is elsewhere in the file.
- `docs/backlog.md:12` "`.github/workflows/release.yml:229`" → line 229 is inside the manifest plist, not the release-notes rendering.
- `catalog/backlog.md:13` "`catalog/README.md:127`" → line 127 is the `-ти` suffix sentence; the component-free greeting rule is at 134-136.
- `catalog/backlog.md:124-127` "`time/sw.json:58`", "`:54`", "`en.json:29`", "`catalog/dates/sw.json:3-9`" → `saa tatu asubuhi kamili` is at `catalog/areas/time/sw.json:91`, line 58 is a note under `second`; `time/en.json:29` is `a-while-ago`; `dates/sw.json:3-9` is now the `dateNotes` block, the `abbr` rows start at 17.
- `docs/2026-09-28-audio-quality-tools.md:122-123` names `model_v8.onnx` as needed; only `sig_bak_ovr.onnx` was vendored (`scripts/dnsmos/`), and `scripts/audio_measure.py:141` reads that one alone.

### 1.14 Things checked and found true (so nobody re-checks them)
- All three stability bars (`GROWING_STABILITY` 6, `SETTLED_STABILITY` 25, `MATURED_STABILITY` 120), the ladder `[10m, 1d, 10m, 3d, 10m, 7d, 10m, 30d]` (`model/Config.kt:57-59`), `desiredRetention` 0.85, `SESSION_FLOOR_CARDS` 7 = `SHORT_ROUND_CARDS`, `ADVANCE_LIVE_MS` 450 / `ADVANCE_EXPLICIT_MS` 1200, `STORE_SCHEMA_VERSION` 2, `LegacyStore.kt`/`LegacyDocument.kt` present, the `build.md` pins (Kotlin 2.4.10, SKIE 0.10.14, serialization 1.11.0, datetime 0.8.0, Gradle 9.6.1, AGP 9.3.1, compileSdk 36 / minSdk 26), the `js { browser() }` target, `catalog/audio/**` excluded from `:kern:jvmTest` inputs (`kern/build.gradle.kts:67`), chrome languages de/en only, eight languages in `languages.json`, `LayerBoundaryTest` with `// layer-ok:` (4 waivers in `android/src`), `IDIOM_EMOJI`, every lint test a doc names (`slugsAreGloballyUnique`, `verbSlugsCarryTheToPrefix`, `alternatesDoNotAddOrDropPolitenessParticles`, `subtitlesAreCompletePerAreaAndDistinctFromTheTitle`, `languageMarkersOnlyAppearWhereTheyResolve`, `parkedAreasStayActivatable`, `contentWritesTheTypewriterApostrophe`, the three `CatalogCollisionLintTest` rules, the four `CatalogAudioProvenanceTest` rules, `CatalogAudioLintTest.noAmbiguousMatchedForm`, `PhoneReadingsTests`, `Numbers<Lang>FormsTests` ×8, `NumbersDateReadingTests`, `PaletteParityTest`).
- Every doc's three-line head is in shape (`scripts/doc-header.py` gates it; plans and archive are exempt and `docs/plans/*.md` indeed lack a scope line).
- No file-describing prose below a head was found (`rg "^this (document|file|page)"` empty).

## Package 2 — dated and plan-shaped files

### 2.1 `docs/2026-09-28-audio-quality-measure.md` and `docs/2026-09-28-audio-quality-tools.md` — shipped research; delete or fold
- Evidence: both open "Research spike …" and cite code under `/private/tmp/claude-501/…/scratchpad/quality*/` (`measure.md:7-9`, `tools.md:8-10`); `quality_score.py`, `evaluate.py`, `extract_features.py`, `run_all.py`, `voice_scan.py`, `wada_snr.py` exist nowhere in the repo. The tools doc's recommendation shipped: `scripts/dnsmos/sig_bak_ovr.onnx` is vendored, `scripts/audio_measure.py:135-186` has `mos()` over it, `scripts/audio-catalog.py:334,717` writes and floors on `mos`, and the standing rule lives in `kern/docs/audio.md:90-92` and `catalog/audio/README.md:14,46,210`. The measure doc's recommendation (the hand-built `quality` combo) was not adopted and `audio_measure.py` has no `quality` function any more (`def` list: version, measure, peak_and_loudness, side_balance, noise, mos, measure_all, perceived_loudness). `tools.md:2` itself says "why DNSMOS was adopted".
- Why: CLAUDE.md "an audit, triage or sweep result … is a plan, never a dated file in docs/"; both are exactly that, and their numbers (62/65 rows, rho 0.45) are frozen snapshots of a 133-row dataset that has moved on.
- Fix: delete both; keep one paragraph in `catalog/audio/README.md` (which already owns `mos`) saying DNSMOS was picked over `noise_margin` and a hand-built combo because it alone ranked every voice into its heard bucket — the only durable fact. Size S to delete, M to fold. Confidence: verified.

### 2.2 `docs/2026-10-09-audio-rating-sources.md` — a survey whose whole standing value is one sentence
- Evidence: `:5-13` "Verdict … Nothing is worth wiring in … Keep rating by ear plus DNSMOS; nothing here changes that." The remaining 75 lines are API query transcripts.
- Why: dated file in `docs/`; the verdict is an absence ("no external rating source exists"), and CLAUDE.md says not to document an absence unless it would be reintroduced — here the one-liner IS worth keeping so nobody repeats the search.
- Fix: one bullet in `catalog/audio/README.md` ("Commons, Wiktionary and Lingua Libre carry no per-file quality rating (checked 2026-10-09); ratings are our own `docs/audio-verdicts.tsv` plus DNSMOS"), delete the file. Size S. Confidence: verified.

### 2.3 `docs/audio-verdicts.tsv` is tooling data filed under docs
- Evidence: 133 rows read by `scripts/audio-listen.py`, `scripts/audio_measure.py`, `scripts/audio_voices.py` (`rg -l audio-verdicts`), and named by `catalog/audio/README.md` and `catalog/backlog.md:88`.
- Why: it is an input to the converter's floors, not a doc; `docs/` is where a session looks for rules, not for a dataset three scripts parse.
- Fix: move to `catalog/audio/verdicts.tsv` (beside the manifests it judges) or `scripts/audio/`, update the five pointers. Size S (implies a code change in three scripts' default path). Confidence: verified.

### 2.4 `docs/plans/website.md` is mostly shipped and says it is parked
- Evidence: `:3` "Parked on the `website` branch (the `../app-website` worktree) until picked up." On this branch `web/` holds `index.html`, `site.css`, `site.js` (with the signup form, `SIGNUP_ENDPOINT = ""` at `site.js:5`, the "Sprössling" copy at `:485`), `c.html`, `privacy*.html`, `impressum*.html`, `.well-known/apple-app-site-association`; `scripts/build-web.sh` exists and copies `.well-known`; kern has the `js { browser() }` target and `net.spross.kern.web`. `git branch -r` shows only `origin/main` and this session's branch — no `website` branch is visible from this clone (shallow; cannot prove it does not exist upstream).
- Why: CLAUDE.md "delete [plans] once shipped"; `docs/backlog.md:7` ("Reconcile the `website` branch … with `main`") rests on the same parked premise. What is still open is the endpoint and the host, which `docs/distribution.md § Impressum` and `§ Challenge links` already track.
- Fix: delete the plan; move the two brand rules worth keeping (palette hex pairs follow `Palette.kt`; copy names what the visitor gains) into a short `web/README.md` or `docs/distribution.md`; rewrite backlog:7 as "`SIGNUP_ENDPOINT` in `web/site.js` is empty and spross.net has no host". Size S. Confidence: verified for the files on this branch; likely for the branch being obsolete.

### 2.5 `docs/plans/sync.md` — legitimately standing
- Evidence: `store/BoxMerge.kt` does not exist; `StoredBoxes.restoring` still replaces (`kern/docs/snapshots.md:43-44`); `docs/design.md:252` "Not yet … accounts/sync". Internally consistent with `source-license.md` and `distribution.md`.
- Fix: none; uncommitted-plan rule aside (it is committed), it is a plan not yet shipped. Confidence: verified.

### 2.6 `docs/facegen.md` documents a tool with no input
- Evidence: `:49-50` "reads the retired `vocab-*.json` seed files (`--seed`), not `catalog/`, so no checkout has input for it"; `tools/FaceGen/Sources/FaceGen/FaceGenCommand.swift:38-39` still takes `--seed` "Directory containing vocab-*.json"; `docs/backlog.md:38` asks "port it to the catalog join or delete it".
- Why: a standing doc whose body is mostly "current limitations" is a plan with the decision pending; it is correctly filed as a decision in the backlog, so the doc is harmless but dead weight.
- Fix: leave until the owner decides; if "delete", the doc and `tools/FaceGen` go together. Size S. Confidence: verified.

### 2.7 Standing and fine
- `docs/growth-evidence.md` (rationale behind `BoxConfig`, linked from `Config.kt:26` and `kern/README.md`), `docs/performance.md` (every pointer resolves: `AppModel.refreshStats`, `HomeStanding`, `refreshTrainerContent`, `AppModel.refreshLetters`, `LettersOverview`, `AppModel.startListening`, `BoxEngine.dueCount`, `BoxBrowser.shelfCounts`), `docs/archive/*` (the archive is the one place history may be dated; `doc-header.py` exempts it), `docs/screenshots/*` (six files, all referenced from `README.md:24-25`; freshness could not be judged here — the last Home UI change was 2026-10-06 and the clone is shallow).
- `docs/audio-format.md` carries "measured 2026-08-22" in its scope line: a dated measurement inside a standing decision doc, which is the right shape (the decision stands, the date says how old the numbers are).

## Package 3 — backlog pruning and the changelog head

### 3.1 `docs/backlog.md` entries that are done or whose premise moved (prune or re-word)
- `:58` the shelf clause is done: `fullyQueuedAndSettled` no longer exists; both phones read kern's `ShelfControl.of` (`android/.../ui/BoxSections.kt:120`, `App/Sources/Screens/BoxAreaSection.swift:64`). The own-word draft (`ui/BoxLogic.kt:53` `OwnWordDraft.word`) and the backup file name (`ui/BackupSetting.kt:79-80` `"Spross-${pick…}${LocalDate.now()}.json"`, `BackupRow.swift:151` `BackupFile.taken`) are still platform-minted. Fix: drop the shelf clause. S, verified.
- `:67` five of the thirteen files are under 300 now: `RunScaffold.kt` 287, `Pronouncer.kt` 280, `BoxSections.kt` 297, `AppModel+Listening.swift` 292, `SessionView.swift` 246. Still over: `AppModel.kt` 453, `ListeningDriver.kt` 303, `ListeningService.kt` 306, `AppModel.swift` 397, `AnswerNormalizer.kt` 398, `NumbersRun.kt` 332, `SessionRun.kt` 325, `Catalog.kt` 317. Fix: rewrite the list. S, verified.
- `:82` "Watch snapshot 60-entry cap" — `WatchSnapshotBuilder.ENTRY_CAP = 120` (`:38`); the ~60 figure is the KB budget, not the entry count. Fix: "`ENTRY_CAP` (120)". S, verified.
- `:7` rests on a `website` branch while `web/` is on this branch (2.4). S, likely.
- `:15` restates the BY-SA counts (1.4). S, verified.
- `:33` dead pointer `docs/sync.md` (1.13). S, verified.
- `:54` still open and now reads "at 8.3.0" — the tree is 8.7.0 and `store/VerbSlugRekey.kt` still exists with "delete at 7.0+" (`VerbSlugRekeyTest.kt:11`); keep, update the version or drop it. S, verified.
- `:72` the hard-wrap list is incomplete (1.12). S, verified.

### 3.2 `docs/backlog.md` entries verified still open (pointer alive, work not done)
`:8-10` (cannot be verified from here — needs the GitHub releases), `:11-13`, `:14-16`, `:17-20` (`DateDrillTasks`, `NumberReadingIndex.INDEXED_CARDINALS` exist), `:21-23` (`MODIFY_AUDIO_SETTINGS` declared in `AndroidManifest.xml:7`), `:24-28`, `:32` (`DrillRunProgress.showsAnswer` at `DrillRunProgress.kt:58`), `:36` (`WatchModel.practiceLap` at `WatchModel.swift:285`; commit `fb7668f4` is outside this shallow clone), `:37`, `:38`, `:39-43` (stale line pointers, 1.13), `:44-48`, `:55` (all four symbols defined in `commonMain` and read only by tests: `Statistics.kt:48`, `CatalogModel.kt:130`, `WordScrambleMasking.kt:17`, `CatalogDrills.kt:56`), `:56` (`CountryDrillRun.kt` 228 lines, `DateDrillRun.kt` 253), `:57`, `:59` (`Presentation.kt:25,51,142,223` still say "v1's", "bit-exact v1 contract", "came to disagree once already"), `:63`, `:64` (`DrillFace.swift:94` `snapshot`), `:65` (`WordWidget.swift:16`, `BoxStore.swift:172`), `:66`, `:68-71` (`TurnFlow` is `android/src/main/kotlin/net/spross/app/TurnFlow.kt`), `:73-76` (`libs.versions.toml:13` compileSdk 36), `:78`, `:84` (`NumberReference.kt:213` `verticalScroll`).

### 3.3 `catalog/backlog.md`
- `:22-25` premise conflicts with the gate (1.6). S, verified.
- `:13`, `:124-127` stale line pointers (1.13). S, verified.
- `:92`, `:96-97`, `:107`, `:136` point outside the repo (1.7); `../../docs/sprachposter-learnings.md` resolves to no file anywhere. S, verified.
- `:31-33` still open: `CountryAtlas.notes` is parsed (`CountryAtlasParser.kt:79`, `CountryAtlas.kt:47`) and `dateNotes` is parsed (`DateCalendarParser.kt:35,90`); no Country screen reads `.notes`. Verified.
- `:88` still open but moved under it: 5ff3c05 made floors follow a listening pass; the entry's "refuse only the clearly bad until the dataset grows" still describes the floor. Likely.
- Everything else (`:7`, `:16-21`, `:26-30`, `:34-63`, `:67-87`, `:93-139`) has live pointers (`scripts/catalog-move.py`, `scripts/notes-vocabulary.py`, `CatalogLintTest.noConceptPairCollidesInTwoLanguages`, `PhraseTemplate.CountForms`, `swahiliNounClass`, `PhraseVocabAuditTests`, `CatalogAudioProvenanceTest.audioEntryFieldsAreWellFormed`, `catalog/areas/idioms/`, `audio-coverage.py --missing`, `audio_voices.is_squashed`) and is a decision or native-speaker item that code cannot close. Verified.

### 3.4 `CHANGELOG.md` head
- `## Unreleased` is present and empty (`:5-7`). Every entry under 8.7.0 … 8.3.0 is one English sentence; no bullet contains a second sentence (`rg '^- .*[a-z]\. [A-Z]'` empty). Two 8.7.0 bullets join clauses with a semicolon ("…so you recall it first; tap to see the options sooner"), which is within the one-sentence rule. Nothing to fix. Verified.

## Package 4 — the gates as written

### 4.1 What each gate checks, and that what it names exists (all verified)
- `scripts/hooks/pre-commit` (installed by `bootstrap.sh` via `core.hooksPath scripts/hooks`): on a staged `Localizable.xcstrings` → `strings.py --check-format` on the staged bytes and `chrome.py` drift; staged `Glance.xcstrings` → format; staged `catalog/` minus audio → `catalog-format.py --check`; any staged `.md` → `doc-header.py`; staged `catalog/audio/` → `audio-coverage.py --check`; staged card files → `card-parity.py --check`; staged `kern/src/commonMain|App/Sources|android/src/main` → `arch-status.py --check`. All seven scripts exist and take those flags.
- `scripts/hooks/commit-msg`: a `!` subject must bump `STORE_SCHEMA_VERSION` in `store/StoreDocument.kt` (it is at `:25`); matches CLAUDE.md "`!` marks a bump of `STORE_SCHEMA_VERSION`, never a catalog edit".
- `scripts/arch-status.py`: pairs kern `*Run.kt|*RunState.kt|*Machine.kt` stems with android `*Flow.kt` and iOS `*View.swift` stems; `--check` exits 1 for a platform machine with no kern run. It guards CLAUDE.md's "a platform may READ a kern decision, never MINT one".
- `scripts/strings.py --fix` restores Xcode's layout, then runs `chrome.py` (header lines 27-33) — matches CLAUDE.md:21.
- `scripts/catalog-format.py --check/--fix` owns every `catalog/` file but the generated audio manifests — matches `catalog/README.md:82-83`.
- `scripts/audio-coverage.py --check` asks `git ls-files` — matches CLAUDE.md:24 and `audio-licensing.md:20`.
- `scripts/doc-header.py` enforces the three-line head and "a standing doc never links a plan" (`docs/plans/`, `docs/archive/`, `.claude/` exempt; a backlog may link a plan).
- Every "lint-enforced / lint holds / pinned by / fails the build" claim in the docs resolves to a real test (list in 1.14); `docs/design.md:65` (chrome pre-commit) and `RUNBOOK-android.md:44` (hooks text-only) are true.

### 4.2 The release gate runs four of the hook's seven checks and says so
- Evidence: `scripts/release.sh:82-88` "# The four text lints run in the pre-commit hook … catalog-format --check, strings --check-format, chrome.py, card-parity --check"; `.github/workflows/release.yml:41-44` runs the same four. Not run by either: `doc-header.py`, `audio-coverage.py --check`, `arch-status.py --check`. `docs/distribution.md:20` says "runs the text lints" without a number.
- Why: the hook's own comment says it exists for clones that never set `hooksPath`, and a release is "where their absence would otherwise first show" — yet three of the seven are absent exactly there. `audio-coverage` has a CI backstop by accident (`everyAudioFileShipsAndIsReferencedExactlyOnce` on a checkout that only holds tracked files); a headless markdown file or a platform-only turn machine can be tagged and released.
- Fix: add the three to `release.sh` and `release.yml`, change "four" to "the hook's text lints". Size S (code: two scripts). Confidence: verified.

### 4.3 `arch-status.py` is a gate no doc states
- Evidence: `rg arch-status README.md CLAUDE.md docs kern` → nothing; it is named only in `scripts/hooks/pre-commit:73-79` and its own docstring.
- Why: CLAUDE.md:131 "A checkable rule earns a gate … and keeps its sentence for the why"; the sentence exists (the layering invariant) but nothing says there is a gate, so a session that hits it has only the hook's error text, and CLAUDE.md § Commands lists every other gate.
- Fix: one line under CLAUDE.md § Commands ("`scripts/arch-status.py` — which turn machines have one home in kern; `--check` in the hook") or in `kern/README.md §7`. Size S. Confidence: verified.

### 4.4 Two rules CLAUDE.md states as absolute have no gate and are the most-broken ones
- Evidence: "ALWAYS use **Semantic linebreaks**" (CLAUDE.md:62) — ~250 mid-clause wraps across 30 files (1.12), tracked only by a hand list in `docs/backlog.md:72`; "Max ~300 lines per file" (CLAUDE.md:53) — thirteen files over, tracked by hand in `docs/backlog.md:67` and already half stale (3.1). `doc-header.py` checks only the head; nothing counts lines.
- Why: CLAUDE.md:131 says a checkable rule earns a gate with `--fix` and a per-line waiver where it can; both are checkable (the wrap heuristic in this review is 30 lines of Python), and the hand lists demonstrably drift.
- Fix: a `--wrap` report mode in `doc-header.py` (report-only first, since the backlog itself is on the list), and a line-count report in `arch-status.py` or a sibling; or soften the two CLAUDE.md sentences to say they are reviewed, not gated. Size M. Confidence: verified (that no gate exists); the fix is a judgment call.

### 4.5 Gate claims that are correct but worded against the past
- `scripts/hooks/pre-commit:48-50` "its rule lived only in CLAUDE.md — where 78% of catalog commits shipped off-layout anyway", `:62-64` "nine of these divergences shipped …", `scripts/chrome.py:5-8` "used to be kept in step by hand and had drifted on some fifty strings", `scripts/arch-status.py:4-6`. Each is a "why" worth one clause; CLAUDE.md's "explain the current state, not the past" would trim them to the trigger and the observable result. Size S. Confidence: likely (style).

## Digest

1. `kern/README.md` links `docs/surfaces.md`, `docs/growth-evidence.md`, `catalog/README.md`, `catalog/phrases/README.md` without `../`; none resolve from `kern/` (verified, S).
2. `kern/docs/reports.md:91` points at a backlog entry (cross-shelf components in `queueableCount`) that was pruned; re-file or drop (verified, S).
3. `docs/read-aloud.md:30` says de/it ship article recordings; fr ships 26 (8.6.0 changelog agrees); `audio-licensing.md:171` + `backlog.md:15` say 2094/3597 BY-SA, manifests hold 5435/8157 (verified, S).
4. Opposites bands and the area-size line each live in two homes that disagree with the code: `turns.md` vs `drills-words.md` (nouns omitted); `areas/README.md` "~40" and `catalog/backlog.md` vs lint `areaDrawerLine = 50` (verified, S).
5. The two `2026-09-28-audio-quality-*.md` files and `2026-10-09-audio-rating-sources.md` are shipped/settled research spikes citing `/private/tmp` scratch code; DNSMOS is vendored and floors on `mos` — delete, fold one paragraph into `catalog/audio/README.md`; `audio-verdicts.tsv` is read by three scripts and belongs beside them (verified, S).
6. `docs/plans/website.md` says "parked on the `website` branch" while `web/` (site, legal pages, `.well-known`, signup form) is on this branch — delete the plan, re-word `backlog.md:7` to the empty `SIGNUP_ENDPOINT` and the missing host (verified, S).
7. `docs/backlog.md` prunes: `:58` shelf clause done (`ShelfControl.of` on both phones), `:67` five files now under 300, `:82` cap is `ENTRY_CAP` 120 not 60, `:33` `docs/sync.md` is `docs/plans/sync.md`; `catalog/backlog.md` has four stale line pointers and four pointers outside the repo (verified, S).
8. `CHANGELOG.md`: `## Unreleased` present and empty, every entry one English sentence — nothing to fix (verified).
9. Gates: all seven pre-commit checks and every "lint-enforced/pinned by" claim resolve to real scripts and tests; but `release.sh`/`release.yml` run only four ("the four text lints"), skipping `doc-header`, `audio-coverage` and `arch-status`, and `arch-status.py` is named by no doc (verified, S); semantic linebreaks and the 300-line cap are stated as absolutes with no gate while being the most-broken rules — ~250 mid-clause wraps in 30 files, `backlog.md:72` lists six (verified, M).
10. Past-justifying prose to cut: `growth-evidence.md:137-139` explains a removed "health gate"; `turns.md:20-22`, `build.md:21` ("Release archive smoke check" — nothing local archives), and `catalog/README.md:18-19` still frames the catalog as German-authored three pairs (verified/likely, S).
