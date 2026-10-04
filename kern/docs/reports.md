# Read models — what a surface draws the box from
The read models a surface draws the box from (the day, one card's standing, the browsable box, the greeting clock); nothing here changes what the box does.

## The day

- **`TodayReport`** (`BoxEngine.today`) is the day's own report, every count read live
  from the review logs, so the numbers hold mid-session — a card's FIRST log entry IS the meeting.
  A settled crossing reads a stability no log entry records,
  so each card answered today has its log replayed up to each of today's answers
  through the same step that recorded them (`answered`, `box/Answer.kt`).
  The walk reads raw schedules rather than the join: switching the known language must not
  un-happen a day's work.
  `recall` is null below `MIN_ANSWERS_FOR_RECALL` — a handful of answers cannot carry a
  ratio — and `recallStrained` names the rule "today is going badly", not the remedy:
  what a surface does with it is the app's call.
- **`TallyPartKind`, `tallyParts`, `TodayReport.worked`, `tomorrowNote`** —
  which parts a day or a finished round spells out, and in which order.
  Every answer is exactly one kind (`tallyKind`):
  `Introduced` (a card's first answer, even one that crosses the bar),
  `Settled` (the answer that carried the card across the settled bar),
  or `Reviewed` (every other answer) —
  so a day's and a round's parts add up to their answers,
  and Home and the round's summary count alike.
  The total stays `TodayReport.answers`, which `recall` and `worked` read.
  A day is `worked` once something was answered,
  which is what separates "done for today" from "caught up":
  nothing is due in either, and only one of them was earned.
  `tallyParts(introduced, reviewed, settled)` reads in that order,
  the rarest part last, non-zero parts only;
  empty means nothing nameable was answered (an unworked day has a state, not a tally),
  and the surface says so plainly rather than printing three zeros.
  `tomorrowNote(hasPackedWords, tomorrowDue)` picks `Packed` / `Fresh` / `Due`:
  a pack outranks the due count, because a finished day composes nothing
  and the round after it is where those words arrive;
  `tomorrowDue` is `dueNow` at `endOfTomorrow`, never a second local-midnight derivation.
  The kinds and their order are the rule;
  the words, plurals and separators for them stay in each platform's string tables.
- **`HomeStanding.of`** is everything Home reads at one instant:
  the day's offer, its `TodayReport`, the tomorrow note and due count, and whether an asked-for round would yield anything.
  Whether there is a round at all is `SessionOffer.hasRound` alone, which `DayLead` reads too;
  the headline turns on the clock, so a surface reads it off the offer when it draws.
- **`RoundSummary.of`** is everything a finished round's summary says:
  the round's `tallyParts`, `grownArea` read against the box the run opened on
  (`SessionRunState.startBox`), `growthHeadline` over it, and `restSuggested`.
  `withArea` takes a supplied tree instead, for a debug launch's sample area.
- **Exposure**: one entry per card by construction; display surfaces always
  render the TARGET realization.

## One card's standing

- **`GrowthStage`** (`BoxEngine.growth`) is the same box told per card instead of per count:
  one Sprosse each for unscheduled / queued / fresh / growing /
  settled / lapsed / suspended, in seed order, with the card's raw stability and whether
  today's answer touched it. Suspension outranks every bar, the stability bars outrank the phase,
  and only under the growing bar does the relearning phase read lapsed — a Sprosse says where a
  card stands now, never how far it once got. The Sprossen name the RULE, so a surface may draw
  two of them the same; what they look like is not the engine's answer. It is the whole-box
  read behind a surface that draws the box itself rather than the totals `statistics`
  aggregates it into, and the reason the app needs no schedule-reading rules of its own.
- **`AreaGrowth`** (`growthByArea`) is that read folded per area:
  each met word in exactly one tier of the same `StageCounts` the box's statistics hold
  (fresh / growing / settled / matured),
  most-grown first, with what is packed and what lapsed;
  a tree's height comes from how many words its area has met.
  `grownArea` names the area a round worked hardest as a `TreeTransition` — before and now,
  and which ranks the round moved — and `growthHeadline` the one claim the summary may make
  about it, read off that area's gain, never off the round's session-wide tallies.

## The box as a list

- **`BoxBrowser`** is the box read as a browsable list,
  and every rule in it is a box rule rather than a layout.
  `areaNames` intersects the catalog's default area order with the areas this profile actually holds cards in,
  and appends the learner's own words LAST, in no group:
  the manifest cannot list an area the catalog does not own,
  and their seed order puts them behind every catalog word anyway
  (their heading is chrome — kern hands back the area key, the app names it).
  `sections` groups those areas as `areas.json` groups them, in manifest order,
  dropping a group left holding none of them,
  with the heading read in the profile's source language, then `en`, then the group id —
  a manifest that forgot one language still names its shelf, visibly wrong rather than blank.
  `defaultExpandedGroupId` opens the first section holding an area with ACTIVE cards —
  where the learner left off — else the first section, so the browser never opens fully folded.
  `cardsInArea` is the shelf in seed order.
  `enqueueableCardIds` is what packing that shelf would take in — unscheduled, not already queued,
  which are `enqueue`'s own guards asked in advance — and `enqueueableCount` is its size,
  so the number a shelf promises and the pack it performs cannot come from two different rules.
  Missing components are the one thing it does not count:
  enqueuing a phrase also prepends the components it lacks,
  and where those live on another shelf a pack takes in more than the count said (`../../docs/backlog.md`).
- **`CardRowState`** (`BoxBrowser.cardRowState`) is what one listed card states besides the word itself:
  `Sleeping`, `PackOffered`, `Packed`, `Plain`, or `Standing(stage)`.
  `packOffered` is the caller's context — a surface that packs a SINGLE word,
  which is a search hit the learner went looking for by name;
  an area listing packs by the shelf, so an unexposed card there is `Plain`:
  NEW is the ABSENCE of a standing, never a standing of its own.
  `Standing` carries the card's `ActiveStage` (fresh / growing / settled / lapsed) rather than a collapsed boolean —
  Fresh, Growing and Settled can no longer be told apart from one flag,
  and a card reaches Review well below `GROWING_STABILITY`, so a mark
  keyed to the raw phase would seal cards the area's settled count leaves out.
  It is read off the same rule as `GrowthStage`, never re-derived from the raw phase:
  a second derivation is a second answer waiting to disagree with the shelf above it.

## The greeting clock

- **The clock a greeting turns on is kern's too**: `dayPart(now, tz, language)` places the
  hour in `Morning` / `Day` / `Evening` / `Night` on the seams of the language being
  greeted in, read off the words its clock drill already teaches
  (`trainer/ClockDayParts.kt`) rather than a boundary table of its own —
  Swahili is at *jioni* by four in the afternoon while German is still at *nachmittags*,
  and a drill that learns better hours moves the greeting with it.
  It departs from the drill in two places, because reading a time is not greeting a person:
  the small hours are `Night` in every language ("two in the morning" is a reading, not a
  greeting), and noon takes the hour before it, since the languages that name midday with a
  word of its own leave nothing at twelve to stand on.
  Sunrise is not in it: that needs a location permission the app does not ask for.
  `partVariant(now, tz, language, count)` picks which of a surface's phrasings that stretch
  wears, FNV-1a over day-plus-part so the pick survives a relaunch and matches on both phones.
  How many phrasings there are, and what they say, is the platform's.
- **What the TARGET says at that stretch is the catalog's**: `Catalog.greeting(lang, part, name)`
  resolves the part to the concept it greets with
  (`good-morning` / `good-day` / `good-evening` / `good-night`, `Greetings.slug`)
  and reads that language's realization — a kern rule, so both phones greet with the same words.
  A morning the language authors none of falls to the all-day greeting, because that IS its
  morning greeting: Spanish, French and Italian leave `good-morning` unauthored rather than
  duplicate "¡Buenos días!" / "Bonjour !" / "Buongiorno!".
  It is null wherever a language authors nothing for the rest,
  which is the surface's cue to say something of its own rather than to greet in another language.
  A name is addressed INTO the sentence (`Greetings.addressed`),
  so the closing mark travels behind it — "Habari za asubuhi!" → "Habari za asubuhi, Tim!" —
  and whatever space stood before that mark is kept, because French sets one there and German does not.

## The streak

- **`BoxStatistics.longestStreak`**: the longest run ever held, under the same forgiveness
  rule the current streak walks back with, over the whole (never pruned) `dailyStats`.
  An unfinished today can extend a run but never end one, so it is always ≥ `streak` —
  equality is what says today's run IS the record.
- **`BoxStatistics.streakHealth`**: what today still owes the run, off the same walk —
  `Earned` once today has reviews, `Bridgeable` while an empty today would only spend the
  run's one bridge, `Ending` when yesterday already spent it, `None` when the streak is 0.
  Surfaces render the urgency; the engine names only the rule.
