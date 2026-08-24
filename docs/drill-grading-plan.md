# Drill typo collisions — implementation plan

A plan, not a spec: delete it once Part 1's last commit has landed,
and move what outlives it into `../kern/docs/grading.md` (the rule)
and `number-forms.md` (what a pack owes the index).
Part 2 is an assessment, not a commitment — it ships as its own series or not at all.

## The hole

`kern/src/commonMain/kotlin/net/spross/kern/trainer/DrillGrading.kt` wraps every free-text
drill answer as ONE synthetic card, and says so in its own KDoc:

> A drill has no catalog behind it: the accepted forms are wrapped as one synthetic card, so
> [Match.OtherWord] can never arise and there is no other concept's word for a slip to be
> mistaken for.

Two callers, so this is every free-text drill: `TrainerRun.grade` (numbers, years, clock,
fraction, forms, phrase sentences) and `CountryDrillRun.grade`.

A learner typing *setenta* when the drill asked for *sesenta* is graded **Hard**, not Wrong —
in the drill whose whole job is keeping numbers apart. Nothing in production knows the two are
different numbers; the knowledge lives in three hand-written test allowlists instead:

| Allowlist | Where | Entries |
|---|---|---|
| `TypoBridgeSweep.KNOWN_BRIDGES` | commonTest | 11 word pairs over 7 languages |
| `TrainerFormsTypoBridgeGuardTests.FORM_BRIDGES` | commonTest | those 11 `+ listOf(12 more)` |
| `ClockCollisionSweepTests.KNOWN_BRIDGES` | jvmTest | 11, of which 5 are retyped from the other two |

None of the three is production code. Ten of the clock's eleven entries restate three
confusions already declared elsewhere (`nne`/`nane`, the six/seven twin, `дев'ять`/`десять`)
in inflected form; exactly one, es `cuarto`/`cuatro`, is genuinely clock vocabulary.
Alongside them the sweeps pin the exact COUNT of collisions each answer space tolerates —
es 231, sw 882, eo 976 and eleven more numbers — which is the maintenance burden this plan
exists to remove.

---

# Part 1 — the collision fix

## The rule

> for typos its not whether its one word or not, its about simplicity - having a limited set
> of collisions to track, making the code not overly complicated but just catching it where
> it is easy to catch

The limited set to track is a set of **values**, not of confusable word pairs:

**A drill answer the typo budget accepted is refused when a word it differs from the expected
reading in — at the same position — is itself a complete reading of a DIFFERENT value in that
language's number index.** Everything else grades exactly as today: the same normalizer, the
same per-word budget, the same verdicts.

The check runs only on the `Match.Typo` arm, so any spelling the task itself accepts has
already returned `Match.Exact`. That is what keeps the clock's twelve-hour cycle safe —
two prompts sharing a reading resolve as Exact before the check is reached.

```kotlin
return when (val match = normalizer.evaluate(trimmed, card)) {
    Match.Exact -> Match.Exact
    is Match.Typo -> otherNumber(normalizer, trimmed, match.corrected, index) ?: match
    is Match.OtherWord, Match.Wrong -> Match.Wrong
}
```

where `otherNumber` splits both sides on `AnswerNormalizer.normalize` — the one true
comparison shape, NOT `TypoBridgeSweep.comparisonShape`, which skips NFC and punctuation
collapsing and only agrees with it by accident on today's twenty literals — walks the
positions where the two differ, and returns `Match.OtherWord` naming the value it found.

## The artifact: a list of values, and no list of pairs

`TrainerLanguagePack.drillNumber(n)` already returns exactly the spellings a drill accepts for
a value, in every one of the eight languages. So the index is built, not authored, and the one
static thing this plan adds is a language-INDEPENDENT list of the values worth indexing:

```
0..20, the tens 30..90, 100, 1000        — the reference band
28, 66, 86, 108, 600, 700                — the values whose readings collide
```

Thirty-six integers, shared by all eight languages, against which every one of the eleven
cardinal bridges resolves. No confusable pair is authored anywhere in the grading path, and
nothing has to be kept in step with a pack: a re-spelled numeral changes the index the moment
the pack changes, because the index IS the pack's own output.

The six outliers are the honest part of the design. They are not reference numbers; they are
there because `ventotto`/`centotto` (28/108), the French `soixante-six`/`soixante-dix` pair
and eo `sescent`/`sepcent` collide, and a bounded index only refuses what it can name. Their
membership is not a judgement call — the sweep computes it (below), and a missing value fails
as a named collision, telling you which integer to add. If the list ever grows past roughly
fifty, index the drill's whole drawable range instead and delete the distinction; at thirty-six
the bounded band is worth keeping, because it also bounds the nudge to numbers a learner
would recognize.

Three properties fall out rather than being designed in:

- **The confusable pairs stop existing as data.** `cuarto`/`cuatro`, the eo six/seven family
  the forms guard derives in six lines, es `un décimo`/`undécimo` — all of them are "the word
  you typed is a reading of another value", never an entry.
