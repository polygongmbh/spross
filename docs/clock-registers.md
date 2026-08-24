# Clock registers

How the hours drill decides what a time is CALLED, in each authored language.
Owns the parts of the day, the display/accepted/gloss split, and the collision rule.
The generators are `kern/src/commonMain/kotlin/net/spross/kern/trainer/*Clock.kt`;
the grading pipeline they answer to is `kern/docs/grading.md`.

## The three slots

A `ClockReading` carries three things, and they are not interchangeable.

- **display** — the one reading the reveal teaches.
  What a native says most naturally at that time, not the most explicit thing they could say.
  Round steps take the construction a speaker reaches for;
  a minute off the five-minute grid is READ OUT instead
  (`two seventeen`, `друга сімнадцять`, `drei Uhr siebzehn`) —
  counting seventeen minutes from an hour is correct and nobody does it.
- **accepted** — every reading that must grade correct.
  Generous on purpose: a learner who types a real, correct alternative must not be marked wrong.
  This is where the registers live — the timetable one, the American one, the elliptical one.
- **gloss** — up to three alternatives, in the language being ANSWERED in,
  each one already in `accepted`, with no exception:
  a gloss that advertises a form the grader rejects is a trap, so `ClockRevealTests` holds it to that.
  Naming what NOT to say is a warning rather than an alternative, and a warning repeated
  at every reveal of every hour past twelve is not worth the line it takes.
  The lead-in and the separator are words of the answer language too —
  `auch: `, `also: `, `también: `, `також: `, `aussi : `, `ankaŭ: ` — never the authoring language's,
  down to the space French puts before its colon.

## What a reveal names

A candidate is dropped when its words are a subsequence of the display's or the display's are of its
(`ClockGloss`): the same reading with a word added or dropped is not another way to say the time.
Three rules sit on top of that, and none of them is derivable from the words alone.

- **A joiner swap is not a different idiom.**
  `quarter to five` · `quarter till five` · `quarter of five` are one construction with the preposition changed,
  and only one may take a line.
  Which joiners are interchangeable is a language's own knowledge,
  so English builds its gloss candidates explicitly instead of filtering its accepted set.
- **`quarter of` is named anyway, because it is a false friend.**
  German `Viertel fünf` is 4:15 and English `quarter of five` is 4:45,
  so a German-speaking learner who carries the idiom across lands half an hour out.
  That is worth a line even though the joiner rule above drops `till`,
  which stays accepted and unnamed.
- **One reading per construction, not per wording.**
  Counting the minute UP from the hour on the clock is one move whichever register says it,
  so `son las cuatro y cuarenta y cinco` and `son las dieciséis cuarenta y cinco` do not both get a line;
  Spanish keeps the timetable one, the register a learner cannot derive from the display.
  Counting DOWN from the coming hour is a different construction, `menos` and `para` are two more,
  and each of those keeps its own line however it names the number —
  which is why `cinco para las cinco` is named at :55 and `son las cuatro y cincuenta y cinco` is not.
  English has the same rule from the other side: with a minute on it
  the 24-hour reading is the digital one in another register, so it is named at :00 only.
  Ukrainian names its spoken-zero digital reading (`дев'ять нуль нуль`) at :00 for the same reason,
  and below thirteen it is the only line left — the official ordinal is the colloquial one there.
  Italian goes further and generates no timetable reading below thirteen at all:
  `sono le undici e trenta` is what both registers say, so there is nothing there for a second line to teach.
  Esperanto's timetable ordinal is the colloquial one below thirteen too, so what it offers there
  is only the spelled-out `la tria horo kaj dek sep minutoj`, which the subsequence rule drops
  wherever the display already counted the minute — and the reveal then goes bare, correctly.
  French names its 24-hour register and nothing else, and that is not a thin gloss but the whole set:
  the count-up and the countdown are its two constructions,
  whichever one the grid step calls for IS the display,
  and the other one says the same clock in the register the line already carries.
  Below thirteen the two registers coincide off the grid (`trois heures cinq` is both),
  and the gloss is then absent rather than empty.

