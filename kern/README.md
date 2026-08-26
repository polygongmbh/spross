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

**The contract states the rule, the declaration states the detail.**
A number, a field list or a signature belongs on the type that carries it, where a reader
already is; what stays here is what no single declaration can say — a decision someone made,
a rule that spans several of them, or a bug a restatement would bring back.

The engine's own semantics are below. Nine domains have a page of their own:
`docs/presentation.md` (what a prompt shows, and when),
`docs/fsrs.md` (parameters, provenance and graduation),
`docs/turns.md` (the turn machine, the drills and listening),
`docs/grading.md` (how a typed answer becomes a rating),
`docs/reports.md` (the read models a surface draws the box from),
`docs/snapshots.md` (the box document and the watch/widget wire),
`docs/catalog.md` (what the engine needs of the catalog),
`docs/audio.md` (pronunciation rules),
`docs/build.md` (KMP pins and the Xcode hand-off).

## 1. Languages & profile

- Profile = (source, target), source ≠ target. `Catalog.availableTargets(source)` answers
  only for a language the catalog declares — an undeclared one is a caller that skipped the
  launch query, `coveredSources()`. `defaultSource(device)` falls back through the device
  language, `en`, and the first covered source, **so no device locale can throw at launch.**
- `LanguageChoices` owns the pair a learner picks: neither picker hides the other's pick,
  so choosing the language the other side holds SWAPS the pair rather than refusing the tap,
  and a source change never leaves the pair half-chosen.
- **Chrome reads the profile's KNOWN language**, falling back to English; the immersion
  subtitle — an action button captioned in the language being LEARNED — asks `hasChrome`
  instead, because it has no fallback by design: absent means no subtitle, never an English
  one. Mapping a returned code to a `Locale` or a string table is the platform's.

## 2. Card — derived, language-symmetric

`Card` and `Realization` document their own fields (`model/Card.kt`); the rules the
declarations cannot state are here.

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
- **Grammar display is target-side only**: the plural line and the article coloring render
  for the target realization alone (`model/DisplayText.kt` resolves what the catalog
  authored; the words a surface prints for each sentinel are chrome).
- **The reveal's family line** excludes every form already standing on screen
  (`alternates`) — a recognition prompt rotates a synonym in, so without the exclusion the
  reveal offers the learner the very word they are looking at as though it were another one.

## 3. One schedule per card, alternating presentation   (user ruling 2026-07-22)

**ONE FSRS schedule per card, keyed by card id** (ids never contain `|`).
No per-role or per-form scheduling —
production and recognition are PRESENTATIONS of the same memory,
both feeding the one schedule ("every answer event is an FSRS review" holds).
No config flag, no user-facing direction anywhere.

- **PRODUCE**: prompt = source text (+ ♀ badge when marked), typed answer in target.
  When `promptAmbiguous`, the prompt carries the card's **area label** as a secondary
  context line ("Im Bad", "Jikoni") — free of leakage because it is in the PROMPT language
  while the answer is in the other, and it is the retrieval cue the learner actually has
  (the box teaches per area). Generalizes the ♀-badge pattern; never graded.
- **RECOGNIZE**: prompt = one target form, **reveal + self-grade** (§6) — never typed, and
  self-grading means no schedule is ever graded against a language it was not learned with.
  Phrases alternate too: self-graded sentence recognition is legitimate comprehension
  practice, and only TYPED phrase recognition was absurd.
  **Never carries the `promptAmbiguous` area cue**: here the prompt is the target form, so
  any cue strong enough to identify the concept would reveal the answer — the same reason
  the emoji leaves the recognition prompt past the first exposure. Nothing is lost:
  a learner who thinks "sich entspannen", reveals "sich ausruhen" and taps Good is doing
  exactly what self-grading is for.
- **Role resolution** is a pure render-time function of `(cardId, log.count)`:
  - First exposure (`count == 0`) is ALWAYS recognition — the learner cannot produce a
    word never seen; the target is PROMPTED first (a learner who already knows it deserves
    the moment to recall it), spoken but WITHOUT its emoji (the cue rule above), and the
    reveal teaches the meaning, self-graded. An honest Again lands in the single learning
    step (§5), so the word returns at the END of the session — as the typed production
    attempt below, which is where the picture supports it.
  - The second review (`count == 1`) is ALWAYS production — seen once, now attempt it
    (ruling 2026-07-22: "returns the same session as production", both hash parities).
  - From `count == 2` role = parity(`count` + FNV-1a-64(cardId)) — the per-card phase
    offset keeps the box from flipping in sync.
