# Drills -- the hub, the Sprosse and the run

What a Sprosse is, what earns a chip on the hub,
and the shape every overview page and every run wears.
What each drill asks stands with the drill:
`drills-tables.md` for the three that grade against a table -- numbers, atlas, calendar --
and `drills-words.md` for the three drawn from the words the box already holds --
letters, word scramble, sentence scramble.
The surfaces around them -- listening, the wrist, the Android companion -- are `surfaces.md`'s;
the review loop these share their card and their answering rules with is `design.md`'s.

## The words

The surfaces nest, and each level answers to one word --
in the code, in the string keys and here:

```
Trainer   the section           kern/trainer/, trainer.* keys, the store  -- displayed as Wiese
 └ Hub    one card, one screen  the Home card and its chips
    └ Drill   one of six        a chip
       └ Overview   its page    picks, Los, reference
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

- **The hub card offers SIX entries** -- Zahlen, Buchstaben, Länder, Datum
  and the two scrambles -- and more than THREE visible chips break the row into TWO lines,
  `ceil(n/2)` above and `floor(n/2)` below.
  A chip is up exactly while its entry can offer something --
  counting content, an alphabet file, a joined atlas or calendars,
  a consolidated word long enough to scramble or a phrase of three words
  (`../kern/docs/turns.md`) --
  and the hub offers only languages with authored content.
- **The roster is kern's `Drill`, and its order is the chip order.**
  The six are enumerated there and nowhere else:
  what each entry gates on, the chip it earns, the glyph it wears and the key that names it
  all key off that one list.
  A glyph, a title, a route and a layout stay the platform's.
- **A step of the ladder is a Sprosse in EVERY interface language**, plural Sprossen:
  it is the brand word (`website.md`),
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
- Clock, sentences and number forms are exercises a run selects, not chips:
  a chip apiece would say they are alternatives to counting rather than what counting earns.
  Modifiers (reverse, fast, mix) are how a run is played.
- **A drill card is a review card** -- same face, same reveal,
  and the revealed reading is spoken and replayable (`read-aloud.md`) --
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

## The overview page

- **An entry opens a page, not a run** -- options and start first, reference under them.
  Reading matter and the run it prepares you for are ONE surface:
  a look-up five taps inside a running drill is a look-up nobody makes.
  The run comes first because it is what the page is opened for.
  Every page wears the app's corners -- the X out on the left, the run in on the right --
  and the right one repeats `Los` on purpose:
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
- **The modifiers are how a run is PLAYED, and only FAST has a price.**
  Reverse flips which side asks --
  the field's placeholder says so and the card never does --
  and where no ladder stands behind it the switch is offered from the first run.
  FAST falls a Sprosse on one clean win instead of the several it costs by default;
  on the atlas and calendar it is earned by having EVER stood on the top Sprosse.

## A run, and what it leaves behind

- **The atlas and the calendar wear their record on the Sprosse circles, and open where it
  stands.**
  A circle is an outline where no run has stood on the Sprosse,
  filled ocean where one has reached it,
  filled forest where one run answered EVERY question of it clean --
  only a Sprosse that enumerates can earn forest,
  so assembled date Sprossen never turn forest.
  `Los` opens on the lowest Sprosse no run has answered out
  (`NumbersMode.entrySprosse`),
  and a row the learner has been on is a control that opens a run on its own Sprosse
  (`NumbersMode.openable`).
  What a run answered out is filed per DIRECTION
  (`../kern/docs/turns.md` storage contract),
  and the ladder the reverse switch shows reads its own direction's mask.
  The record line under it COUNTS rather than places --
  the longest clean streak and the most answers one run took.
- **The numbers page prints the Sprosse each exercise has been climbed to**
  under the exercise's own name,
  because there four ladders are climbed separately,
  their Sprossen draw values rather than listing them (none can be answered out),
  and Zahlen counts its Sprosse in digits.
  The number is never trimmed to the rows on the page:
  a Sprosse goes on counting past the last named one (`DrillRamp.step`).
- **The two scrambles have no page to wear a ladder on, and resume on one anyway.**
  A run opens on the lowest Sprosse no earlier run climbed off UNBLEMISHED --
  every answer correct and unaided
  (`../kern/docs/turns.md` storage contract), filed per learned language.
  An almost banks nothing inside the run
  but takes that Sprosse out of the store's running.
  Nothing overrides where it opens: there is no row to tap and no direction to turn.
- **An endless run offers its exit where it is wanted, not on a schedule.**
  "Fertig" appears under the button that goes on, and only on the SECOND miss in a row;
  a clean answer takes the offer away again.
  The one thing that ends a run unasked is running OUT of questions:
  a run asks each prompt once (`../kern/docs/turns.md`),
  so a ladder answered out hands its figures over.
  The corner X still works.
- **A run is a FULL screen however it was started, and its X is the one way out.**
  The four overviews open theirs in a cover;
  the two scrambles the hub opens directly wear the same one.
- **A closed run has no screen of its own.**
  The endless drills hand their figures --
  answered, best streak, whether the record fell --
  to the page that started them;
  the page wears them as one tile above the picks and scrolls up to meet it.
- **Only the numbers, atlas and calendar ladders keep a RECORD of their own** --
  the longest clean streak and the most answers one run took.
  The letter drill and the two scrambles keep none,
  and no drill books a review or touches a schedule (`../kern/README.md`),
  so a run costs the box nothing and can be closed at any moment.
  That is what names the card the Wiese / Meadow:
  open ground beside the tended orchard the box is.
