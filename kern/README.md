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

**Engine APIs name the rule, never the rendering.**
A kern type or function says what may be shown and why, never where it lands on screen:
`EmojiCue { Upfront, OnReveal }` (the leakage rule), not `EmojiPlacement { Prompt, Reveal }`
(the face it happened to sit on in one layout).
The engine's answer has to outlive any particular screen — a placement-named API goes on
compiling while silently lying the moment the app moves the element, and drags a rename
through kern, both apps, the snapshots and the docs for what was an app-side layout change.
Screen positions, sizes, and which face of a card something rides on are `docs/design.md`'s.
The same test applies to snapshot fields: withholding data because shipping it could leak
the answer is policy and belongs here; withholding it because a surface has nowhere to
draw it is rendering and does not.

## 1. Languages & profile

- `Language` = string code from `catalog/languages.json` — open set, no enum.
- `LanguageInfo(code, name, englishName, flag, optionalVerbPrefixes, articles)` —
  per-language metadata from `catalog/languages.json` (field semantics: `catalog/README.md`);
  `articles` replaces v1's hardcoded German article list.
- Profile = (source, target), source ≠ target.
  `Catalog.availableTargets(source)` requires ≥ 50 joinable concepts.
  (Picker display and the device-language default are app rules.)

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
  Every real plural carries the "Pl. " label, suffixes resolved against the word
  ("-nen" → "Pl. Lehrerinnen"); sentinels "=" → "= Pl.", "only" → "nur Pl."
  via localized chrome strings, not hardcoded German.

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
- **RECOGNIZE**: prompt = one target form, **reveal + self-grade** (`SelfGrading`, §6 —
  never typed; comprehension check, and self-grading means no schedule is ever graded
  against a language it wasn't learned with).
  Phrases alternate too — self-graded sentence recognition is legitimate comprehension
  practice; only TYPED phrase recognition was absurd.
  **Never carries the `promptAmbiguous` area cue**: here the prompt is the target form, so
  any cue strong enough to identify the concept would reveal the answer — the same reason
  the emoji leaves the recognition PROMPT past the first exposure. Nothing is lost:
  recognition is self-graded, so a learner who thinks "sich entspannen", reveals
  "sich ausruhen" and taps Good is doing exactly what self-grading is for.
- **Role resolution** is a pure render-time function of `(cardId, log.count)`:
  - First exposure (`count == 0`) is ALWAYS recognition — the learner cannot produce a
    word never seen; the target is PROMPTED first (a learner who already knows it deserves
    the moment to recall it) WITH its emoji as the cue, and the reveal teaches the meaning,
    self-graded. An honest Again lands in the single learning step (§5), so the word returns
    at the END of the session — as the typed production attempt below.
    (Matches v1's `presentationDirection` first-exposure rule.)
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
- **Emoji cue**: `emojiCue(role, settled, reviewCount)` answers WHEN the picture appears,
  never whether it appears at all and never where (that is the renderer's, and it is fixed).
  **Upfront** iff (first exposure) OR (role == Produce ∧ the word has not settled, §5) —
  the two places it supports recall without giving the answer away, since a produce prompt
  already names the concept in the source language;
  **OnReveal** everywhere else, in every phase.
  The first exposure is the one recognition prompt that carries it, deliberately:
  it is the cue that makes a first recall attempt possible at all.
  Hiding it outright once a word was learned took it away from exactly the reviews where a
  word is still matched on novelty rather than on meaning; once the answer is out there is
  nothing left to leak, and binding the picture to the meaning is what those reviews need.
  Settledness, not the FSRS phase, is what "still landing" means here.
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
| `maxUnsettled` (active cards that have not settled — §6) | 20 |
| `sessionCap` / `dueSoftCap` | 30 / 30 |
| `growthReserve` | ≤ 5 |
| `Growth.TRICKLE_CARDS` (the floor under the new-word budget) | 2 |
| `SessionComposer.SESSION_FLOOR_CARDS` (a round worth sitting down for — §6) | 7 |
| `SessionComposer.NEW_CARDS_PER_ROUND` (first sights one round may offer — §6) | 7 |
| `TodayReport.MIN_ANSWERS_FOR_RECALL` / `RECALL_STRAIN_MARGIN` (§6) | 10 / 0.2 |

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
- Steps are config; the ENGINE defaults stay the reference pair (`learning [1m, 10m]`,
  `relearning [10m]`) so the golden vectors run verbatim.
  **The product runs ONE learning step, `[2m]`** (`relearning [10m]` unchanged).
  Two minute-scale steps put a missed word back in front of the learner half a dozen cards
  later, where it passes on being recognised as "that new one" rather than on the
  source↔target pair having bound. What keeps the retry out of THIS sitting is the run
  boundary, not the clock — a composed session never refills (§6) — so the step only has to
  be short enough that the word is there for the next one: a follow-up sitting or "Weiter
  üben", either of which is minutes away. By role resolution (§3) that retry is the typed
  production attempt, the first real recall. Past that FSRS decides. (User ruling
  2026-07-29; `3m` was tried first and outlasted the day's practice altogether.)
  **No in-session lapse retry** (breadth ruling 2026-07-22): a lapsed review card returns
  after 10 m, typically next session; the run it lapsed in does not wait for it.
  Graduation follows the reference machine (one step later than v1's hand-rolled steps —
  accepted, tested against the pinned minute tables).
- **Graduated intervals are continuous in the product.** `Fsrs.intervalRawDays` is the
  fractional interval the model asks for; `FsrsScheduler.graduate` quantizes it to
  `intervalGranularitySeconds` and floors it at `minimumIntervalSeconds`.
  Both default to 86_400 s — whole-day rounding is the reference bucket convention, not part
  of FSRS, and the default keeps the golden vectors on their exact day multiples.
  The product sets granularity to 1 s, so a 7.6-day interval is scheduled at 7.6 days.
  The one-day FLOOR is deliberate and stays: bringing a card back inside the same day is
  what a learning step is for, not what an already-graduated schedule should ask.
- Desired retention: engine default 0.9 (vector anchor); product `BoxConfig` 0.8, no slider.
  Product maximum interval 365.
- Leech: lapse counted iff `phase == review && rating == again`; 8 → auto-suspend (per card).
- **Two "has this word landed" thresholds**, both Review phase AND stability ≥ the
  threshold, so a lapse un-lands a card either way — the point: it needs the support again.
  - `settledStability` = 2.0 days (`Statistics.isSettled`, facade
    `BoxEngine.isSettled(state, cardId)`) gates the new-word budget (§6) and picks which
    support a word gets while it is still on its way in (§3).
  - `consolidatedStability` = 6.0 days (`Statistics.isConsolidated`, facade
    `BoxEngine.isConsolidated(state, cardId)`) gates phrase unlock (§6) and splits settled
    from fresh in the progress UI — set strictly between S0(Good) and S0(Easy) so a single
    Good answer no longer reads as "landed" while a single Easy still does.
  Recalibrated for FSRS-6: at retention 0.8 the interval is 3.316 × stability, so
  S0(Good) = 2.3065 crosses `settledStability` at graduation the way v1's FSRS-5 3.0 did
  (≈ 7.6 days out, Easy ≈ 27.5) while S0(Hard) = 1.2931 does not — a word answered Good on
  sight settles (budget/presentation) but does not consolidate (stats/unlock) on its first
  answer; S0(Easy) = 8.2956 clears both.
- Golden vectors copied verbatim from the pinned releases with PROVENANCE (repo/tag/SHA);
  FSRS-6 property tests re-express the v1 property suite. Weight optimization stays out.

## 6. Box / Session semantics (deltas from the v1 port map)

- **Self-grading takes a verdict and a clock** (`SelfGrading`):
  the learner reports Unknown / Tough / Knew,
  and only a Knew that arrived inside the instant budget
  (`base + perChar × prompt length`, so a phrase gets the reading time it needs)
  becomes Easy rather than Good.
  The verdict is never overruled — a fast answer the learner knows was shaky stays Hard,
  a slow one they knew stays a pass —
  because only the learner can tell a solid recall from a lucky one,
  or a pause for thought from an interruption.
  Easy is thus earned rather than chosen,
  which takes away the standing incentive to grade a session shorter than it was;
  and since the clock can only ever upgrade,
  a learner who walks away mid-card needs no cut-off to protect them.
  The elapsed span is the recall attempt (prompt shown → answer asked for),
  not the time spent choosing afterwards.

Everything in the engine scout map ports 1:1 (budgets, health gate, growth-reserve formula,
introduction = first answer, silent answer drop, extra round, endless, exposure tiers,
statistics, streak forgiveness, endSession fold + 60-day prune, deterministic orderings,
day-key `yyyy-MM-dd`) with:
- **`BoxStatistics.longestStreak`**: the longest run ever held, under the same forgiveness
  rule the current streak walks back with, over the whole (never pruned) `dailyStats`.
  An unfinished today can extend a run but never end one, so it is always ≥ `streak` —
  equality is what says today's run IS the record.
- **Introduction is the card's first answer** (v1 semantics; the unit-era eligibility lag
  and one-per-plan rules are gone with the unit model). `enqueued` holds card ids;
  enqueued cards lead composition, bypass the health gate, respect the new-word budget,
  and dequeue at introduction.
  Zero-component phrases follow seed order, never the unlock fast path (v1 rule restated).
- **The new-word budget measures unsettled LOAD, not headcount**:
  `Growth.newBudget = max(TRICKLE_CARDS, maxUnsettled − unsettledLoad)`, where
  `unsettledLoad` counts active cards that have not settled (§5).
  The old learning pool counted cards in the Learning phase, which measured how many cards
  were started rather than how much was in flight: eight words the learner already knew
  filled it exactly as eight that would not stick.
  Words answered on sight settle at once and now cost the budget nothing, so easy material
  keeps the way open, while a pile at low stability closes growth down to a trickle rather
  than dead — a session with nothing new in it is a grind on the very words that are not
  landing.
  `maxUnsettled = 0` is the one way to stop growth entirely: it reads as the learner saying
  "stop", and the trickle must not talk over that.
- **A round offers at most `NEW_CARDS_PER_ROUND` first sights** (user ruling 2026-07-31):
  the budget measures how much may be *in flight*, and a box with nothing in flight opens it
  to nearly `maxUnsettled` — a rested learner was handed twenty unseen words in one plan,
  which reads as a wall rather than an offer.
  The ceiling is a round's worth, the size `SESSION_FLOOR_CARDS` measures a round to be,
  and it applies to every composed round (today's, endless, the extra round) including
  enqueued cards, since a packed queue overloads exactly the same way.
  Nothing is withdrawn, only deferred: what the budget still allows is offered again next round.
  `growthReserve` (≤ 5) is unchanged — it reserves slots against a full due queue,
  it does not cap growth.
- **Health gate = backlog only**: projected post-session backlog stays under `dueSoftCap`.
  Time debt is a different axis from load; how much material is unsettled is the budget's
  job, and `gatedNewBudget` is 0 while the gate is shut.
- **Phrase unlock** reads each component's schedule **by card id** — join- and
  source-independent, so a source switch can never re-lock phrases. Components with no
  TARGET realization are excluded from the gate (v1 unresolved-component semantics).
  Gate: not suspended, and settled (§5) — the predicate, never a restated threshold.
- **Due order is day-bucketed, then shuffled**: reviews drain oldest overdue DAY first
  (backlog fairness), but inside a day the order is `fnv1a64("<dueEpochDay>:<fnv1a64(cardId)>")`,
  card id last as the collision tie-break.
  A plain timestamp sort kept cards introduced together — seed neighbours, so often related
  concepts — adjacent for the life of the box, and the learner answered from sequence.
  Seeding the hash with the card's OWN due day keeps the function pure (no clock read) while
  reshuffling the bucket differently from one day to the next.
  Introduction order is untouched: new cards still arrive in seed order.
- **A plan names each part for what it is**: `reviews` (due), `ahead` (not due, pulled
  forward), `unlockedPhrases` and `newCards` (never answered).
  `SessionPlan.queue` is the run in order — due work, then warm-ups, then unseen words —
  and callers build their queue from it rather than concatenating the lists themselves.
- **A round shorter than `SESSION_FLOOR_CARDS` is filled out** (user ruling 2026-07-30):
  a loaded box throttles growth to `TRICKLE_CARDS`, and a couple of new words offered as
  the day's work reads as the app having nothing to give.
  `composeSession` tops such a round up with reviews pulled forward, soonest due first —
  honest FSRS reviews, never extra new words, because the budget that made the round small
  is the one thing the floor must not talk over.
  An empty plan is filled out too, but ONLY while the day has no reps in it yet
  (user ruling 2026-07-30): a learner who has answered nothing today should always find a
  round to do, so nothing due plus nothing done is a round pulled forward, not a closed box.
  Once the day HAS been worked, an empty plan stays empty — re-filling it would make every
  visit a treadmill and erase the spacing the engine exists to keep. `composeSession`
  therefore takes `tzId`: "today" is a local-calendar question.
- **`TodayReport`** (`BoxEngine.today`) is the day's own report: reviews and misses read
  live from the review logs (so the numbers hold mid-session), introductions and settled
  crossings from the day counters the engine books at answer time (`newIntroduced`,
  `settledCrossed`, both folded into `DayStats` at `endSession` and pruned together).
  `recall` is null below `MIN_ANSWERS_FOR_RECALL` — a handful of answers cannot carry a
  ratio — and `recallStrained` names the rule "today is going badly", not the remedy:
  what a surface does with it is the app's call.
- **A composed session never refills** (user ruling 2026-07-29): the plan IS the run.
  Cards falling due while the learner sits there — a learning step maturing, most often —
  used to be drained straight in, so the count they were counting down to moved away from
  them mid-sitting. Nothing joins a run under way now; the work is still due, and endless
  practice (`composeEndless`, explicitly asked for from the summary) is where it lands.
  `dueNow` therefore feeds counts, rings and fresh pulls only.
- **Join filter inventory**: composition, dueNow, dueCount, statistics, exposure operate on
  cards that join the current profile; the unlock check and `answer()` history reads
  operate on raw schedules by id. Non-joining schedules and enqueued entries are kept
  **inert** (never pruned; both revive on switch-back).
- `answer(cardId, rating, nowMillis, tzId)` on a non-joining or unknown id is a defined
  no-op (`AnswerStatus.StaleCard`) the UI skips past. `SessionPlan` carries a
  `joinStamp` (source, target, catalog fingerprint); the app recomposes when stale.
- `setSuspended(cardId)` — per card, as v1.
- **`BoxEngine.consolidatedCardIds(state)`** — the words a drill may practise, in seed order.
  It reads through the join-filtered active inventory (scheduled, joining, not suspended) and
  keeps what `Statistics.isConsolidated` accepts, so a lapse takes a card off the list on its
  own: consolidation wants the Review phase, and a lapsed card sits in Relearning until it
  earns the stability back.
  The rule lives here rather than in each app because "which words does the learner already
  hold" is an engine question — restated over `box.cards` on two platforms it would drift,
  and it would drift silently, since a drill that practises a word too early only feels
  slightly harder.
  Seed order, not the due shuffle: a drill samples with its own `Random` and needs a list
  that is stable under it, not a second ordering rule; the seedIndex tie-break on card id
  keeps that order total.
  The query never writes — drills are stateless and book no reviews (transcription is not
  recall), so nothing here touches FSRS.
- **Exposure**: one entry per card by construction (tiers as v1); display surfaces always
  render the TARGET realization.
- **AnswerNormalizer contract** (produce only — recognition is button self-grade;
  catalog-fixture tested with "Kwaheri!", "to cook", "Der Kühlschrank ist leer.",
  "Мене звуть …"):
  normalize both sides (lowercase, ß→ss, delete joiners `-'’`, punctuation → space incl.
  `…—`, collapse whitespace) → ONE leading listed article of the answer language optional
  on both sides → iff `kind == verb`: any listed `optionalVerbPrefixes` entry (normalized
  the same way, space-preserving — en `"to "`) optional on both sides → Damerau-Levenshtein
  typo budget → article-mismatch-demotes-to-typo only when the expected
  answer's grammar carries `gender`; stray-short-leading-word rule unchanged.
  **Typo budget**: one slip per six letters (spaces excluded), floor 1,
  and zero below four letters — wider than v1's `<5 → 0, one per ten` at both ends.
  Leniency is safe to the extent the catalog can disprove it:
  `CatalogAnswerGrader` withdraws the credit wherever the typed form is really
  another concept's word, so a wider budget buys forgiveness for genuine slips
  without ever forgiving a confusion the catalog teaches apart.
  Article leniency is constructor-opt-out for drill grading:
  `AnswerNormalizer(language, articleLeniency = false)` keeps the article in `normalize`
  and only matches a form whose leading article equals the typed one —
  wrong or missing article grades Wrong (never typo-bridged);
  the one-arg init stays the lenient vocab-review default (both inits in the ObjC header).
  The budget is likewise constructor-switched for drill grading:
  `AnswerNormalizer(language, articleLeniency, maxTyposPerWord = 1)` grades **word by word** —
  each word forgives one slip, a word carrying a digit forgives none
  ("21"/"29", "18:05" → "18" "05" sit one edit apart),
  and a typed answer with a different word COUNT falls back to the whole-form rule.
  What a drill must never accept is one number for another, and that danger lives inside
  the number rather than across the sentence around it:
  distinct cardinals sit ≥ 2 edits apart, so a per-word cap of 1 keeps them apart
  while the frame ("Ich habe … Hefte.") may fumble once per word.
  The cardinal sweep (TrainerTypoBridgeGuardTests) grades every 0–999 German pair
  through the real drill normalizer and proves none is accepted for another;
  audited exceptions — sw `nne`↔`nane` (4↔8, incl. tens compounds)
  and uk `дев'ять`↔`десять` (9↔10) — are gated explicitly in the sweep.
  Vocab reviews (`maxTyposPerWord = null`, the default) keep one budget over the whole form.
  `matchingPrefixWordCount(input, answer)` is a UI-only sibling of `evaluate` — how many
  leading whole words already match, each within its own single-word budget — so a miss's
  retry field can keep the words already right and drop only the wrong tail; it never
  feeds a rating, only what the retry field starts from.
- **Catalog-wide produce grading** — `CatalogAnswerGrader(normalizer, cards)`, the app's
  produce path. One card at a time the normalizer cannot tell a slip from a different word,
  so another concept's answer lands inside this card's typo budget:
  sw `kufunga` (abschließen) ↔ `kufungua` (aufschließen), en `to pay` ↔ `to say`.
  A form the join already accepts **exactly** elsewhere is that word, not a slip of this one:
  it grades `Match.OtherWord(word, meanings)` — no typo credit, and the reveal can name what
  was written (`meanings` = the source words of every owning concept, seed order).
  Exact on the prompted card always wins, so a form two concepts share stays correct;
  the feminine base leniency (§3) is left to its own demotion; and dropping a citation
  prefix off the INPUT reaches verb owners only (a noun never owns `kupika`).
  Only the collision is catalog-wide — nothing else about "wrong" widens.
  `RealCatalogGradingTest` sweeps every near pair of every de→{en,sw,uk} join:
  no catalog word grades as a forgiven slip of another.

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
  entries (target-side text, emoji, article tint), per-card `{due}` for render-time
  `dueCount(now)`, the settled-card count (`settledCount`, resolved phone-side —
  it does not move with the clock), dailyStats tail
  (~70 days) for the streak walk, `schemaVersion`. Built by a KMP `SnapshotBuilder`,
  written by the app.
- **WatchSnapshot v3**: direction/pair/`german` are gone — one entry per CARD with BOTH
  sides pre-resolved: `{cardId, sourceText, targetText, accepted[], emoji?, articleTint?,
  femMarker, due, stability, nextRole, promptForm, distractors[], optionForm?}` + `schemaVersion`.
  `distractors` (v3) are the multiple-choice tiles for that entry, picked by
  `session/MultipleChoice` and read on THIS entry's option side —
  so the watch only shuffles and cannot put the two languages in one question.
  Nothing but MEANING may separate the answer from its company, and three rules keep it so:
  same word class first (a lone verb among nouns is answerable off its `ku` alone),
  then same area (four kitchen words test the kitchen),
  then shape (length gap + a heavy part-count penalty).
  The pool is every SCHEDULED card, not the capped entry list — the cap is a wire budget,
  and a pool that small leaves a question no same-class company to keep;
  unscheduled cards stay out, since a word first met as somebody else's wrong answer
  is no longer new when it arrives. Up to ten per entry, omitted when the box has
  nothing else to offer.
  Where a class marker survives the ranking anyway, the writing gives it up:
  `optionForm` is the entry's own option with a bound stem's dash and a verb's
  citation prefix dropped (`-zuri` → `zuri`, `kupika` → `pika`), absent when it would
  equal the taught form, which the reveal shows either way.
  The prefixes come from `languages.json` via the builder's `citationPrefixes` —
  an empty map simply leaves every verb whole.
  The shortlist is the variety knob: three of the ten reach a question, so the
  same card keeps offering the same handful until the next push.
  It is also the first thing to cut if the snapshot ever crowds the ~60 KB cap —
  a full 60-entry de→sw snapshot measures ~18 KB, ~7 KB of it distractors;
  shipping card ids instead of texts would recover most of that, at the price of
  making the watch resolve the option side again (the v2 bug's home).
  The phone resolves `nextRole` and the rotated `promptForm` from the log count at build
  time; presentation is the app layer's
  and `emoji` is pre-gated to the PROMPT side by §3 — the watch quiz has no reveal face to
  hang a picture on, so a reveal-side emoji is simply omitted from the entry.
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
  en absent (the hub's handling of that is an app rule).
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
  learning Hard = 6m at step0 / ×1.5 single-step, new+Again+Good needs a further Good under
  the reference two-step config (the product's single step graduates it),
  graduation intervals from FSRS-6 S0, day one introduces up to the unsettled cap;
  direction-scoped statistics tests are obsolete; v1 MixedDirectionTests port as
  bit-exact `presentationRole` FNV vectors; everything else behavioral ports 1:1.
- **Catalog tests split three ways, by who owns the expectation.**
  `CatalogFixtureTest` (commonTest, synthetic `Fixture.kt`) pins exact values —
  the test owns its input, so parser/join plumbing is asserted there.
  `CatalogLintTest` (jvmTest, real catalog) validates content *rules* without pinning values.
  `RealCatalogJoinTest` (jvmTest, real catalog) keeps only join *rules*,
  each derived from the catalog or exercised through a representative entry.
  Never pin real-catalog field values or totals:
  an ordinary authoring edit (a Swahili plural landing on `friji`)
  then reads as a join regression, and the assertion measures content, not code.
  A test that restates the mapping it asserts — comparing `RawRealization` to `Realization`
  field by field — is a change-detector for a copy function, not coverage.
- New suites: CatalogLintTest (§8), parser fixtures (feminine ♀ fallback, Sie/du variants,
  sparse coverage, en "to "/sw ku-kw prefixes, notes selection),
  first-exposure-always-recognition + emoji-cue policy + synonym-rotation coverage,
  join-inertness + source-switch round-trip (schedules + enqueued revive; phrases stay
  unlocked), stale-card answer no-op, FSRS-6 golden vectors + properties,
  DST/non-Gregorian day-key vectors, snapshot builders.

## 11. Pronunciation

- **When audio may play** — `PronunciationCue { Upfront, OnReveal }`,
  declared beside `EmojiCue` in `model/Presentation.kt` because it is the same kind of rule:
  what may be shown (heard) without giving the answer away.
  `pronunciationCue(role)` is `Upfront` iff the role is Recognize — the target form stands on the card from frame one —
  and `OnReveal` for Produce, which asks for that very form.
  Both apps CONSUME the cue; neither re-derives `role == Recognize` for audio.
  Which transitions actually fire, and how autoplay sits beside the auto-advance timers, is `../docs/design.md`'s.
- **What is spoken is the bare headword** — the form the card teaches, never its rendering.
  The inline article, the ♀ badge, the plural line and the area cue are grammar decoration and never reach a synthesizer;
  gender is taught by the article-colour device, not by audio.
  The recordings speak bare headwords, so this is the only rule that holds for the recorded and the synthesized branch alike.
- **Two normalizations, both normative** (`catalog/Pronunciation.kt`):
  `speechKey(form)` — trim whitespace, strip ONE leading `-` (the Swahili adjective stem citation `-zuri`),
  strip leading/trailing sentence punctuation and quote marks, NFC, lowercase.
  `utterance(form)` — what a synthesizer is handed: the leading `-` gone (it gets vocalized as "minus"),
  terminal punctuation KEPT, because it carries prosody.
  `speechKey` is applied identically to a manifest's `matches` and to the visible form; nothing else folds.
- **Lookup is keyed by the MATCHED SPOKEN FORM, never by the slug.**
  `audio/<lang>/manifest.json` records, per slug, the form the recording actually speaks (`matches`).
  `AudioManifest` builds two indices — the exact NFC form, then the `speechKey` — and exact wins.
  A rotated synonym nobody recorded simply misses, and the app speaks it live:
  a card never plays a word it does not show.
- **Collision rule.** Entries sharing a `speechKey` whose bytes are IDENTICAL are one recording fetched under two slugs, and resolve.
  Entries whose bytes differ (de `husten` = cough / to cough) have no right answer,
  so the lookup returns null and the visible form is spoken live instead of guessed at.
  That state may not ship: `CatalogAudioLintTest.noAmbiguousMatchedForm` fails the build,
  and the converter resolves collisions when it generates the manifest.
- **Kern returns paths and strings, never bytes.**
  Manifests are JSON text read through `CatalogSource` like every other catalog file;
  recording paths come back catalog-relative (`audio/uk/office.mp3`),
  and every player, synthesizer and voice table stays app-side.
- **Audio is exempt from the fingerprint.**
  `Catalog.load` reads the manifests through the RAW source, outside `FingerprintingSource`:
  recordings cannot change the join, so a refreshed pack must never restamp a `JoinStamp`
  and recompose a session that is already running.
- Surface: `Catalog.pronunciation(lang, visibleForm) -> Pronunciation(form, utterance, lang, recordingPath?)`;
  `Catalog.letterRecordingPath(lang, glyph)` for the letter drill
  (the recording speaks the letter's NAME — the name string itself is the alphabet file's, and the manifest's
  `letters` section is the only home of letter audio and its licence data);
  `Catalog.audioCredits() -> [AudioCredit]`, grouped per (language, author, licence) with per-file rows.
  BY and BY-SA cannot share one notice, so the groups ARE the credit rows,
  and they derive from the shipped manifests, so the screen can never credit what is not bundled.
- Lint (`CatalogAudioLintTest`, real catalog, vacuously green while `catalog/audio/` is empty):
  entries name slugs their language realizes, every `matches` is reachable from a visible form,
  no ambiguous speech key, slug-named word files and codepoint-named letter files
  (glyph filenames decompose under NFD on APFS), every file ships and is referenced exactly once,
  each sha256 re-hashed against the committed bytes — Commons transcodes ship untouched,
  because re-encoding is an adaptation under BY-SA — and no author is a placeholder.
- The manifest's own schema (fields, naming rules, provenance) is `catalog/README.md`'s:
  this section owns the engine rule, not the file format.

## Deliberately dropped (recorded)

- Per-role/per-form scheduling — `Role`-as-schedule, `UnitKey`, recognize eligibility lag,
  one-unit-per-card-per-plan (user ruling 2026-07-22: one schedule per card).
- Typed recognition (user ruling 2026-07-22: self-grade only — the panel's paraphrase
  finding stands) and the phrase-recognition exclusion (phrases alternate, self-graded).
- In-session lapse retry (breadth ruling 2026-07-22: relearning = reference `[10m]`).
- Two minute-scale learning steps (the reference `[1m, 10m]`): a retry that lands a handful
  of cards later is answered on novelty, not on the pair — the product runs one `[2m]` step,
  which outlasts a short sitting, so the retry starts the next one (§5).
- The relearning-share sub-gate (< 20 % of active, once active ≥ 10): relearning cards are
  unsettled by definition, so the load the new-word budget already reads subsumes it (§6);
  the health gate keeps backlog, which is a different axis.
- `AnswerStatus.DroppedPoolFull`: the budget never reaches zero on its own, so the
  answer-time re-check could not fire (`maxUnsettled = 0` refuses at composition).
- `variantOf` (user ruling 2026-07-22: the 4 near-duplicate phrase twins were unified
  instead — base slug keeps an adapted realization; schema field deleted everywhere).
- Homonym disambiguation as **content**: a per-realization `sense`/`gloss` string and a
  concept-level `homonymOf`/`disambiguator` link. Both rejected — the area label already
  carries it for free, in every language, lint-guaranteed to exist; `sense` would be a new
  authored field for ~9 entries, and `homonymOf` encodes at concept level a fact that is
  per-language (`kupumzika` is ambiguous in sw only) and rots as languages are added.
  Also rejected: emoji-as-cue (12 of 13 colliding concepts are verbs, which carry no emoji
  at all, so the cue is absent exactly where the ambiguity bites), cluster-wide
  grading leniency (accepting any cluster member teaches away the distinction the learner
  is there to acquire; if a same-area cluster ever proves unfixable, revisit as `Typo`,
  never `Exact`), and suppressing/deferring a cluster member (breaks composition
  determinism to hide a content problem, and the collision returns once both are learned).
- `Direction`, `mixedDirections` as a flag (alternation is the only mode), `LanguagePair`,
  `id|direction` keys, per-pair store docs, slugified de-centric card ids, persisted Cards,
  reconcile upsert half, the `"/"`-join↔split grading contract,
  v1 immersion subtitle for chrome-less targets (kept for de/en),
  Swift DuoKern + FSRS-5 vectors + DuoKernTrainer product split, watch Kotlin linkage.
