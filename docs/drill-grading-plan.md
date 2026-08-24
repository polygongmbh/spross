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

The limited set to track is a set of **values**, not of confusable word pairs. A drill answer
the typo budget accepted is refused when it names a different value, checked by two probes
against one per-language index of readings:

1. **The whole answer.** If the normalized answer is a complete reading of another value, it
   is that value, not a slip of this one. (This is what `CatalogAnswerGrader.otherWord`
   already does for catalog cards.)
2. **Each differing word, positionally.** Otherwise, walk the positions where the answer and
   the expected reading differ; if a word typed there is itself a complete reading of a
   different value, refuse.

Everything else grades exactly as today: the same normalizer, the same per-word budget, the
same verdicts. The check runs only on the `Match.Typo` arm, so any spelling the task itself
accepts has already returned `Match.Exact` — which is what keeps the clock's twelve-hour cycle
safe, since two prompts sharing a reading resolve as Exact before the check is reached.

```kotlin
return when (val match = normalizer.evaluate(trimmed, card)) {
    Match.Exact -> Match.Exact
    is Match.Typo -> otherNumber(normalizer, trimmed, match.corrected, index) ?: match
    is Match.OtherWord, Match.Wrong -> Match.Wrong
}
```

Both probes compare on `AnswerNormalizer.normalize` — the one true comparison shape, NOT
`TypoBridgeSweep.comparisonShape`, which skips NFC and punctuation collapsing and agrees with
it only by accident on today's twenty literals.

**The whole-answer probe is not an optimization; it is load-bearing.** es `un décimo` (1/10)
and `undécimo` (11th) differ by a space, so the two sides have different word counts and no
positional diff can ever see them. It also does most of the work on compounds: `ciento setenta
y ocho` IS a complete reading of 178, so it is refused before any word is examined.

## The artifact: an index built from the packs, and nothing authored

Every reading the index holds already exists as a function of the packs:

- `TrainerLanguagePack.drillNumber(n)` — the spellings a cardinal drill accepts;
- `TrainerLanguagePack.formReading(value)` — the same for a `NumberValue`;
- `FormLimits` — which forms a pack reads, its fraction denominators, its ordinal range.

And the enumeration of everything drawable already exists too: `drawableValues(limits)` in
`TrainerFormsTypoBridgeGuardTests`. It moves to `commonMain`, where the index and the sweep
both read it — one description of the answer space, not two.

So there is **no static list to author and nothing to keep in step with a pack**: the index is
the pack's own output, and a re-spelled numeral changes it the moment the pack changes. The
earlier draft's thirty-six reference values and its six hand-picked outliers are both gone;
the index covers the range the drill actually draws.

Only two form kinds need indexing beyond the cardinals. Negative, decimal, percent and
multiplicative are an INVARIANT wrapper word around an unmodified cardinal (`hasi $n`,
`menos $n`, `$n percent`), so they mint no vocabulary and the cardinal index already answers
for them. Ordinal and Fraction mint their own word stock, and both enumerate cheaply —
fractions over `2..12` with `gcd(n, d) == 1`, which is a few dozen values.

**Build it lazily and cache it per language.** The check runs only on the `Match.Typo` arm, so
nothing needs an index until the first near-miss of a run; C1 measures the build and bounds
the cardinal range to the digit counts a run has drawn if it is not cheap enough.

Three properties fall out rather than being designed in:

- **The confusable pairs stop existing as data.** `cuarto`/`cuatro`, the eo six/seven family
  the forms guard derives in six lines, `un décimo`/`undécimo` — all of them are "what you
  typed is a reading of another value", never an entry.
- **Compounds need no compound rule.** They are readings, so the whole-answer probe has them;
  where a wrapper hides the difference (`hasi nne` ↔ `hasi nane`) the positional probe does.
- **The welded-form question dissolves.** The index is keyed in the normalizer's comparison
  shape, the only shape the grader ever sees, so `soixantedix` and `девять` are one word there
  and no hyphen ruling is needed.

