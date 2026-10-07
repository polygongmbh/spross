# Drills -- the hub, the Sprosse and the run
What a Sprosse is, what earns a chip on the hub, which drill Home suggests, and the shape every overview page and every run wears.
Neighbors: each drill `drills-tables.md` and `drills-words.md`, surfaces around them `surfaces.md`, the review loop `design.md`.

## The words

The surfaces nest, and each level answers to one word --
in the code, in the string keys and here:

```
Trainer   the section           kern/trainer/, trainer.* keys, the store
 └ Hub    one card, one screen  the Home card and its chips
    └ Drill   one of seven      a chip
       └ Overview   its page    picks, start, reference
          └ Run                 one sitting
```

A drill earns its chip because it asks a distinct skill,
but nothing is CALLED a skill -- the entry is a drill, and the word for it is that one.
A **Sprosse** is one step of a **ladder**, and both belong to every drill.
An **exercise** is a pick on the numbers overview (Counting, Clock, Phrases, Forms)
and a **reading** is what one of its tasks asks for (Cardinal, Year, Clock, Form, Fraction);
both are the numbers drill's alone.

One prefix, one scope: `Trainer*` is the section, `Drill*` is any drill,
and a prefix naming a topic -- `Country*`, `WordScramble*` -- is that drill's own.
Nothing wears a prefix one scope wider than what it serves.

## The hub, and what a Sprosse is

- **The hub card offers SEVEN entries** -- numbers, letters, the atlas, the calendar,
  the two scrambles and the opposites.
  A chip is up exactly while its entry can offer something --
  counting content, an alphabet file, a joined atlas or calendars,
  a settled word long enough to scramble, a phrase of three words
  or enough opposite pairs whose both words have arrived
  (`../kern/docs/turns.md`) --
  and the hub offers only languages with authored content.
- **The roster is kern's `Drill`, and its order is the chip order.**
  The seven are enumerated there and nowhere else:
  what each entry gates on, the chip it earns, the glyph it wears and the key that names it
  all key off that one list, which also carries each glyph and how the chips break into lines.
  A title, a route and the drawing stay the platform's.
- **A step of the ladder is a Sprosse in EVERY interface language**, plural Sprossen:
  it is the brand word,
  so English chrome says "Sprosse 5".
- **A Sprosse has to ask something no other Sprosse already asks.**
  One that composes answers the ladders teach separately is a free Sprosse:
  the calendar dropped its bare day-of-month because that reading IS the numbers drill's
  cardinal or the Forms drill's ordinal.
  Where the composition adds a genuine rule -- a concord, an agreement, another numeral
  family -- that rule is what the Sprosse is for.
- **A Sprosse adds exactly ONE thing where it opens a new KIND of question** --
  a question or a wider pool, never both:
  a learner who slips has to be able to name what got harder.
  A Sprosse that only turns the same demand up owes nothing of the sort,
  so the word scramble's climb takes a cue away and asks a longer word at once.
  Inside a Sprosse the shorter eligible words still come first.
- **A Sprosse costs three clean wins, two in a drill that generates every question.**
  A pool of rows -- the atlas, the letters, the scrambles, the calendar's names --
  should show a climb more than a couple of its rows before it moves on;
  only the numbers drill is fully generated, so it has no pool to cover.
  The counts are kern's (`WINS_TO_ADVANCE`, `Numbers.winsToAdvance`).
- Clock, sentences and number forms are exercises a run selects, not chips:
  a chip apiece would say they are alternatives to counting rather than what counting earns.
- **A drill card is a review card** -- same face, same reveal,
  and the graded answer is spoken, right or wrong, and replayable (`read-aloud.md`) --
  and carries nothing but the prompt:
  the run's header line already names what is drilled,
  and the field's placeholder names what is owed.
- **The one thing it does carry is the FIRST-SIGHT hint**,
  always a word in the language being LEARNED:
  the place word the first time a length appears,
  the word a form adds the first time a mark does ("Neu: menos", "Neu: Komma"),
  and on the calendar the word a date's pattern adds ("Neu: tarehe", then "Neu: mwaka wa").
  One slot, the form winning where both could fire.
  The word is DERIVED and never authored, on one rule for both
  (`formMarker`, `DateDrill.patternWord`).
  A reversed task gets no hint at all:
  the prompt is the reading, which says it in words already.

## The suggestion

- **Home names ONE drill, so seven chips are never the question.**
  It is named once the day has answered more cards than are still due,
  none left being the plain case of that (`DrillSuggestion.shown`).
- **A named drill leads the day's card** (`DayLead`):
  what the day has done on top, the drill and its reason as the card's body and first action,
  and one more round as the button beneath it --
  the rest of the due reviews while any are left, an extra round once none are.
  It stands nowhere else on Home;
  with no drill to name, the round card or the done card holds the slot as before.
- **The candidates are the chips the hub offers, less every ladder that is mastered**,
  however long ago it last ran:
  a ladder with nothing left to clear has nothing to suggest.
- **Three terms decide, added together** (`DrillSuggestion`):
  local days since the drill last ran, full after a week, a drill never run counting as longest ago;
  what the box would get out of it --
  letters while the learned script is new and the box young, numbers while the box is young,
  the scrambles and the opposites once enough words have settled;
  and how much of its ladder is left.
  The card says why in the words of whichever term carried it.
- **It turns over with the greeting and never between renders**:
  a small seeded nudge per drill, keyed on the greeting's stretch of the day,
  settles close calls, and a closed run moves its drill's last-run stamp
  (`../kern/docs/turns.md` storage contract).

## The overview page

