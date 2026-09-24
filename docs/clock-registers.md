# Clock registers

How the hours drill decides what a time is CALLED, in each authored language.
Owns the parts of the day, the display/accepted/gloss split, and the collision rule.
The generators are `kern/src/commonMain/kotlin/net/spross/kern/trainer/*Clock.kt`;
the grading pipeline they answer to is `kern/docs/grading.md`.
The calendar sibling -- what a spoken date is called -- is `date-readings.md`.

## The three slots

A `ClockReading` carries three things, and they are not interchangeable.

- **display** -- the one reading the reveal teaches.
  What a native says most naturally at that time, not the most explicit thing they could say.
  Round steps take the construction a speaker reaches for;
  a minute off the five-minute grid is READ OUT instead
  (`two seventeen`, `друга сімнадцять`, `drei Uhr siebzehn`).
- **accepted** -- every reading that must grade correct.
  Generous on purpose:
  a learner who types a real, correct alternative must not be marked wrong.
  This is where the registers live -- the timetable one, the American one, the elliptical one.
- **gloss** -- up to three alternatives, in the language being ANSWERED in,
  each one already in `accepted`, with no exception:
  a gloss that advertises a form the grader rejects is a trap,
  so `ClockRevealTests` holds it to that.
  The lead-in and the separator are words of the answer language too --
  `auch: `, `also: `, `también: `, `також: `, `aussi : `, `ankaŭ: ` --
  never the authoring language's,
  down to the space French puts before its colon.

## What a reveal names

A candidate is dropped when its words are a subsequence of the display's or vice versa
(`ClockGloss`).
Three rules sit on top of that:

- **A joiner swap is not a different idiom.**
  `quarter to five` / `quarter till five` / `quarter of five` are one construction
  with the preposition changed, and only one may take a line.
  Which joiners are interchangeable is a language's own knowledge,
  so English builds its gloss candidates explicitly instead of filtering its accepted set.
- **`quarter of` is named anyway, because it is a false friend.**
  German `Viertel fünf` is 4:15 and English `quarter of five` is 4:45,
  so a German-speaking learner who carries the idiom across lands half an hour out.
- **One reading per construction, not per wording.**
  Counting the minute UP from the hour is one move whichever register says it,
  so `son las cuatro y cuarenta y cinco` and `son las dieciséis cuarenta y cinco`
  do not both get a line;
  Spanish keeps the timetable one, the register a learner cannot derive from the display.
  Counting DOWN from the coming hour is a different construction,
  `menos` and `para` are two more, and each keeps its own line.
  English: with a minute on it, the 24-hour reading is the digital one in another register,
  so it is named at :00 only.
  Ukrainian: the spoken-zero digital reading (`дев'ять нуль нуль`) is named at :00;
  below thirteen it is the only line left, since the official ordinal is the colloquial one.
  Italian generates no timetable reading below thirteen at all:
  `sono le undici e trenta` is what both registers say.
  Esperanto's timetable ordinal is the colloquial one below thirteen too,
  so it offers only the spelled-out `la tria horo kaj dek sep minutoj`,
  which the subsequence rule drops wherever the display already counted the minute.
  French names its 24-hour register and nothing else:
  the count-up and the countdown are its two constructions,
  and the other one says the same clock in the register the line already carries.
  Below thirteen the two registers coincide off the grid (`trois heures cinq` is both),
  and the gloss is then absent rather than empty.

Spanish is where a REGISTER earns a line of its own.
Off the round steps, `son las nueve y diecisiete minutos` spells the noun out,
marking the number as a COUNTED minute --
the contrast a learner needs against `y cuarto` and `y media`.
Named wherever the minute is not 0, 15, 30 or 45:
after the countdown and `para` families (other constructions, outranking a register),
and beside the timetable reading.
At round steps the subsequence rule drops it.

A gloss is ABSENT, not empty, wherever a language has no second construction at that time.

## The named-hour rule

Where a reading counts toward the COMING hour,
the part of the day belongs to that hour, not the one on the clock.

- 19:45 `son las ocho menos cuarto` -- eight in the evening, so `de la noche`.
- 19:45 `huit heures moins le quart du soir` -- same reason.
- 11:45 `за чверть дванадцята дня` -- names noon; `дванадцята ранку` is not a thing.
- 11:30 `пів на дванадцяту дня` -- same reason.

