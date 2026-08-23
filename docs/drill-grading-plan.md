# Drill typo collisions — implementation plan

A plan, not a spec: delete it once Part 1's last commit has landed,
and move what outlives it into `../kern/docs/grading.md` (the rule)
and `number-forms.md` (what a pack owes the confusable set).
Part 2 is an assessment, not a commitment — it ships as its own series or not at all.

## The hole

`kern/src/commonMain/kotlin/net/spross/kern/trainer/DrillGrading.kt` wraps every free-text
drill answer as ONE synthetic card, and says so in its own KDoc:

> A drill has no catalog behind it: the accepted forms are wrapped as one synthetic card, so
> [Match.OtherWord] can never arise and there is no other concept's word for a slip to be
> mistaken for.

Two callers, so this is every free-text drill: `TrainerRun.grade` (numbers, years, clock,
fraction, forms, phrase sentences) and `CountryDrillRun.grade`.

The cost is paid as three allowlists, all counted in the source:

| Allowlist | Entries | Pinned pair counts |
|---|---|---|
| `TypoBridgeSweep.KNOWN_BRIDGES` | 11 word pairs over 7 languages | cardinals: de 0, uk 1, en 10, es 100, fr 30, it 1, sw 10, eo unpinned |
| `TrainerFormsTypoBridgeGuardTests.FORM_BRIDGES` | those 11 + 12 more = 23 | forms: de 0, en 6, es 231, it 2, sw **882**, fr 103, uk 30, eo 976 — **2230 total** |
| `ClockCollisionSweepTests.KNOWN_BRIDGES` | 11 word pairs | clock: es 1, sw 1, fr 1, uk 5, eo 3; de/en/it 0 |

Two corrections to the brief: "882 more in the forms answer space" is the SWAHILI figure
alone — the forms guard pins 2230 pairs in total; and the "can never be authored" sentence
Part 2 has to renegotiate lives in `catalog/drills/README.md:49`, not in `catalog/README.md`.
Everything else in the brief checks out.

A learner typing *setenta* when the drill asked for *sesenta* is graded **Hard**, not Wrong —
in the drill whose whole job is keeping numbers apart.

---

# Part 1 — the collision fix

## The rule

> for typos its not whether its one word or not, its about simplicity - having a limited set
> of collisions to track, making the code not overly complicated but just catching it where
> it is easy to catch

So there is no predicate. There is a **list**, and one place that consults it:

**A drill answer that the typo budget accepted is refused when the whole typed answer and the
whole expected reading are a listed confusable pair.** Everything else grades exactly as
today — the same normalizer, the same per-word budget, the same verdicts.

That is the whole mechanism:

```kotlin
return when (val match = normalizer.evaluate(trimmed, card)) {
    Match.Exact -> Match.Exact
    is Match.Typo -> if (confusable(normalizer, trimmed, accepted, language)) Match.Wrong else match
    is Match.OtherWord, Match.Wrong -> Match.Wrong
}
```

where `confusable` is `accepted.any { setOf(normalizer.normalize(trimmed), normalizer.normalize(it)) in DrillConfusables.pairs(language) }`.
Ten lines inside `gradeDrillAnswer`, which both callers already share.
`AnswerNormalizer`, `CatalogAnswerGrader` and every run signature stay untouched.

Three properties fall out rather than being designed in:

- **Compound bridges stay fine, by construction.** `ciento sesenta y ocho` is not equal to
  `ciento setenta y ocho` as a listed pair, so it never fires — the owner's ruling holds
  without a word count, a letter share, or a leading-word rule.
- **The welded-form question dissolves.** The pairs are stored in the normalizer's own
  comparison shape, which is the only shape the grader ever sees; `soixantedix` and `девять`
  are already written that way in `KNOWN_BRIDGES` today. No hyphen ruling is needed.
- **Phrase answers and reversed answers are untouched.** A sentence is never equal to a
  listed pair member, and a reversed answer is digits (already exact-only via `wordBudget`).