- **Synonym rotation** on recognition prompts, and **sound-prompted production**
  (`producePrompt`): asking a word by ear WITHDRAWS the meaning rather than adding support,
  so it needs the stricter consolidated bar (§5) — it is not a third role, only the side the
  card asks FROM moving, so one schedule still sees one kind of answer. The ANSWER moves
  with it: what is typed is the meaning, in the source language, because a word heard and
  written back down has been transcribed rather than understood. It is the one turn typed in
  the language the learner already has, and it stands on the same reasoning the recognition
  rule does — a self-graded reveal would not tell whether the word was understood at all,
  which is the whole of what a sound prompt asks.
- **The target is spoken with its article; the source is not** (user ruling 2026-08-21),
  everywhere a target word is synthesized.
- **Emoji cue**: `emojiCue(role, consolidated)` answers WHEN the picture appears, never
  whether it appears at all and never where. **Upfront** iff role == Produce ∧ the word has
  not landed (§5) — the one prompt it can support recall on without giving the answer away;
  **OnReveal** everywhere else. **The first exposure does not carry it** (ruling 2026-08-07):
  a first exposure is recognition and the picture depicts the very concept being asked for,
  so on a self-graded card it is not a cue but the answer, and the schedule cannot tell
  "the emoji was obvious" from "I knew the word".
  Where each branch falls and why is `docs/presentation.md`.
- **♀** is a labeled badge, never graded: the production prompt shows source base + badge;
  the recognition reveal decorates the source answer with the badge.
  A base-word answer typed on a feminine produce card grades as typo, not failure
  (graded against `Card.baseAccepted`; corrected shows the feminine canonical text).
- Composition is **role-agnostic** — plans carry card ids; the role of each entry is
  resolved at render from the card's log count.
- Scheduling keys are source-agnostic → **switching source preserves every schedule**.

## 4. Denomination — everything in cards

One schedule per card ⇒ one review touches one card.
**Every user-facing count** — the due ring, "x neu", active, the widget, every `DayStats`
field — **is in cards**; `DayStats.reviews` alone counts answer EVENTS.

The numbers themselves are not one table: the tunable ones are `BoxConfig`'s fields, and the
rest are private constants beside the rule each of them serves (`SessionComposer`,
`TodayReport`, `net.spross.kern.listen`), where the declaration says what its number buys.
`BoxConfig.product()` hands the shipped calibration out as a value, because Kotlin default
arguments do not cross the ObjC boundary
(`docs/snapshots.md` re-applies it to every loaded box).

## 5. FSRS-6

Parameters, provenance and the golden vectors are `docs/fsrs.md`; the numbers behind each
bar are on `BoxConfig` itself. What the product decided:

- **The product runs ONE learning step, `[2m]`** (user ruling 2026-07-29). What keeps a
  missed word out of THIS sitting is the run boundary, not the clock — a composed session
  never refills (§6) — so the step only has to be short enough that the word is there for
  the next one, a follow-up sitting or an endless run minutes away.
  By role resolution (§3) that retry is the typed production attempt, the first real recall.
  **No in-session lapse retry** (breadth ruling 2026-07-22): the run a card lapsed in does
  not wait for it.
- **A graduated interval floors at one day.** Bringing a card back inside the same day is
  what a learning step is for, not what an already-graduated schedule should ask.
- **Leech: breadth over retention** (user ruling 2026-08-07). A lapse is any `Again` past
  introduction — learning- and relearning-step retries count too, not just review-phase
  ones — and 2 lapses auto-suspend the card. A word that has not stuck in two tries is
  pushed outward rather than repeating on the learner indefinitely; suspension is the same
  reversible state `setSuspended` uses everywhere else — the learner can always revive it
  from the Box.
