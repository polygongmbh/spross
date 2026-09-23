# SprossKern -- engine contract

The standing contract for the Kotlin Multiplatform core (`:kern`):
scheduling, growth, sessions, grading, snapshots.
App-layer UX rules stay in `../docs/design.md`; this doc owns the engine.
Product frame:
any source (known) / target (learning) language pair from the catalog;
no user-facing direction concept;
progress tracked per target language;
`net.spross.app` / Spross branding; pre-production -- no data-format preservation.

**Engine APIs name the rule, never the rendering.**
A kern type or function says what may be shown and why, never where it lands on screen:
`EmojiCue { Upfront, OnReveal }` (the leakage rule),
not `EmojiPlacement { Prompt, Reveal }` (a layout that would go on compiling while silently lying
the moment the app moves the element).
Screen positions, sizes, and which face of a card something rides on are `docs/design.md`'s.
The same test applies to snapshot fields.

**The contract states the rule, the declaration states the detail.**
A number, a field list or a signature belongs on the type that carries it;
what stays here is what no single declaration can say --
a decision someone made, a rule that spans several of them,
or a bug a restatement would bring back.

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
The one engine rule that is not the box's own work is the seam to an outside chat assistant:
`Briefing` writes what it may be told about a learner
and `Harvest` reads back what it sends home;
where those two surface is `../docs/design.md`.

## 1. Languages & profile

- Profile = (source, target), source != target.
  `Catalog.availableTargets(source)` answers only for a declared language --
  an undeclared one is a caller that skipped the launch query, `coveredSources()`.
  `defaultSource(device)` falls back through the device language, `en`,
  and the first covered source, **so no device locale can throw at launch.**
- `LanguageChoices` owns the pair a learner picks:
  neither picker hides the other's pick,
  so choosing the language the other side holds SWAPS the pair rather than refusing the tap.
- **Chrome reads the profile's KNOWN language**, falling back to English;
  the immersion subtitle asks `hasChrome` instead,
  because absent means no subtitle, never an English one.
  Mapping a returned code to a `Locale` or string table is the platform's.

## 2. Card -- derived, language-symmetric

`Card` and `Realization` document their own fields (`model/Card.kt`);
the rules the declarations cannot state are here.

- Cards derive at load from the catalog join; **never persisted**.
  The learner's own words are the one other source (section 6) and follow every rule here.
- **Identity is the slug alone** -- globally unique across areas, lint-enforced
  (`catalog/README.md`).
  `area` and `kind` are presentation metadata the content may restructure freely:
  moving or reclassifying a concept keeps its schedule.
  `components` and `feminineOf` are card ids (bare slugs).
- **Join rule**: emit iff TARGET realizes the concept AND a source prompt exists:
  source realization, else (feminineOf only) the base concept's source realization
  with `promptFeminineMarker = true`; if that is also absent, skip.
  Non-feminine concepts without a source realization are skipped.
  A feminine card additionally carries `baseAccepted` --
  the base concept's TARGET-side `text + teaches + accepts` --
  empty when the target never realizes the base.
- **Homonyms / target-language merges**: after emitting, the join counts cards per
  *displayed* prompt key -- NFC-normalized `source.text` plus the female state --
  and sets `promptAmbiguous` on every member of a key shared by >1 card.
  Keying on what the learner SEES means citation conventions
  (de noun capitals, en `"to "`, sw `ku-`) correctly keep noun/verb homographs apart.
  From the TARGET side the same merge is not a residue but a fact of the word:
  a form more than one concept prints means all of what they mean
  (sw `ndege` is Vogel AND Flugzeug),
  so a card asked what it MEANS credits any of them
  and the reveal names the rest (section 3).
  That join is literal, not the lenient grading one -- de `Arm` is not `arm`.
- **Grammar display is target-side only**:
  the plural line and the article coloring render for the target realization alone
  (`model/DisplayText.kt`).
- **The reveal's family line** excludes every form already standing on screen
  (`alternates`) -- otherwise the reveal offers the learner
  the very word they are looking at as though it were another one.

## 3. One schedule per card, alternating presentation   (user ruling 2026-07-22)