- **An entry opens a page, not a run** -- options and start first, reference under them.
  Reading matter and the run it prepares you for are ONE surface:
  a look-up five taps inside a running drill is a look-up nobody makes.
  The run comes first because it is what the page is opened for.
  Every page wears the app's corners -- the X out on the left, the run in on the right --
  and the right one repeats the start button on purpose:
  it is the one still in reach from inside the reading.
- **The picks are never stored** -- they last as long as the screen does,
  so a page reopened offers the defaults.
  The stored Sprosse is written by a closing RUN, never by the page.
- **The table is GENERATED from what the run grades against**,
  so the page cannot claim one reading and mark another;
  kern names its bands and the app words their headings.
  A Sprosse the run just earned unlocks its row on the way back out.
  Every row is READ by tapping the row itself,
  spoken in the language being learned (`read-aloud.md`).
- **Two to four authored prose notes sit under a generated table**,
  on what trips a learner up in that language.
- **Every Sprosse row is ONE line**, named for what the Sprosse asks or adds.
- **What is not open yet keeps its row and states its price**,
  out of kern's unlock table rather than a number authored beside it.
  Where the Sprossen are not earned at all -- the atlas' and the calendar's --
  the rows carry neither padlock nor price and say what a Sprosse ASKS.
  What the pair cannot offer whatever the learner does -- no forms reading,
  no realized frames, a calendar with no year pattern -- has no row.
- **An unlock is marked once.**
  The first time a page shows open a row it last showed padlocked,
  that row is marked and a screen reader hears what opened (`DrillUnlockMark`).
  A row that never wore a padlock is never marked, and neither is a first visit.
- **The modifiers are how a run is PLAYED, and only FAST has a price.**
  Reverse flips which side asks --
  the field's placeholder says so and the card never does --
  and where no ladder stands behind it the switch is offered from the first run.
  FAST falls a Sprosse on one clean win instead of the usual count;
  on the atlas and calendar it is earned by having EVER stood on the top Sprosse.

## A run, and what it leaves behind

- **The atlas and the calendar wear their record on the Sprosse circles, and open where it
  stands.**
  A circle is an outline where no run has stood on the Sprosse,
  filled ocean where one has reached it,
  filled forest where one run answered EVERY question of it before its first miss or almost --
  a run that has slipped once clears nothing more, in every drill that files Sprossen
  (`DrillSprossen`, `../kern/docs/turns.md`) --
  and only a Sprosse that enumerates can earn forest,
  so assembled date Sprossen never turn forest.
  The start button opens on the lowest Sprosse no run has answered out
  (`NumbersMode.entrySprosse`),
  and a row the learner has been on is a control that opens a run on its own Sprosse
  (`NumbersMode.openable`).
  What a run answered out is filed per DIRECTION
  (`../kern/docs/turns.md` storage contract),
  and the ladder the reverse switch shows reads its own direction's mask.
- **The numbers page prints the Sprosse each exercise has been climbed to**
  under the exercise's own name,
  because there four ladders are climbed separately,
  their Sprossen draw values rather than listing them (none can be answered out),
  and Zahlen counts its Sprosse in digits.
  The number is never trimmed to the rows on the page:
  a Sprosse goes on counting past the last named one (`DrillRamp.step`).
- **The two scrambles and the opposites have no page to wear a ladder on, and fast-climb one anyway.**
  A run opens at Sprosse 1 and passes each Sprosse an earlier run cleared on one clean answer
  until its own first slip (`../kern/docs/turns.md` storage contract), filed per learned language.
  Nothing overrides where it opens: there is no row to tap and no direction to turn.
  The letter drill files its tile and typed formats the same way (`drills-words.md`).
- **An endless run offers its exit where it is wanted, and pauses at a natural stop.**
  An exit button appears under the button that goes on, and only on the SECOND miss in a row;
  a clean answer takes the offer away again.
  After a booked answer the run pauses at the first of three moments (`DrillPacing`):
  a stretch of answers since it opened or last went on;
  something new after a shorter stretch --
  a Sprosse cleared for the first time
  (climbed off in the scrambles, the opposites and the letters,
  answered out in the run's own direction on the atlas and the calendar,
  climbed past where the exercise stood in numbers),
  or the standing answer-streak record beaten (numbers, atlas, calendar);
  or most of the last few answers missed, where the pause says a stop is fine and why.
  The pause stands in place of the question on the round summary's own screen --
  its glyph where the summary's tree or popper stands, the run's figures as its tally --
  and wears the round's exit pair:
  one closes the run as the X does, the other goes on with the SAME run --
  prompts asked, ladder, answer streak -- and starts the next stretch.
  A timed run ends on its clock and never pauses.
  What ends a run unasked is running OUT of questions:
  a run asks each prompt once (`../kern/docs/turns.md`),
  so a ladder answered out hands its figures over.
- **A run is a FULL screen however it was started, and its X is the one way out.**
  The four overviews open theirs in a cover;
  the scrambles and the opposites the hub opens directly wear the same one.
- **A closed run has no screen of its own; a paused one shows its figures in place.**
  The endless drills hand their figures --
  answered, best answer streak, whether the record fell --
  to the page that started them;
  the page wears them as one tile above the picks and scrolls up to meet it.
  The scrambles and the opposites, opened straight from the hub, leave their tile on the hub card,
  and only after a run long enough to report (`DrillRunSummary.worthReporting`).
  The pause counts what was answered and names only what the stretch reached:
  the climb from the Sprosse it opened on, and a record beaten.
- **Only the numbers, atlas and calendar ladders keep a RECORD of their own** --
  the longest clean answer streak and the most answers one run took.
  The letter drill, the two scrambles and the opposites keep none,
  and no drill books a review or touches a schedule (`../kern/README.md`),
  so a run costs the box nothing and can be closed at any moment.