- **ONE "has this word landed" threshold**: `consolidatedStability`
  (`Statistics.isConsolidated`, facade `BoxEngine.isConsolidated(state, cardId)`) —
  Review phase AND stability ≥ the bar, so a lapse un-lands a card, which is the point:
  it needs the support again. That one bar gates phrase unlock and the drill pools (§6),
  splits consolidated from fresh in the progress UI, and picks the support a word gets
  while it is still on its way in (§3) — the emoji that props recall up and the sound
  prompt that withdraws the meaning read the same bar from opposite sides.
- **Weight optimization stays out of scope.**

## 6. Box / Session semantics

- **Self-grading takes a verdict and a clock** (`SelfGrading`), and **the verdict is never
  overruled** — a fast answer the learner knows was shaky stays Hard, a slow one they knew
  stays a pass, because only the learner can tell a solid recall from a lucky one, or a
  pause for thought from an interruption. The clock can only ever UPGRADE a Knew to Easy,
  so Easy is earned rather than chosen — which takes away the standing incentive to grade a
  session shorter than it was, and means a learner who walks away mid-card needs no cut-off
  to protect them. The span measured is the recall attempt (prompt shown → answer asked
  for), not the time spent choosing afterwards.

- **A turn is a machine, not a screen** (`session.TurnMachine`): one produce/recognize turn
  is immutable state plus `reduce(state, intent, nowEpochMillis)`, and **the learner's TEXT
  is never in the state** — the platform owns the field and hands text in through intents.
  Every rule about what that text is worth is the engine's, because it lived twice before
  and drifted both ways: a pickable Easy on one platform, no retype after a miss on the
  other. The beats, the write-out, the recall span and the asked-by-ear rules are
  `docs/turns.md`.

The engine also owns budgets and the growth-reserve formula, the silent answer drop, the
extra round, endless, exposure tiers, statistics, streak forgiveness, the `endSession` fold
and its 60-day prune, deterministic orderings, and the `yyyy-MM-dd` day key. Beyond those:
- **The streak is one commitment across every target language, not one per language.**
  `dailyStats` persists per (source, target) box, so `BoxEngine.statistics` and
  `BoxEngine.growth`'s siblings take `otherLanguagesDailyStats` — every OTHER target
  language's `dailyStats`, gathered by the caller — and fold them into THIS state's own
  via `Statistics.mergeDailyStats` before walking the streak. A day earns the streak
  whichever language(s) it was spent on; every other bucket (`activeCount`, `dueCount`,
  the areas) stays scoped to the join in view. `WidgetSnapshotBuilder.build` takes the
  same parameter so a widget's render-time streak walk agrees (`docs/snapshots.md`).
- **Introduction is the card's first answer.** `enqueued` holds card ids;
  enqueued cards lead composition, respect the per-round cap, and dequeue at introduction.
  Zero-component phrases follow seed order, never the unlock fast path.
- **Intake is bounded per round, and by nothing else.** A round's worth of first sights,
  across EVERY composed round and including packed cards, since a packed queue overloads the
  same way; the rest is not withdrawn, only deferred. **Nothing throttles on how shaky the
  material is, and nothing on how far behind the box has fallen** — neither predicts
  retention, and a small constant reserve cannot compound a backlog the learner never works
  off, so a box far behind still gets its round (`docs/growth-evidence.md`).
- **Phrase unlock** reads each component's schedule **by card id** — join- and
  source-independent, so a source switch can never re-lock phrases. Components with no
  TARGET realization are excluded from the gate.
  Gate: not suspended, and consolidated (§5) — the predicate, never a restated threshold.
- **Due order is day-bucketed, then shuffled**: reviews drain the oldest overdue DAY first
  for backlog fairness, but inside a day the order is a hash, seeded with the card's OWN due
  day so the function stays pure and the bucket still reshuffles from one day to the next.
  A plain timestamp sort kept cards introduced together — seed neighbors, so often related
  concepts — adjacent for the life of the box, and **the learner answered from sequence.**
  Introduction order is untouched: new cards still arrive in seed order.
- **A plan names each part for what it is**: `reviews` (due), `ahead` (not due, pulled
  forward), `unlockedPhrases` and `newCards` (never answered).
  `SessionPlan.queue` is the run in order — due work, then warm-ups, then unseen words —
  and callers build their queue from it rather than concatenating the lists themselves.