- **Compounds are covered without a compound rule.** `ciento setenta y ocho` differs from
  `ciento sesenta y ocho` in one position, and `setenta` is 70, so it is refused — as is
  sw `kumi na nane`, fr `cent dix` and eo `sesdek kvin`.
- **The welded-form question dissolves.** The index is keyed in the normalizer's comparison
  shape, which is the only shape the grader ever sees, so `soixantedix` and `девять` are one
  word there and no hyphen ruling is needed.

⚠ **The check fires on the word TYPED, never on the word missed** — which makes it asymmetric
wherever only one side of a difference is a number, and that asymmetry is forced. Refusing
because the EXPECTED word is indexed would refuse `setnta` for `sesenta` too, since a number
word is indexed and its fumble is not: every typo on every numeral would become Wrong, which
is the one behavior the drill has to keep. So the rule needs positive evidence that the
learner wrote a different value, and only the typed side can carry it.

Consequence, at the clock: typing `cuatro` where `cuarto` belonged is refused and named
(4 is indexed), while `cuarto` for `cuatro` stays a typo (the index holds no such reading, so
there is nothing to name). All eleven cardinal pairs have both members indexed and are
unaffected. An ordinal index would not make the rule symmetric — it would widen the evidence,
so `cuarto` resolves to 4th and the same rule fires unchanged.

## The nudge

`Match.OtherWord` is already the verdict for "that is somebody else's word", it already
carries `word` + `meanings`, and both platforms already render it —
`session.otherWord %@ %@` in `Localizable.xcstrings` ("By the way: %1$@ means “%2$@”")
and `Chrome.otherWordNote` on Android. No new copy in either place.

**The index cards must put the DIGITS on the source side.** `drillGradingCard` today uses one
`Realization` for both sides, and `CatalogAnswerGrader` reads `word` off the target and
`meanings` off the source — so an index built that way would say *"setenta means setenta"*.
Built with `source.text = "70"` and `target.text` = the reading, it says *"setenta means 70"*,
which is the whole point of the nudge.

This is where the platform work is, and the earlier draft's "no platform work" claim does not
survive it: `TrainerRunState` carries `feedback: TurnFeedback` but no `otherWord`, so the
field is added there and rendered in `DrillField.kt` and the iOS drill view. Two small
renderings against copy that already exists, and no new `TurnFeedback` arm.

## The country drill

Its index is the real catalog, not a list: `Ĉinio`/`Ĉilio`, `Uswisi`/`Uswidi`,
`Waswisi`/`Waswidi` and `данці`/`ганці` are all cards, so a `CatalogAnswerGrader` over the
country cards names them with no data added at all.

The index must be scoped to ONE kind at a time — countries against countries, peoples against
peoples — or the country↔nationality pairs the same measurement turns up (de
`Spanien`/`Spanier` and eleven more, it `Russia`/`russi` and eleven more) would turn a dozen
German near-misses into misses. Those are two rungs of the ladder asking two questions, never
one accepted set.

## What it costs

The index is built once and cached, exactly as `TurnMachine` already does for every normal
card turn — the "grades on every character" hazard in `TrainerRun.typed` is only a hazard if
the index is rebuilt inside it. What threads through is a `Map<Language, …>` beside the
existing `normalizer: AnswerNormalizer?` parameter on `TrainerRun.reduce`/`grade` and
`CountryDrillRunConfig`; both already carry a nullable grading dependency, so the shape is
established and a preview with no language info keeps falling back to `plainVerdict`.

## What was considered instead

