# SprossKern — engine contract

The standing contract for the Kotlin Multiplatform core (`:kern`):
scheduling, growth, sessions, grading, snapshots.
App-layer UX rules stay in `../docs/design.md`; this doc owns the engine.
Product frame:
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

The engine's own semantics are below. Four domains have their own pages:
`docs/snapshots.md` (the box document and the watch/widget wire),
`docs/catalog.md` (what the engine needs of the catalog),
`docs/audio.md` (pronunciation rules),
`docs/build.md` (KMP pins and the Xcode hand-off).

## 1. Languages & profile

- `Language` = string code from `catalog/languages.json` — open set, no enum.
- `LanguageInfo(code, name, englishName, flag, optionalVerbPrefixes, articles)` —
  per-language metadata from `catalog/languages.json` (field semantics: `catalog/README.md`).
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
  val kind: CardKind,         // noun | verb | adjective | phrase | idiom
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
- **`Idiom` emoji is fixed, not per-concept**: the join sets `emoji = IDIOM_EMOJI` for
  every idiom card regardless of what the catalog concept carries (nothing — the parser
  rejects an authored `emoji` on `kind: "idiom"`). Every other kind's emoji is a
  per-concept meaning cue; an idiom's is a kind marker, so a learner recognizes "this is
  figurative" from the glyph alone before reading either language's text. Idioms also
  carry no `components`/`feminineOf` (structurally forbidden) and so no unlock gate —
  see `catalog/README.md` "Idioms are the exception".
- **Grammar display is target-side only**: plural line and article coloring render only for
  the target realization.
  Every real plural carries the "Pl. " label, suffixes resolved against the word
  ("-nen" → "Pl. Lehrerinnen"); sentinels "=" → "= Pl.", "only" → "nur Pl."
  via localized chrome strings, not hardcoded German.

## 3. One schedule per card, alternating presentation   (user ruling 2026-07-22)

**ONE FSRS schedule per card, keyed by card id** (ids never contain `|`).
No per-role or per-form scheduling —
production and recognition are PRESENTATIONS of the same memory,
both feeding the one schedule ("every answer event is an FSRS review" holds).
No config flag, no user-facing direction anywhere.

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
  - The second review (`count == 1`) is ALWAYS production — seen once, now attempt it
    (ruling 2026-07-22: "returns the same session as production", both hash parities).
  - From `count == 2` role = parity(`count` + FNV-1a-64(cardId)):
    FNV-1a 64-bit over UTF-8, offset `0xcbf29ce484222325`, prime `0x100000001b3` —
    the per-card phase offset keeps the box from flipping in sync.
- **Synonym rotation** on recognition prompts: the prompted form cycles deterministically
  through `text` + `synonyms` — index = (`count / 2` + id-hash offset) mod formCount
  (parity-independent: recognition happens every other review); first exposure always
  prompts canonical text; variants never rotate.
  Every form gets prompted at zero extra scheduling cost.
  Reveal always shows the full family; the source-side reveal may show source synonyms
  informatively ("Amt / Verwaltung").
- **Sound-prompted production**: `producePrompt(cardId, reviewCount, consolidated, audible)`
  answers whether a produce turn asks by MEANING or by ear. Not a third role — the role
  function is fixed and a word asked from its sound is still produced,
  so only the prompt side moves and one schedule still sees one kind of answer.
  `Sound` needs the STRICTER consolidated bar (§5), because this WITHDRAWS the meaning
  rather than adding support, plus the app's word that the form can be heard right now
  (no recording and no voice, reading aloud off, or a screen reader — each falls back to
  `Source` rather than putting up an empty card). Alternation divides the count by two like
  the synonym rotation: roles alternate per review, so `reviewCount % 2` is CONSTANT across
  one card's produce turns. Grading narrows to the form that played (`session.spokenOnly`,
  shared with the letter drill's dictation); a synonym of the same card is amber, never
  wrong, since the reveal itself teaches those forms.