- **A round shorter than `SESSION_FLOOR_CARDS` is filled out** (user ruling 2026-07-30):
  two or three cards offered as the day's work reads as the app having nothing to give, so
  the round is topped up with reviews pulled forward — honest FSRS reviews, never extra new
  words, which are capped per round on purpose (`docs/growth-evidence.md`).
- **A long round can be taken short** (user ruling 2026-08-20): a long enough round is also
  offered stopped early — a strict PREFIX of the round the day just promised, so it inherits
  the day-done question with it. `SHORT_ROUND_CARDS` **is** `SESSION_FLOOR_CARDS` rather
  than a free number: the floor is also what a day has to hold before it counts as worked,
  so a learner who only ever takes the short round still closes the day.
  Below the offer threshold nothing is offered — the two rounds would be one round under
  two names.
- **A quiet day is built, not found** (user ruling 2026-08-01): with nothing due, half the
  floor at most is held for cards coming due inside tomorrow and new words take the rest.
  **The pull-aheads supplement the new words, they never lead** — a round of first sights
  with a few known words mixed through is the intended shape of a caught-up box, and an
  exhausted catalog still opens a round rather than an empty screen.
- **A day can be over** (user ruling 2026-08-01): nothing due, nothing coming back soon, and
  a round's worth already answered. "Nothing more right now" is a real answer, and
  manufacturing another round would make every visit a treadmill.
  **Nothing composes past that, packed cards included** (user ruling 2026-08-03): packing IS
  an explicit ask, and the round the learner opens is where it is answered — letting it
  through the day's own round instead produced a four-card round of first sights, because a
  done day skips the fill the tomorrow reservation had already docked the budget for.
  **A word on a learning step is the day's own unfinished business** (user ruling 2026-08-03),
  and that span is rolling rather than a calendar edge: what makes a word today's is that it
  comes back while the learner is still here, which midnight knows nothing about.
  Only the QUESTION of whether the day is over moves — a round carrying a returning word is
  an ORDINARY round, so there is no second composition path to keep in step.
  "Today" and "tomorrow" are local-calendar questions and `composeSession` takes `tzId` for
  them; the returning span is the one deliberate exception.
- **No surface derives a card's standing from a raw phase** — the engine reports the rung
  (`GrowthStage`), and every listing carries it beside the phase rather than re-reading it:
  a card reaches Review well below `consolidatedStability`, so a second derivation is a
  second answer waiting to disagree. The read models a surface draws the box from —
  the day's report, the rungs, the browsable box, the greeting clock — are `docs/reports.md`.
  **The color a rung wears is the same fact, extended to drawing**: `CardRowState.Standing.swatch`
  resolves it once, off `net.spross.kern.design.Palette`, so a row's own badge and the shelf's
  own progress bar read the identical color for the identical rung on both platforms — neither
  a platform's badge logic nor its bar logic re-derives which color a rung gets.
  **Packing and unpacking act on the area, never a single word, except where a search
  reached that word by name**: `BoxEngine.enqueue`/`dequeueArea` are the shelf's own controls,
  batching the whole queue; `dequeue` alone (a single card id) exists for the one context that
  names a word rather than browsing a shelf for it. A surface must not offer a per-word pack
  or unpack control anywhere else — `CardRowState.Packed.removalOffered` and `PackOffered`
  both gate on the same `packOffered` context flag so this can't drift per platform either.
- **A composed session never refills** (user ruling 2026-07-29): the plan IS the run.
  Cards falling due while the learner sits there — a learning step maturing, most often —
  used to be drained straight in, so the count they were counting down to moved away from
  them mid-sitting. Nothing joins a run under way now; the work is still due, and endless
  practice (explicitly asked for from the summary) is where it lands.
  `dueNow` therefore feeds counts, rings and fresh pulls only.
- **One composer for every round** (user ruling 2026-08-03). **User agency decides WHICH
  round opens, never what goes in one**: the caller names a round the box already has rules
  for, and no size, budget or flag crosses the boundary. The extra round and endless each
  used to have a composer of their own and had drifted to opposite extremes, so which one a
  screen happened to call decided whether the learner got a wall of first sights or a wall of
  cards dragged forward from days out.
- **Join filter inventory**: composition, dueNow, dueCount, statistics, exposure operate on
  cards that join the current profile; the unlock check and `answer()` history reads
  operate on raw schedules by id. Non-joining schedules and enqueued entries are kept
  **inert** (never pruned; both revive on switch-back).