Spanish is where a REGISTER earns a line of its own.
At a minute off the round steps, `son las nueve y diecisiete minutos` spells the noun out,
and spelling it is what marks the number as a COUNTED minute —
the contrast a learner needs against `y cuarto` and `y media`, the fraction words the round steps taught them.
So it is named wherever the minute is not 0, 15, 30 or 45:
after the countdown and the `para` families, which are other constructions and outrank a register,
and beside the timetable reading, which it does not displace.
At the round steps themselves it is the display's own count with a word added, and the subsequence rule drops it.

A gloss is ABSENT, not empty, wherever a language has no second construction at that time.
German's reveal goes bare at most of its times, and that is the correct output, not a gap.

## The named-hour rule

Where a reading counts toward the COMING hour, the part of the day belongs to that hour,
not to the one on the clock.

- 19:45 is `son las ocho menos cuarto` — eight in the evening, so `de la noche`, while the clock still says seven.
- 19:45 is `huit heures moins le quart du soir` for the same reason.
- 11:45 is `за чверть дванадцята дня` — it names noon, and `дванадцята ранку` is not a thing.
- 11:30 is `пів на дванадцяту дня` for the same reason.

Counting DOWN to noon or to one is the exception Spanish makes:
`son las doce menos cuarto del día` is not said, so a countdown core naming 12 or 13
takes the morning and the afternoon respectively.
Esperanto makes the same exception at noon — 11:45 is `kvarono antaŭ la dek-dua antaŭtagmeze` —
which is why its `dayParts` takes the direction as an argument and Ukrainian's does not.

## Parts of the day

Which words fit which hour is authored once, in the per-language `dayParts` functions —
`GermanClock.dayParts`, `EnglishClockRegisters.dayParts`, `SpanishClockForms.dayParts`,
`FrenchClockForms.dayParts`, `ItalianClockForms.dayParts`,
`SwahiliClock.dayParts`, `UkrainianClockForms.dayParts`, `EsperantoClockForms.dayParts`.
Those functions ARE the grid; each carries its own non-derivable notes in its KDoc,
and each pack's `clockDayParts` is the union over that language's own function —
one set per language, never one pooled across them,
so `dayPartReadingsCloseTheTwelveHourCycle` grades German readings against German markers.
Esperanto's markers are adverbs (`matene`, `posttagmeze`, `nokte`) and its set carries
their x-system twins too, or a reading spelled without `ŭ` would read as period-less there.
Boundaries overlap where speakers do, and both readings are accepted across an overlap.

Naming the part is optional everywhere — every reading is accepted bare —
and WHERE a language may attach one is its own rule, which the functions alone do not show:
German's is in `GermanClock.dayParts`' KDoc;
Spanish attaches it to the conversational readings and to the shortest `para` form,
not to the timetable register, which names 0–23 already;
Ukrainian's official register does the same, and so do French's and Italian's.
French has one exclusion of its own: `midi` and `minuit` name the half of the day themselves,
so nothing is hung on them and `midi de l'après-midi` never exists.

Italian is the one language here that leaves an hour with no part of the day at all.
`mezzogiorno` and `mezzanotte` are the words that separate the two noons,
and Italian makes no further division of that hour —
`le dodici e mezza` stands unmarked, and neither `di mattina` nor `di pomeriggio` is said of it.
So `dayParts(12)` is empty on purpose, and the readings that name twelve
stay open across the cycle the way every period-less reading does.

## What two times may share

A **period-less** reading is open across the 12-hour cycle by design.
`quarter to five` is the right answer to 16:45 and to 04:45 alike;
`saa sita` is midnight and noon;
the language leaves the cycle open and the drill may not close it.
`ClockCollisionSweepTests` skips same-cycle pairs for exactly this reason.

A reading that NAMES the part of the day must close it —
naming it is the whole point — and `dayPartReadingsCloseTheTwelveHourCycle` holds it to that.

Everything else is a bug: no reading may be accepted for a second time in the same cycle.
The word pairs that sit one slip apart are gated as audited exceptions —
`nne`/`nane`, `cuarto`/`cuatro`, `six`/`dix`, `ses`/`sep`, and `дев'ять`/`десять`,
the last listed once per Ukrainian ordinal case it reaches the clock in.
The French pair reaches the clock twice over, as an hour word and as a minute count,
and `ses`/`sep` reaches the Esperanto clock as a minute count, as the hour ordinal
(`la sesa`) and welded into the timetable one (`dek-sesa`, whose hyphen the pipeline deletes).

