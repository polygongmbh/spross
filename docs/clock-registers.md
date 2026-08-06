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
  each one already in `accepted`.
  A gloss that advertises a form the grader rejects is a trap, so `ClockRevealTests` holds it to that.
  The one exception is a labelled warning after an em dash
  (English names `"fourteen o'clock"` as the thing not to say).

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
Four word pairs sit one slip apart and are gated as audited exceptions
(`nne`/`nane`, `cuarto`/`cuatro`, and the Ukrainian `дев'ять`/`десять` ordinal cases).

Two exclusions are load-bearing and are commented at the point they are made:

- English `ten of three` — `of` is one edit from the digital joiner `oh`,
  so it would grade correct for 3:10. Only `quarter of` is safe.
- English's 24-hour hour word keeps its hyphen: spaced,
  `twenty two eleven` comes within a slip per word of `twenty to eleven`.

## Rejected, with reasons

- **English a.m./p.m.** — at the two hours it would lead with it is the form style guides tell learners to avoid,
  and it is confirmed fatal: `two a.m.` grades correct at 14:00.
  The part-of-day suffixes cover the same need.
- **English approximators** (`gone four`, `nearly four`) — an approximator names an interval,
  so accepting it at 16:20 necessarily accepts it at 16:05.
- **German odd-minute relative readings** (`siebzehn nach drei`) —
  the colloquial alternates exist on the five-minute grid only.
- **Ukrainian `нуль годин`** — a calque, and one edit from a digital reading of another hour.
- **Spanish `veinte a las tres`** — `a` never replaces `para`.