`Match.Exact` still wins: the check runs only on the `Match.Typo` arm, so any spelling the
task itself accepts has already returned. That is also what keeps the clock's twelve-hour
cycle and its several registers per prompt safe, since two prompts sharing a reading resolve
as Exact before the check is reached.

## The set: invert `KNOWN_BRIDGES` rather than write a new one

`TypoBridgeSweep.KNOWN_BRIDGES` is ALREADY the small enumerated set this rule needs — it is
just labeled as defects to tolerate. The move is to change what it means, not what it holds:

- it moves from `commonTest` to `commonMain` as `DrillConfusables`, keyed by language;
- production consults it on the WHOLE answer (strict);
- the sweeps keep consulting it per differing WORD (lenient inside a compound), which is
  exactly what `isKnownBridge` does today.

One artifact, two readings of it, and a diff measured in tens of lines.

Every one of the 11 entries is a pair of COMPLETE readings of two values, which is why the
whole-answer check reaches all of them. The forms list adds 12 more, of which 9 are likewise
complete readings; the remaining 3 (`{ninths, ninth}`, `{septièmes, septième}`,
`{девятих, десятих}`) are fragments that only ever appear inside a longer reading and simply
never fire. So the strict set is **20 literal pairs**, and the same 23 entries keep serving
the compound classification.

⚠ **The French welded pair is now a plain yes/no, not a design question.** Entries 6 and 7
(`soixante-six`/`soixante-dix`, `quatre-vingt-six`/`quatre-vingt-dix`) are one word only
because the pipeline deletes the hyphen. Under a predicate that would have been a rule to
argue; under a list it is one decision: keep them in the strict set or drop them. Keeping
them (recommended — 66 and 70 are different numbers, and the shared `soixante` is a prefix
rather than a word the learner got right) puts fr cardinals at 27; dropping them puts it at
29 and leaves entries 6 and 7 in the list for compound classification only.