**ONE FSRS schedule per card, keyed by card id** (ids never contain `|`).
No per-role or per-form scheduling --
production and recognition are PRESENTATIONS of the same memory,
both feeding the one schedule ("every answer event is an FSRS review" holds).

- **PRODUCE**: prompt = source text (+ female badge when marked), typed answer in target.
  When `promptAmbiguous`, the prompt carries the card's **area label** as a secondary
  context line -- free of leakage because it is in the PROMPT language while the answer
  is in the other. Never graded.
- **RECOGNIZE**: prompt = one target form, **reveal + self-grade** (section 6) -- never typed.
  Phrases alternate too: self-graded sentence recognition is legitimate comprehension practice.
  **Never carries the `promptAmbiguous` area cue**: the prompt is the target form,
  so any cue strong enough to identify the concept would reveal the answer.
- **Role resolution** is a pure render-time function of `(cardId, log.count)`:
  - First exposure (`count == 0`) is ALWAYS recognition --
    the learner cannot produce a word never seen;
    the target is PROMPTED first, spoken but WITHOUT its emoji (the cue rule above),
    and the reveal teaches the meaning, self-graded.
    An honest Again lands in the single learning step (section 5),
    so the word returns at the END of the session as the typed production attempt below.
  - The second review (`count == 1`) is ALWAYS production --
    seen once, now attempt it (ruling 2026-07-22: both hash parities).
  - From `count == 2` role = parity(`count` + FNV-1a-64(cardId)) --
    the per-card phase offset keeps the box from flipping in sync.
- **Synonym rotation** on recognition prompts, and **sound-prompted production**
  (`producePrompt`): asking a word by ear WITHDRAWS the meaning rather than adding support,
  so it needs the stricter consolidated bar (section 5).
  The ANSWER moves with it: what is typed is the meaning, in the source language,
  because a word heard and written back down has been transcribed rather than understood.
  EVERY meaning the played form carries counts, not only this card's --
  the borrowed one books in full while pausing on the meaning THIS card teaches
  (`AlmostReason.Merged`).
- **The target is spoken with its article; the source is not** (user ruling 2026-08-21).
- **Emoji cue**: `emojiCue(role, consolidated)` answers WHEN the picture appears,
  never whether or where.
  **Upfront** iff role == Produce and the word has not landed (section 5) --
  the one prompt it can support recall on without giving the answer away.
  **OnReveal** everywhere else.
  **The first exposure does not carry it** (ruling 2026-08-07):
  on a self-graded card the picture depicts the very concept being asked for.
  Where each branch falls and why is `docs/presentation.md`.
- **Female** is a labeled badge, never graded:
  a base-word answer typed on a feminine produce card grades as typo, not failure
  (graded against `Card.baseAccepted`; corrected shows the feminine canonical text).
- Composition is **role-agnostic** -- plans carry card ids;
  the role of each entry is resolved at render from the card's log count.
- Scheduling keys are source-agnostic -> **switching source preserves every schedule**.

## 4. Denomination -- everything in cards

One schedule per card -> one review touches one card.
**Every user-facing count** -- the due ring, "x neu", active, the widget -- **is in cards**;
the day counts alone (`answerDays`) count answer EVENTS.

The numbers themselves are not one table:
the tunable ones are `BoxConfig`'s fields,
and the rest are private constants beside the rule each serves
(`SessionComposer`, `TodayReport`, `net.spross.kern.listen`).
`BoxConfig.product()` hands the shipped calibration out as a value,
because Kotlin default arguments do not cross the ObjC boundary
(`docs/snapshots.md` re-applies it to every loaded box).

## 5. FSRS-6

Parameters, provenance and the golden vectors are `docs/fsrs.md`;
the numbers behind each bar are on `BoxConfig` itself.