Counting DOWN to noon or to one is the exception Spanish makes:
`son las doce menos cuarto del día` is not said,
so a countdown core naming 12 or 13 takes morning and afternoon respectively.
Esperanto makes the same exception at noon --
11:45 is `kvarono antaŭ la dek-dua antaŭtagmeze` --
which is why its `dayParts` takes the direction as an argument and Ukrainian's does not.

## Parts of the day

Which words fit which hour is authored once, in the per-language `dayParts` functions --
`GermanClock`, `EnglishClockRegisters`, `SpanishClockForms`, `FrenchClockForms`,
`ItalianClockForms`, `SwahiliClock`, `UkrainianClockForms`, `EsperantoClockForms`.
Those functions ARE the grid;
each carries its own non-derivable notes in its KDoc,
and each pack's `clockDayParts` is the union over that language's own function --
one set per language, never pooled across them,
so `dayPartReadingsCloseTheTwelveHourCycle` grades readings against that language's markers.
Esperanto's markers are adverbs (`matene`, `posttagmeze`, `nokte`)
and its set carries their x-system twins too.
Boundaries overlap where speakers do, and both readings are accepted across an overlap.

Naming the part is optional everywhere -- every reading is accepted bare --
and WHERE a language may attach one is its own rule:
Spanish attaches it to conversational readings and the shortest `para` form,
not to the timetable register, which names 0--23 already;
Ukrainian's official register does the same, and so do French's and Italian's.
French exclusion: `midi` and `minuit` name the half of the day themselves,
so nothing is hung on them.

Italian is the one language that leaves an hour with no part of the day at all.
`mezzogiorno` and `mezzanotte` separate the two noons,
and Italian makes no further division --
`le dodici e mezza` stands unmarked.
So `dayParts(12)` is empty on purpose.

## What two times may share

A **period-less** reading is open across the 12-hour cycle by design.
`quarter to five` is the right answer to 16:45 and to 04:45 alike;
`saa sita` is midnight and noon.
Nothing may treat a same-cycle pair as a collision.

A reading that NAMES the part of the day must close it --
`dayPartReadingsCloseTheTwelveHourCycle` holds it to that.
So must the 24-hour register from thirteen up and at midnight, which names it by number --
`twentyFourHourReadingsCloseTheTwelveHourCycle` holds it to that.

Everything else is a bug: no reading may be accepted for a second time in the same cycle.
The word pairs that sit one slip apart are gated as audited exceptions --
`nne`/`nane`, `cuarto`/`cuatro`, `six`/`dix`, `ses`/`sep`, and `дев'ять`/`десять`,
the last listed once per Ukrainian ordinal case it reaches the clock in.
The French pair reaches the clock twice over, as an hour word and as a minute count,
and `ses`/`sep` reaches the Esperanto clock as a minute count,
as the hour ordinal (`la sesa`) and welded into the timetable one (`dek-sesa`).

Two exclusions are load-bearing and are commented at the point they are made:

- English `ten of three` -- `of` is one edit from the digital joiner `oh`,
  so it would grade correct for 3:10. Only `quarter of` is safe.
- English's 24-hour hour word keeps its hyphen:
  spaced, `twenty two eleven` comes within a slip per word of `twenty to eleven`.

## Why one generator per language

The files rhyme because clocks are one object, not because they run one computation.
The past/to pivot is `:31` in most languages and `:25` in German
(which counts against the HALF hour);
the half hour names the coming hour in de and uk, the current one in en/es/it/sw/fr/eo;
Swahili's hours are offset by six;
German has no cores, English hangs day parts on two readings after every bare one,
and French dresses each core in a copula.
`leadWith` and the empty-gloss rule carry no language rule and are shared outright.
`TrainerLanguagePack.clockDayParts` shares the SLOT only;
the derivations do not collapse into a shared helper because Spanish's and Esperanto's
unions run over more than the hour.