⚠ **Esperanto is the one language whose confusables are a rule rather than a list.** Its
endings weld onto every numeral, so `ses`/`sep` returns inside every ordinal, `-ono` and
`-foje` built on 6 or 7 — `sesa`/`sepa`, `sesdeka`/`sepdeka` and so on, all of them complete
one-word readings. `TrainerFormsTypoBridgeGuardTests.sixSevenTwins` already derives that
family in six lines, and its own KDoc gives the reason ("the derivation IS the finding:
forty literals would bury it"). Recommendation: move that derivation to `DrillConfusables`
beside the literals. If it is left out, eo `sesa` for `sepa` stays a typo while fr `sixième`
for `dixième` is refused, which is an inconsistency a learner of both would meet.

### The country drill's four pairs

Nothing audits the country drill today, and it has genuine one-edit collisions between
DIFFERENT countries — measured over `catalog/countries/*.json` with the comparison pipeline's
shape: eo `Ĉinio`/`Ĉilio` (China/Chile), sw `Uswisi`/`Uswidi` and `Waswisi`/`Waswidi`
(Switzerland/Sweden), uk `данці`/`ганці` (Danes/Ghanaians). All four are whole answers, so
they are four more literal entries in the same list and cost nothing beyond the sweep that
finds them authoritatively.

They must NOT be joined by the country↔nationality pairs the same measurement turns up
(de `Spanien`/`Spanier` and eleven more, it `Russia`/`russi` and eleven more): those are two
different questions the ladder asks at two different rungs, never one accepted set, and
listing them would turn a dozen German near-misses into misses for no gain. The forms guard
already states that principle — a confusion only counts where "a learner can meet both in one
run graded against one accepted set".

## What was considered instead

| Alternative | Verdict |
|---|---|
| **Grade through `CatalogAnswerGrader` over a per-drill card index** (the earlier draft of this plan, and the dates plan's shape) | Rejected. It works, and it costs a `DrillGrader` object, a lazily built owner index per (kind, language), a signature change on `TrainerRun.reduce`/`grade` and `CountryDrillRunConfig`, a keystroke-path hazard (`typed()` grades on every character), and an asymmetry where a value outside the index protects only its twin. It buys reach nobody asked for above the enumerated band. |
| **Consult the list per differing WORD** (the sweep's own `isKnownBridge`) | Rejected: it refuses `ciento setenta y ocho`, which the owner explicitly called a fine typo. |
| **A word-count / letter-share / leading-word predicate** | Withdrawn by the owner. It also forced the hyphen ruling, which no longer exists. |
| **Author the pairs in the catalog** | Deferred to Part 2, where the words themselves would be authored. Part 1 must not wait for it. |

## The bridges table

"Killed" = the pair no longer grades `Typo`; the check is symmetric, so both directions go at
once. Every value below is the bare reading of the value named.

| # | Lang | Pair | Killed |
|---|---|---|---|
| 1 | sw | `nne` 4 ↔ `nane` 8 | yes |
| 2 | uk | `дев'ять` 9 ↔ `десять` 10 | yes |
| 3 | en | `eight` 8 ↔ `eighty` 80 | yes |
| 4 | es | `sesenta` 60 ↔ `setenta` 70 | yes |
| 5 | fr | `six` 6 ↔ `dix` 10 | yes |
| 6 | fr | `soixante-six` 66 ↔ `soixante-dix` 70 | yes |
| 7 | fr | `quatre-vingt-six` 86 ↔ `quatre-vingt-dix` 90 | yes |
| 8 | it | `ventotto` 28 ↔ `centotto` 108 | yes |
| 9 | eo | `ses` 6 ↔ `sep` 7 | yes |
| 10 | eo | `sesdek` 60 ↔ `sepdek` 70 | yes |
| 11 | eo | `sescent` 600 ↔ `sepcent` 700 | yes |

**Bottom line: all 11 die, in both directions, with no index and no new grading path.**
Nine more die in the forms space: uk `дев'ятий`/`десятий`, `дев'ята`/`десята`,
`дев'яте`/`десяте`; es `un décimo` 1/10 ↔ `undécimo` 11th; fr `sixième`/`dixième`,
`soixante-sixième`/`soixante-dixième`, `quatre-vingt-sixième`/`quatre-vingt-dixième`;
it `ventesimo`/`centesimo` and `ventesima`/`centesima`.

**What survives, deliberately**: every derived compound — es's 99 `ciento sesenta y ocho`
shapes, en's 9 `one hundred eighty`, sw's 9 `kumi na nane`, fr's 27 `cent dix`, and the whole
Swahili forms family of 882 behind `hasi`, `asilimia` and `mara`. They stop being audited
defects and become documented behavior in the same commit.

**The clock is untouched.** Every one of its 11 gated pairs sits inside a multi-word reading —
no authored language reads a time as a single word (checked across all eight) — so
`cuarto`/`cuatro` stays a typo. That is the least comfortable corner of the owner's rule,
since one word of five carries the whole minute there, and it is worth saying out loud now
rather than discovering later.

## The reveal

**No work, and the earlier recommendation is withdrawn.** This mechanism returns `Match.Wrong`,
never `Match.OtherWord`, so `TurnFeedback` gains no arm, no run state gains a field, and none
of the 14 platform files that match on `TurnFeedback` are touched. The reveal already shows
the right answer and the learner's own text is still in the field.

If naming the other value is ever wanted ("*setenta* is 70"), the list is where it would go —
a third element per entry — and it stays a pure additive change.

## What happens to the sweeps

All four survive; three change what they assert, and the fourth is new.

**`TypoBridgeSweep.run`** reads its pairs from `DrillConfusables` instead of owning them, and
gains one assertion: for every pair it still finds, the two readings must not BE a listed pair
whole — i.e. no bare confusable survives. The KDoc inverts with it: `KNOWN_BRIDGES` stops
being "AUDITED EXCEPTIONS the sweep found and gates explicitly" and becomes *the word pairs a
LONGER reading is allowed to bridge on, one slip inside a compound whose other words already
fixed the magnitude.* Since the verdict is now direction-independent, nothing about the
sweep's ordering has to change.

Pinned counts — **recompute at implementation rather than trusting this table**, since only
the source can settle which recorded pairs are bare:

| Test | Today | After |
|---|---|---|
| `germanCardinals0To999…` | 0 | 0 |
| `ukrainianCardinals0To99…` | 1 | **0** — assertion becomes `assertEquals(emptyList(), …)` |
| `italianCardinals0To999…` | 1 | **0** — same |
| `englishCardinals0To999…` | 10 | 9 |
| `spanishCardinals0To999…` | 100 | 99 |
| `swahiliCardinals0To99…` | 10 | 9 |
| `frenchCardinals0To999…` | 30 | 27 |
| `esperantoCardinals0To999…` | unpinned | −3 (6/7, 60/70, 600/700); **pin the count while you are there** |
| `italianForms…` | 2 | **0** |
| `frenchForms…` | 103 | 100 |
| `spanishForms…` | 231 | 230 |
| `ukrainianForms…` | 30 | 27 |
| `esperantoForms…` | 976 | unchanged, or minus the whole-reading ordinals if the derived family lands |
| `englishForms…` / `swahiliForms…` / `germanForms…` | 6 / 882 / 0 | unchanged |

**`ClockCollisionSweepTests`** keeps all 11 gated pairs and gains one assertion: that no
authored clock reading is a single word. That turns the clock's exemption from a claim into a
checked fact, and fails the day a pack welds one.

**New `CountryCollisionSweepTests`** (jvmTest, real catalog, added to `corpusSweeps` in
`kern/build.gradle.kts`): every country name against every other, every people's name against
every other, every language name against every other — same kind only — asserting the drill
verdict is `Wrong`. Its allowlist is empty; the four pairs above are what it exists to hold,
and the Python approximation that found them is not authoritative.

**A gap for `docs/backlog.md`**: year readings are swept by nothing at all today, and de/it/eo
read a year as one word. Out of scope here; one line in the backlog.

## Commits, smallest first, each green on its own

No shared grading file is edited, so the fast gate stays narrow; the sweeps still need
`-Psweeps`, and `--rerun-tasks` wherever `catalog/` is read.

**C1 — `DrillConfusables` in `commonMain`.** The 11 + 9 literal pairs keyed by language, the
eo derivation, and the sweeps reading from it instead of owning the lists. Pure move; every
count above still holds at this commit. Gate: `:kern:jvmTest -Psweeps`.

**C2 — the check.** Ten lines in `gradeDrillAnswer`, plus `DrillGradingTests`: bare
`setenta`→`sesenta` refused, `ciento sesenta y ocho` still Typo, a real slip (`setnta`) still
Typo, an exact alternative spelling still Exact, a phrase answer unchanged, a reversed answer
unchanged. Gate: `:kern:jvmTest`.

**C3 — the sweeps inverted.** The new bare-pair assertion and every re-pinned count.
Gate: `:kern:jvmTest -Psweeps --rerun-tasks`.

**C4 — the country drill.** The new sweep, then the four pairs it reports.
Gate: `:kern:jvmTest -Psweeps --rerun-tasks`.

**C5 — docs.** A short section in `../kern/docs/grading.md` naming the rule and where the list
lives, a pointer from `number-forms.md`, `CHANGELOG.md` under `## Unreleased`,
and this file deleted (Part 2 moving to `backlog.md` if it is not taken).

No platform work in any of them, and no app build gate: nothing user-visible changes shape,
only which of two existing verdicts a handful of answers earn.

## Risks

1. **The list rots the other way now.** Today a vanished pair fails the sweep because the
   allowlist must be exhausted. After the inversion a listed pair that stops colliding still
   fails (the sweep keeps returning what it found), but a listed pair that never WAS a whole
   reading — a fragment like `{ninths, ninth}` — sits in the strict set doing nothing forever.
   Cheap guard: a test asserting every strict entry is a complete reading of some value in its
   language's answer space, which also catches a typo in a hand-written pair.
2. **A learner's fat finger now costs the rung.** eo `ses` → `sep` is one keystroke, and at
   rung 1 the shortest words are exactly where this bites hardest. That IS the point, but it
   is the one behavior change a learner will notice, and there is no reveal copy softening it
   (see above). Worth a look on the simulator even though no gate demands it.
3. **The set is language-keyed for a reason.** Unkeyed, `{six, dix}` would fire on an English
   prompt where `dix` is not a word — harmless in effect, wrong in reasoning, and a trap for
   whoever adds the next language. One map lookup buys the reasoning back.

---

# Part 2 — the authored numbers lexicon (assess, then stage separately)

> for a catalog numbers list, the idea would be that the trainer uses that rather than coding
> everything itself in code

**Recommendation: do not bundle this with Part 1.** Part 1 needs nothing from it, and this is
a structural change to how packs are constructed. What follows is the assessment the decision
needs.

## Where the lexicon/grammar line actually falls, per pack

Read out of the eight packs rather than assumed. Every one of them has a small word table and
a body of composition rules, and the tables are the same shape everywhere — ones, teens, tens,
sometimes hundreds, plus scale words:

| Pack | Lexicon | Grammar that cannot be data |
|---|---|---|
| eo | 9 unit words, and nothing else | tens/hundreds weld, thousands stay apart, a hyphen before every ending, the x-system twin |
| sw | ones 9, tens 9, `sifuri`, `mia`, `elfu`, `milioni`, `bilioni` — ~23 | `na` joining, `mia moja`/`moja` for a bare multiplier, the `na`-less accepted twin |
| de | ones 9, teens 10, tens 8 — 27 | reversed `…und…`, the `ein` apocope, `einhundert`, welding, `eine Million`/`Millionen` |
| it | ones, teens, tens — ~29 | total welding, vowel elision before `uno`/`otto`, `cento` elision, the acute on `-tré`, the `e` before a scale tail, the `centootto` twin |
| en | ones, teens, tens | `hundred`, `and`, hyphenation |
| fr | units 0–16 (17), decades 20–60 (5), two scale nouns | vigesimal 70/80/90, `et` at 21…71, **three varieties** (`septante`/`huitante`/`nonante`), three spellings of every reading, feminine `une`, the year's `mil` and hundred-style |
| es | ones, teens, **twenties as their own list**, tens, hundreds — ~50 | `y` above 30, `cien`/`ciento`, and a three-way agreement (masculine / apocopated / feminine) that reaches `uno` AND `-cientos` |
| uk | onesMasc, onesFem, teens, tens, hundreds — ~48 | Slavic three-way count agreement (`agree`), `тисяча`/`тисячі`/`тисяч`, gendered ones, `FEMININE_ONES` derived from the table |

Two things stop the split from being clean, and they are the same two in every language that
has them:

- **Some tables are inflection, not vocabulary.** uk `onesFem`, es `twenties`/`hundreds` in
  three agreements, fr `feminine` — a JSON list would have to carry agreement columns or the
  pack keeps deriving them. Deriving is right: `UkrainianNumbers` already derives
  `FEMININE_ONES` from its own table precisely so "the pair cannot drift from the table that
  produces it".
- **Scale words are agreement-bearing nouns**, not one word each: de `eine Million`/`Millionen`,
  uk `тисяча`/`тисячі`/`тисяч`, fr `million`/`milliard` pluralizing. The catalog can author the
  citation form; every other form stays the pack's.

So the honest line is: **the catalog owns the WORDS; the pack owns every form derived from
them and all composition.** That is roughly 9–50 authored strings per language against
composition rules that stay in Kotlin in all eight.

## What it buys

- A contributor re-spelling a numeral, adding a regional variant, or fixing a wrong word edits
  JSON instead of Kotlin — real, and the most common kind of content fix.
- The confusable list of Part 1 could sit beside the words it names, which is where it wants to
  live long-term; the two ideas converge on one artifact.
- The overview's rows and the drill's answers would visibly share one source.

What it does **not** buy, and this should be said before it is sold as such: a NEW language
still needs a pack, because every language's composition is Kotlin. Authoring words alone
unlocks nothing until a composer can be described as data too, which the fr/uk/es rows above
say it cannot.

## What it costs

- A schema and a hand-parser (`catalog/numbers/<lang>.json`, on `CountryAtlasParser`'s
  conventions), plus lint: exact list lengths, no blanks, no duplicates, no leading/trailing
  space.
- **The structural cost, which is the real one**: the packs are module-level `object`s in a
  `linkedMapOf` registry (`trainerPacks`), reachable from `Trainer.pack(language)` with no
  catalog in hand. Reading authored content means the registry becomes a function of a loaded
  catalog, and that reaches `Trainer`, `TrainerMode`, `NumberReference`, `PhraseSlots`, every
  clock and forms pack that calls its own numbers object, and every sweep. Eight migrations
  plus a threading change is not a diff to hide inside a typo fix.

## The spec conflict, head on

`catalog/drills/README.md:49` — not `catalog/README.md` — states:

> the table is derived from the trainer's own readings and can never be authored

What that was buying is drift protection: the overview cannot print a reading the drill would
not accept, because both come from the same Kotlin literal.

**The rule after the change, which keeps the protection by construction:** *the words are
authored; the readings never are.* The pack composes FROM the authored lexemes, and the
overview keeps printing `pack.number(n)` — so the table and the drill still read the same
words through the same composer, and drift remains impossible rather than merely tested.
What the change does introduce is that a wrong authored word is now shippable without a
compile error, so two guards replace the compile:

- a **golden-vector test** pinning composed readings for a fixed value set per language
  against the pre-migration ones — which is exactly the bar the Invariants block already sets
  for a migration ("migrations need only behavioral (golden-vector) parity");
- the catalog lint above, so a missing or duplicated word fails the load rather than the drill.

## Staging

1. Part 1 lands complete and alone.
2. If Part 2 is taken, its own first commit is the golden-vector test over today's packs —
   written before anything moves, since it is the only thing that can prove the migration.
3. Then one language at a time, easiest split first: **eo** (9 words), **sw** (~23),
   **de**/**it**/**en**, and **fr**/**es**/**uk** last, where the agreement tables decide how
   much can be data at all.
4. The confusable list moves into the same files once they exist, and `DrillConfusables`
   becomes a reader instead of a literal.

If Part 2 is not taken, this section becomes a `docs/backlog.md` one-liner with a pointer to
the per-language table above.

---

## Reconciliation with the dates plan

`dates-drill-plan.md` proposes `CatalogAnswerGrader` over a calendar card set, plus a
`probeWords` flag and an `alsoSkipping` parameter. **Nothing here conflicts with it, and
nothing here depends on it.** The two answer different questions: the dates drill grades
ASSEMBLED answers whose components must be checked inside the string, while these drills need
a listed pair of whole answers refused. A third mechanism is not being invented — this is
strictly less than the grader, not sideways to it.

One finding worth carrying over there before that extension lands: **a skip-set keyed by drawn
card cannot express the `cuarto`/`cuatro` shape**, where the confusable word also appears
legitimately elsewhere in the same answer. A POSITIONAL diff — probe only the input words that
differ from the expected reading at the same position — expresses it, needs no extra parameter
(the grader already holds the card and its accepted forms), and solves the dates plan's own
`kumi na nne` example as a side effect.