| Alternative | Verdict |
|---|---|
| **A hand-written confusable pair list**, consulted on the whole answer | Rejected. It is the smallest diff, but it leaves ~24 pairs to author and keep from rotting, refuses nothing inside a compound, and gives the learner no nudge. |
| **The same list consulted per differing WORD** (the sweep's own `isKnownBridge`) | Superseded. Its BEHAVIOR is what this plan adopts — the owner accepted that `ciento setenta y ocho` is refused — but resolving the differing word against an index instead of a pair list drops the data and adds the nudge. |
| **A word-count / letter-share / leading-word predicate** | Withdrawn by the owner. |
| **Indexing the drill's whole drawable range** rather than a reference band | Held in reserve. Simpler, no outlier values, but it nudges on any compound and the band is currently small enough not to need it. |
| **Author the numbers in the catalog** | Part 2, and this plan does NOT need it: the index reads the packs. The earlier claim that the two ideas "converge on one artifact" is withdrawn — with no pair list left, there is nothing for the catalog to adopt. |

## What happens to the sweeps

This is where the pinned counts go.

Every pair the sweeps classify as an audited bridge is one the normalizer accepts AND whose
differing words are all listed twins — which under the new rule is exactly a pair production
now refuses. So the allowlists and the counts both stop being the assertion, and each sweep
asserts one thing instead:

> for every colliding pair found, `gradeDrillAnswer` returns a miss.

That is strictly stronger than today, because it exercises the production grader rather than
a proxy normalizer, and there is nothing to re-pin: es 231, sw 882, eo 976 and the eleven other
numbers are deleted, not recomputed. A collision whose differing word is not in the index fails
the sweep by name, which is what makes the value list self-correcting.

One assertion replaces the rot guard the exhausted allowlist used to give: **every value in the
index is reachable as a reading in the language's answer space**, so a stale integer cannot sit
there doing nothing.

`ClockCollisionSweepTests` loses its own list entirely — `cuarto`/`cuatro` is caught by the
cardinal index — and keeps its two unrelated assertions (the twelve-hour closure and the
peeled-leading-word guard). `TrainerFormsTypoBridgeGuardTests.FORM_BRIDGES` is the one
allowlist that SURVIVES, as sweep data only: the forms space has no index in this plan, so its
collisions stay tolerated and stay pinned until an ordinal index exists. Say so in its KDoc
rather than leaving it looking like an oversight.

**A gap for `docs/backlog.md`**: year readings are swept by nothing at all today, and de/it/eo
read a year as one word.

## Commits, smallest first, each green on its own

**C1 — the index.** The value list, the per-language build over `drillNumber(n)` with digits on
the source side, and its cache. No caller yet. Gate: `:kern:jvmTest`.

**C2 — the check.** `otherNumber` in `DrillGrading.kt`, the index threaded through
`TrainerRun.reduce`/`grade`, and a new `DrillGradingTests` (none exists today): bare
`setenta`→`sesenta` refused and named, `ciento setenta y ocho` refused, a real slip (`setnta`)
still Typo, an exact alternative spelling still Exact, a phrase answer unchanged, a reversed
answer unchanged. Gate: `:kern:jvmTest`.

**C3 — the sweeps.** All four rewritten to assert the production verdict; every pinned count and
two of the three allowlists deleted; the index-reachability assertion added. This is the commit
that proves the value list complete, so any outlier the sweep names is added HERE.
Gate: `:kern:jvmTest -Psweeps --rerun-tasks`.

**C4 — the country drill.** The catalog-backed index, kind-scoped, plus the new
`CountryCollisionSweepTests` (jvmTest, real catalog, added to `corpusSweeps`).
Gate: `:kern:jvmTest -Psweeps --rerun-tasks`.

**C5 — the nudge on both platforms.** `TrainerRunState.otherWord`, rendered in `DrillField.kt`
and the iOS drill view against the existing strings. Gates: `:android:testDebugUnitTest`, the
app build, and both screens seen side by side.

**C6 — docs.** The rule in `../kern/docs/grading.md`, what a pack owes the index in
`number-forms.md`, `CHANGELOG.md` under `## Unreleased`, and this file deleted.

## Risks

1. **A learner's fat finger now costs the rung, and it costs it inside compounds too.**
   eo `ses` → `sep` is one keystroke, `kumi na nne` → `kumi na nane` one letter in the last
   word. That IS the ruling, and the nudge is what softens it — which is why C5 is not
   optional. Worth a look on the simulator even though no gate demands it.
2. **The asymmetric corner.** `cuarto` for `cuatro` is forgiven while `cuatro` for `cuarto` is
   refused. The reason is sound and stated above — the check needs evidence on the typed side,
   or every fumbled numeral becomes Wrong — but it will look arbitrary to anyone reading the
   grader without it, so `otherNumber` owes it a KDoc line rather than this file alone.
3. **The index is language-keyed for a reason.** Unkeyed, `dix` would resolve to 10 on an
   English prompt where it is not a word — harmless in effect, wrong in reasoning, and a trap
   for whoever adds the next language.
4. **The forms space is left as it is**, with 2230 tolerated collisions still pinned. That is a
   deliberate deferral, not an oversight, and the one place this plan does not reduce the
   maintenance burden.

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
4. Part 1's index keeps reading `drillNumber(n)` throughout — it never sees the migration,
   because a pack composing from authored words still answers the same call.

If Part 2 is not taken, this section becomes a `docs/backlog.md` one-liner with a pointer to
the per-language table above.

---

## Reconciliation with the dates plan

`dates-drill-plan.md` proposes `CatalogAnswerGrader` over a calendar card set, plus a
`probeWords` flag and an `alsoSkipping` parameter. **Nothing here conflicts with it, and
nothing here depends on it** — the two now share a mechanism rather than merely coexisting.
Both grade against an index of whole readings; the difference is only which readings go in it.

The positional diff is the piece to carry over. A skip-set keyed by drawn card cannot express
the `cuarto`/`cuatro` shape, where the confusable word also appears legitimately elsewhere in
the same answer; probing only the input words that differ from the expected reading at the
same position expresses it, needs no `probeWords` flag and no `alsoSkipping` parameter (the
grader already holds the card and its accepted forms), and solves the dates plan's own
`kumi na nne` example as a side effect. If Part 1 lands first, the dates drill inherits
`otherNumber` rather than adding a second probe.