- **Emoji cue**: `emojiCue(role, consolidated, reviewCount)` answers WHEN the picture appears,
  never whether it appears at all and never where (that is the renderer's, and it is fixed).
  **Upfront** iff (first exposure) OR (role == Produce ∧ the word has not landed, §5) —
  the two places it supports recall without giving the answer away, since a produce prompt
  already names the concept in the source language;
  **OnReveal** everywhere else, in every phase.
  The first exposure is the one recognition prompt that carries it, deliberately:
  it is the cue that makes a first recall attempt possible at all.
  Hiding it outright once a word was learned took it away from exactly the reviews where a
  word is still matched on novelty rather than on meaning; once the answer is out there is
  nothing left to leak, and binding the picture to the meaning is what those reviews need.
  Having landed, not the FSRS phase, is what "still landing" means here.
- **♀** is a labeled badge, never graded: the production prompt shows source base + badge;
  the recognition reveal decorates the source answer with the badge.
  A base-word answer typed on a feminine produce card grades as typo, not failure
  (graded against `Card.baseAccepted`; corrected shows the feminine canonical text).
- Composition is **role-agnostic** — plans carry card ids; the role of each entry is
  resolved at render from the card's log count.
- Scheduling keys are source-agnostic → **switching source preserves every schedule**.

## 4. Denomination — everything in cards

One schedule per card ⇒ one review touches one card:

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
ObjC boundary and a platform that cannot see them would restate the numbers (`docs/snapshots.md` re-applies
this to every loaded box).

## 5. FSRS-6

- 21 weights; defaults = ts-fsrs v5.4.1 / py-fsrs v6.3.1 (identical), **w20 decay 0.1542**
  (brief's 0.2 was a pre-release value). Formula set + cross-check resolutions per the
  pinned reference report: same-day `sinc ≥ 1` mask for G ≥ Hard, S_MIN 0.001, fuzz OFF,
  engine maximum_interval 36500.
- `elapsedDays` = fractional `max(0, (now − lastLog)/86400)`; short-term path < 1.0.
  Golden vectors all review exactly at due; real-timestamp vectors stay out of the suite.
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
  Graduation follows the reference machine, tested against the pinned minute tables.
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
- **ONE "has this word landed" threshold**: `consolidatedStability` = 6.0 days
  (`Statistics.isConsolidated`, facade `BoxEngine.isConsolidated(state, cardId)`),
  Review phase AND stability ≥ the bar, so a lapse un-lands a card — the point: it needs
  the support again. It gates phrase unlock and the drill pools (§6), splits consolidated
  from fresh in the progress UI, and picks the support a word gets while it is still on
  its way in (§3) — the emoji that props recall up, and the sound prompt that withdraws
  the meaning, read the same bar from opposite sides.
  A second, `MATURED_STABILITY` = 30 days (`GrowthStage.Matured`), gates NOTHING and is a
  constant rather than a `BoxConfig` field: it exists so the ladder has a top rung
  to report, and there is no product decision to tune behind it.
  Calibrated for FSRS-6: at retention 0.8 the interval is 3.316 × stability, and the bar
  sits in the gap between the two first answers that pass — S0(Good) = 2.3065 stays under
  it while S0(Easy) = 8.2956 clears it. That gap is the whole point. A first Good is as
  easily an emoji recognised as a word recalled, so the word keeps its support until a
  second answer says otherwise; Easy is earned by a fast learner-reported Knew
  (`SelfGrading`, §6) and never picked, so a word genuinely known on sight still lands at
  once — the learner met where they are, without the guess riding along.
  A separate `settledStability` = 2.0 used to gate presentation support on its own.
  It sat BELOW S0(Good), so a single Good — the emoji-lucky case included — withdrew the
  support one review early, and that next review is the first TYPED one, the first that
  can actually catch the guess. The two bars were never separable in practice either:
  the forest drew `Settled` and `Consolidated` with the same mark.
- Golden vectors copied verbatim from the pinned releases with PROVENANCE (repo/tag/SHA).
  Weight optimization stays out.

## 6. Box / Session semantics

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

The engine also owns budgets and the growth-reserve formula, the silent answer drop, the
extra round, endless, exposure tiers, statistics, streak forgiveness, the `endSession` fold
and its 60-day prune, deterministic orderings, and the `yyyy-MM-dd` day key. Beyond those:
- **`BoxStatistics.longestStreak`**: the longest run ever held, under the same forgiveness
  rule the current streak walks back with, over the whole (never pruned) `dailyStats`.
  An unfinished today can extend a run but never end one, so it is always ≥ `streak` —
  equality is what says today's run IS the record.
- **Introduction is the card's first answer.** `enqueued` holds card ids;
  enqueued cards lead composition, respect the per-round cap, and dequeue at introduction.
  Zero-component phrases follow seed order, never the unlock fast path.
- **Intake is bounded per round, and by nothing else**: a round offers at most
  `NEW_CARDS_PER_ROUND` first sights — a round's worth, the size `SESSION_FLOOR_CARDS`
  measures a round to be — across every composed round (today's, endless, the extra round)
  including enqueued cards, since a packed queue overloads the same way.
  Nothing is withdrawn, only deferred: the next round offers the rest again.
  There is no cap on how much may be *in flight*. `maxUnsettled` used to impose one, read off
  how many active cards sat below the landed bar of the day (`settledStability`, 2.0, since
  merged away); that bar was cleared by a single Good, so it counted the words answered WRONG
  and throttled breadth on a difficulty signal that does not predict retention
  (`docs/growth-evidence.md`).
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
  TARGET realization are excluded from the gate.
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
- **`GrowthStage`** (`BoxEngine.growth`) is the same box told per card instead of per count:
  one rung each for unscheduled / queued / learning / fresh / consolidated /
  matured / relearning / suspended, in seed order, with the card's raw stability and whether
  today's answer touched it. Suspension and a lapse outrank every bar — a rung says where a
  card stands now, never how far it once got. The rungs name the RULE, so a surface may draw
  two of them the same; what they look like is not the engine's answer. It is the whole-box
  read behind a surface that draws the box itself rather than the totals `statistics`
  aggregates it into, and the reason the app needs no schedule-reading rules of its own.
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
- `setSuspended(cardId)` — per card.
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
- **Exposure**: one entry per card by construction; display surfaces always
  render the TARGET realization.
- **AnswerNormalizer contract** (produce only — recognition is button self-grade;
  catalog-fixture tested with "Kwaheri!", "to cook", "Der Kühlschrank ist leer.",
  "Мене звуть …"):
  normalize both sides (lowercase, ß→ss, delete joiners `-'’`, punctuation → space incl.
  `…—`, collapse whitespace) → ONE leading listed article of the answer language optional
  on both sides → iff `kind == verb`: any listed `optionalVerbPrefixes` entry (normalized
  the same way, space-preserving — en `"to "`) optional on both sides → Damerau-Levenshtein
  typo budget → article-mismatch-demotes-to-typo only when the expected
  answer's grammar carries `gender`; a stray short leading word that, once dropped,
  makes the rest match is a typo — **vocab reviews only**: it recurses, peeling one
  word per level, and a drill grades against a whole reading where every word names
  which time it is ("fünf vor halb sieben" is not a misspelling of "halb sieben").
  **Typo budget**: one slip per six letters (spaces excluded), floor 1,
  and zero below four letters.
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
  `ClockCollisionSweepTests` is its clock half, over all 1440 times in all five languages;
  which readings a clock may share and which it may not is `../docs/clock-registers.md`.
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

## 7. Testing & gates

- Fast gate: `./gradlew :kern:jvmTest`. iOS gate: xcodegen + `xcodebuild -scheme Spross
  build` + simulator run-through. Release archive smoke.
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

## Rejected designs

Roads not taken — never built, so there is no diff to find them in.
What was built and later removed is git's to remember, not this doc's.

- **Typed recognition** (user ruling 2026-07-22): self-grade only, the panel's paraphrase
  finding stands. Nor is there a phrase-recognition exclusion — phrases alternate too.
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