Esperanto is the one clock whose readings compose into a PREPOSITIONAL frame.
`la tria` is a bare nominative noun phrase;
`je` does not contract, so `je la tria`, `je tagmezo`, `je kvarono antaŭ la kvara`
all read as written -- where Italian's `alle`/`all'` cannot,
and Ukrainian's `о` takes only the locative readings of its own.
The countdown is `kvarono antaŭ la kvara`,
with `la kvara minus kvarono` accepted and never shown --
it is a calque, attested only at the quarter,
so off the quarter the countdown says `antaŭ` alone.

A further language takes all of:

- a new `*Clock.kt` with its own `dayParts`;
- its entry in `trainerPacks` (`TrainerLanguagePack.kt`) --
  without it the generator is dead code and every sweep skips it in silence;
- `clockDayParts` on that pack, derived from its `dayParts`, and `clockTwentyFourHour` --
  abstract, so forgetting either is a compile error;
- a cap in `ClockRevealTests`, plus its gloss lead-in in `alternativeMarkers`
  or its name in `ruleHintGlosses`, and its gloss separator in the `separators`;
- coverage by the two cycle sweeps above, which iterate every pack.

## English a.m./p.m., accepted knowingly

a.m./p.m. rides on numeric readings only --
the digital one (`four forty-five p.m.`) and the bare hour at :00 (`four p.m.`) --
because `quarter to five p.m.` is not English.

Both spellings are emitted because both are correct English, and only an emitted reading grades exact.
`cleaned()` turns `.` into a space and deletes only `-''`,
so `four forty-five p.m.` is four words against `four forty-five pm`'s three;
differing word counts drop to the whole-form budget, where the two sit one space apart.

`am` and `pm` are one substitution apart,
and the drill's budget is one slip per word flat,
so each grades correct for the other and the twelve-hour cycle stays open across the meridiem.
That is accepted: typing the wrong meridiem is a knowledge error rather than a slip,
and a typo verdict holds the card and shows the correct form.
The meridiem is left out of the cycle check by CONSTRUCTION:
it lives in `EnglishClockRegisters.meridiem`, never in `dayParts`,
so it never reaches `clockDayParts`.
The phrase day parts, which do live there, still close the cycle.

At 00:00 and 12:00, `twelve a.m.` and `twelve p.m.` are accepted but never named:
native speakers themselves get that pair backwards,
so the reveal teaches `midnight` and `noon` there.

## French's bare reading

The French reading is BARE -- `deux heures et quart`, `midi`, `minuit` --
and `il est deux heures et quart` grades beside it as a reading of its own.
The split keeps French's prepositional frames:
`à` never contracts with an hour word,
so `à deux heures et quart` composes for every draw.
The copula is declared in `TrainerLanguagePack.readingPrepositions`:
dropped where a frame already says it,
skipped where a frame would double it.

Two agreements are the drill's own content:
`une heure` against `deux heures` (counted noun),
and the timetable register counts the same way up to `vingt et une heures`.
`midi` and `minuit` replace that noun outright and take `et demi`
where an hour takes `et demie`.

Every reading also grades fully spaced,
because the comparison pipeline deletes hyphens
(`docs/number-forms.md` owns that rule; the clock obeys it).

## Ukrainian's time-when

«о» governs the locative, so no nominative reading can follow it:
`о четвертій дня`, `о шістнадцятій тридцять`, and `об` before a vowel, `об одинадцятій`.
Both registers generate that time-when beside the nominative --
the full hour and the face read out, the minute staying its nominative count --
as accepted readings the bare drill never displays.
The relative constructions (`пів на п'яту`, `за чверть п'ята`) stay predicate-only.
`TrainerLanguagePack.readingPrepositions` lists `о` and `об`,
and `readingPrepositionsGovernCase` holds a frame that says one
to the readings leading with one, the reading's own preposition replacing the frame's;
the frame displays the first of them.

## Rejected, and likely to be proposed again

A reading earns a line here only if a plausible widening would put it back.
Everything else the drill does not accept is answered by the commit that dropped it.

- **English approximators** (`gone four`, `nearly four`) --
  an approximator names an interval, so accepting it at 16:20 necessarily accepts it at 16:05,
  and a reading accepted twice in one cycle is the bug the collision sweep catches.
- **German odd-minute relative readings** (`siebzehn nach drei`) --
  the colloquial alternates exist on the five-minute grid only.
- **Ukrainian `нуль годин`** --
  a calque, and one edit from a digital reading of another hour.
- **Spanish `veinte a las tres`** --
  `a` never replaces `para`; `a` is not on Spanish's interchangeable-joiner list.
- **Swahili `kasa`** --
  not a Swahili word (owner-confirmed);
  the quarter words are `na robo` and `kasorobo`, both already generated.
- **The emphatic full hour, in every language** --
  de `punkt sechs`, en `six o'clock sharp`, es `en punto`, fr `pile`/`précises`,
  it `in punto`, sw `kamili`, uk `рівно`.
  They say the hour is EXACT, a different claim from what time it is,
  and no learner answering "18:00" volunteers the suffix.
  Taught in the catalog instead, by `time/nine-am-sharp`:
  the knowledge is WHERE the word sits (de and uk prepose, the rest postpose),
  plus agreements a sentence carries and a word card cannot.
