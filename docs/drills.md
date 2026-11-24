# Drills -- the hub, the Sprosse and the run
What a Sprosse is, what earns a chip on the hub, and the shape every overview page and every run wears; each drill's own rules and which drill Home suggests are the KDoc on its kern types (`Drill`, `DrillSuggestion`).
Neighbors: the card a drill wears `review.md`, what crosses to Swift `build.md`.

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
  (each drill's `*Availability`) --
  and the hub offers only languages with authored content.
- **Listening earns no chip**: it asks nothing, grades nothing and has no Sprosse to reach,
  so it is a card of its own on Home (`design.md`) rather than a peer of counting and spelling.
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
  A Sprosse that only turns the same demand up owes nothing of the sort.
  Inside a Sprosse the shorter eligible words still come first.
- **A Sprosse costs three clean wins, two in a drill that generates every question.**
  A pool of rows -- the atlas, the letters, the scrambles, the calendar's names --
  should show a climb more than a couple of its rows before it moves on;
  only the numbers drill is fully generated, so it has no pool to cover.
- Clock, sentences and number forms are exercises a run selects, not chips:
  a chip apiece would say they are alternatives to counting rather than what counting earns.
- **A drill card is a review card** -- same face, same reveal,
  and the graded answer is spoken, right or wrong, and replayable (`audio.md`) --
  and carries nothing but the prompt:
  the run's header line already names what is drilled,
  and the field's placeholder names what is owed.
- **The one thing it does carry is the FIRST-SIGHT hint**,
  always a word in the language being LEARNED:
  the place word the first time a length appears,
  the word a form adds the first time a mark does ("Neu: menos", "Neu: Komma"),
  and on the calendar the word a date's pattern adds ("Neu: tarehe", then "Neu: mwaka wa").
  One slot, the form winning where both could fire.
  The word is DERIVED and never authored (`formMarker`, `DateDrill.patternWord`).

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
  spoken in the language being learned (`audio.md`).
- **Two to four authored prose notes sit under a generated table**,
  on what trips a learner up in that language.
- **Every Sprosse row is ONE line**, named for what the Sprosse asks or adds.
- **What is not open yet keeps its row and states its price**,
  out of kern's unlock table rather than a number authored beside it.
  Where the Sprossen are not earned at all -- the atlas' and the calendar's --
  the rows carry neither padlock nor price and say what a Sprosse ASKS.
  What the pair cannot offer whatever the learner does -- no forms reading,
  no realized frames, a calendar with no year pattern -- has no row.
- **The modifiers are how a run is PLAYED, and only FAST has a price.**
  Reverse flips which side asks --
  the field's placeholder says so and the card never does --
  and where no ladder stands behind it the switch is offered from the first run.
  FAST falls a Sprosse on one clean win instead of the usual count,
  and where it is earned the locked switch names its price (`fastPrice`).

## A run, and what it leaves behind

- **A run that has slipped once clears nothing more**, in every drill that files Sprossen;
  what a run files and where the next one opens are kern's (`SprosseMark`, `DrillBookings`, `NumbersMode.entrySprosse`).
- **An endless run offers its exit where it is wanted, and pauses at a natural stop** (`DrillRunProgress.offersFinish`, `DrillPacing`).
  The pause stands in place of the question on the round summary's own screen
  and wears the round's exit pair: one closes the run as the X does, the other goes on with the SAME run.
  What ends a run unasked is running OUT of questions:
  a run asks each prompt once, so a ladder answered out hands its figures over.
- **A run is a FULL screen however it was started, and its X is the one way out.**
  The four overviews open theirs in a cover;
  the scrambles and the opposites the hub opens directly wear the same one.
- **A closed run has no screen of its own** (`DrillRunSummary`):
  its figures are one tile on the page that started it,
  or on the hub card for a drill opened straight from the hub;
  the tile reports the run just closed, so the app going to the background clears it.
- **Only the numbers, atlas and calendar ladders keep a RECORD of their own** --
  the longest clean answer streak and the most answers one run took.
  The letter drill, the two scrambles and the opposites keep none,
  and no drill books a review or touches a schedule (`../kern/README.md`),
  so a run costs the box nothing and can be closed at any moment.
- **A closed run's answers count toward the day streak** --
  booked once, as the X closes it, onto the day's bar of the activity strip;
  the day's round budget never sees them.
