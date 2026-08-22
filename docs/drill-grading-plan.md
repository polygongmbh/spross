# Strict drill grading — implementation plan

A plan, not a spec: delete it once the last commit below has landed,
and move what outlives it into `../kern/docs/grading.md` (the rule)
and `number-forms.md` (whatever a pack owes the core index).

## The hole

`kern/src/commonMain/kotlin/net/spross/kern/trainer/DrillGrading.kt` wraps every free-text
drill answer as ONE synthetic card, and says so in its own KDoc:

> A drill has no catalog behind it: the accepted forms are wrapped as one synthetic card, so
> [Match.OtherWord] can never arise and there is no other concept's word for a slip to be
> mistaken for.

Two callers, so this is every free-text drill: `TrainerRun.grade` (numbers, years, clock,
fraction, forms, phrase sentences) and `CountryDrillRun.grade`.

The cost is paid as three allowlists, all verified in the source:

| Allowlist | Entries | Pinned pair counts |
|---|---|---|
| `TypoBridgeSweep.KNOWN_BRIDGES` | 11 word pairs over 7 languages | cardinals: de 0, uk 1, en 10, es 100, fr 30, it 1, sw 10, eo unpinned |
| `TrainerFormsTypoBridgeGuardTests.FORM_BRIDGES` | those 11 + 12 more = 23 | forms: de 0, en 6, es 231, it 2, sw **882**, fr 103, uk 30, eo 976 — **2230 total** |
| `ClockCollisionSweepTests.KNOWN_BRIDGES` | 11 word pairs | clock: es 1, sw 1, fr 1, uk 5, eo 3; de/en/it 0 |

The brief's "882 more in the forms answer space" is the SWAHILI figure alone; the forms guard
pins 2230 pairs in total. Everything else in the brief's counts checks out.

A learner typing *setenta* when the drill asked for *sesenta* is graded **Hard**, not Wrong —
in the drill whose whole job is keeping numbers apart.

## What the owner ruled, and what it costs the design

> sesenta for setenta is actually wrong, but if it is "ciento setenta y ocho" instead of
> "ciento sesenta y ocho" I would say that is okay to count as a typo

So the target is NOT "no reading is ever accepted for another".
It is a boundary, and the plan's real job is to state where it falls.

### The rule

**A drill answer grades strictly when its expected reading is ONE word in comparison form;
otherwise it keeps today's per-word budget untouched.**
On the strict path an input the drill's own answer space owns exactly, and this task does not,
is that value — not a slip of this one.

The principle, said once so a new language can be judged against it:
*the strict path applies exactly where the differing word IS the whole answer.*
In a longer reading the other words still pin the magnitude and the structure,
so the answer shows the learner built the number correctly and slipped once —
which is the definition of the typo the drills already forgive everywhere else.

Both of the owner's data points fall out of it: `sesenta` is one word, `ciento setenta y ocho`
is four. The residue it leaves is intended, not overlooked — eo `dek ses`/`dek sep` (16/17),
fr `cent six`/`cent dix` (106/110), es `ciento sesenta`/`ciento setenta` (160/170),
sw `kumi na nne`/`kumi na nane` (14/18) all keep the slip, because in each the leading
word already fixed the decade and only the unit moved.

### The two predicates that were tested against the real readings, and rejected

| Predicate | What it changes | Why not |
|---|---|---|
| **≤ 2 words** | also refuses eo 16/17, fr 106/110, es 160/170 | no principle separates two words from three, and it contradicts the owner's `ciento setenta y ocho` ruling only by one word — a line that has to be argued value by value is not a rule |
| **≤ 2 *content* words** (joiners `y`/`na`/`und`/`e` not counted) | additionally refuses sw 14/18, keeps es 168 lenient | needs a per-language joiner list that exists nowhere today; deriving it ("a word that is not itself a number's reading") misfires on es `ciento`, which is not the reading of 100 (`cien`) |

Both stay in this file rather than in a commit: if the owner wants 16↔17 refused,
"≤ 2 words" is a one-constant change and the sweep re-pins itself.

### ⚠ Ruling wanted: which form the word count is measured on

fr `soixante-dix` is one word only because the comparison pipeline DELETES the hyphen
(`AnswerNormalizer`: joiners `-'’` are dropped, other punctuation becomes a space);
eo `sesdek`, `sepcent` and it `centotto` are genuinely one word.

- **Comparison form** (recommended): fr 66↔70 and 86↔90 become Wrong.
  It is the only form the grader ever sees, so a raw-orthography word count would be a
  second notion of "word" living nowhere else in the pipeline, and it makes
  orthographically welded French behave like genuinely welded Italian and Esperanto.