- **ONE ALTERNATING ladder, `stepsSeconds`, shared by Learning and Relearning**
  (user ruling 2026-09-02) --
  a brand-new word and a lapsed one wait on the same cadence.
  A lapse is any `Again` past introduction -- learning- and relearning-step retries count too --
  and is always tracked (`CardScheduling.lapses`, the dictation draw's weighting reads it),
  but no longer auto-suspends:
  each `Again` climbs `stepsSeconds` (`FsrsScheduler.stepOutcome`)
  instead of resetting to its first entry, capped at the ladder's last Sprosse.
  The product ships `[10m, 1d, 10m, 3d, 10m, 7d, 10m, 30d]` --
  minutes and days alternate, so a word that will not stick comes back
  at most TWICE in any day while the gaps between pairs widen.
  The last Sprosse is a MONTH rather than a week --
  a word still missed after four same-day pairs is a leech,
  but the box never suspends on its own, so the ladder parks it within reach.
  `Again` is the ONLY rating that stays on the ladder:
  `Hard`, `Good` and `Easy` all graduate to Review immediately.
  High on the ladder that hands a `Hard` a SHORTER interval than another Sprosse would have.
  **No in-session lapse retry** (breadth ruling 2026-07-22):
  the run a card lapsed in does not wait for it;
  by role resolution (section 3), the retry that follows is the typed production attempt.
  Suspension is now purely the learner's own call -- `setSuspended`, reversible from the Box.
- **A graduated interval floors at one day.**
- **TWO growth bars, not one**:
  `growingStability` (`Statistics.isGrowing`, facade `BoxEngine.isGrowing(state, cardId)`)
  is gate (a) -- Review phase AND stability >= 6 days,
  so a lapse un-lands a card.
  That bar gates phrase unlock, the letter drill's pool (section 6),
  and picks the support a word gets while it is still on its way in (section 3).
  `MATURED_STABILITY` (`Statistics.isConsolidated`, facade `BoxEngine.isConsolidated`)
  is a later, stricter DISPLAY bar -- 25 days --
  behind the progress-UI split, the Grown badge, the area-complete mark, the day tallies,
  the words a brief hands over as known, and the word scramble's pool.
  Deliberately distinct.
- **Weight optimization stays out of scope.**

## 6. Box / Session semantics

- **Self-grading takes a verdict and a clock** (`SelfGrading`),
  and **the verdict is never overruled** --
  a fast answer the learner knows was shaky stays Hard,
  a slow one they knew stays a pass.
  The clock can only ever UPGRADE a Knew to Easy,
  so Easy is earned rather than chosen --
  which takes away the incentive to grade a session shorter than it was.
  The span measured is the recall attempt (prompt shown -> answer asked for).

- **A turn is a machine, not a screen** (`session.TurnMachine`):
  one produce/recognize turn is immutable state plus `reduce(state, intent, nowEpochMillis)`,
  and **the learner's TEXT is never in the state** --
  the platform owns the field and hands text in through intents.
  Every rule about what that text is worth is the engine's.
  The beats, the write-out, the recall span and the asked-by-ear rules are `docs/turns.md`.

The engine also owns budgets and the growth-reserve formula, the silent answer drop,
the extra round, endless, exposure tiers, statistics, streak forgiveness,
deterministic orderings, and the `yyyy-MM-dd` day key. Beyond those:

- **The streak is one commitment across every target language.**
  A day's answers are counted off the review logs
  (`answerDays`, keyed `yyyy-MM-dd` in the CALLER's zone),
  so `BoxEngine.statistics` and `BoxEngine.activityWindow` take
  `otherLanguagesAnswerDays` and merge them with THIS state's own
  via `mergeAnswerDays` before walking the streak.
  Suspended and unjoined schedules count too: the answer really happened.
  Every other bucket (`activeCount`, `dueCount`, the areas) stays scoped to the join in view.
  `WidgetSnapshotBuilder.build` takes the same parameter (`docs/snapshots.md`).
- **Introduction is the card's first answer.**
  `enqueued` holds card ids, stored oldest-packed first;
  enqueued cards lead composition **most recently packed first**
  (`Growth.enqueuedEligible` reverses the list),
  respect the per-round cap, and dequeue at introduction.
  Zero-component phrases follow seed order, never the unlock fast path.
- **Related words arrive together.**
  Seed order runs an area's related words side by side (spoon, fork, knife),
  so a round introduces them together and the learner meets them as a set to relate.
- **Intake is bounded per round, and by nothing else.**
  A round's worth of first sights, across EVERY composed round and including packed cards.
  **Nothing throttles on how shaky the material is,
  and nothing on how far behind the box has fallen** --
  neither predicts retention (`docs/growth-evidence.md`).
- **Phrase unlock** reads each component's schedule **by card id** --
  join- and source-independent, so a source switch can never re-lock phrases.
  Components with no TARGET realization are excluded from the gate.
  Gate: not suspended, and growing (section 5) -- the predicate, never a restated threshold.
  It gates INTRODUCTION alone;
  the sentence scramble arranges the whole join,
  since handing a phrase's own words back in the wrong order asks for syntax, not the words.
- **Due order is day-bucketed, then shuffled**:
  reviews drain the oldest overdue DAY first for backlog fairness,
  but inside a day the order is a hash,
  seeded with the card's OWN due day so the function stays pure
  and the bucket still reshuffles from one day to the next.
  Introduction order is untouched: new cards still arrive in seed order.
- **A plan names each part for what it is**:
  `reviews` (due), `ahead` (not due, pulled forward),
  `unlockedPhrases` and `newCards` (never answered).
  `SessionPlan.queue` is the run in order --
  due work, then warm-ups, then unseen words --
  and callers build their queue from it.
- **A round shorter than `SESSION_FLOOR_CARDS` is filled out** (user ruling 2026-07-30):
  topped up with reviews pulled forward --
  honest FSRS reviews, never extra new words, which are capped per round on purpose
  (`docs/growth-evidence.md`).
- **A long round can be taken short** (user ruling 2026-08-20):
  a strict PREFIX of the round the day promised,
  so it inherits the day-done question with it.
  `SHORT_ROUND_CARDS` **is** `SESSION_FLOOR_CARDS`:
  the floor is what a day has to hold before it counts as worked,
  so a learner who only takes the short round still closes the day.
  Below the offer threshold nothing is offered.
- **A quiet day is built, not found** (user ruling 2026-08-01):
  with nothing due, half the floor at most is held for cards coming due inside tomorrow
  and new words take the rest.
  **The pull-aheads supplement the new words, they never lead** --
  a round of first sights with a few known words mixed through is the intended shape,
  and an exhausted catalog still opens a round.
- **A day can be over** (user ruling 2026-08-01):
  nothing due, nothing coming back soon, and a round's worth already answered.
  **Nothing composes past that, packed cards included** (user ruling 2026-08-03):
  packing IS an explicit ask, and the round the learner opens is where it is answered.
  **A word on a learning step is the day's own unfinished business** (user ruling 2026-08-03),
  and that span is rolling rather than a calendar edge.
  Only the QUESTION of whether the day is over moves --
  a round carrying a returning word is an ORDINARY round.
  "Today" and "tomorrow" are local-calendar questions and `composeSession` takes `tzId`;
  the returning span is the one deliberate exception.
- **No surface derives a card's standing from a raw phase** --
  the engine reports the Sprosse (`GrowthStage`),
  and every listing carries it whole (`CardRowState.Standing.stage`).
  The read models a surface draws the box from are `docs/reports.md`.
  **The color a Sprosse wears is the same fact, extended to drawing**:
  `CardRowState.Standing.swatch` resolves it once, off `net.spross.kern.design.Palette`,
  so a row's badge and the shelf's progress bar read the identical color on both platforms.
- **Packing and unpacking act on the area, never a single word,
  except where a search reached that word by name**:
  `BoxEngine.enqueue`/`dequeueArea` are the shelf's own controls;
  `dequeue` alone (single card id) exists for the one context that names a word.
  `CardRowState.Packed.removalOffered` and `PackOffered` both gate on the same
  `packOffered` context flag.
- **A composed session never refills** (user ruling 2026-07-29): the plan IS the run.
  Nothing joins a run under way now;
  endless practice (explicitly asked for from the summary) is where late cards land.
  `dueNow` therefore feeds counts, rings and fresh pulls only.
- **One composer for every round** (user ruling 2026-08-03).
  **User agency decides WHICH round opens, never what goes in one**:
  the caller names a round the box already has rules for,
  and no size, budget or flag crosses the boundary.
- **Join filter inventory**: composition, dueNow, dueCount, statistics, exposure
  operate on cards that join the current profile;
  the unlock check and `answer()` history reads operate on raw schedules by id.
  Non-joining schedules and enqueued entries are kept **inert**
  (never pruned; both revive on switch-back).
- `answer(cardId, rating, nowMillis, tzId)` on an unknown id leaves the state untouched.
  `SessionPlan` carries a `joinStamp` (source, target, catalog fingerprint);
  a stale run recomposes as the round that opened it (`SessionOpening`).
- **"Which words does the learner already hold" is an engine question**,
  answered once by `BoxEngine.growingCardIds`.
- **A drill run is a pure machine too** (`net.spross.kern.trainer`),
  and **one injected `Random` per run** feeds every draw,
  so a seeded run is reproducible end to end.
  Drills book no reviews and their storage keys are byte-identical across the two stores.
  The ladder, the modes and the availability gates are `docs/turns.md`.
- **Listening is a playlist over the learner's own words** (`net.spross.kern.listen`) --
  target word, meaning in the source language, then the target again.
  **Both halves must be sayable**, or a turn plays a word and then silence.
  **The playlist is dealt, not drawn**: one priority per word --
  the shakiest lead, then words the learner packed (most recently packed first),
  then the rest of the unseen ones (catalog's earliest stretch first as a group,
  shuffled within it) -- and the run walks and laps that order.
  **Suspended cards stay in the pool**: a suspended card pays a toll on its own Sprosse
  instead of being sent to the back.
  Hearing a word does not introduce it --
  `ListeningRun` holds no `BoxState` at all.
  The pool, the ladder, the deal, the beats and the bedtime fade are `docs/turns.md`.
- **Leniency is safe only to the extent the catalog can disprove it.**
  The typo budget forgives a slip,
  and `CatalogAnswerGrader` withdraws that credit wherever the typed form
  is really another concept's word.
  A drill caps at one slip per word:
  distinct cardinals sit >= 2 edits apart.
  The pipeline, the budgets and the sweeps are `docs/grading.md`.
- **`Match.producedRating()` is the one place a produce match becomes a rating** --
  both apps call it rather than re-deciding the mapping.
- **Own words** (`OwnWord`, `OwnWords`, `BoxEngine.addOwnWord/updateOwnWord/removeOwnWord`) --
  what the learner writes when the catalog has no word for what they need.
  They are the second and last source of cards,
  and the only CONTENT the box document holds:
  every other card is re-derived on load.
  Their ids carry an `own:` prefix and their area is fixed,
  so a catalog that grows can neither collide with the learner's words nor reclaim them.
  `removeOwnWord` is **the one deletion that reaches a single word**,
  and it reaches own words only.
  A word written in only ONE language is a **suggestion** (`OwnWord.isSuggestion`):
  it joins no card and is never scheduled;
  `addedAt` is stamped by `addOwnWord`, never by the caller.
  **Which of the three a word is, is the WORD's answer and never the open profile's**
  (user ruling 2026-09-16):
  two languages or more is a pair, whatever pair is on screen.
  What the profile decides is whether it can be STUDIED
  (`OwnWord.joins`, which is what `OwnWords.cards` emits a card for).
  An entry with a comment and NO language at all is a **remark** (`OwnWord.isRemark`):
  a note that names no word.
  The three are disjoint and exhaustive (`isPair`, `isSuggestion`, `isRemark`),
  and `Feedback` keeps them apart everywhere it lists them.
  `updateOwnWord` rewrites one in place, **keeping its id** and with it the schedule,
  the queue slot and anything filed against it.
  It keeps `addedAt` too: that records when the word was written.
- **The catalog catching up** (`CatalogMatches`, `BoxEngine.mergeOwnWord`) --
  a word the learner wrote because the catalog had none,
  beside the catalog word that has since landed.
  A match needs one side spelled EXACTLY as the catalog spells it
  (`teaches`, `accepts` and the articled form count), or both sides leaning;
  `MatchSide` says which agreed, and only `Both` is offered ticked.
  Own-word cards are never matched against each other.
  Merging keeps the progress:
  **the longer log wins, whole, and the two are never folded together**
  (user ruling 2026-09-16) --
  two records of learning ONE word replayed as one would overshoot stability.
  A tie goes to the catalog card.
  Suspended if either was, the queue slot moves across,
  and a merged suggestion packs the catalog word in its place.
  The fuzziness is `FormLikeness`, shared with the arrival matcher and deliberately NOT with
  grading: they share the distance and not the budget (`docs/grading.md`).
  `clearFeedback` is the bulk deletion, and it reaches the OUTBOX rather than the words:
  every suggestion, every remark and every filed report go together.
  A word written in both languages is never cleared -- it is a card with progress on it.
  `Feedback.clearableCount` is what a clear comes to.
  Suspending reaches a card the box has never asked --
  which mints a New schedule carrying nothing but the suspension;
  waking one that was never answered DROPS that schedule,
  since growth only ever reaches a card with none (`Growth.isIntroducible`).
  In a session, `SessionIntent.SuspendCurrent` does it without a rating.
  `BoxEngine.reset` is the destructive fresh start --
  schedules, queue and tallies go; the join, the configuration, the own words and the reports stay.
  `BoxEngine.forget` is the same idea aimed at ONE card:
  its schedule goes, the card and its report stay,
  and the day counters stay too since they carry no card ids.
  **Clearing what the box KNOWS must never delete what it HOLDS.**
- **Reports** (`ReportedIssue`, `Feedback`, `BoxEngine.reportIssue/dismissReportedIssue`) --
  what the learner says back about the catalog.
  Whatever they had typed is carried along.
  Reporting is **independent of `setSuspended`** and neither verb implies the other.
  A report needs no schedule -- reveal comes before the first answer.
  `Feedback` renders the suggestions, the notes and the reports as text --
  three sections, never one -- in kern rather than per platform,
  because a report is an INTERCHANGE format.
  `BoxState.lastExportAt` (set by `markExported`) is what "only what is new" measures against.
- **`Legal`** -- the addresses Spross publishes about itself.
  The one place both apps read them from.

## 7. Testing & gates

- **Catalog tests split three ways, by who owns the expectation.**
  `CatalogFixtureTest` (commonTest, synthetic `Fixture.kt`) pins exact values --
  the test owns its input.
  `CatalogLintTest` (jvmTest, real catalog) validates content *rules* without pinning values.
  `RealCatalogJoinTest` (jvmTest, real catalog) keeps only join *rules*,
  each derived from the catalog or exercised through a representative entry.
  Never pin real-catalog field values or totals:
  an ordinary authoring edit then reads as a join regression.
- `PaletteParityTest` (jvmTest) holds every hand-copied palette to `Palette`;
  its KDoc owns that rule.
- **A deliberate content change is allowed to move the tests that pin it.**
  Pinned expectations say what the drill teaches,
  so changing what it teaches SHOULD move them --
  contorting the content to keep them byte-identical
  inverts which of the two is the source of truth.
  Move the expectation and say so in the commit;
  the zero-test-edit bar belongs to REFACTORS.

## Rejected designs

Roads not taken -- never built, so there is no diff to find them in.
What was built and later removed is git's to remember.

- **Typed recognition** (user ruling 2026-07-22): self-grade only.
  Phrases alternate too.
- **Homonym disambiguation as content**: a per-realization `sense`/`gloss` string and a
  concept-level `homonymOf`/`disambiguator` link.
  Both rejected -- the area label already carries it for free, lint-guaranteed.
  Also rejected: emoji-as-cue -- a merged pair merges on one meaning and wears one picture,
  leaving the cue silent exactly where the ambiguity bites.
  Also rejected: cluster-wide grading leniency IN THE PRODUCE DIRECTION
  (accepting any cluster member teaches away the distinction).
  The meaning direction credits every owner (section 3): the merge is the target language's own.
  Also rejected: suppressing/deferring a cluster member
  (breaks composition determinism, and the collision returns once both are learned).
- **Number word tables in the catalog** (`catalog/numbers/<lang>.json`), assessed 2026-08:
  the packs are module-level objects in the `trainerPacks` registry,
  reachable from `Numbers.pack(language)` with no catalog in hand,
  so authored tables would make the registry a function of a loaded catalog
  and reach every sweep;
  a reading is generated, never authored,
  and `numberNotes` is the one place a language's irregularities get said in words
  (`catalog/phrases/README.md`).
