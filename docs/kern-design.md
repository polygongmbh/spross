# SprossKern — KMP core design (v2 engine contract)

The implementation contract for the Kotlin Multiplatform core replacing Swift DuoKern.
Supersedes `kmp-rewrite-brief.md` where they conflict (deviations were adversarially reviewed).
App-layer UX rules stay in `design.md`; this doc owns the engine.
Product frame (overrides v1 where they conflict):
any source (known) / target (learning) language pair from the catalog;
no user-facing direction concept;
progress tracked per target language;
`net.spross.app` / Spross branding; pre-production — no data-format preservation.

## 1. Languages & profile

- `Language` = string code from `catalog/languages.json` — open set, no enum.
- `LanguageInfo(code, name, optionalVerbPrefixes: List<String>, articles: List<String>)`.
  `articles` is a NEW languages.json field (de `["der","die","das","ein","eine"]`,
  en `["the","a","an"]`; absent sw/uk) — replaces v1's hardcoded German article list.
- Profile = (source, target), source ≠ target.
  `Catalog.availableTargets(source)` requires ≥ 50 joinable concepts;
  pickers show concept counts; default source = device language when covered, else en.

## 2. Card — derived, language-symmetric

```kotlin
data class Card(              // data class: Swift sees value equality (SwiftUI diffing)
  val id: String,             // "area/slug" — concept identity; never contains '|'
  val kind: CardKind,         // noun | verb | phrase
  val area: String,
  val emoji: String?,
  val seedIndex: Int,
  val components: List<String>,
  val feminineOf: String?,
  val source: Realization,    // known-language side
  val target: Realization,    // learning-language side
  val promptFeminineMarker: Boolean,
)
data class Realization(
  val lang: String, val text: String,
  val synonyms: List<String>,   // distinct-knowledge alternates → own recognize units
  val variants: List<String>,   // accepted surface forms → grading only, never scheduled
  val grammar: Map<String, String>,
  val note: String?,            // already selected: notes[source] ?: null — UI cannot leak
)
```

- Cards derive at load from the catalog join; **never persisted**.
- **Join rule**: emit iff TARGET realizes the concept AND a source prompt exists:
  source realization, else (feminineOf only) the base concept's source realization with
  `promptFeminineMarker = true`; if the base's source realization is also absent, skip.
  Non-feminine concepts without a source realization are skipped.
  `variantOf` concepts (see §8) are skipped when their base also joins.
- **Notes**: selected by SOURCE language at join time, no cross-language fallback
  (a de note never surfaces for an en-source user; non-de sources are note-less until authored).