- **Raw reading**: fr 66↔70 and 86↔90 stay typos, and the fr counts below move by 2 instead of 3.

## The mechanism — and it touches no shared grading file

The strict path routes through **`CatalogAnswerGrader.grade(input, card)` directly**,
over a bounded sibling card set. That class, `AnswerNormalizer` and `gradeDrillAnswer`
are all left exactly as they are: `gradeDrillAnswer` keeps its
`is Match.OtherWord -> Match.Wrong` flattening and keeps serving every multi-word reading.
The strict path simply does not route through the lenient one.

Nothing is minted. `CatalogAnswerGrader`'s own KDoc is already this plan's sentence:

> A form the catalog already accepts elsewhere is that word, never a slip of this one …
> the check never widens what counts as wrong — it re-labels a miss, and withdraws typo
> credit where the catalog can prove the word is taken.

Three consequences worth naming:

- **No `probeWords`, no `alsoSkipping`.** The dates plan needs them because a date is
  ASSEMBLED and its assembled space (7 × 12 × 31) is an inventory, not a card set.
  A one-word answer has nothing to assemble: the whole-string probe the grader already
  makes IS the word probe. See [Reconciliation](#reconciliation-with-the-dates-plan).
- **Phrase tasks are untouched.** A phrase answer is a sentence and never one word,
  so the assembled-answer problem does not arise here at all.
- **Reversed tasks are untouched.** A reversed answer is digits, and a word carrying a
  digit already grades exact-only (`AnswerNormalizer.wordBudget`). The strict path skips
  any expected form containing a digit rather than relying on that twice.

### The sibling set, per drill

Derived from the packs, never authored — `catalog/docs/drills.md:49` already rules that
"the table is derived from the trainer's own readings and can never be authored",
and an authored core would put a second copy of every numeral beside the generator that
grades it. What IS borrowed from the overview is the *shape*: the bands of
`REFERENCE_VALUES` in `NumberReference.kt` (base 0–15, tens, irregulars 16–30,
compounds, hundreds, places) are exactly the rows the owner pointed at.

| Drill | Index | Size per language |
|---|---|---|
| Numbers / Years | every value in 0–100 ∪ {101, 200…900, 1000, 2000, 5000, 10⁶, 2·10⁶, 10⁹} **whose reading is one word**, with its full drill accepted set (`Trainer.drillNumber`, so sw's `na`-less spelling and the de/it/eo variant twins are owned) | es ~30, sw ~30, de/it/eo ~110 |
| Forms | ordinals over `FormLimits.ordinalRange`, multiplicatives 1–100, the reduced fraction pool — the only forms whose readings can be one word; negative, decimal and percent always carry a marker word | ≤ 250 |
| Country | `CountryDrillContent.countries` / `.languages`, **scoped to the task's own kind** | ~130 |
| Clock | none — no authored language reads a time as one word (verified de/en/es/it/sw/fr/uk/eo) | — |

The index is *defined by the predicate that selects the strict path*: a value belongs to it
exactly when its reading is one word. For a welding language that is most of 0–999;
for Spanish it stops around 100 on its own, because `ciento uno` is two words.

**Same-kind scoping is load-bearing**, and it is the forms guard's own argument
("two prompts can only be confused if a learner can meet both in one run graded against one
accepted set"): cardinals and forms keep separate indexes, so de `achte` typed for `acht`
stays a typo; country names and people's names keep separate indexes, so de `Spanier`
typed for `Spanien` stays a typo instead of becoming a miss on twelve German pairs.

### Where it is built, and what it costs per turn

`TrainerRun.grade` is called on **every keystroke** (`typed()` runs the live approve through
it), so the index must never be built inside it. One object built once when the run opens
and passed in exactly as the normalizer is today:

- `TrainerRun.reduce(state, intent, normalizer, rng)` → `reduce(state, intent, grader, rng)`,
  where `DrillGrader` is a kern class holding the drill normalizer plus a lazily built
  `CatalogAnswerGrader` per (kind, language). Same for `TrainerRun.grade`.
- `CountryDrillRunConfig.normalizer` → `.grader`, built by a kern factory from the content
  the config already carries. Platforms call the factory; they mint nothing (`LayerBoundaryTest`).
- Lazy per kind, so a Numbers-only run never pays for the forms index; memoized per
  (kind, language) in the grader, so a resumed run pays once.

Cost at run open: ≤ ~110 cardinal readings + ≤ 250 form readings, each through
`normalize`. Sub-millisecond either way, and off the keystroke path entirely.

## The bridges table

Every pair is one of the 11 `KNOWN_BRIDGES` entries; "killed" means the strict path returns
`Match.OtherWord` where today it returns `Match.Typo`. Direction matters — the probe looks up
the string the learner TYPED, so a member outside the index only protects the other member.

| # | Lang | Pair (value ↔ value) | Both members one word? | In the index? | Killed |
|---|---|---|---|---|---|
| 1 | sw | `nne` 4 ↔ `nane` 8 | yes | both | both ways |
| 2 | uk | `дев'ять` 9 ↔ `десять` 10 | yes | both | both ways |
| 3 | en | `eight` 8 ↔ `eighty` 80 | yes | both | both ways |
| 4 | es | `sesenta` 60 ↔ `setenta` 70 | yes | both | both ways |
| 5 | fr | `six` 6 ↔ `dix` 10 | yes | both | both ways |
| 6 | fr | `soixante-six` 66 ↔ `soixante-dix` 70 | only after hyphen deletion ⚠ | both | both ways, **iff the comparison-form ruling** |
| 7 | fr | `quatre-vingt-six` 86 ↔ `quatre-vingt-dix` 90 | only after hyphen deletion ⚠ | both | both ways, same ruling |
| 8 | it | `ventotto` 28 ↔ `centotto` 108 | yes | 28 only | **one way only** — see below |
| 9 | eo | `ses` 6 ↔ `sep` 7 | yes | both | both ways |
| 10 | eo | `sesdek` 60 ↔ `sepdek` 70 | yes | both | both ways |
| 11 | eo | `sescent` 600 ↔ `sepcent` 700 | yes | both (round hundreds band) | both ways |

**Bottom line: 10 of the 11 die in both directions; the Italian pair dies in one.**
Nothing else survives that the owner has not said should survive.

**The one that survives, and what to do about it.** `centotto` (108) is not a round hundred,
so a 28 prompt answered `centotto` still grades Typo. The fix is one constant: widen the
cardinal band from 0–100 to 0–999. The predicate filters it anyway, so for Spanish, English,
French, Swahili and Ukrainian the index barely grows, while Italian, German and Esperanto —
the languages that weld, and therefore the only ones with three-digit one-word readings —
gain the ~900 entries where their own bridges live. **Recommended**, at a cost of one
`(0..999)` in place of `(0..100)` and a few hundred more strings at run open.
`ItalianNumbers` already reasons this way about content:

> The elided "centuno" is the one recorded spelling this pack leaves out. It sits a single
> substitution from "ventuno", so a drill accepting it would take 21 for 101.

**Multi-word derivations are NOT killed, by design**: es's 99 compound pairs
(`ciento sesenta y ocho`), en's 9 (`one hundred eighty`), sw's 9 (`kumi na nane`),
fr's 27 (`cent dix`), and every forms compound behind `hasi`, `asilimia`, `mara`, `menos`
and `мінус`. They stop being audited defects and become documented behavior.

**Two allowlists are untouched.** The clock's 11 gated pairs all sit inside multi-word
readings, so `ClockCollisionSweepTests` keeps every one of them — `cuarto`/`cuatro` included,
where one word of five carries the whole minute. That is the least comfortable corner of the
owner's rule and it is worth saying out loud rather than discovering later.

**A drill nobody swept gains real protection.** The country drill has genuine one-edit
collisions between DIFFERENT countries, measured over the atlas with the normalizer's own
comparison shape: eo `Ĉinio`/`Ĉilio` (China/Chile), sw `Uswisi`/`Uswidi` and
`Waswisi`/`Waswidi` (Switzerland/Sweden), uk `данці`/`ганці` (Danes/Ghanaians).
All four are single-word and all four die.

## Does the reveal name what was typed

Yes, and on the strict path it comes free: `CatalogAnswerGrader.grade` returns
`Match.OtherWord(word, meanings)` unflattened, because that path never calls
`gradeDrillAnswer`. `TrainerRun.submit` and `CountryDrillRun.submit` fold it into
`TurnFeedback.Revealed` today via their `else ->` arms, which books it as a miss —
correct, and the information is simply dropped.

**Recommendation: keep the verdict, but do NOT add a `TurnFeedback` arm.**
`TurnFeedback` is matched in 14 platform files (`KernBridge.swift`, `SessionView+Produce`,
`TrainerSessionView+Drill`, `AnswerInputView`, `CountryDrillView`, `LetterDrillView+Stages`,
`TurnFlow.kt`, `SessionTurn.kt`, `DrillField.kt`, `TrainerPrompt.kt`, `ProduceCard.kt`,
`CountryDrillScreen.kt`, …), and a new sealed arm churns every exhaustive `when`/`switch`
for one line of copy. `AlmostReason` is the wrong home too: an `Almost` books
`AnswerOutcome.Almost`, i.e. correct, which this is not.

Cheapest correct shape: an additive nullable field on `TrainerRunState` and
`CountryDrillRunState` — `wroteInstead: MistakenWord?` (`word`, `meanings`) — set beside
`TurnFeedback.Revealed` and cleared in the same transaction as the next prompt, exactly as
`hintUsed` is. Platforms read it only where the reveal card is drawn: two Swift files,
two Kotlin files, one string per platform. `producedRating()` already returns null for
`OtherWord`, so nothing in FSRS or the ramp changes.
Honest copy: the word probe names the WORD's owner, so the string is
*„nane" heißt 8*, never *"you wrote 8"* — at a compound the two are not the same claim.

## What happens to the sweeps

None of the four is deleted; all four change what they assert.

**`TypoBridgeSweep.run`** gains the real grading path. For each ordered pair it asks whether
the EXPECTED reading is one word: if so it asserts the verdict under the strict grader is
`Wrong` or `OtherWord` and reports nothing; if not it classifies against the allowlist as
today. Because direction now decides the verdict, the sweep must grade **both** orderings of
each pair instead of only `i` as input to `j`.

`KNOWN_BRIDGES` survives with its KDoc rewritten: it stops being "AUDITED EXCEPTIONS the
sweep found and gates explicitly" and becomes *the word pairs a LONGER reading is allowed to
bridge on, one slip inside a compound whose other words already fixed the magnitude*.
Entries 1–5 and 9–11 stay, because their compounds still bridge; the entries are still what
`isKnownBridge` matches per differing word.

Pinned counts, to be recomputed at implementation rather than trusted from here —
the direction of each recorded pair decides two of them:

| Test | Today | After (comparison-form ruling) |
|---|---|---|
| `ukrainianCardinals0To99…` | 1 | **0** — the only pair is the bare one; the assertion becomes `emptyList()` |
| `italianCardinals0To999…` | 1 | **0** — same, once 0–999 is indexed; **1** if the band stops at 100 |
| `englishCardinals0To999…` | 10 | 9 |
| `spanishCardinals0To999…` | 100 | 99 |
| `swahiliCardinals0To99…` | 10 | 9 |
| `frenchCardinals0To999…` | 30 | 27 (29 under the raw-reading ruling) |
| `germanCardinals0To999…` | 0 | 0 |
| `esperantoCardinals0To999…` | unpinned | loses `ses`/`sep`, `sesdek`/`sepdek`, `sescent`/`sepcent`; **pin the count while you are there** |
| `italianForms…` | 2 | **0** — both are bare ordinals |
| `frenchForms…` | 103 | 100 |
| `spanishForms…` | 231 | 230 in the `undécimo` → `un décimo` direction only ⚠ |
| `ukrainianForms…` | 30 | minus the bare ordinal pairs (`дев'ятий`/`десятий` and its cases) |
| `esperantoForms…` | 976 | minus every bare `-a` ordinal pair |
| `englishForms…` / `swahiliForms…` / `germanForms…` | 6 / 882 / 0 | unchanged — every entry is multi-word |

⚠ The Spanish `un décimo` (1/10) ↔ `undécimo` (11th) pair is the one place the rule is
asymmetric on its face: the ordinal is one word and the fraction is two, so typing the
fraction for the ordinal is refused and the reverse is not. That is the rule working
(the fraction's `un` did not carry the whole answer), but it is worth a comment in the test.

**`ClockCollisionSweepTests`** keeps all 11 gated pairs and gains one assertion:
that no authored clock reading is a single word. That is what makes the clock's exemption
a checked fact instead of a claim, and it fails the day a pack welds one.

**New `CountryCollisionSweepTests`** (jvmTest, real catalog, added to `corpusSweeps` in
`kern/build.gradle.kts`): every single-word country name, people's name and language name in
every pair, graded against every other of the SAME kind under the strict grader, asserting
`Wrong` or `OtherWord`. Its allowlist starts and stays empty — the four collisions above are
the reason it exists, and an entry appearing in it is a report, not a waiver.

**A gap this plan does not close, for `backlog.md`**: year readings are swept by nothing at
all today, and de/it/eo read a year as one word. The strict path reaches them only if the
year band joins the index; it is out of scope here and should be a one-liner in the backlog.

## Commits, smallest first, each green on its own

The strict path touches no file the vocab reviews share, so the gate does not have to
re-cover `CatalogAnswerGraderTests`/`AnswerNormalizerTests`. The sweeps still gate on
`./gradlew :kern:jvmTest -Psweeps`, and `--rerun-tasks` wherever `catalog/` is read
(Gradle does not track it as a test input).

**C1 — the rule, written down.** A section in `../kern/docs/grading.md` naming the strict
path, the one-word predicate, the comparison-form ruling and the same-kind scoping.
`number-forms.md` gains a one-line pointer. No gate.

**C2 — `DrillGrader` and the number core.** The kern class, the lazy per-(kind, language)
`CatalogAnswerGrader`, the core enumeration filtered by the predicate, and the strict entry
point beside `gradeDrillAnswer` in `DrillGrading.kt` (additive — the existing function is not
edited). `DrillGraderTests` on a fixture: predicate, index membership, `Exact` beating
`OtherWord`, a digit-bearing expected form skipping the strict path.
Gate: `:kern:jvmTest --tests '*DrillGrader*'`.

**C3 — route the trainer.** `TrainerRun.grade`/`reduce` take the grader; the strict path is
chosen per task. `TrainerRunTests` gains the four cases: bare `setenta`→`sesenta` refused,
`ciento sesenta y ocho` still Typo, a real slip (`setnta`) still Typo, a phrase answer
unchanged. Gate: `:kern:jvmTest`.

**C4 — route the country drill.** `CountryDrillRunConfig.grader`, same-kind index from the
content the config already holds. Gate: `:kern:jvmTest --tests '*Country*'`.

**C5 — the sweeps.** All of the table above, plus the new country sweep and the clock's
single-word assertion. The commit that proves the other four.
Gate: `:kern:jvmTest -Psweeps --rerun-tasks`.

**C6 — the reveal.** `wroteInstead` on both run states, both platforms' reveal chrome, the
String Catalog entries, `scripts/strings.py --fix`.
Gate: `:android:testDebugUnitTest`, the app build, `scripts/run-sim.sh --shot` and
`scripts/run-emu.sh --shot` — a user-facing change lands on both platforms in one sweep.

**C7 — `CHANGELOG.md`** under `## Unreleased`, and this file deleted.

## Risks

1. **The keystroke path.** `TrainerRun.typed` grades on every character. Building or even
   re-wrapping the index inside `grade` turns a 110-entry `normalize` loop into a
   per-keystroke cost. The grader is constructed at run open and passed in; a test that
   counts index builds across a typed run is cheap insurance.
2. **`Match.Exact` must keep winning.** The strict path passes the drawn task's OWN accepted
   list as the prompted card (`task.accepted`, not the index's copy), so every alternative
   spelling the drill accepts returns `Exact` before `otherWord` ever runs — including the
   clock's several registers per prompt and the twelve-hour cycle, where two prompts
   legitimately share a reading. Verified in `CatalogAnswerGrader.grade`; pin it with a test
   rather than re-deriving it.
3. **Learner-visible changes that are not obviously improvements.** A fat finger on a
   three-letter Esperanto numeral (`ses` → `sep`) now costs the rung where it used to cost
   nothing — which IS the point, but it lands hardest on the shortest words at the lowest
   rungs. The asymmetry of #8 is visible too: `centotto` typed for 28 is forgiven while
   `ventotto` typed for 108 is not, until the band is widened. And `Spanier` for `Spanien`
   would have become a miss on a dozen German pairs had the index not been scoped per task
   kind — if that scoping is ever dropped, this is what comes back.
4. **The predicate can move under the plan's feet.** A pack that welds a reading, or a new
   language, silently moves answers between the two paths. The sweeps pin the multi-word
   bridge lists exactly, so a move fails the gate — but only if the counts above are
   re-pinned as literals rather than as `assertTrue { all { … } }`.

## Reconciliation with the dates plan

`dates-drill-plan.md` and this plan use the same mechanism — `CatalogAnswerGrader` over a
bounded card set the drill builds itself — and differ only in what they need on top of it:

- The dates drill grades ASSEMBLED answers (`Montag, der dritte Juli`), so it needs the probe
  to reach inside the string. This plan's drills grade single words on the strict path and
  leave assembled answers to the lenient one, so they need nothing added.
- If the `probeWords` / `alsoSkipping` extension does land for dates, note one finding from
  here before it does: **a skip-set keyed by drawn card cannot express the `cuarto`/`cuatro`
  shape**, where the confusable word also appears legitimately elsewhere in the same answer.
  A POSITIONAL diff — probe only the input words that differ from the expected reading at
  the same position — expresses it, needs no extra parameter (the grader already holds the
  card and thus its accepted forms), and solves the dates plan's own `kumi na nne` example
  as a side effect. Worth raising there rather than deciding here.
