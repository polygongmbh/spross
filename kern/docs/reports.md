# Read models — what a surface draws the box from

The reports the engine hands out so a surface never reads a schedule itself:
the day's own numbers, one card's standing, the box as a browsable list, and the clock a
greeting turns on. Nothing here changes what the box DOES — only how a surface reads what it
already did. Engine contract: `../README.md`.

## The day

- **`TodayReport`** (`BoxEngine.today`) is the day's own report: reviews and misses read
  live from the review logs (so the numbers hold mid-session), introductions and consolidated
  crossings from the day counters the engine books at answer time (`newIntroduced`,
  `consolidatedCrossed`, both folded into `DayStats` at `endSession` and pruned together).
  `recall` is null below `MIN_ANSWERS_FOR_RECALL` — a handful of answers cannot carry a
  ratio — and `recallStrained` names the rule "today is going badly", not the remedy:
  what a surface does with it is the app's call.
- **`TodayReport.worked` / `tallyParts()`, `completionTallyParts`, `tomorrowNote`** —
  which parts a day or a finished round spells out, and in which order.
  A day is `worked` once something was answered,
  which is what separates "done for today" from "caught up":
  nothing is due in either, and only one of them was earned.
  `tallyParts()` is empty on an unworked day (a day has a state then, not a tally)
  and otherwise leads with reviews, then today's first meetings, then the crossings —
  the rarest part reads last.
  `completionTallyParts(introduced, consolidated, reviews)` is the ROUND's own tally
  in the order a summary reads it, non-zero parts only;
  empty means the round bought nothing nameable and the surface says so plainly
  rather than printing three zeros.
  `tomorrowNote(hasPackedWords, tomorrowDue)` picks `Packed` / `Fresh` / `Due`:
  a pack outranks the due count, because a finished day composes nothing
  and the round after it is where those words arrive;
  `tomorrowDue` is `dueNow` at `endOfTomorrow`, never a second local-midnight derivation.
  The kinds and their order are the rule;
  the words, plurals and separators for them stay in each platform's string tables.
- **Exposure**: one entry per card by construction; display surfaces always
  render the TARGET realization.

## One card's standing

- **`GrowthStage`** (`BoxEngine.growth`) is the same box told per card instead of per count:
  one rung each for unscheduled / queued / learning / fresh / consolidated /
  matured / relearning / suspended, in seed order, with the card's raw stability and whether
  today's answer touched it. Suspension and a lapse outrank every bar — a rung says where a
  card stands now, never how far it once got. The rungs name the RULE, so a surface may draw
  two of them the same; what they look like is not the engine's answer. It is the whole-box
  read behind a surface that draws the box itself rather than the totals `statistics`
  aggregates it into, and the reason the app needs no schedule-reading rules of its own.

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
  and where those live on another shelf a pack takes in more than the count said (`docs/backlog.md`).
- **`CardRowState`** (`BoxBrowser.cardRowState`) is what one listed card states besides the word itself:
  `Sleeping`, `PackOffered`, `Packed`, `Plain`, or `Standing(phase, consolidated)`.
  `packOffered` is the caller's context — a surface that packs a SINGLE word,
  which is a search hit the learner went looking for by name;
  an area listing packs by the shelf, so an unexposed card there is `Plain`:
  NEW is the ABSENCE of a standing, never a standing of its own.
  `Standing.consolidated` travels beside the phase instead of being read out of it —
  a card reaches Review well below `consolidatedStability`,
  so a mark keyed to the phase would seal cards the area's consolidated count leaves out.
  It is derived from `GrowthStage` and `Statistics.isConsolidated`, never from the raw phase:
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
