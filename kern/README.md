# SprossKern — engine contract

The standing contract for the Kotlin Multiplatform core (`:kern`):
scheduling, growth, sessions, grading, snapshots.
Grew out of the 2026-07 KMP rewrite (per-unit deviations were adversarially reviewed;
the 2026-07-22 presentation-model rulings are folded into §3/§4).
App-layer UX rules stay in `../docs/design.md`; this doc owns the engine.
Product frame (overrides v1 where they conflict):
any source (known) / target (learning) language pair from the catalog;
no user-facing direction concept;
progress tracked per target language;
`net.spross.app` / Spross branding; pre-production — no data-format preservation.

## 1. Languages & profile

- `Language` = string code from `catalog/languages.json` — open set, no enum.
- `LanguageInfo(code, name, englishName, flag, optionalVerbPrefixes, articles)` —
  per-language metadata from `catalog/languages.json` (field semantics: `catalog/README.md`);
  `articles` replaces v1's hardcoded German article list.
- Profile = (source, target), source ≠ target.
  `Catalog.availableTargets(source)` requires ≥ 50 joinable concepts.
  (Picker display and the device-language default are app rules — `../docs/design.md`.)

## 2. Card — derived, language-symmetric

```kotlin
data class Card(              // data class: Swift sees value equality (SwiftUI diffing)
  val id: String,             // the concept's catalog slug — never contains '|' or '/'
  val kind: CardKind,         // noun | verb | adjective | phrase
  val area: String,
  val emoji: String?,
  val seedIndex: Int,
  val components: List<String>,
  val feminineOf: String?,
  val baseAccepted: List<String>, // base concept's TARGET texts (feminine cards only, else empty)
  val source: Realization,    // known-language side
  val target: Realization,    // learning-language side
  val promptFeminineMarker: Boolean,
  val promptAmbiguous: Boolean, // another card shows the IDENTICAL produce prompt
)
data class Realization(
  val lang: String, val text: String,
  val synonyms: List<String>,   // alternates: rotate as recognition prompt forms, shown on reveal
  val variants: List<String>,   // accepted surface forms → grading only, never prompted
  val grammar: Map<String, String>,
  val note: String?,            // already selected: notes[source] ?: null — UI cannot leak
)
```

- Cards derive at load from the catalog join; **never persisted**.
- **Identity is the slug alone** — globally unique across areas, lint-enforced
  (`catalog/README.md`). `area` and `kind` are presentation metadata the content may
  restructure freely: moving or reclassifying a concept keeps its schedule, because the
  id it is keyed by never mentions either. `components` and `feminineOf` are card ids
  (bare slugs) for the same reason.
- **Join rule**: emit iff TARGET realizes the concept AND a source prompt exists:
  source realization, else (feminineOf only) the base concept's source realization with
  `promptFeminineMarker = true`; if the base's source realization is also absent, skip.
  Non-feminine concepts without a source realization are skipped.
  A feminine card additionally carries `baseAccepted` — the base concept's TARGET-side
  `text ∪ synonyms ∪ variants` — empty when the target never realizes the base.
- **Homonyms / target-language merges**: after emitting, the join counts cards per
  *displayed* prompt key — NFC-normalized `source.text` plus the ♀ state — and sets
  `promptAmbiguous` on every member of a key shared by >1 card. Keying on what the learner
  SEES means citation conventions (de noun capitals, en `"to "`, sw `ku-`) correctly keep
  noun/verb homographs apart (`Husten`/`husten`, `jua`/`kujua`), and a ♀ sibling is already
  disambiguated by its badge. The residue is real: Swahili merges pairs German splits
  (`kuvaa` = anziehen + sich anziehen, `kupumzika` = 3 concepts), so an sw-source learner
  gets prompts no cue in the answer language could resolve. Produce-side only — see §3.
- **Notes**: selected by SOURCE language at join time, no cross-language fallback
  (a de note never surfaces for an en-source user; non-de sources are note-less until authored).
