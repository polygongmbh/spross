# Clock registers

How the hours drill decides what a time is CALLED, in each authored language.
Owns the parts of the day, the display/accepted/gloss split, and the collision rule.
The generators are `kern/src/commonMain/kotlin/net/spross/kern/trainer/*Clock.kt`;
the grading pipeline they answer to is `kern/README.md` §AnswerNormalizer.

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
  `auch: `, `also: `, `también: `, `також: ` — never the authoring language's.

## What a reveal names

A candidate is dropped when its words are a subsequence of the display's or the display's are of its
(`ClockGloss`): the same reading with a word added or dropped is not another way to say the time.
Three rules sit on top of that, and none of them is derivable from the words alone.

- **A joiner swap is not a different idiom.**
  `quarter to five` · `quarter till five` · `quarter of five` are one construction with the preposition changed,
  and only one may take a line.
  This is deliberately NOT a blanket "same word count, one position differs" rule:
  German `punkt sechs` and `um sechs` sit exactly one word apart and are two constructions, not one said twice.
  What keeps `punkt` off the German gloss is a judgment about what it MEANS —
  an emphasis, "exactly at six", rather than another name for the hour — which no word-shape test could make;
  it stays accepted throughout.
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
- 11:45 is `за чверть дванадцята дня` — it names noon, and `дванадцята ранку` is not a thing.
- 11:30 is `пів на дванадцяту дня` for the same reason.

Counting DOWN to noon or to one is the exception Spanish makes:
`son las doce menos cuarto del día` is not said, so a countdown core naming 12 or 13
takes the morning and the afternoon respectively.

## Parts of the day

Boundaries overlap where speakers do, and both readings are accepted across an overlap.
Naming the part is optional everywhere — every reading is accepted bare.

| hour | de | en | es (named hour) | sw | uk |
|---|---|---|---|---|---|
| 0 | nachts | in the morning · at night | de la noche (+ de la madrugada past :00) | usiku · usiku wa manane | ночі |
| 1–2 | nachts | in the morning | de la madrugada · de la mañana | usiku · usiku wa manane | ночі |
| 3 | nachts · morgens | in the morning | de la madrugada · de la mañana | usiku · usiku wa manane | ночі · ранку |
| 4 | früh · morgens · nachts | in the morning | de la madrugada · de la mañana | usiku · alfajiri · asubuhi | ранку |
| 5 | früh · morgens · nachts | in the morning | de la madrugada · de la mañana | alfajiri · usiku · asubuhi | ранку |
| 6 | morgens · früh | in the morning | de la mañana · de la madrugada | asubuhi · alfajiri | ранку |
| 7–11 | morgens/vormittags | in the morning | de la mañana | asubuhi | ранку |
| 12 | mittags | in the afternoon | del mediodía · del día · de la mañana (· de la tarde past :00) | mchana | дня |
| 13 | nachmittags · mittags | in the afternoon | de la tarde · del mediodía | mchana | дня |
| 14–16 | nachmittags | in the afternoon | de la tarde | mchana / jioni · alasiri from 15 | дня |
| 17 | nachmittags · abends | in the afternoon · in the evening | de la tarde | jioni · mchana · alasiri | вечора · дня |
| 18–20 | abends | in the evening | de la tarde · de la noche at 19, both at 20 | jioni at 18, usiku · jioni at 19 | вечора |
| 21–22 | abends · nachts | in the evening · at night at 21 | de la noche | usiku | вечора |
| 23 | nachts · abends | at night | de la noche | usiku | вечора · ночі |

Notes that are not derivable from the table:

- English does not use "at night" for the small hours — `two o'clock in the morning` is what natives say.
- German's `früh` is accepted, never taught.
- Swahili's `alasiri` is the LATE afternoon only and never leads;
  `usiku wa manane` is the pack's one multi-word part and must never lead either,
  because `TrainerGoldenTests` recovers the bare reading by dropping the display's last word.
- Spanish attaches the part to the conversational readings and to the shortest `para` form,
  not to the timetable register, which names 0–23 already.
  Ukrainian's official register does the same.

## What two times may share

A **period-less** reading is open across the 12-hour cycle by design.
`quarter to five` is the right answer to 16:45 and to 04:45 alike;
`saa sita` is midnight and noon;
the language leaves the cycle open and the drill may not close it.
`ClockCollisionSweepTests` skips same-cycle pairs for exactly this reason.

A reading that NAMES the part of the day must close it —
naming it is the whole point — and `dayPartReadingsCloseTheTwelveHourCycle` holds it to that.

Everything else is a bug: no reading may be accepted for a second time in the same cycle.
Three word pairs sit one slip apart and are gated as audited exceptions —
`nne`/`nane`, `cuarto`/`cuatro`, and `дев'ять`/`десять`,
the last listed once per Ukrainian ordinal case it reaches the clock in.

Two exclusions are load-bearing and are commented at the point they are made:

- English `ten of three` — `of` is one edit from the digital joiner `oh`,
  so it would grade correct for 3:10. Only `quarter of` is safe.
- English's 24-hour hour word keeps its hyphen: spaced,
  `twenty two eleven` comes within a slip per word of `twenty to eleven`.

## Why five generators and not one

The five files rhyme because clocks are one object, not because they run one computation.
The past/to pivot is `:31` in four of them and `:25` in German,
which counts against the HALF hour rather than the coming one — 6:25 is "fünf vor halb sieben";
the half hour names the coming hour in de and uk and the current one in en, es and sw;
Swahili's hours are offset by six and its display is assembled outside its accepted list;
German has no cores at all, and English hangs its parts of the day on two readings after every bare one.
Only `leadWith` (`ClockReadings.kt`) and the empty-gloss rule (`ClockGloss.line`) carry no language rule, and those are shared.
The reusable artifact is this document, not a base class.

A sixth language takes all of:

- a new `*Clock.kt` and a row in the table above;
- its entry in `trainerPacks` (`TrainerLanguagePack.kt`) — without it the generator is dead code
  and every sweep skips it in silence, with nothing going red;
- a cap in `ClockRevealTests`, plus its gloss lead-in in that test's `alternativeMarkers`
  — or its name in `ruleHintGlosses` where the gloss is a hint rather than a list —
  and its gloss separator in the `separators` it splits on;
  a marker or separator that matches nothing skips 1440 rows quietly, which is exactly what that test is there to catch;
- its own `@Test` in `ClockCollisionSweepTests` calling `sweep()` with its gated pairs, and an entry in that file's `DAY_PARTS`
  — the sweep runs off hand-written per-language tests, so a language with neither gets no collision coverage and nothing goes red.

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
`ClockCollisionSweepTests.DAY_PARTS` therefore does not list the meridiem,
while the phrase day parts beside it still have to close the cycle.

At 00:00 and 12:00 `twelve a.m.` and `twelve p.m.` are accepted but never named:
that pair is the one native speakers themselves get backwards,
so the reveal keeps teaching `midnight` and `noon` there.

## Rejected, with reasons

- **English approximators** (`gone four`, `nearly four`) — an approximator names an interval,
  so accepting it at 16:20 necessarily accepts it at 16:05.
- **German odd-minute relative readings** (`siebzehn nach drei`) —
  the colloquial alternates exist on the five-minute grid only.
- **Ukrainian `нуль годин`** — a calque, and one edit from a digital reading of another hour.
- **Spanish `veinte a las tres`** — `a` never replaces `para`.