Two exclusions are load-bearing and are commented at the point they are made:

- English `ten of three` — `of` is one edit from the digital joiner `oh`,
  so it would grade correct for 3:10. Only `quarter of` is safe.
- English's 24-hour hour word keeps its hyphen: spaced,
  `twenty two eleven` comes within a slip per word of `twenty to eleven`.

## Why one generator per language, and not one for all

The files rhyme because clocks are one object, not because they run one computation.
The past/to pivot is `:31` in most of them and `:25` in German,
which counts against the HALF hour rather than the coming one — 6:25 is "fünf vor halb sieben";
the half hour names the coming hour in de and uk and the current one in en, es, it, sw, fr and eo;
Swahili's hours are offset by six and its display is assembled outside its accepted list;
German has no cores at all, English hangs its parts of the day on two readings after every bare one,
and French dresses each core in a copula instead — `il est` is a reading, not a frame word.
`leadWith` (`ClockReadings.kt`) and the empty-gloss rule (`ClockGloss.line`) carry no language
rule, and those are shared outright.
`TrainerLanguagePack.clockDayParts` shares the SLOT and nothing else:
the words are a language's own vocabulary and each pack derives its set from its own `dayParts`.
The derivations do not collapse into a shared helper either, though most of them look alike:
those enumerate the hours, while Spanish's words decide on hour, minute and direction and
Esperanto's on hour and direction, so their unions run over more than the hour
and a sentinel argument would miss a word a future rule keys on.
The fraction and half words are not shareable for exactly that reason:
the word is never the emitted unit, the frame is,
and the frames agree on word order, on which hour is named, and on case in no combination —
Italian's `e mezza` sits where Spanish's `y media` does and takes a different agreement,
and its countdown says `meno` where the Spanish one may also say `para`.
The reusable artifact is otherwise this document, not a base class.

Esperanto is the one clock whose readings compose into a PREPOSITIONAL frame.
`la tria` is a bare nominative noun phrase carrying its own article and no copula,
and `je` does not contract, so `je la tria`, `je tagmezo` and `je kvarono antaŭ la kvara`
all read as written — where Italian's `alle`/`all'` and Ukrainian's `о` + locative cannot
(`../catalog/phrases/README.md` § "Language constraints").
The hour is an ordinal with `horo` elided, and both spellings of it grade;
the countdown is `kvarono antaŭ la kvara`, with `la kvara minus kvarono` accepted and never shown —
it is a calque, and one register of it (the quarter) is as far as it is attested,
so off the quarter the countdown says `antaŭ` alone.


A further language takes all of:

- a new `*Clock.kt` with its own `dayParts`;
- its entry in `trainerPacks` (`TrainerLanguagePack.kt`) — without it the generator is dead code
  and every sweep skips it in silence, with nothing going red;
- `clockDayParts` on that pack, derived from its `dayParts` and never authored a second time.
  It is abstract, so forgetting it is a compile error rather than a quiet gap:
  `dayPartReadingsCloseTheTwelveHourCycle` iterates `trainerPacks` and reads the markers off the pack;
- a cap in `ClockRevealTests`, plus its gloss lead-in in that test's `alternativeMarkers`
  — or its name in `ruleHintGlosses` where the gloss is a hint rather than a list —
  and its gloss separator in the `separators` it splits on;
  a marker or separator that matches nothing skips 1440 rows quietly, which is exactly what that test is there to catch;
- its own `@Test` in `ClockCollisionSweepTests` calling `sweep()` with its gated pairs
  — that sweep runs off hand-written per-language tests, so a language without one gets no collision coverage and nothing goes red.

## English a.m./p.m., accepted knowingly

A learner meets a.m./p.m. constantly, so the drill has to know it.
It rides on the numeric readings only — the digital one (`four forty-five p.m.`) and the bare hour at :00 (`four p.m.`) —
because `quarter to five p.m.` is not English, and the 24-hour prompt decides which of the two it is.

Both spellings are emitted because both are correct English, and only an emitted reading grades exact.
`cleaned()` turns `.` into a space and deletes only `-'’`,
so `four forty-five p.m.` is four words against `four forty-five pm`'s three;
differing word counts drop to the whole-form budget, where the two sit one space apart.
Listing only one would therefore book the other — a correct answer — amber as a typo.