- **Grammar display is target-side only**: plural line and article coloring render only for
  the target realization (v1's "plural only for learners OF German", generalized).
  Suffix plurals render dictionary-style "Lehrerin, -nen"; sentinels "=" → "= Pl.",
  "only" → "nur Pl." via localized chrome strings, not hardcoded German.

## 3. Exercise units

One concept yields:
- **PRODUCE** (always): prompt = source text (+ ♀ badge when marked), typed answer in target.
  Accepted: target `text ∪ synonyms ∪ variants`, article-optional (target articles),
  verb-prefix-optional (`kind == verb` only). ONE unit per card.
- **RECOGNIZE** (nouns/verbs only, NEVER phrases): prompt = one target form,
  **reveal + self-grade** (Again/Hard/Good/Easy — no typing; comprehension check, and
  source-switch can never regrade it). One unit per **synonym-class form**: canonical text
  and each entry of `synonyms` (NOT `variants` — those rotate as display alternates of the
  canonical unit, least-recently-shown first).
- **Eligibility lag**: a card's recognize units become introducible only once its produce
  unit is in `review` phase. Kills first-rating echo contamination and halves day-one pool
  pressure. Composer additionally enforces **at most one unit per card per composed plan**;
  the drain loop is exempt (learning steps of both may legitimately interleave).
- **Emoji policy**: never rendered on recognize prompts (it depicts the answer);
  produce keeps v1's hide-after-learning rule. ♀ is a labeled badge, never graded;
  on recognize reveals it decorates the source answer text. A base-word answer typed on a
  feminine produce card grades as typo, not failure.
- **Unit keys** (persisted): `id|produce`; `id|recognize|<form>` for EVERY recognize unit,
  canonical form included (form-agnostic canonical keys mis-attach history when a curator
  swaps text↔synonym). `<form>` = NFC-normalized, trimmed, whitespace-collapsed,
  `|`/newline-stripped form text (Cyrillic-safe; slugify would destroy it).
  `UnitScheduling` stores `cardId`/`role`/`form` explicitly; the map key is derived,
  validated on decode.
- **Unit order** (fully pinned): `(seedIndex, cardId, roleRank produce=0 recognize=1,
  formIndex in catalog order)`. Map iteration order never leaks (v1 invariant).
- Keys are source-agnostic → **switching source preserves every schedule**; recognize is
  self-graded so no schedule is ever graded against a language it wasn't learned with.

## 4. Denomination — growth in concepts, workload in units

| Quantity | Unit of account | Default |
|---|---|---|
| Learning pool (`maxLearning`) | **concepts** with any unit in learning | 8 (day-one: 8 new concepts, v1 parity) |
| `sessionCap`, `dueSoftCap` | **units** (answer events ≈ time; recognize answers are fast) | 30 / 60 |
| `growthReserve` | units | ≤ 5 |
| Relearning-share gate | units | < 20 % of active, once active ≥ 10 |
| Every user-facing count (due ring, "x neu", active, widget) | **concepts** | — |
| `DayStats` | reviews = answer events; introduced = concepts (produce intro); activeCount = concepts | — |

Steady-state math (documented trade): ~2 units/word ⇒ per-concept review load ≈ 2× v1,
but each concept is held in both skills instead of v1's alternating shared schedule;
`dueSoftCap` 60 units ≈ v1's 30-card backlog in concept terms.

## 5. FSRS-6

- 21 weights; defaults = ts-fsrs v5.4.1 / py-fsrs v6.3.1 (identical), **w20 decay 0.1542**
  (brief's 0.2 was a pre-release value). Formula set + cross-check resolutions per the
  pinned reference report: same-day `sinc ≥ 1` mask for G ≥ Hard, S_MIN 0.001, fuzz OFF,
  engine maximum_interval 36500.
- `elapsedDays` = fractional `max(0, (now − lastLog)/86400)`; short-term path < 1.0.
  Copied vectors all review exactly at due where conventions agree; ts-only real-timestamp
  vectors are excluded from the port.
- Steps are config: engine defaults = reference (`learning [1m,10m]`, `relearning [10m]`)
  so golden vectors run verbatim. **Product config: `relearning [1m]`** — preserves v1's
  in-session retry (reference `[10m]` would end the drain loop before a lapsed card
  returns; nobody chose that regression). Learning stays `[1m,10m]`; graduation follows
  the reference machine (one step later than v1's hand-rolled steps — accepted, tested
  against the pinned minute tables, adapted assertions listed in the test port table).
- Desired retention: engine default 0.9 (vector anchor); product `BoxConfig` 0.8, no slider.
  Product maximum interval 365.
- Leech: lapse counted iff `phase == review && rating == again`; 8 → auto-suspend (per unit).
- **`phraseUnlockStability` = 2.0** (recalibrated: FSRS-6 S0(Good) = 2.3065 crosses at
  graduation like v1's FSRS-5 3.0 did; S0(Hard) 1.29 doesn't).
- Golden vectors copied verbatim from the pinned releases with PROVENANCE (repo/tag/SHA);
  FSRS-6 property tests re-express the v1 property suite. Weight optimization stays out.

## 6. Box / Session semantics (deltas from the v1 port map)

Everything in the engine scout map ports 1:1 (budgets, health gate, growth-reserve formula,
introduction = first answer, silent answer drop, drain, extra round, endless, exposure tiers,
statistics, streak forgiveness, endSession fold + 60-day prune, deterministic orderings,
day-key `yyyy-MM-dd`) with:
- **Introduction is per UNIT** at its own first answer. A concept counts introduced at its
  produce introduction. `enqueued` holds concept ids; the produce unit inherits enqueued
  priority (+ health-gate bypass, load-throttled); the concept dequeues when its produce
  unit introduces (v1: dequeue at answer). Recognize units arrive later via normal seed
  order (they're backfill, and lower seedIndex naturally leads new material).
  Zero-component phrases follow seed order, never the unlock fast path (v1 rule restated).
- **Phrase unlock** reads each component's `id|produce` schedule **by key** — join- and
  source-independent, so a source switch can never re-lock phrases. Components with no
  TARGET realization are excluded from the gate (v1 unresolved-component semantics).
  Gate: review phase, not suspended, stability ≥ 2.0.
- **Join filter inventory**: composition, dueNow, dueCount, statistics, exposure operate on
  units whose card joins the current profile; the unlock check and `answer()` history reads
  operate on raw schedules by key. Non-joining schedules and enqueued entries are kept
  **inert** (never pruned; both revive on switch-back).
- `answer(unitKey, rating, nowMillis, tzId)` on a non-joining or unknown key is a defined
  no-op (`AnswerOutcome.staleUnit`) the UI skips past. `SessionPlan` carries a
  `joinStamp` (source, target, catalog fingerprint); the app recomposes when stale.
- `setSuspended(unitKey)` — per unit; Box browser rows headline the weakest role's state,
  expand to per-unit rows, revive per unit.
- **Exposure**: rank units (tiers as v1), dedupe by card keeping the unit with lowest
  `(tier, order, key)`, apply `limit` AFTER dedup; display surfaces always render the
  TARGET realization regardless of winning role.
- **AnswerNormalizer contract** (produce only; catalog-fixture tested with "Kwaheri!",
  "to cook", "Der Kühlschrank ist leer.", "Мене звуть …"):
  normalize both sides (lowercase, ß→ss, delete joiners `-'’`, punctuation → space incl.
  `…—`, collapse whitespace) → ONE leading listed article of the answer language optional
  on both sides → iff `kind == verb`: any listed `optionalVerbPrefixes` entry (normalized
  the same way, space-preserving — en `"to "`) optional on both sides → Damerau-Levenshtein
  typo budget (v1 formula) → article-mismatch-demotes-to-typo only when the expected
  answer's grammar carries `gender`; stray-short-leading-word rule unchanged.

## 7. Store & snapshots

- One document per TARGET: `box-<target>.json` in App Group `group.net.spross.app`.
  `BoxDocument { schemaVersion: 1, target, source, config, scheduling, enqueued,
  newIntroduced, dailyStats }` — kotlinx.serialization; dates as ISO-8601 UTC strings via
  explicit `kotlin.time.Instant` serializers; facade encodes with **sorted keys**
  (deterministic bytes). All `@Serializable` types are `internal`; the public surface is a
  narrow facade (`encode/decode/migrate`) — keeps the ObjC header small (probe showed
  serialization internals otherwise flood it).
- Engine boundary time: `nowEpochMillis: Long` + `tzId: String` (kotlinx-datetime 0.8 has
  no Swift-Date bridging; Instant/TimeZone are constructed inside). TimeZone = device-current
  per call (v1 parity). Day keys are ISO regardless of device calendar (v1 latent
  non-Gregorian bug fixed; DST + non-Gregorian vectors in the test suite).
- **WidgetSnapshot** (NEW): the phone precomputes on every persist; the iOS widget is
  decode-only Swift (an extension cannot run the join: no catalog in its bundle, ~30 MB
  memory cap vs 33 MB measured Kotlin debug framework). Contents: pre-resolved exposure
  entries (deduped, target-side text, emoji, article tint), per-unit `{due, stability,
  lastReview}` for render-time `dueCount(now)`/`averageRetrievability(now)` (retrievability
  power curve duplicated in Swift with the w20 constant, documented), dailyStats tail
  (~70 days) for the streak walk, `schemaVersion`. Built by a KMP `SnapshotBuilder`,
  written by the app.
- **WatchSnapshot v2**: direction/pair/`german` are gone — entries are pre-resolved
  `{unitKey, prompt, answer, accepted[], emoji?, articleTint?, due, stability}` +
  `schemaVersion`; capped/deduped (unit growth would cross the 60 KB
  `updateApplicationContext` limit sooner). `make` lives phone-side; watch stays pure Swift.

## 8. Catalog schema additions (same-series migration)

- `languages.json`: `articles` (§1).
- Realization: `variants: [String]` next to `synonyms` (§3 split). Migration:
  uk gender-agreement/diminutive/internationalism entries (завела, мишка, контракт,
  компанія) move synonyms → variants; the 14 slash-joined de Sie/du texts become
  `text` = Sie-form + `variants` = [du-form] (embedded `" / "` was untypeable).
- Concept: `variantOf: <slug>` on the 4 en-realized near-duplicate phrase twins
  (kitchen pot, desk laptop/wifi, hall doorbell); join skips a `variantOf` concept when its
  base joins (en profiles would otherwise study near-identical sentences as two concepts).
- `catalog/README.md` updated; **CatalogLintTest** (permanent, on the real catalog) enforces:
  parse/shape/order rules, slug charset (no `|`), seedIndex uniqueness, synonyms ≠ text,
  no duplicate synonym/variant entries, no `" / "` in text, components resolve same-area,
  variantOf resolves, feminineOf resolves, emoji well-formed.

## 9. KMP project & Apple integration

- Gradle root `app/` (wrapper committed; `.gitignore` += `build/`, `.gradle/`, `.kotlin/`,
  `local.properties`); module `:kern` at **`app/kmp/kern`** (`kern/` at root is the same
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
  en absent → hub hides gracefully. Phrase templates keyed (source, target); reverse mode
  when target == de. Android later: AGP + `google()` repo + minSdk-26-or-desugaring noted.

## 10. Testing & gates

- Fast gate: `./gradlew :kern:jvmTest` (replaces `cd Kern && swift test` — CLAUDE.md/README
  update in the same series). iOS gate: xcodegen + `xcodebuild -scheme Spross build` +
  simulator run-through. Release archive smoke.
- Ported suites per the engine scout inventory, with the **FSRS-6 adaptation table**:
  relearning-entry step 10m→product 1m (config), learning Hard = 6m at step0 / ×1.5 single-step,
  new+Again+Good does not graduate (needs a further Good), graduation intervals from FSRS-6
  S0; direction-scoped statistics tests and MixedDirectionTests are obsolete (unit model);
  everything else behavioral ports 1:1 in the new denomination.
- New suites: CatalogLintTest (§8), parser fixtures (feminine ♀ fallback, variantOf skip,
  Sie/du variants, sparse coverage, en "to "/sw ku-kw prefixes, notes selection),
  unit expansion/eligibility-lag/one-per-plan composer rules, join-inertness + source-switch
  round-trip (schedules + enqueued revive; phrases stay unlocked), stale-unit answer no-op,
  FSRS-6 golden vectors + properties, DST/non-Gregorian day-key vectors, snapshot builders.

## Deliberately dropped (recorded)

- `Direction`, `mixedDirections`, FNV-1a presentation flip, `LanguagePair`, `id|direction`
  keys, per-pair store docs, slugified de-centric card ids, persisted Cards,
  reconcile upsert half, the `"/"`-join↔split grading contract, typed recognition,
  phrase recognize units, v1 immersion subtitle for chrome-less targets (kept for de/en),
  Swift DuoKern + FSRS-5 vectors + DuoKernTrainer product split, watch Kotlin linkage.
