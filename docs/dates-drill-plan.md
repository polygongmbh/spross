# Dates drill — implementation plan

A plan, not a spec: delete it once the last commit below has landed,
and move what outlives it into `date-readings.md` (the per-language rules),
`catalog/docs/dates.md` (the format) and `surfaces.md` (the screen).

## The hole

Weekday names and month names exist nowhere in the product.
Not as concepts (`catalog/time/concepts.json` has day, week, month, year, hour, minute
and the parts of the day, and no Monday and no March),
not as realizations, not as drill content.
The only catalog hits for `montag|januar|monday|january|jumatatu|январ|понеділок|січень`
are Italian and French `montagna`/`montagne`.

`catalog/drills/frames.json` carries 18 frames over four slot kinds —
`clock` ×7, `numbers` ×7, `years` ×3, `fraction` ×1 —
so the clock drill can already say *the train departs at 14:30*
while the learner has no way to say which day it departs.
That is an A1 hole in a product whose thesis is breadth of exposure.

The owner's framing decides the shape:

> weekdays and month names could find their way into a separate dates drill
> which trains such independently and then assembles them

So the unit of work is not nineteen vocabulary cards.
It is a ladder that drills the weekday names alone, the month names alone,
the day-of-month numeral alone, and then composes them into a spoken date —
*Montag, der dritte März* · *Jumatatu, tarehe 3 Machi* · *понеділок, третього березня*.

## The design

**A fourth drill of its own** (`DateDrill`), on the Country/Letter drill pattern:
its own task type, its own run machine, its own overview page,
reusing `DrillRamp`, `DrillGrading`, the `DrillRun` envelope and the platform `DrillChrome`.
Its content is authored in `catalog/dates/`, its numerals come from the existing trainer packs,
and it is the first drill whose answer set is **half authored and half generated** —
which is the whole reason it needs both a catalog lint and a collision sweep.

### Why not a new frame slot kind

`catalog/docs/drills.md` states there is deliberately no `forms` slot,
and the argument it gives is not about number forms:

> a frame is grammatically bound to the family it carries —
> an ordinal frame needs the NUMERAL declined by the frame (`auf dem vierten Platz`),
> and the only agreement device runs the other way, from the numeral to the noun (`count`).

A `weekday`, `month` or `date` slot walks into exactly that wall, in every language that has cases:
German needs *am Montag* against *der Montag ist frei*,
Ukrainian needs the locative *у березні* against the nominative *березень*
and the genitive *березня* the date itself wants,
Italian and French need *le lundi* against a bare *lundi* in a dated headline.
The frame would have to decline the drawn name, and no device runs that way.
That passage is not an obstacle to argue around here; it predicts this case correctly.

Two further reasons close it:

- **The slot value is generated in the language being typed** (`catalog/docs/drills.md`),
  and a frame drill exists only where the answer language has a trainer pack.
  Weekday and month names are lexical content that must be authored per language.
  Feeding a frame slot from the catalog would invert the layering —
  `TrainerLanguagePack` is code that computes readings; nineteen names are not computed.
- **The `{slot}` contract is exactly one marker per frame text.**
  A date is three or four markers at once, and its punctuation and articles differ per language.

### Why not a variant of the numbers trainer

`TrainerKind` may be appended to and `DrillVariant` could take a `Dates` arm,
which would buy `TrainerRun`, the ramp, the records, the modifiers and both platform screens for free.
It is rejected on the rule `surfaces.md` already states:

> Each is its own SKILL, which is the only thing that earns a chip.
> Clock, sentences and number forms are not siblings of the numbers drill
> but ways of being asked.