`am` and `pm` themselves ARE one substitution apart, and the drill's budget is one slip per word flat,
so each grades correct for the other and the twelve-hour cycle stays open across the meridiem.
That is accepted, for two reasons:
typing out the wrong meridiem is a knowledge error rather than a slip, and an unlikely one;
and a typo verdict does not auto-advance — it holds the card and shows the correct form,
so the learner is corrected and the answer books amber.
Being corrected is the teaching outcome wanted here.
The meridiem is therefore left out of the cycle check by CONSTRUCTION:
it lives in `EnglishClockRegisters.meridiem`, never in `dayParts`,
so it never reaches `clockDayParts` and nothing has to remember to exclude it.
The phrase day parts, which do live there, still have to close the cycle.

At 00:00 and 12:00 `twelve a.m.` and `twelve p.m.` are accepted but never named:
that pair is the one native speakers themselves get backwards,
so the reveal keeps teaching `midnight` and `noon` there.

## French's bare reading, and the copula beside it

The French reading is BARE — `deux heures et quart`, `midi`, `minuit` —
and `il est deux heures et quart` grades beside it as a reading of its own.
That split is what keeps French's prepositional frames:
`à` never contracts with an hour word, so `à deux heures et quart` composes for every draw
where Ukrainian's «о» + locative cannot,
and French realizes the five `à`-frames Ukrainian has to drop for predicate ones.
The copula is declared in `TrainerLanguagePack.readingPrepositions`, which is what makes it composable:
it is dropped where a frame already says it (`Le réveil dit qu'il est deux heures.`)
and the reading is skipped where a frame would double it (`Le train part à il est deux heures.` is not French).

Two agreements are the drill's own content and not decoration.
The hour is a counted noun, so it is `une heure` against `deux heures`,
and the timetable register counts it the same way up to `vingt et une heures`.
`midi` and `minuit` replace that noun outright — they never take `heures` —
and being masculine they take `et demi` where an hour takes `et demie`.

Every reading also grades fully spaced, because the comparison pipeline deletes hyphens:
`deux heures quarante-cinq` and `deux heures quarante cinq` are otherwise unrelated strings
(`docs/number-forms.md` § French owns that rule; the clock only obeys it).

## Rejected, and likely to be proposed again

A reading earns a line here only if a plausible widening would put it back,
and each line says what would then go wrong.
Everything else the drill does not accept is answered by the commit that dropped it.

- **English approximators** (`gone four`, `nearly four`) — an approximator names an interval,
  so accepting it at 16:20 necessarily accepts it at 16:05,
  and a reading accepted twice in one cycle is the bug the collision sweep exists to catch.
- **German odd-minute relative readings** (`siebzehn nach drei`) —
  the colloquial alternates exist on the five-minute grid only,
  so generalizing the grid widens `accepted` to German nobody says.
- **Ukrainian `нуль годин`** — a calque, and one edit from a digital reading of another hour,
  which the one-slip-per-word budget would then grade correct.
- **Spanish `veinte a las tres`** — `a` never replaces `para`:
  which joiners are interchangeable is each language's own knowledge, and `a` is not on Spanish's list.
- **Swahili `kasa`** — not a Swahili word (owner-confirmed), however plausible it looks beside `kasorobo`;
  the quarter words are `na robo` and `kasorobo`, and both are generated already.
- **The emphatic full hour, in every language** — de `punkt sechs`, en `six o'clock sharp` / `on the dot` / `exactly six o'clock`,
  es `en punto`, fr `six heures pile` / `précises`, it `in punto`, sw `kamili`, uk `рівно`.
  They say the hour is EXACT, which is a different claim from what time it is,
  and no learner answering "18:00" volunteers the suffix —
  so the coverage never paid for the branch each generator carried at `m == 0`.
  They are taught in the catalog instead, by `time/nine-am-sharp`:
  the knowledge is WHERE the word sits — de and uk prepose it, en, es, fr, it and sw postpose it,
  es and sw put it after the part of the day, French agrees `précises` with the hour it follows
  (`midi précis` against `deux heures précises`), and Italian's canonical puts it before
  the part of the day (`alle nove in punto del mattino`) — which a sentence carries and a word card cannot.
