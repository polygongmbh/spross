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
  `Catalog.availableTargets(source)` requires ≥ 50 joinable concepts, and answers only for a
  language the catalog declares — an undeclared one is a caller that skipped the launch query.
  That query is `coveredSources()`: every language with at least one learnable target, sorted;
  `defaultSource(device)` picks the device language when it is covered, else `en`
  (else, for a catalog that cannot teach from English, its first covered source).
  So no device locale can throw at launch. (Picker display is an app rule.)

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
  The learner's own words are the one other source of them (§6) and follow every rule here.
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
- **Sound-prompted production**: `producePrompt(cardId, reviewCount, consolidated, audible)`
  answers whether a produce turn asks by MEANING or by ear. Not a third role — the role
  function is a bit-exact v1 contract and a word asked from its sound is still produced,
  so only the prompt side moves and one schedule still sees one kind of answer.
  `Sound` needs the STRICTER consolidated bar (§5), because this WITHDRAWS the meaning
  rather than adding support, plus the app's word that the form can be heard right now
  (no recording and no voice, reading aloud off, or a screen reader — each falls back to
  `Source` rather than putting up an empty card). Alternation divides the count by two like
  the synonym rotation: roles alternate per review, so `reviewCount % 2` is CONSTANT across
  one card's produce turns. Grading narrows to the form that played (`session.spokenOnly`,
  shared with the letter drill's dictation); a synonym of the same card is amber, never
  wrong, since the reveal itself teaches those forms.
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
| `sessionCap` | 25 |
| `growthReserve` | ≤ 5 |
| `SessionComposer.SESSION_FLOOR_CARDS` (a round worth sitting down for — §6) | 7 |
| `SessionComposer.NEW_CARDS_PER_ROUND` (first sights one round may offer — §6) | 7 |
| `TodayReport.MIN_ANSWERS_FOR_RECALL` / `RECALL_STRAIN_MARGIN` (§6) | 10 / 0.2 |

Every user-facing count (due ring, "x neu", active, widget) and `DayStats` field is in
cards; `DayStats.reviews` = answer events.

`BoxConfig.product()` hands that calibration out as a value — the table is the `BoxConfig`
defaults and the factory returns them, because Kotlin default arguments do not cross the
ObjC boundary and a platform that cannot see them would restate the numbers (§7 re-applies
this to every loaded box).

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
    `BoxEngine.isConsolidated(state, cardId)`) gates phrase unlock (§6) and splits consolidated
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

Everything in the engine scout map ports 1:1 (budgets, growth-reserve formula,
introduction = first answer, silent answer drop, extra round, endless, exposure tiers,
statistics, streak forgiveness, endSession fold + 60-day prune, deterministic orderings,
day-key `yyyy-MM-dd`) with:
- **`BoxStatistics.longestStreak`**: the longest run ever held, under the same forgiveness
  rule the current streak walks back with, over the whole (never pruned) `dailyStats`.
  An unfinished today can extend a run but never end one, so it is always ≥ `streak` —
  equality is what says today's run IS the record.
- **Introduction is the card's first answer** (v1 semantics; the unit-era eligibility lag
  and one-per-plan rules are gone with the unit model). `enqueued` holds card ids;
  enqueued cards lead composition, respect the per-round cap, and dequeue at introduction.
  Zero-component phrases follow seed order, never the unlock fast path (v1 rule restated).