- **Grammar display is target-side only**: plural line and article coloring render only for
  the target realization (v1's "plural only for learners OF German", generalized).
  Suffix plurals render dictionary-style "Lehrerin, -nen"; sentinels "=" → "= Pl.",
  "only" → "nur Pl." via localized chrome strings, not hardcoded German.

## 3. One schedule per card, alternating presentation   (user ruling 2026-07-22)

**ONE FSRS schedule per card, keyed by card id** (ids never contain `|`).
No per-role or per-form scheduling —
production and recognition are PRESENTATIONS of the same memory,
both feeding the one schedule ("every answer event is an FSRS review" holds).
This is v1's `mixedDirections` model kept as the ONLY mode:
no config flag, no user-facing direction anywhere.

- **PRODUCE**: prompt = source text (+ ♀ badge when marked), typed answer in target.
  Accepted: target `text ∪ synonyms ∪ variants`, article-optional (target articles),
  verb-prefix-optional (`kind == verb` only).
  Synonyms show on reveal as alternates ("auch: відомство"); variants stay silent.
  When `promptAmbiguous`, the prompt carries the card's **area label** as a secondary
  context line ("Im Bad", "Jikoni") — free of leakage because it is in the PROMPT language
  while the answer is in the other, and it is the retrieval cue the learner actually has
  (the box teaches per area). Generalizes the ♀-badge pattern; never graded.
- **RECOGNIZE**: prompt = one target form, **reveal + self-grade** (Again/Hard/Good/Easy —
  never typed; comprehension check, and self-grading means no schedule is ever graded
  against a language it wasn't learned with).
  Phrases alternate too — self-graded sentence recognition is legitimate comprehension
  practice; only TYPED phrase recognition was absurd.
  **Never carries the `promptAmbiguous` area cue**: here the prompt is the target form, so
  any cue strong enough to identify the concept would reveal the answer — the same reason
  the emoji matrix hides the emoji on recognition measurement reviews. Nothing is lost:
  recognition is self-graded, so a learner who thinks "sich entspannen", reveals
  "sich ausruhen" and taps Good is doing exactly what self-grading is for.
- **Role resolution** is a pure render-time function of `(cardId, log.count)`:
  - First exposure (`count == 0`) is ALWAYS recognition — the learner cannot produce a
    word never seen; the target is PROMPTED first (a learner who already knows it deserves
    the moment to recall it) WITH its emoji as the cue, and the reveal teaches the meaning,
    self-graded. An honest Again lands in the 1 m learning step and returns the same
    session. (Matches v1's `presentationDirection` first-exposure rule.)
  - The second review (`count == 1`) is ALWAYS production — seen once, now attempt it
    (ruling 2026-07-22: "returns the same session as production", both hash parities).
  - From `count == 2` role = parity(`count` + FNV-1a-64(cardId)):
    FNV-1a 64-bit over UTF-8, offset `0xcbf29ce484222325`, prime `0x100000001b3` —
    bit-exact v1 port; the per-card phase offset keeps the box from flipping in sync.
- **Synonym rotation** on recognition prompts: the prompted form cycles deterministically
  through `text` + `synonyms` — index = (`count / 2` + id-hash offset) mod formCount
  (parity-independent: recognition happens every other review); first exposure always
  prompts canonical text; variants never rotate.
  Every form gets prompted at zero extra scheduling cost.
  Reveal always shows the full family; the source-side reveal may show source synonyms
  informatively ("Amt / Verwaltung").
- **Emoji matrix**: visible iff (first exposure) OR (role == Produce ∧ phase == Learning);
  hidden on recognition measurement reviews (`count > 0` — it depicts the answer)
  and from Review/Relearning on (v1's hide-after-learning rule).
  The first exposure is the one recognition prompt that carries it, deliberately:
  it is the cue that makes a first recall attempt possible at all.
- **♀** is a labeled badge, never graded: the production prompt shows source base + badge;
  the recognition reveal decorates the source answer with the badge.
  A base-word answer typed on a feminine produce card grades as typo, not failure
  (graded against `Card.baseAccepted`; corrected shows the feminine canonical text).
- Composition is **role-agnostic** — plans carry card ids; the role of each entry is
  resolved at render from the card's log count.
- Scheduling keys are source-agnostic → **switching source preserves every schedule**.

## 4. Denomination — everything in cards

v1 calibration restored (one schedule per card ⇒ one review touches one card):

| Quantity | Default (all in cards) |
|---|---|
| `maxLearning` (cards in Learning phase) | 8 |
| `sessionCap` / `dueSoftCap` | 30 / 30 |
| `growthReserve` | ≤ 5 |
| Relearning-share gate | < 20 % of active, once active ≥ 10 |

Every user-facing count (due ring, "x neu", active, widget) and `DayStats` field is in
cards; `DayStats.reviews` = answer events.

## 5. FSRS-6

- 21 weights; defaults = ts-fsrs v5.4.1 / py-fsrs v6.3.1 (identical), **w20 decay 0.1542**
  (brief's 0.2 was a pre-release value). Formula set + cross-check resolutions per the
  pinned reference report: same-day `sinc ≥ 1` mask for G ≥ Hard, S_MIN 0.001, fuzz OFF,
  engine maximum_interval 36500.
- `elapsedDays` = fractional `max(0, (now − lastLog)/86400)`; short-term path < 1.0.
  Copied vectors all review exactly at due where conventions agree; ts-only real-timestamp
  vectors are excluded from the port.
- Steps are config; **product config = reference defaults**: `learning [1m, 10m]`,
  `relearning [10m]` — golden vectors run verbatim.
  **No in-session lapse retry** (breadth ruling 2026-07-22): a lapsed review card returns
  after 10 m, typically next session; the drain loop does not wait for it.
  Graduation follows the reference machine (one step later than v1's hand-rolled steps —
  accepted, tested against the pinned minute tables).
- Desired retention: engine default 0.9 (vector anchor); product `BoxConfig` 0.8, no slider.
  Product maximum interval 365.
- Leech: lapse counted iff `phase == review && rating == again`; 8 → auto-suspend (per card).
- **`phraseUnlockStability` = 2.0** (recalibrated: FSRS-6 S0(Good) = 2.3065 crosses at
  graduation like v1's FSRS-5 3.0 did; S0(Hard) 1.29 doesn't).
- Golden vectors copied verbatim from the pinned releases with PROVENANCE (repo/tag/SHA);
  FSRS-6 property tests re-express the v1 property suite. Weight optimization stays out.

## 6. Box / Session semantics (deltas from the v1 port map)

Everything in the engine scout map ports 1:1 (budgets, health gate, growth-reserve formula,
introduction = first answer, silent answer drop, drain, extra round, endless, exposure tiers,
statistics, streak forgiveness, endSession fold + 60-day prune, deterministic orderings,
day-key `yyyy-MM-dd`) with:
- **Introduction is the card's first answer** (v1 semantics; the unit-era eligibility lag
  and one-per-plan rules are gone with the unit model). `enqueued` holds card ids;
  enqueued cards lead composition, bypass the health gate, respect the pool throttle,
  and dequeue at introduction.
  Zero-component phrases follow seed order, never the unlock fast path (v1 rule restated).
- **Phrase unlock** reads each component's schedule **by card id** — join- and
  source-independent, so a source switch can never re-lock phrases. Components with no
  TARGET realization are excluded from the gate (v1 unresolved-component semantics).
  Gate: review phase, not suspended, stability ≥ 2.0.
- **Join filter inventory**: composition, dueNow, dueCount, statistics, exposure operate on
  cards that join the current profile; the unlock check and `answer()` history reads
  operate on raw schedules by id. Non-joining schedules and enqueued entries are kept
  **inert** (never pruned; both revive on switch-back).
- `answer(cardId, rating, nowMillis, tzId)` on a non-joining or unknown id is a defined
  no-op (`AnswerStatus.StaleCard`) the UI skips past. `SessionPlan` carries a
  `joinStamp` (source, target, catalog fingerprint); the app recomposes when stale.
- `setSuspended(cardId)` — per card, as v1.
- **Exposure**: one entry per card by construction (tiers as v1); display surfaces always
  render the TARGET realization.
- **AnswerNormalizer contract** (produce only — recognition is button self-grade;
  catalog-fixture tested with "Kwaheri!", "to cook", "Der Kühlschrank ist leer.",
  "Мене звуть …"):
  normalize both sides (lowercase, ß→ss, delete joiners `-'’`, punctuation → space incl.
  `…—`, collapse whitespace) → ONE leading listed article of the answer language optional
  on both sides → iff `kind == verb`: any listed `optionalVerbPrefixes` entry (normalized
  the same way, space-preserving — en `"to "`) optional on both sides → Damerau-Levenshtein
  typo budget (v1 formula) → article-mismatch-demotes-to-typo only when the expected
  answer's grammar carries `gender`; stray-short-leading-word rule unchanged.
  Article leniency is constructor-opt-out for drill grading:
  `AnswerNormalizer(language, articleLeniency = false)` keeps the article in `normalize`
  and only matches a form whose leading article equals the typed one —
  wrong or missing article grades Wrong (never typo-bridged);
  the one-arg init stays the lenient vocab-review default (both inits in the ObjC header).
  The typo budget is likewise constructor-clamped for drill grading:
  `AnswerNormalizer(language, articleLeniency, maxTypoBudget = 1)` caps the v1 formula
  and grades digit-bearing accepted forms exact-only
  ("21"/"29", "18:05"/"18:06" sit one edit apart at any sentence length);
  the cardinal sweep (TrainerTypoBridgeGuardTests) proves budget 1 never bridges
  two distinct German 0–999 cardinals;
  audited exceptions within one edit — sw `nne`↔`nane` (4↔8, incl. tens compounds)
  and uk `дев'ять`↔`десять` (9↔10) — are gated explicitly in the sweep.
  Vocab reviews (`maxTypoBudget = null`, the default) keep the v1 budget untouched.

## 7. Store & snapshots

- One document per TARGET: `box-<target>.json` in App Group `group.net.spross.app`.
  `BoxDocument { schemaVersion: 1, target, source, config, scheduling, enqueued,
  newIntroduced, dailyStats }` — scheduling keys are card ids;
  kotlinx.serialization; dates as ISO-8601 UTC strings via explicit `kotlin.time.Instant`
  serializers; facade encodes with **sorted keys** (deterministic bytes).
  All `@Serializable` types are `internal`; the public surface is a narrow facade
  (`encode/decode` — no `migrate()` until a schema v2 exists) — keeps the ObjC header
  small (probe showed serialization internals otherwise flood it).
- Engine boundary time: `nowEpochMillis: Long` + `tzId: String` (kotlinx-datetime 0.8 has
  no Swift-Date bridging; Instant/TimeZone are constructed inside). TimeZone = device-current
  per call (v1 parity). Day keys are ISO regardless of device calendar (v1 latent
  non-Gregorian bug fixed; DST + non-Gregorian vectors in the test suite).
- **WidgetSnapshot** (NEW): the phone precomputes on every persist; the iOS widget is
  decode-only Swift (an extension cannot run the join: no catalog in its bundle, ~30 MB
  memory cap vs 33 MB measured Kotlin debug framework). Contents: pre-resolved exposure
  entries (target-side text, emoji, article tint), per-card `{due, stability,
  lastReview, review}` for render-time `dueCount(now)`/`averageRetrievability(now)`
  (the average runs over `review`-phase cards; retrievability
  power curve duplicated in Swift with the w20 constant, documented), dailyStats tail
  (~70 days) for the streak walk, `schemaVersion`. Built by a KMP `SnapshotBuilder`,
  written by the app.
- **WatchSnapshot v2**: direction/pair/`german` are gone — one entry per CARD with BOTH
  sides pre-resolved: `{cardId, sourceText, targetText, accepted[], emoji?, articleTint?,
  femMarker, due, stability, nextRole, promptForm}` + `schemaVersion`.
  The phone resolves `nextRole` and the rotated `promptForm` from the log count at build
  time; presentation is the app layer's (`../docs/design.md` §Watch & widgets)
  and `emoji` is pre-gated by the §3 matrix.
  Ranking is **due-first** (a due card is never evicted by a non-due lower tier), then
  exposure tiers, capped at 60 entries (the ~60 KB `updateApplicationContext` limit).
  `make` lives phone-side; watch stays pure Swift.

## 8. Catalog schema additions (same-series migration)

- `languages.json`: `articles` (§1).
- `areas.json`: a group's `areas` is an array of **objects** (`{ "area", "emoji" }`),
  no longer bare strings. The emoji is language-neutral display metadata,
  so the catalog now owns the area icon that was a hardcoded map in the iOS app
  and both apps read the same one.
  The parser rejects unknown keys and validates the emoji with the concept-emoji rule
  (non-blank, ≤ 12 chars, every char ≥ U+2000), so a new area cannot ship without one.
  `AreaGroup.areas: [String]` is unchanged — the ordered names every consumer flat-maps;
  the emoji rides alongside in `AreaGroup.areaEmojis: [String: String]` and is read via
  `Catalog.areaEmoji(area) -> String?`, the language-neutral sibling of `areaTitle`.
- Realization: `variants: [String]` next to `synonyms` — a **display/accept distinction
  only**, never a scheduling one (§3): synonyms rotate as recognition prompt forms and
  show on reveal; variants are accepted silently and never prompted. Migration:
  uk gender-agreement/diminutive/internationalism entries (завела, мишка, контракт,
  компанія) move synonyms → variants; the 14 slash-joined de Sie/du texts become
  `text` = Sie-form + `variants` = [du-form] (embedded `" / "` was untypeable).
- `catalog/README.md` updated; **CatalogLintTest** (permanent, on the real catalog) enforces:
  parse/shape/order rules, slug charset (no `|`), seedIndex uniqueness, synonyms ≠ text,
  no duplicate synonym/variant entries, no `" / "` in text, components resolve same-area,
  feminineOf resolves, concept emoji well-formed, every manifest area carries an emoji.
- **Homonym gates** (no schema field — the area label is the disambiguator, §2/§3).
  Lint owns what the engine cannot fix, runtime tolerates the rest:
  - `noPromptCollisionWithinAnArea` — a display-identical prompt inside ONE area is a hard
    error: the area cue would be identical, so the prompt stays unanswerable. Fix in content.
  - `noConceptPairCollidesInTwoLanguages` — the same pair colliding in two languages means
    one meaning authored twice; unify (the `variantOf` ruling). Caveat when it fires: check
    it is not simply two languages independently merging a distinction de/en do draw —
    `relax`/`rest` collided in sw AND uk while de (sich entspannen/sich ausruhen) and en
    keep them apart, so the fix was a precise uk realization, not a deletion.
  - `crossAreaPromptCollisionsAreKnown` — pins the tolerated cross-area set, so adding
    `outside/river` next to `bedroom/pillow` (both sw `mto`) fails the gate instead of
    silently minting an ambiguous prompt. Comparison is case-SENSITIVE: `Husten`/`husten`
    is a real visual distinction and must stay legal.

## 9. KMP project & Apple integration

- Gradle root `app/` (wrapper committed; `.gitignore` += `build/`, `.gradle/`, `.kotlin/`,
  `local.properties`); module `:kern` at **`app/kern`** (`kern/` at root is the same
  APFS inode as Swift `Kern/` — never create it). Package `net.spross.kern`
  (+ `.trainer`). Pins (probe-proven, Xcode 26.6): Kotlin **2.4.0** (SKIE 0.10.13's ceiling —
  bump only as a pair; comment in the version catalog), serialization 1.11.0,
  datetime 0.8.0, Gradle 9.6.1, JDK 17. Configuration cache on.
- Targets: `jvm()` (fast gate + Android-ready), `iosArm64`, `iosSimulatorArm64` — static
  framework **SprossKern**. No watchOS targets (nothing links Kotlin on watch; 3 unused
  slice builds cost ~40–60 % of every kern-edit rebuild, measured 23.7 s → target ≈ 10 s).
- Xcode: the app target links the framework directly (`FRAMEWORK_SEARCH_PATHS`, no SwiftPM
  binaryTarget — wrong build ordering + clean-checkout deadlock). An in-target xcodegen
  `preBuildScripts` phase branches on `$CONFIGURATION`/`$SDK_NAME`, runs the matching
  `linkDebug/ReleaseFramework<Target>` Gradle task, and copies the framework to a
  configuration-neutral search path. `scripts/bootstrap.sh` for fresh clones; a Release
  archive smoke check joins the gates. Only the APP target links Kotlin; widget/watch/
  complication are decode-only Swift (§7).
- Swift ergonomics: UI-crossing Kotlin types are data classes; a small Swift bridge file in
  App/Sources adds `Date ↔ epochMillis` helpers and `Identifiable`/`Equatable`
  conformances; Kotlin `Int` surfaces as `Int32` — bridge there, not at call sites.
- Trainer: single `:kern` module, `Long` cardinals everywhere (Kotlin `Int` is 32-bit on
  all platforms — v1's arm64_32 fix generalizes). Trainer registry: de/sw/uk authored,
  en absent (the hub's handling of that is an app rule — `../docs/design.md`).
  Phrase templates keyed (source, target); reverse mode when target == de.
  German clock ACCEPTS 24-hour readings ("achtzehn Uhr fünfunddreißig", "null/vierundzwanzig
  Uhr" at midnight) alongside the colloquial display forms; display stays 12-hour.
  An hour word directly before "Uhr" apocopates: "ein Uhr", never "eins Uhr";
  bare "eins" stays ("punkt eins", "um eins", "halb eins").
  `PhraseSlots` samples level-aware — same per-kind ramp tables as the plain drills
  (a template's slot kind clamps the level).
  The unleveled `sample` overload keeps the prototype's biased full-difficulty draws
  (numbers favor 2–3 digits, years cluster 1950–2050);
  only Clock's unleveled draw coincides with the leveled ceiling.
  Android: landed — `androidLibrary` KMP target
  (`com.android.kotlin.multiplatform.library`, AGP 9.3.0, compileSdk 36 / minSdk 26),
  androidMain NFC actual mirrors jvmMain; `:android` consumes the same facades.
  Gate: `./gradlew :kern:compileAndroidMain`.

## 10. Testing & gates

- Fast gate: `./gradlew :kern:jvmTest` (replaces `cd Kern && swift test` — CLAUDE.md/README
  update in the same series). iOS gate: xcodegen + `xcodebuild -scheme Spross build` +
  simulator run-through. Release archive smoke.
- Ported suites per the engine scout inventory, with the **FSRS-6 adaptation table**:
  relearning entry = reference 10 m (no in-session retry — drain assertions adapted),
  learning Hard = 6m at step0 / ×1.5 single-step, new+Again+Good does not graduate
  (needs a further Good), graduation intervals from FSRS-6 S0, day-one introduces 8 cards;
  direction-scoped statistics tests are obsolete; v1 MixedDirectionTests port as
  bit-exact `presentationRole` FNV vectors; everything else behavioral ports 1:1.
- New suites: CatalogLintTest (§8), parser fixtures (feminine ♀ fallback, Sie/du variants,
  sparse coverage, en "to "/sw ku-kw prefixes, notes selection),
  first-exposure-always-recognition + emoji-policy matrix + synonym-rotation coverage,
  join-inertness + source-switch round-trip (schedules + enqueued revive; phrases stay
  unlocked), stale-card answer no-op, FSRS-6 golden vectors + properties,
  DST/non-Gregorian day-key vectors, snapshot builders.

## Deliberately dropped (recorded)

- Per-role/per-form scheduling — `Role`-as-schedule, `UnitKey`, recognize eligibility lag,
  one-unit-per-card-per-plan (user ruling 2026-07-22: one schedule per card).
- Typed recognition (user ruling 2026-07-22: self-grade only — the panel's paraphrase
  finding stands) and the phrase-recognition exclusion (phrases alternate, self-graded).
- In-session lapse retry (breadth ruling 2026-07-22: relearning = reference `[10m]`).
- `variantOf` (user ruling 2026-07-22: the 4 near-duplicate phrase twins were unified
  instead — base slug keeps an adapted realization; schema field deleted everywhere).
- Homonym disambiguation as **content**: a per-realization `sense`/`gloss` string and a
  concept-level `homonymOf`/`disambiguator` link. Both rejected — the area label already
  carries it for free, in every language, lint-guaranteed to exist; `sense` would be a new
  authored field for ~9 entries, and `homonymOf` encodes at concept level a fact that is
  per-language (`kupumzika` is ambiguous in sw only) and rots as languages are added.
  Also rejected: emoji-as-cue (12 of 13 colliding concepts are verbs, which carry no emoji,
  and the matrix deliberately hides emoji exactly where the ambiguity bites), cluster-wide
  grading leniency (accepting any cluster member teaches away the distinction the learner
  is there to acquire; if a same-area cluster ever proves unfixable, revisit as `Typo`,
  never `Exact`), and suppressing/deferring a cluster member (breaks composition
  determinism to hide a content problem, and the collision returns once both are learned).
- `Direction`, `mixedDirections` as a flag (alternation is the only mode), `LanguagePair`,
  `id|direction` keys, per-pair store docs, slugified de-centric card ids, persisted Cards,
  reconcile upsert half, the `"/"`-join↔split grading contract,
  v1 immersion subtitle for chrome-less targets (kept for de/en),
  Swift DuoKern + FSRS-5 vectors + DuoKernTrainer product split, watch Kotlin linkage.