⚠ **The check fires on what was TYPED, never on what was missed**, and that is forced.
Refusing because the EXPECTED reading is indexed would refuse `setnta` for `sesenta` too — a
number word is indexed and its fumble is not — so every typo on every numeral would become
Wrong, which is the one behavior the drill has to keep. The rule needs positive evidence that
the learner wrote a different value, and only the typed side can carry it. Where a differing
word is in no index at all (`media`, `y`, a clock's part-of-day), nothing fires and the slip
stays a typo, which is the right answer: there is no other value to name.

## The nudge

`Match.OtherWord` is already the verdict for "that is somebody else's word", it already
carries `word` + `meanings`, and both platforms already render it —
`session.otherWord %@ %@` in `Localizable.xcstrings` ("By the way: %1$@ means “%2$@”")
and `Chrome.otherWordNote` on Android. No new copy in either place.

**The index cards must put the DIGITS on the source side.** `drillGradingCard` today uses one
`Realization` for both sides, and `CatalogAnswerGrader` reads `word` off the target and
`meanings` off the source — so an index built that way would say *"setenta means setenta"*.
Built with `source.text = "70"` and `target.text` = the reading, it says *"setenta means 70"*,
which is the whole point of the nudge. An ordinal or a fraction names itself the same way
("11.", "1/10").

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
| **A hand-written confusable pair list**, consulted on the whole answer | Rejected. Smallest diff, but ~24 pairs to author and keep from rotting, nothing refused inside a compound, and no nudge. |
| **The same list consulted per differing WORD** (the sweep's own `isKnownBridge`) | Superseded. Its BEHAVIOR is what this plan adopts — the owner accepted that `ciento setenta y ocho` is refused — but resolving against an index instead of a pair list drops the data and adds the nudge. |
| **A bounded band of ~36 reference values** | Withdrawn after measurement. It cannot hold eo's ordinal residue (below), it needs six hand-picked outlier values, and it gives up the whole-answer probe's reach over compounds for nothing. |
| **A word-count / letter-share / leading-word predicate** | Withdrawn by the owner. |
| **Author the numbers in the catalog** | Part 2, and this plan does NOT need it: the index reads the packs. The earlier claim that the two ideas "converge on one artifact" is withdrawn — with no pair list left, there is nothing for the catalog to adopt. |

## What the forms space actually costs — measured, not assumed

An earlier draft deferred the whole forms space on the assumption that its 2230 pinned
collisions were untouchable without a forms index. That was wrong by roughly twenty times.
The generators were ported and every pinned count reproduced exactly before splitting them:

| Lang | Pinned today | Refused by the CARDINAL index alone | Needs an ordinal/fraction index |
|---|---|---|---|
| sw | 882 | 882 | 0 |
| es | 231 | 230 | 1 |
| eo | 976 | 949 | 27 |
| fr | 103 | 96 | 7 |
| uk | 30 | 25 | 5 |
| en | 6 | 5 | 1 |
| it | 2 | 0 | **2** |
| **total** | **2230** | **2187** | **43** |

Swahili is 100% because its forms never touch the numeral — `nne`/`nane` appears bare under
every wrapper. Italian is the mirror image and the reason the forms index is not optional:
both of its collisions are ordinal-only (`ventesimo`/`centesimo`), so it gains NOTHING from
the cardinal index. The rest of the residue is uk's ordinal and fraction genders/cases,
en `ninths`/`ninth`, fr's `-ième` twins, and es `un décimo`/`undécimo` — the one pair that
needs the whole-answer probe rather than any index scope.

Esperanto's 27 are the reason the reference band was abandoned. Its ordinal welds the entire
cardinal into one token before `-a`, and the pipeline deletes the hyphen, so every one of them
is a WHOLE-string collision spread across 6, 16, 26 … 96 and the whole 60s and 70s. Indexing
the drawn range holds them; a band never could.

## What happens to the sweeps

This is where the pinned counts go, and the allowlists with them.

Every pair the sweeps classify as an audited bridge is one the normalizer accepts AND whose
differing words are all listed twins — which under the new rule is exactly a pair production
now refuses. So each sweep asserts one thing instead:

> for every colliding pair found, `gradeDrillAnswer` returns a miss.

That is strictly stronger than today, because it exercises the production grader rather than
a proxy normalizer, and there is nothing to re-pin: es 231, sw 882, eo 976 and the eleven
other numbers are deleted, not recomputed. **All three allowlists go** —
`TypoBridgeSweep.KNOWN_BRIDGES`, `FORM_BRIDGES` and the clock's own list, whose one
genuinely-clock entry `cuarto`/`cuatro` the ordinal index now answers for.

One assertion replaces the rot guard the exhausted allowlists used to give: **the sweep's
enumeration and the index's are the same `drawableValues`**, so a reading the drill can draw
is indexed by construction and there is no list left to go stale.

`ClockCollisionSweepTests` keeps its two unrelated assertions (the twelve-hour closure and the
peeled-leading-word guard).

**A gap for `docs/backlog.md`**: year readings are swept by nothing at all today, and de/it/eo
read a year as one word.

## Commits, smallest first, each green on its own

**C1 — the index.** `drawableValues` moved to `commonMain`; the per-language index over
cardinals, ordinals and fractions with digits on the source side; lazy build and cache, with
the build measured. No caller yet. Gate: `:kern:jvmTest`.

**C2 — the check.** `otherNumber` in `DrillGrading.kt` (whole-answer probe, then positional),
the index threaded through `TrainerRun.reduce`/`grade`, and a new `DrillGradingTests` (none
exists today): bare `setenta`→`sesenta` refused and named, `ciento setenta y ocho` refused,
`un décimo`→`undécimo` refused, `hasi nane`→`hasi nne` refused, a real slip (`setnta`) still
Typo, an exact alternative spelling still Exact, a phrase answer unchanged, a reversed answer
unchanged. Gate: `:kern:jvmTest`.

**C3 — the sweeps.** All four rewritten to assert the production verdict; every pinned count
and all three allowlists deleted. This is the commit that proves the index complete: anything
the sweep still finds unrefused is a gap in the enumeration, not an entry to add.
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
2. **The check keys on the typed side.** Sound, and stated above, but it will look arbitrary
   to anyone reading the grader without the reason, so `otherNumber` owes it a KDoc line
   rather than this file alone.
3. **The index is language-keyed for a reason.** Unkeyed, `dix` would resolve to 10 on an
   English prompt where it is not a word — harmless in effect, wrong in reasoning, and a trap
   for whoever adds the next language.
4. **The index is now the thing that can be incomplete.** The old failure was a rotted
   allowlist; the new one is an answer space the enumeration does not reach — a form a pack
   reads that `drawableValues` does not offer. Sharing one enumeration between the sweep and
   the index is what contains it, and it is the invariant `number-forms.md` should carry.
5. **Build cost is unmeasured.** Indexing the drawn range is thousands of `drillNumber` calls
   per language. Lazy and cached it should never be felt, but C1 measures rather than assumes,
   and the fallback (bound to the digit counts a run has drawn) is cheap.

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

`catalog/phrases/README.md:49` — not `catalog/README.md` — states:

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