- **Intake is bounded per round, and by nothing else**: a round offers at most
  `NEW_CARDS_PER_ROUND` first sights — a round's worth, the size `SESSION_FLOOR_CARDS`
  measures a round to be — across every composed round (today's, endless, the extra round)
  including enqueued cards, since a packed queue overloads the same way.
  Nothing is withdrawn, only deferred: the next round offers the rest again.
  There is no cap on how much may be *in flight*. `maxUnsettled` used to impose one, read off
  how many active cards sat below `settledStability`; that bar is cleared by a single Good,
  so it counted the words answered WRONG and throttled breadth on a difficulty signal that
  does not predict retention (`docs/growth-evidence.md`).
  `growthReserve` (≤ 5) reserves slots against a full due queue, and only for candidates that
  will actually appear — a box with nothing left to introduce hands every slot back to reviews.
- **Backlog steers nothing either**, and the reserve is why it does not need to. At
  `desiredRetention` 0.8 a sitting sends far more cards away on longer intervals than the few
  reserved slots bring in, and the reserve is a small constant rather than something that
  scales with the queue, so growth cannot compound a backlog the learner never works off.
  A `dueSoftCap` gate used to shut growth entirely once the projected post-session backlog
  passed a cap; it only ever fought the reserve, whose whole job is letting a busy box keep
  growing. A box far behind still gets its round (`docs/growth-evidence.md`).
- **Phrase unlock** reads each component's schedule **by card id** — join- and
  source-independent, so a source switch can never re-lock phrases. Components with no
  TARGET realization are excluded from the gate (v1 unresolved-component semantics).
  Gate: not suspended, and consolidated (§5) — the predicate, never a restated threshold.
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
  two or three cards offered as the day's work reads as the app having nothing to give, so
  `composeSession` tops such a round up with reviews pulled forward, soonest due first —
  honest FSRS reviews, never extra new words, which are capped per round on purpose.
  Soonest-due-first is what makes that cheap: an early review buys least when recall is still
  near-certain, so the cards nearest their due date are the ones whose spacing costs nothing
  to spend (`docs/growth-evidence.md`).
- **A quiet day is built, not found** (user ruling 2026-08-01): with nothing due, at most half
  the floor is held for cards coming due inside tomorrow and new words take the rest.
  Pulling tomorrow's card forward costs almost no spacing; one due in three weeks burns real
  spacing, which is why the reservation counts only that horizon.
  **The pull-aheads supplement the new words, they never lead**: their job is to keep a quiet
  round from being all-new, so the share is capped at half rather than balanced toward recall.
  A round of first sights with a few known words mixed through is the intended shape of a
  caught-up box, and Heute names it as an offer of new words accordingly.
  Nothing due tomorrow also means nothing was recently missed — then the round is new words
  alone. Reaching past tomorrow happens only when there is nothing new left, so an exhausted
  catalog still opens a round instead of an empty screen.
  A round is withheld in exactly one case: nothing due, **nothing coming back within
  `RETURNING_SOON_MILLIS`** (12 h), and the day already worked, where worked means a round's
  worth of answers rather than a single tap. "Nothing more right now" is a real answer, and
  manufacturing another round would make every visit a treadmill.
  **Nothing composes past that, packed cards included** (user ruling 2026-08-03): packing IS an
  explicit ask, and the round the learner opens is where it is answered. Letting it through the
  day's own round instead produced a four-card round of first sights — the tomorrow reservation
  docked the budget by its half, and the pull-aheads it was docked FOR never came, because a
  done day skips the fill.
  **A word on a learning step is the day's own unfinished business** (user ruling 2026-08-03):
  it was missed minutes ago and returns in minutes, so a day closed in between is a claim the
  scheduler overturns by itself. The span is rolling rather than a calendar edge, because what
  makes a word today's is that it comes back while the learner is still here — midnight knows
  nothing about that, and the same step would be today's at nine in the morning and tomorrow's
  at five to twelve. Twelve hours is a waking day: it holds every learning and relearning step
  (the only schedules that land inside one, a graduated interval flooring at a day) and reaches
  for nothing that is genuinely a day out.
  Only the QUESTION of whether the day is over moves; a round carrying a returning word is an
  **ordinary round** — `fillOut` tops it up with pull-aheads as on any short day, and growth
  resumes with it, so there is no second composition path to keep in step.
  `composeSession` takes `tzId` for all of this: "today" and "tomorrow" are local-calendar
  questions — the returning span is the one deliberate exception.
- **`TodayReport`** (`BoxEngine.today`) is the day's own report: reviews and misses read
  live from the review logs (so the numbers hold mid-session), introductions and consolidated
  crossings from the day counters the engine books at answer time (`newIntroduced`,
  `consolidatedCrossed`, both folded into `DayStats` at `endSession` and pruned together).
  `recall` is null below `MIN_ANSWERS_FOR_RECALL` — a handful of answers cannot carry a
  ratio — and `recallStrained` names the rule "today is going badly", not the remedy:
  what a surface does with it is the app's call.
- **A composed session never refills** (user ruling 2026-07-29): the plan IS the run.
  Cards falling due while the learner sits there — a learning step maturing, most often —
  used to be drained straight in, so the count they were counting down to moved away from
  them mid-sitting. Nothing joins a run under way now; the work is still due, and endless
  practice (explicitly asked for from the summary) is where it lands.
  `dueNow` therefore feeds counts, rings and fresh pulls only.
- **One composer for every round** (user ruling 2026-08-03): `composeRound` is what the day
  opens, what the extra round off a finished day opens, and what each endless refill pulls.
  User agency decides WHETHER a round opens; it never decides what goes in one. The extra
  round and endless each used to have a composer of their own and had drifted to opposite
  extremes — packed cards with a pull-ahead tail sized to `sessionCap`, versus new words with
  pull-aheads withheld entirely — so which one a screen happened to call decided whether the
  learner got a wall of first sights or a wall of cards dragged forward from days out.
  Under the shared rules a round's SIZE is the box's to set: due work carries it when the
  learner is behind, cards coming due inside tomorrow when the box is settling, new words when
  little is coming up. `composeSession` is `composeRound` plus the day-done question, and
  nothing else. A refill therefore pulls ahead like any round, so an endless run ends when the
  learner closes it rather than when the catalog does — which is what "endless" is asked for.
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
  each word forgives one slip FLATLY, regardless of the word's own length
  (a three-letter word forgives the same one slip a long one does),
  and a word carrying a digit forgives none
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
- **Own words** (`OwnWord`, `OwnWords`, `BoxEngine.addOwnWord/removeOwnWord`) — what the
  learner writes when the catalog has no word for what they need. They are the second and
  last source of cards, and the only CONTENT the box document holds: every other card in it
  is re-derived on load, so losing this entry would lose a word rather than a computation.
  - `texts` is keyed by language exactly as a concept's realizations are, which buys the
    catalog's coverage rule unchanged — a word joins the profiles that pair two languages
    it is written in, goes inert in the others, and revives on the way back.
  - Ids carry the `own:` prefix and areas are fixed to `own`, so a catalog that grows can
    neither collide with the learner's words nor quietly reclaim them. `seedIndex` starts
    at `OwnWords.SEED_BASE`, behind every catalog concept — automatic growth walks seed
    order, and a word asked for by name is packed on the spot anyway (`addOwnWord`
    enqueues, because waiting for growth to reach a word the learner just wrote is absurd).
  - `removeOwnWord` takes the word, its schedule and its queue place out together. It is
    the one deletion the engine offers, and it reaches own words only: a catalog word is
    not the learner's to delete, only to suspend.
  - `BoxEngine.reset(state)` is the destructive fresh start — schedules, queue and tallies
    go; the join, the configuration and the own words stay. Clearing what the box KNOWS
    must never delete what it HOLDS.

## 7. Store & snapshots

- One document per TARGET: `box-<target>.json` in App Group `group.net.spross.app`.
  `BoxDocument { schemaVersion: 1, target, source, config, scheduling, enqueued,
  newIntroduced, dailyStats, ownWords }` — scheduling keys are card ids;
  `ownWords` is the document's only content (§6), defaulted so a box written before the
  learner could author any decodes as one who has authored none;
  the stored `config` is a record of the calibration a box was written under, never an input —
  `BoxState.withProductCalibration()` re-applies the build's (§4) to every box that loads;
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
  `dueCount(now)`, the consolidated-card count (`consolidatedCount`, resolved phone-side —
  it does not move with the clock), dailyStats tail
  (~70 days) for the streak walk, `schemaVersion`. Built by a KMP `SnapshotBuilder`,
  written by the app.
- **WatchSnapshot v5**: direction/pair/`german` are gone — one entry per CARD with BOTH
  sides pre-resolved: `{cardId, sourceText, targetText, emoji?, revealEmoji?, articleTint?,
  femMarker, due, stability, nextRole, promptForm, distractors[], optionForm?}` + `schemaVersion`.
  **The wire carries only what a surface draws**: v4 dropped `accepted[]` (the full target
  family), which was shipped for a reveal the quiz does not have — the watch answers by
  picking a tile, so there is no second face to list alternates on. Should the watch ever
  grow the phone's "auch: …" line, the field comes back with the surface that reads it,
  not ahead of it.
  `distractors` (v3) are the multiple-choice tiles for that entry, picked by
  `session/MultipleChoice` and read on THIS entry's option side —
  so the watch only shuffles and cannot put the two languages in one question.
  Nothing but MEANING may separate the answer from its company, and four rules keep it so:
  same word class first (a lone verb among nouns is answerable off its `ku` alone),
  then the same `sentenceShape` (a lone question mark among full stops is answerable
  without the tile being read; the closing mark names the shape in every catalog language,
  since Spanish never writes `¿`/`¡` without its partner, and every single word is `Bare`),
  then same area (four kitchen words test the kitchen),
  then shape (length gap + a heavy part-count penalty).
  All four RANK and none filters, so a thin box still fills four tiles.
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
  a full 60-entry de→sw snapshot measured ~18 KB, ~7 KB of it distractors (taken while
  `accepted[]` was still aboard, so v4 sits under it);
  shipping card ids instead of texts would recover most of that, at the price of
  making the watch resolve the option side again (the v2 bug's home).
  The phone resolves `nextRole` and the rotated `promptForm` from the log count at build
  time; presentation is the app layer's.
  **v5** carries the held-back picture as well: §3's emoji cue no longer decides WHETHER the
  picture ships but which KEY it ships under — `emoji` for one the learner may see from
  frame one, `revealEmoji` for one that may only be seen once a tile has been tapped. Exactly
  one is ever set. v4 omitted the second outright, on the grounds that the watch had no
  reveal face to hang it on; the graded feedback window is that face, so the picture now has
  an honest moment and no longer has to be withheld to stay honest. Two keys rather than one
  key plus a flag, so a surface that reads `emoji` and draws it immediately — the
  complication does exactly this — cannot leak a reveal-side picture by forgetting the flag.
  Ranking is **due-first** (a due card is never evicted by a non-due lower tier), then
  exposure tiers, capped at 60 entries (the ~60 KB `updateApplicationContext` limit).
  A second cap is a LEGIBILITY budget rather than a wire one: `MAX_TEXT_CHARS` (24) keeps a
  card off the watch entirely when any form it can render — both sides, plus the target
  synonyms a rotated `promptForm` reaches for — runs longer than a tile in a 2×2 grid holds.
  It gates the option pool as well as the entries, from the one predicate, so a distractor
  can never overflow a tile an answer could not have. It drops ~9% of a pair's cards, all of
  them long sentences: a four-way pick between those is exposure rather than recall, and the
  phone gives exposure better, on a card with room for it.
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
- `catalog/alphabet/<lang>.json` → `Alphabet`/`AlphabetEntry` (`AlphabetParser`, hand-parsed
  on CatalogParser conventions; `JsonSupport` gained `optionalBoolean` and `stringListMap`).
  The registry is file presence — `Catalog.alphabet(lang)` is null where no file is
  authored — and alphabet reads fold into the fingerprint (content: editing one recomposes
  a stale session once on upgrade; the audio manifest stays fingerprint-exempt). Example
  resolution splits target and reader halves (`alphabetExample` / `exampleMeaning`) so the
  sheet degrades per reader instead of erroring. Lint: shape/closure/homophone/gap rules on
  synthetic JSON in `AlphabetFixtureTest`; real-content rules in **`AlphabetLintTest`**
  (declared-language files only, own-language example realization, names on drill-true
  letters, exactly-one-gap on gap rows, letters-manifest glyph collision).
  `letters{}.matches == name` is WAIVED — the audio manifest schema rejects the field, so
  the name↔recording check is a manual listening pass (backlog).
- `catalog/drills/` — the sentence frames, a top-level sibling outside `areas.json`
  (format owned by `catalog/README.md`). A frame is a concept + per-language realizations,
  joined at runtime like a card, but it is not a card: no area, no `seedIndex`, outside the
  phrase-unlock gate. **Frames are read through the RAW `CatalogSource`, not the
  fingerprinting wrapper** — the same exemption the audio manifest has, and for the same
  reason: a frame edit can never change the card join, so it must not restamp and recompose
  a running box. An absent `drills/` folder is legal. Lint: **`CatalogFrameLintTest`**
  (slug shape/uniqueness/disjointness from concepts, one `{slot}` per text and per variant,
  `{count}` ⟺ `count` and only on a `numbers` frame, note keys are declared languages);
  vocab grounding of every answer side in **`PhraseVocabAuditTests`**.

## 9. KMP project & Apple integration

- Gradle root `app/` (wrapper committed; `.gitignore` += `build/`, `.gradle/`, `.kotlin/`,
  `local.properties`); module `:kern` at **`app/kern`** (`kern/` at root is the same
  APFS inode as Swift `Kern/` — never create it). Package `net.spross.kern`
  (+ `.trainer`). Pins (probe-proven, Xcode 26.6): Kotlin **2.4.10** (SKIE 0.10.14's ceiling —
  bump only as a pair; comment in the version catalog), serialization 1.11.0,
  datetime 0.8.0, Gradle 9.6.1, JDK 21 toolchain. Configuration cache on.
  Toolchain auto-provisioning is off: JDK 21 must be installed, and the Homebrew keg
  path is named in `gradle.properties` because Gradle cannot auto-detect it.
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
  all platforms — v1's arm64_32 fix generalizes). Trainer registry: de/en/es/sw/uk
  authored; a language outside it has no drills (the hub's handling of that is an app rule).
  `Catalog.phraseTemplates(source, target)` is the frames' half of the card join:
  one `PhraseTemplate` per frame realized in BOTH languages, directional like a `Card`,
  with `count`/`masculineNumeral`/`note` riding along from the ANSWER realization.
  Nothing pair-shaped is stored, so authoring one language file lights up every pair it
  makes. Availability gate: **empty unless `Trainer.supports(target)`** — sampling generates
  the answer side's number words, so a language without a pack can only ever supply prompts.
  Reverse mode is the same template read the other way, for any pair, not only `target == de`.
  German clock ACCEPTS 24-hour readings ("achtzehn Uhr fünfunddreißig", "null/vierundzwanzig
  Uhr" at midnight) alongside the colloquial display forms; display stays 12-hour.
  An hour word directly before "Uhr" apocopates: "ein Uhr", never "eins Uhr";
  bare "eins" stays ("punkt eins", "um eins", "halb eins").
  `PhraseSlots` samples level-aware — same per-kind ramp tables as the plain drills
  (a template's slot kind clamps the level).
  The unleveled `sample` overload keeps the prototype's biased full-difficulty draws
  (numbers favor 2–3 digits, years cluster 1950–2050);
  only Clock's unleveled draw coincides with the leveled ceiling.
  **`LetterDrill` is a separate facade, not a `TrainerKind` case**: its registry is
  alphabet file presence in the catalog (adding a language edits no Kotlin), its ramp is
  stateless and kern-owned (`entryLevel`/`winsToAdvance`/`advance` — both D11 halves in
  one place so two platforms cannot drift), sampling takes an injected `Random` and an
  app-computed promptable set (device voices are an app fact).
  A gap row draws its word from a POOL (`Catalog.alphabetExamples`, rules in
  `catalog/README.md` § Alphabet), the app narrowing it to what the device can say and
  flagging what the box already holds; kern favours the known words while at least three
  stand, and spends no randomness where a row offers one word.
  Dictation weighs its draw (`dictationWeight`): a floor of one that shuts nothing out,
  plus how many of the language's own hard graphemes the word carries (`Alphabet.trickyGlyphs`),
  its lapses, and FSRS difficulty above the midpoint — each capped, so one leech cannot take
  a rung over, and all three zero on a clean plain word, where the draw is bit-for-bit the
  uniform one. The two schedule figures ride in on `DictationCandidate`; kern reads no state.
  Dictation draws only
  `BoxEngine.consolidatedCardIds` through `dictationGradingCard` — it never books a
  review (transcription is not recall; drills are stateless).
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
  `pronunciationCue(role, prompt)` is `Upfront` iff the role is Recognize — the target form stands on the card from frame one —
  or the produce prompt IS the sound; `OnReveal` for a produce card that asks for that very form.
  Both apps CONSUME the cue; neither re-derives `role == Recognize` for audio.
  Which transitions actually fire, and how autoplay sits beside the auto-advance timers, is `../docs/design.md`'s.
- **What is spoken is the bare headword** — the form the card teaches, never its rendering.
  The inline article, the ♀ badge, the plural line and the area cue are grammar decoration and never reach a synthesizer;
  gender is taught by the article-colour device, not by audio.
  The recordings speak bare headwords, so this is the only rule that holds for the recorded and the synthesized branch alike.
- **Two normalizations, both normative** (`catalog/Pronunciation.kt`):
  `speechKey(form)` — trim whitespace, strip ONE leading `-` (the Swahili adjective stem citation `-zuri`),
  strip leading/trailing sentence punctuation and quote marks — `¡`/`¿` among them, because Spanish writes them and no one says them —, NFC, lowercase.
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
- **The analysis index is measurement data, never an edit** (user ruling 2026-08-01).
  An entry may carry `gain` (dB from the catalog's analysis target) and `lead` (dead air at its head, ms),
  and `Pronunciation`/`LetterRecording` carry both on as `AudioIndex` — 0/0 where the field is absent or nothing plays.
  The mp3 bytes stay the untouched Commons transcode, because re-encoding is an adaptation under BY-SA;
  the packs share no loudness and the uk letters open a second late, so what corrects them is a MEASUREMENT of the shipped bytes
  which only the player applies.
  A third measurement, `snr` (peak minus noise floor), corrects nothing and reaches no player:
  it exists so lint can hold a pack's median and bad tail, and refuse a rebuild that reintroduces removed hiss.
  What was measured, against which target and under which scheme is `scripts/audio-catalog.py`'s `ANALYSIS`;
  the sha256 gate is untouched by any of it.
- **Audio is exempt from the fingerprint.**
  `Catalog.load` reads the manifests through the RAW source, outside `FingerprintingSource`:
  recordings cannot change the join, so a refreshed pack must never restamp a `JoinStamp`
  and recompose a session that is already running.
- Surface: `Catalog.pronunciation(lang, visibleForm) -> Pronunciation(form, utterance, lang, recordingPath?, gain, leadMs)`;
  `Catalog.letterRecording(lang, glyph) -> LetterRecording(path, gain, leadMs)` for the letter drill,
  and `Catalog.letterRecordingPath` for the callers that only ask whether a letter can be played at all
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
- The relearning-share sub-gate (< 20 % of active, once active ≥ 10), and after it the whole
  unsettled-load throttle it had been folded into (`maxUnsettled`, `TRICKLE_CARDS`, and the
  learner-facing dial that set them): both steered growth by how shaky the material was, which
  is a difficulty signal, not a retention one (`docs/growth-evidence.md`).
- The backlog health gate (`dueSoftCap`) that outlived them, for the reason in §6: the growth
  reserve already bounds intake to a small constant a 0.8-retention sitting more than repays,
  so the gate only ever fought the reserve it shared a session with.
- `AnswerStatus.DroppedPoolFull`: intake is bounded per composed round, so there is nothing
  for an answer-time re-check to refuse.
- `BoxStatistics.newSlotsAvailable`: no surface ever read it.
- `variantOf` (user ruling 2026-07-22: the 4 near-duplicate phrase twins were unified
  instead — base slug keeps an adapted realization; schema field deleted everywhere).
- Homonym disambiguation as **content**: a per-realization `sense`/`gloss` string and a
  concept-level `homonymOf`/`disambiguator` link. Both rejected — the area label already
  carries it for free, in every language, lint-guaranteed to exist; `sense` would be a new
  authored field for ~9 entries, and `homonymOf` encodes at concept level a fact that is
  per-language (`kupumzika` is ambiguous in sw only) and rots as languages are added.
  Also rejected: emoji-as-cue — verbs and phrases carry an emoji now, but a merged pair
  merges on one meaning and so wears one picture, leaving the cue silent exactly where the
  ambiguity bites; the area label resolves it for free. Also rejected: cluster-wide
  grading leniency (accepting any cluster member teaches away the distinction the learner
  is there to acquire; if a same-area cluster ever proves unfixable, revisit as `Typo`,
  never `Exact`), and suppressing/deferring a cluster member (breaks composition
  determinism to hide a content problem, and the collision returns once both are learned).
- `Direction`, `mixedDirections` as a flag (alternation is the only mode), `LanguagePair`,
  `id|direction` keys, per-pair store docs, slugified de-centric card ids, persisted Cards,
  reconcile upsert half, the `"/"`-join↔split grading contract,
  v1 immersion subtitle for chrome-less targets (kept for de/en),
  Swift DuoKern + FSRS-5 vectors + DuoKernTrainer product split, watch Kotlin linkage.