Reading a date is not a way of being asked for a number:
two of its rungs ask for a word the catalog authored,
and its ladder is stages that change what a question *is* (the letter drill's kind),
not magnitudes that change how big a number is (the numbers ladder's kind).
`DrillVariant` and `DrillUnlocks` cover only the trainer-generated numbers family,
and Country and Letter deliberately sit outside both.
The dates drill follows them.

### Why not cards alone

Nineteen concepts in `time/` would give the names FSRS and nothing else:
a card can ask *Montag* ↔ *Monday* and can never ask for *der dritte März*.
The composition IS the skill, there are 366 of them, and a rule is what a drill trains —
the same split the numbers drill already embodies, where `Zeit` is a card and `zwanzig` is not.
Cards are still recommended, as their own series; see [Do the names also deserve cards](#do-the-names-also-deserve-cards).

### Rejected: prompting a weekday by its index

An early shape had a rung asking *day 3* → *Mittwoch*, on the grounds that
a card cannot ask that and a drill can.
It is dropped: a weekday's ordinal position is a **calendar convention, not a language fact**.
ISO 8601 makes Monday 1; English, Spanish and Swahili usage makes Sunday 1;
and Swahili's names count from a third origin again —
*Jumatatu* is literally "day two" and falls on Monday.
A rung prompting a digit would teach a convention while claiming to teach a word.
The Swahili counting is a `note`, not a rung.

## The ladder

Seven rungs, each keeping what is below it, three clean wins to advance
(`DrillRamp` with `winsToAdvance`, fast unlocked by having stood on the top rung —
the country drill's pacing, for the country drill's reason).

| Rung | Asks | Prompt | Source of the answer |
|---|---|---|---|
| 1 | weekday | the source language's name | catalog |
| 2 | month | the source language's name | catalog |
| 3 | day of month | `3.` (numeric, language-neutral) | pack |
| 4 | day + month | `3.3.` in the source's numeric format | pattern |
| 5 | full date | `Mo, 3.3.` — source `abbr` + numeric | pattern |
| 6 | full date with year | `Mo, 3.3.2026` | pattern + pack |
| 7 | everything above, drawn at random | — | — |

Rungs 1–2 are a symmetric pair join, exactly like the country drill:
a name realized on both sides becomes a task,
and `reverse` flips which side asks.
Rungs 3–6 prompt with digits on both sides,
so the source language contributes only its weekday abbreviation and its numeric date format —
which is why the whole drill still requires a `dates/<lang>.json` on **both** sides.

A rung a language cannot carry is absent, not locked:
Ukrainian has no rung 6 (see the table below), and the ladder simply tops out at 5 there.
The rungs are not earned and carry no padlock,
because the drill books no review and keeps no schedule
(`surfaces.md`, the country drill's rule).

## Per-language date-reading rules

The sibling page of `clock-registers.md` and `number-forms.md`.
Everything below moves into `docs/date-readings.md` in commit C1;
this table is the plan's input, and every cell marked ⚠ wants a native ruling.

### Reach

| | Weekday | Month | Day of month | Year in a date |
|---|---|---|---|---|
| de | Montag…Sonntag | Januar…Dezember | **ordinal**, weak after the article | plain cardinal |
| en | Monday…Sunday | January…December | **ordinal** | the pack's year reading (`twenty twenty-six`) |
| eo | lundo…dimanĉo | januaro…decembro | **ordinal** (`-a`) | plain cardinal |
| es | lunes…domingo | enero…diciembre | **cardinal**, ⚠ 1st | plain cardinal |
| fr | lundi…dimanche | janvier…décembre | **cardinal**, `premier` for the 1st | plain cardinal, `mil` graded 1001–1999 |
| it | lunedì…domenica | gennaio…dicembre | **cardinal**, `primo` for the 1st | plain cardinal |
| sw | Jumatatu…Jumapili | Januari…Desemba | **cardinal** after `tarehe`, ⚠ 1st | plain cardinal |
| uk | понеділок…неділя | січень…грудень | **ordinal, genitive** | **absent — see below** |

Every day-of-month reading is GENERATED by the pack, never authored:
numerals are `number-forms.md`'s to own, and a second table of thirty-one ordinals per language
would be the same fact in two homes.
Only the *words* — the names, the abbreviation, the pattern around them — are authored.

### German

`Montag, der 3. März` reads *Montag, der dritte März*.
The ordinal is the weak `-e` after `der`;
`den dritten März` is the accusative a learner meets in *am Mittwoch, den 3. März*
and grades as a pattern variant.
`GermanForms` already emits `-er`/`-en`/`-es` beside the canonical `-e` (`number-forms.md` § German),
so the day form composes with nothing new.
The year is the plain cardinal the pack reads, hundred-style variants included.

⚠ `Sonnabend` beside `Samstag` and `Jänner` beside `Januar` are different lexemes, not spellings,
so by `catalog/README.md`'s rule they would be `synonyms` and take their turn as prompts.
Both are regionally alive and nationally marked; a native has to say whether they earn that
or whether the `octante` ruling applies (accepted-but-not-taught is a register nobody uses).

### English

`March 3rd` / `the third of March`, both current, both ordinal.
⚠ Which leads the reveal is a ruling: the bare `March third` is what a speaker says out loud,
`the third of March` is what the learner's own language most resembles.
The year takes the pack's year reading, so `2026` is *twenty twenty-six* and not *two thousand twenty-six*
— but both are in `EnglishNumbers.yearVariants` already.

### Esperanto

`lundo, la 3-a de marto` reads *lundo, la tria de marto*.
Fully regular: `-a` over every numeral, no first-day exception, no case.
The x-system twin rides behind every reading exactly as `number-forms.md` § Esperanto requires,
so `jxauxdo` must grade beside `ĵaŭdo` — and that is authored, since it is a *word*, not a derivation.

### Spanish

`el 3 de marzo` reads *el tres de marzo*, cardinal, and the pattern carries two `de`
once the year is on it (`el 3 de marzo de 2026`).
⚠ The first of the month is the live split: `el primero de marzo` across most of Latin America,
`el uno de marzo` in Spain. Both grade; which one the reveal teaches is a native's call.
⚠ `de 2026` against `del 2026` — both attested; the pattern must pick one and accept the other.
Note that Spanish ordinals reach only 1–12 (`number-forms.md` § Reach),
which costs nothing here because the day is a cardinal.

### French

`le 3 mars` reads *le trois mars*, cardinal, with `le premier mars` the one exception —
never `le un mars`, so the pack's `dateDay(1)` must emit `premier` and refuse the cardinal.
`FrenchForms` already carries `premier` as the ordinal canonical, so this is a reuse, not a new reading.
The year is the plain cardinal, and `number-forms.md` § French already rules that
`mil neuf cent …` "belongs to dates of the Christian era" and grades for 1001–1999 —
which is precisely the reading this drill exists to exercise.

### Italian

`il 3 marzo` reads *il tre marzo*, cardinal, with `il primo marzo` the one exception.
⚠ Whether the weekday takes its article inside a date (`lunedì 3 marzo` against `il lunedì 3 marzo`)
is a usage question the pattern has to settle.

### Swahili

`Jumatatu, tarehe 3 Machi` — `tarehe` is a word of the pattern, and the day is a bare cardinal.
This is the language the design fits best:
Swahili has no ordinals at all (`number-forms.md` § Swahili: the associative concord slot has no noun
to agree with, so the pack refuses to invent one), and a date needs none.
⚠ Three rulings wanted: whether `tarehe mosi` is what a speaker says for the 1st,
whether the traditional `Mwezi wa Tatu` should grade beside `Machi`
(it carries the concord slot the pack will not fill, so it can only be authored as a synonym, never generated),
and how to word the note that the names count from a different origin than the week they fall in.

### Ukrainian

`понеділок, 3 березня` reads *понеділок, третього березня* —
**genitive ordinal day + genitive month**.
Two consequences, and they are the hard part of this whole plan:

- The month name has two forms the drill needs: the nominative it is *called*
  (`березень`, what a rung-2 answer is) and the genitive it takes *inside a date*
  (`березня`). Both are authored; the second is the schema's `dateForm`.
- The genitive ordinal is not something `UkrainianForms` emits today —
  its ordinal canonical is the nominative masculine `двадцять перший`
  with the feminine and neuter graded and the plural refused.
  The date form is a mechanical suffix swap on the **last word only**
  (`-ий → -ого`, `-ій → -ього`, `третій → третього`), and 1–31 sits well inside the authored 1–100 reach,
  so `dateDay` overrides it rather than widening the numbers drill's answer space.
  Adding it to `NumberForm` instead would put a case form into a drill that asks for none.

**Ukrainian takes the year gap.**
`3 березня 2026 року` reads *третього березня дві тисячі двадцять шостого року* —
an ordinal genitive year, which the pack does not produce and which is not
a date rule but a whole second numeral family.
So Ukrainian carries no rung 6, exactly as
"Ukrainian year frames would need ordinal and case forms the trainer does not produce"
already took that gap for the frames (`catalog/docs/drills.md`),
and exactly as French carries no counted-noun frame.
An honest missing rung costs a learner nothing but the rung.

Every Ukrainian reading uses the ASCII apostrophe `U+0027` (`п'ятниця`),
for the reason `number-forms.md` § Ukrainian gives.

## Catalog schema

New top-level folder, a sibling of `alphabet/`, `countries/` and `drills/`,
and a new section in `catalog/docs/dates.md` linked from `catalog/README.md`'s
"The rest of the format" list. Written in that file's voice:

> ## The calendar (`catalog/dates/`)
>
> The names of the days and the months, and how this language writes a whole date.
> What the engine composes out of them is `../../kern/docs/catalog.md`.
>
> The calendar itself is not authored: seven weekdays and twelve months in ISO order
> are the same list in every language, so there is no manifest beside these files
> the way `countries/atlas.json` sits beside its names —
> *which* countries the atlas holds is an authoring decision and which days a week holds is not.
>
> **`dates/<lang>.json`**:
> ```json
> { "weekdays": [ { "text": "Montag", "abbr": "Mo" }, … ],
>   "months":   [ { "text": "März" }, … ],
>   "numeric":  "{d}.{m}.{y}",
>   "patterns": {
>     "dayMonth":     { "text": "der {day} {month}", "variants": ["den {day} {month}"] },
>     "date":         { "text": "{weekday}, der {day} {month}", "variants": ["{weekday}, den {day} {month}"] },
>     "dateWithYear": { "text": "{weekday}, der {day} {month} {year}" } } }
> ```
>
> The Ukrainian side, carrying what only Ukrainian needs:
> ```json
> { "months": [ { "text": "січень", "dateForm": "січня" }, … ],
>   "numeric": "{d}.{m}.{y}",
>   "patterns": {
>     "dayMonth": { "text": "{day} {month}" },
>     "date":     { "text": "{weekday}, {day} {month}" } } }
> ```
>
> - `weekdays` — exactly seven, **Monday first**; `months` — exactly twelve, **January first**.
>   Both are all-or-nothing: a file naming eleven months is a bug, not a coverage gap,
>   so lint requires the full set rather than tolerating a hole
>   (the `subtitle` rule, for the same reason — a partial calendar reads as a broken drill,
>   not as a language that does not use August).
> - `text`, `synonyms`, `variants` and `notes` behave exactly as a realization's do
>   (`README.md` § Realization fields): a different lexeme is a synonym and takes its turn as a prompt,
>   a spelling is a variant and is only ever accepted.
>   Esperanto's x-system twins are variants; German `Sonnabend` is a synonym.
> - `abbr` (weekdays only) — the short form the PROMPT side wears in a dated line (`Mo, 3.3.`).
>   It is never an answer, so it is not graded and never taught;
>   it exists because the prompt has to name a weekday without writing it out in the answer language.
> - `dateForm` — the form this word takes **inside a date**, where that differs from `text`.
>   Named for the rule and not for the case, so a language whose date takes some other form
>   than a genitive needs no second key. Absent everywhere but Ukrainian today.
> - `numeric` — how this language writes a date in digits, over `{d}`/`{m}`/`{y}`.
>   Display only, on the prompt side; nothing is graded against it.
> - `patterns` — the assembly, over `{weekday}`, `{day}`, `{month}` and `{year}`.
>   `dayMonth` and `date` are required, `dateWithYear` is optional:
>   a language that cannot read a year inside a date simply omits it and the ladder stops one rung short.
>   `variants` on a pattern is accept-only, exactly as a frame's is.
> - The **day** is never authored here. It is generated by the language's trainer pack
>   (`../../docs/date-readings.md`), which is why a dates file without a pack is legal
>   for the prompt side of a pair and never for the answer side —
>   the frame rule again, and the same registry rule: **file presence is the registry**,
>   and a pair drills dates only where both sides carry a file.
> - An absent `dates/` folder is legal.
> - Nothing here is a concept and nothing here joins a card,
>   so a name is not a slug and editing one never restamps a learner's box.

## Kern-side work, one green commit at a time

Each of these builds and passes `./gradlew :kern:jvmTest` on its own.
Gates named per commit; a docs-only commit needs none.

**C1 — `docs/date-readings.md`.**
The per-language table above, with its sources, and the two rulings it hands to a native.
`number-forms.md` and `clock-registers.md` gain a one-line pointer; nothing else moves.
No gate.

**C2 — the catalog content and its parser.**
`catalog/dates/de.json` + `en.json` (two languages, so a pair joins),
`DateCalendar` / `DateNames` / `DatePattern` in `kern/…/catalog/`,
a `DateCalendarParser` hand-parsed on `CountryAtlasParser`'s conventions,
`Catalog.dateNames(lang)` and `Catalog.dateDrillContent(source, target): DateDrillContent?`
returning null where either side has no file.
Read through the **RAW** `CatalogSource`, not the fingerprinting wrapper —
the atlas' and the frames' exemption, for their reason: this joins no card, so it must not restamp a box.
Parse-shape rules hard-fail the load (unknown keys, wrong list length, a pattern marker
the pattern's own kind does not take, `{day}` without a pack-readable language).
`CatalogDatesFixtureTest` on synthetic JSON covering every shape.
Gate: `:kern:jvmTest --tests '*Dates*'`.

**C3 — `TrainerLanguagePack.dateDay(day: Int): List<String>`.**
Defaulted on the interface so no pack is forced to change at once
(the `formReading` precedent), then implemented for all eight:
de/en/eo delegate to the existing ordinal reading;
es/sw return the cardinal; fr/it return the cardinal with `premier`/`primo` at 1;
uk overrides with the genitive suffix swap on the last word.
`TrainerDateReadingTests` pins canonical-and-accepted per language against `date-readings.md`.
Purely additive — no drill exists yet.
Gate: `:kern:jvmTest --tests '*DateReading*'`.

**C4 — the tasks and the ladder.**
`DateTaskKind` (Weekday, Month, DayOfMonth, DayAndMonth, FullDate, FullDateWithYear),
`DateDrillTask`, `DateDrill` (`MAX_LEVEL`, `winsToAdvance`, `fastUnlocked`, `kinds`, `sample`,
`reference`, `answerLanguage`/`promptLanguage`), and the pattern filling that turns
a drawn `(weekday, day, month, year?)` into a display and an accepted set —
the day from the pack, the names and the pattern from the content, `dateForm` substituted for `{month}`.
A pattern's `variants` cross-multiply with the day's accepted readings,
the same way a clock reading's accepted set is built.
`DateDrillTests` on a hand-built two-language fixture: ladder shape, per-rung kinds, task shapes.
Gate: `:kern:jvmTest --tests '*DateDrill*'`.

**C5 — the run machine.**
`DateDrillRunConfig(content, reverse, fast, normalizer)`, `DateDrillIntent`, `DateDrillReduction`,
`DateDrillClose`, `DateDrillRunState`, `DateDrillRun` (`open`/`openAt`/`reduce`/`grade`/`close`) —
`CountryDrillRun`'s shape, reusing `DrillRamp`, `DrillTally`, `DrillEffect` and `DrillRunSummary`.
One injected `Random` per run. Books no review, writes no schedule, persists only the best rung
under its own storage key, byte-identical across the two stores.
`DateDrillRunTest`: intents, grading, effects, ramp, close/summary/best-rung.
Gate: `:kern:jvmTest --tests '*DateDrillRun*'`.

**C6 — the collision mechanism** (see below), plus `CatalogDatesLintTest` on the real catalog.
Gate: `:kern:jvmTest`.

**C7 — the remaining six languages' content.** `eo es fr it sw uk`, one commit or six.
Content-only; `--rerun-tasks` because Gradle does not track `app/catalog/` as a test input.
Gate: `:kern:jvmTest -Psweeps --rerun-tasks`.

**C8 — iOS.** A fourth entry on the Sprossen row, `DatesOverview` (+`+Practice`, `+Reference`),
`DateDrillView` (+`+Content`, `+Grading`), the `HubDestination` arm, the String Catalog entries,
`scripts/strings.py --fix`.
⚠ The row "offers THREE entries … on ONE row, which all three sit on comfortably" (`surfaces.md`);
whether a fourth still sits comfortably is a layout ruling for the design pass, not for this plan.
Gate: the app build; `scripts/run-sim.sh --shot`.

**C9 — Android.** The same, in the same sweep — a user-facing change lands on both platforms
and is never deferred to a parity pass. `DrillAvailability.datesOffered`, the `Screen` arm,
`DateDrillFlow`, the two Compose screens, `DrillWiringTest`.
Gate: `:android:testDebugUnitTest`; `scripts/run-emu.sh --shot`.

**C10 — docs and changelog.**
`catalog/docs/dates.md` written, `catalog/README.md`'s pointer list extended,
`surfaces.md` gains the Datum entry, `kern/docs/catalog.md` gains the `catalog/dates/` bullet,
`CHANGELOG.md` under `## Unreleased`.

## Collision safety

**This is where the dates drill differs from every drill that came before it, and it must not be waved through.**

The drill normalizer is `AnswerNormalizer.drill(language)` —
`articleLeniency = false`, `maxTyposPerWord = 1` — which grades word by word,
forgives one flat slip per word, and forgives none on a word carrying a digit
(`kern/docs/grading.md`). The safety of that budget rests on one empirical claim:

> distinct cardinals sit ≥ 2 edits apart, so a per-word cap of 1 keeps them apart

**Calendar names break that claim.** Measured over the normalizer's own comparison shape
(lowercased, ß→ss, joiners deleted), the pairs at distance ≤ 2 are:

| Language | Pair | Distance |
|---|---|---|
| de | **Juni / Juli** | **1** |
| de | Montag / Sonntag | 2 |
| en | June / July | 2 |
| en | Monday / Sunday · Tuesday / Thursday | 2 |
| eo | **junio / julio** | **1** |
| eo | **mardo / marto** (weekday × month) | **1** |
| eo | marto / majo · mardo / ĵaŭdo · lundo / junio · mardo / majo | 2 |
| es | **junio / julio** | **1** |
| es | marzo / mayo | 2 |
| fr | mars / mai · mardi / mars · mardi / mai | 2 |
| it | — | — |
| sw | Jumatatu / Jumatano · Juni / Julai | 2 |
| uk | березень / вересень · червень / серпень (and their `dateForm`s) | 2 |

The three at distance 1 would be graded **correct for each other** by the shipping drill normalizer.
A drill that accepts *Juni* for *Juli* is worse than no drill:
it certifies the single confusion German speakers famously say *Juno* and *Julei* to avoid.

### The mechanism

**Derive, never author.** `AnswerNormalizer` gains one constructor parameter —
a set of normalized words that forgive no typos — implemented as one clause beside the rule
that already exists for digits:

```kotlin
private fun wordBudget(word: String, cap: Int): Int =
    if (word.any { it.isDigit() } || word in unforgiving) 0 else cap
```

The rule states itself: **a calendar name is a digit.**
It names one member of a closed, numbered set whose members sit one edit apart,
so it forgives no slip for exactly the reason `21` and `29` do not.

The set is **computed by kern from the content**, never authored:
every reading of a language's calendar that sits within one edit of another distinct reading
in the same calendar. `Juni` and `Juli` are in it; `Mittwoch` is not,
so an ordinary slip on an unconfusable name still costs nothing.
Adding a language, or a synonym to a language, recomputes it —
there is no allowlist to rot and no ruling to forget.

Rejected alternatives, briefly:
`maxTyposPerWord = 0` across the whole date (harsh, and it punishes the pattern's function words
for the names' problem);
routing the drill through `CatalogAnswerGrader`'s `OtherWord` demotion
(`DrillGrading` deliberately wraps the accepted forms as ONE synthetic card so `OtherWord`
can never arise, and reopening that would give every drill a catalog-wide grading path
it does not want).

### The sweeps

**`DateCollisionSweepTests`** (jvmTest, real catalog, `-Psweeps`-gated by name in
`kern/build.gradle.kts`'s `corpusSweeps` list) — the clock sweep's shape, over the dates answer space:
every weekday, every month, every day 1–31, and every date the ladder can draw
(7 × 12 × 31, plus a sampled year band for rung 6) in every language with a `dates/` file.
The invariant: no reading grades non-`Wrong` for a *different* value.
Findings are declared per language and asserted equal, so a vanished pair fails too —
`assertEquals(gated, found.toList())`, the anti-rot the clock sweep already uses.

**`DatesTypoBridgeGuardTests`** (commonTest, `-Psweeps`-gated) — `TypoBridgeSweep.run`
over `TypoBridgeSweep.Prompt` per calendar value, scoped to the dates answer space only.
Scoping is the point, for the reason `TrainerFormsTypoBridgeGuardTests` gives:
a run grades one task against one accepted set, so sweeping date readings against plain cardinals
would fail on `dritte` ↔ `drei` for a confusion no run can produce.
Its allowlist should be **empty** — with the unforgiving-word rule in place,
every pair in the table above is held apart by construction rather than by a waiver.
An entry appearing in it is the signal that the derivation missed a case.

**`CatalogDatesLintTest`** (jvmTest, real catalog) — declared-language files only,
seven weekdays and twelve months in order, every pattern's markers matching its kind,
`abbr` on every weekday of a file used as a prompt side, `dateForm` never equal to `text`,
forms trimmed / deduped / never echoing their `text`, note keys are declared readers,
and every ordered pair of dates languages joining non-empty.
Plus the function-word audit `PhraseVocabAuditTests` does for frames:
a pattern's non-marker words are content the learner has to type,
and `tarehe`, `de`, `der`, `la` and `року` are either grounded in the card join
or listed as documented exceptions with a reason.

## Do the names also deserve cards

Drills book no reviews — transcription is not recall (`kern/README.md`) — so this drill
gives **exposure, not spacing**. A learner who tops the ladder in March and opens the app in June
has met *Juni* many times and been asked to *recall* it never.

**Recommendation: yes, as a follow-on series, and deliberately not bundled with the drill.**

The case for cards is that the names are ordinary vocabulary of the strongest kind:
a closed set of nineteen, high frequency, wholly arbitrary (nothing derives *Mittwoch* from *Wednesday*),
and needed at a delay — which is the exact profile FSRS exists for.
The country drill's precedent points the other way, and does not transfer:
two hundred proper nouns would swamp a box, and nobody is asked to produce *Kirgisistan* on a Tuesday.

The case for not bundling is that a card is more than a word.
It wants an `emoji` (and weekday and month names are precisely where a picture misleads,
so they take none, like the function words), a `grammar` block, an introduction position,
and eventually a recording. Nineteen new cards landing in every learner's box
in the same release that ships an untried drill is two changes wearing one commit.

If it is taken, the shape is small and additive:
the concepts go to the **end** of `catalog/time/concepts.json` — the area is already titled
*Zeit und Datum* and already owns day, week, month and year, so no new area is needed,
and appending never disturbs anyone's introduction order.
`catalog/dates/` then gains a language-neutral `calendar.json`
mapping the two ordered positions to those slugs (`countries/atlas.json`'s role, four lines),
the names move out of `dates/<lang>.json` into `time/<lang>.json`,
the drill reads them through the card join instead,
and lint requires every `calendar.json` slug to resolve as a concept.
`dates/<lang>.json` keeps `abbr`, `dateForm`, `numeric` and `patterns` —
the four things a card cannot carry.
The reads become **tracked** at that point, since a name would then land inside a joined card text.

## Open questions a native speaker must settle

Ordered by how much of the design turns on the answer.

1. **Spanish's first of the month.** `el primero de marzo` (most of Latin America)
   against `el uno de marzo` (Spain) — both grade; which one the reveal *teaches* is the ruling.
   And whether the year takes `de 2026` or `del 2026`.
2. **Swahili's date words.** Whether `tarehe mosi` is what a speaker says for the 1st
   or whether `tarehe moja` is; whether the traditional `Mwezi wa Tatu` should grade beside `Machi`
   (it can only ever be authored, since the concord slot is the one the pack refuses to invent);
   and how to word the note that the names count from a different origin than the ISO week.
3. **Ukrainian's year.** The plan takes the gap and gives Ukrainian no rung 6.
   Confirm that `дві тисячі двадцять шостого року` is genuinely a second numeral family
   and not a suffix swap like the day, in which case rung 6 comes back cheaply.
4. German `Sonnabend` and `Jänner`: synonyms that take their turn as prompts, or accepted-and-untaught?
5. English: does the reveal lead with `March third` or `the third of March`?
6. Italian: does the weekday take its article inside a date — `lunedì 3 marzo` or `il lunedì 3 marzo`?
7. Whether a reversed run should exist at all above rung 3.
   Reversed, the learner owes *digits*, and the numeric side of a date
   is a separator convention rather than a language skill —
   the same thinness that cost the country drill its flag rung in reverse.
   The plan ships `reverse` for rungs 1–2 only unless this is overruled.