- `answer(cardId, rating, nowMillis, tzId)` on an unknown id leaves the state
  untouched. `SessionPlan` carries a `joinStamp` (source, target, catalog
  fingerprint); the app recomposes when stale.
- **"Which words does the learner already hold" is an engine question**, answered once by
  `BoxEngine.consolidatedCardIds` — restated over `box.cards` on two platforms it would
  drift, and drift silently, since a drill that practices a word too early only feels
  slightly harder.
- **A drill run is a pure machine too** (`net.spross.kern.trainer`), and **one injected
  `Random` per run** feeds every draw, so a seeded run is reproducible end to end and
  identical on both platforms. Drills book no reviews — transcription is not recall — and
  their storage keys are byte-identical across the two stores.
  The ladder, the modes and the availability gates are `docs/turns.md`.
- **Listening is a playlist over the learner's own words** (`net.spross.kern.listen`) — a
  target word, its meaning in the source language, then the target again, so a language
  reaches the hours that ask for no typing and no tap.
  **Both halves must be sayable**, or a turn plays a word and then silence.
  **The playlist is dealt, not drawn**: one priority per word — the shakiest lead, then the
  words the learner packed, then the rest of the unseen ones in catalog order, so a language
  with an empty box plays from its very first word — and the run walks that order and laps
  it, so the same box gives the same run.
  **Suspended cards stay in the pool**: the leech rule pushes a word out of the box's queue
  (§5) and never said stop meeting the word, so a leech pays a toll on its own rung instead
  of being sent to the back.
  Hearing a word does not introduce it — introduction is the first answer, and listening
  answers nothing; `ListeningRun` holds no `BoxState` at all, which is what makes that
  structural rather than promised. The pool, the ladder, the deal, the beats and the bedtime
  fade are `docs/turns.md`.
- **Leniency is safe only to the extent the catalog can disprove it.** The typo budget
  forgives a slip, and `CatalogAnswerGrader` withdraws that credit wherever the typed form
  is really another concept's word — so a wider budget buys forgiveness for genuine slips
  without ever forgiving a confusion the catalog teaches apart. A drill caps at one slip
  per word for the same reason: distinct cardinals sit ≥ 2 edits apart, and the cap is what
  keeps `21` from being accepted for `29`. The pipeline, the budgets and the sweeps that
  hold it are `docs/grading.md`.
- **`Match.producedRating()` is the one place a produce match becomes a rating** — both apps
  call it rather than re-deciding the mapping, which is how the two produce screens drifted
  apart on a typo before it existed.
- **Own words** (`OwnWord`, `OwnWords`, `BoxEngine.addOwnWord/removeOwnWord`) — what the
  learner writes when the catalog has no word for what they need. They are the second and
  last source of cards, and the only CONTENT the box document holds: every other card in it
  is re-derived on load, so losing this entry would lose a word rather than a computation.
  Their ids carry an `own:` prefix and their area is fixed, so a catalog that grows can
  neither collide with the learner's words nor quietly reclaim them.
  `removeOwnWord` is **the one deletion the engine offers**, and it reaches own words only:
  a catalog word is not the learner's to delete, only to suspend.
  `BoxEngine.reset` is the destructive fresh start — schedules, queue and tallies go; the
  join, the configuration and the own words stay. **Clearing what the box KNOWS must never
  delete what it HOLDS.**

## 7. Testing & gates

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
- `PaletteParityTest` (jvmTest) holds the four hand-copied palettes to the app's design
  tokens; `../docs/portability.md` owns that rule and its `--rerun-tasks` caveat.
- **A deliberate content change is allowed to move the tests that pin it.**
  Pinned expectations say what the drill teaches,
  so changing what it teaches SHOULD move them —
  contorting the content to keep them byte-identical
  inverts which of the two is the source of truth.
  Move the expectation and say so in the commit;
  the zero-test-edit bar belongs to REFACTORS, where an unchanged test is the proof.
  Exhaustive coverage is not the goal either:
  judge a variant by whether a learner would plausibly type it, not by whether it exists somewhere,
  since a rare regional form costs the review attention the common ones deserve more.

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
