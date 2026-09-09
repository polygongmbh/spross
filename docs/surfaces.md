# Surfaces beyond the review loop

Listening, the wrist and the Android companion. The Sprossen drills are `drills.md`'s;
the review loop itself, and the auto-advance beats these share with it, are `design.md`'s.

## Listening

- **Listening is not a Sprosse.** Only a skill with a ladder to climb earns a chip on that
  row (`drills.md`); listening asks nothing, grades nothing and has no
  Sprosse to reach, so a fourth chip would say it is a peer of counting and spelling when it is
  a different kind of thing entirely. It gets ONE CARD on Home, under the day's round: the
  round is what the box asks of the learner, and this is what the learner can do when
  answering is not on the table — a walk, a commute, a sink full of dishes.
  Its title names the mode once, and its second line carries the two facts the name cannot:
  which words it leans on, and that it needs no hands.
- **It keeps the drill contract even so**: it books no review, writes no schedule and moves
  no streak, so a run costs the box nothing and can be closed at any moment. It has no end
  screen for the drills' reason — a run the learner ends when they like has nothing to
  celebrate — and no way of ending by itself: it laps for as long as it is left playing.
- **What it plays is the box, short of the words it already calls grown, shakiest first** —
  the box's own growing bar is the whole ladder: the words still short of it play first, all
  of them, before a word past it is heard at all, and then they keep coming back among the
  growing ones — the fewer are slipping, the more often each of them returns, though never
  inside thirty turns. Fully grown words are not in the pool: the hour is for what is not
  sticking. Never by what is due, since a schedule is about when to ASK and nothing is being
  asked. New words are a fixed two turns in five from the very first turn — hearing a word you
  have never answered, target-meaning-target, is the mode's cheapest breadth — with the words
  you have PACKED leading them, so the mode that asks the least of you still honors the queue
  the day's round does, and a language you have only just started plays from its basics
  onward rather than from anywhere in the catalog. Suspended words are in the pool:
  hand-suspending a word takes it out of the rotation, which makes the words you set aside
  exactly the ones a due-driven surface would never reach — they come in with the growing
  ones rather than leading. Hearing a word does NOT introduce it: introduction is the first
  ANSWER, and this surface has none. The rule and its numbers are `../kern/docs/turns.md`.
- **It plays with the screen locked.** That is the point of it — the mode is for the hours
  the phone is in a pocket, and one that stopped at the lock screen would be a mode for
  staring at a phone that is already speaking. Both platforms put the run on the lock screen
  and take play/pause/next from it and from a headphone button, driving the same reducer the
  on-screen buttons drive; how it is spoken, and what it does to whatever else was playing,
  is `read-aloud.md`.
  What that card SAYS is the run and never the turn: the app icon, the app and mode as the
  title, the pair of languages under it, and nothing that moves word to word. That is what
  lets it be PUSHED when the run changes — opened, paused, resumed, given a bedtime, closed —
  rather than three times a minute: the artwork crosses to the system whole on every push,
  and a mode built for the hours a phone spends in a pocket cannot pay that per word. A
  bedtime, and only a bedtime, gives the card a progress bar: it is the one thing a run that
  otherwise laps forever has a length of, and the system runs the bar on between pushes.

## Android companion

`android/` renders THIS contract with Compose — same engine facades, and since the turn
and both drill runs moved into kern the same rules by construction, not by porting discipline.
The Werkstatt ships there whole (`drills.md`).
Platform deltas only: the catalog and the chimes ship as APK assets synced from
`catalog/` and `App/Resources/Sounds/`,
the box is app-private and written after every answer rather than debounced,
runs are full screens rather than covers — Back mirrors ✕ everywhere, and inside a run
the reference panel eats Back first — and a fallen record celebrates in the tile's own
words, without confetti.
The home-screen tile ships there too, in Glance, and it is ONE grid sized to the tile
rather than iOS's three home-screen families (§ Watch & widgets):
an Android tile is dragged to any shape,
so a bucket boundary would change what the tile IS over a cell of width nobody can see.
Columns and rows come off the size the host hands over, against a minimum readable cell
and a bound the platform's ten-children container forces —
the smallest tile is one word, and every shape up to that bound fills with as many as fit.
Only the cell's TYPE moves with it, and it moves in steps, off the cell's own box:
the picture over the pair, beside it where a tile one row tall leaves no third line.
The tile is app-private like the box, and one the launcher has just placed shows the
platform's own loading face until the first composition lands.
What has not landed there is `design.md` § Not yet.

## Watch & widgets (decode-only)

- The phone precomputes both snapshots on every persist; the surfaces decode and draw,
  and never compute what the phone could pre-resolve.
- **iOS's three home-screen families differ in kind, not in row count.** WidgetKit offers three fixed
  sizes and no shape between them, so each is its own answer. Small is one word with its
  picture and a single stats line. Medium is a short list whose rows meet at a fixed emoji
  column — word right of one edge, meaning left of the other, both touching the picture — so
  a pair is read in place instead of scanned across the tile. Large is a poster of stacked
  cells rather than a longer list: equal rows have no hierarchy, so a glance reads none of
  them, and a cell gives each side the full column width that a shared line denies it.
  The two lock-screen families carry the rotating word alone — rectangular as word over
  meaning, inline as one line of picture, article and word — and no stats.
- **Whatever a tile holds, it holds it shortest pair first, over what today owes.** Which
  cards travel is kern's attention ranking, where they land is the tile's. Every home-screen
  tile states the run and the due count, and adds the fortnight's review bars where the width
  carries them — which is the header, since the bottom of a tile has no such room.
- **A widget with no readable snapshot draws the sprout, never sample words.** A placed
  tile only ever shows the learner's own — so when the store holds no snapshot this build
  can decode (what an app update leaves behind until the app next runs), the tile says
  where the words come from instead of inventing a box, and the launch that follows writes
  the snapshot and pushes the tile a redraw. Only the picker advertises with a sample box,
  and only where a picker can hold one: iOS previews the gallery entry from a made-up box,
  while the Android widget picker gets the app's own mark rather than a second layout
  written to say what a real tile says better.
  Every family names its tap destination, so the tile opens the app in that state too.
- **A tile rotates when it is redrawn, not on a schedule of its own.** iOS hands its host a
  timeline of quarter-hour entries and gets the rotation for free. A Glance tile has no
  timeline to hand over: the head of the window is derived from the clock at draw time and
  moves every half hour, which is the shortest refresh the platform will schedule, and the
  app pushes a redraw itself on every persist — so the numbers are as fresh as the last
  answer, and the words turn over on the half hour whether or not anyone opened the app.
- Watch: one graded **multiple-choice** loop — the watch never types, and the options
  arrive ranked from kern so that nothing but meaning tells the answer from its company:
  word class, then how the sentence closes, then area, then string shape
  (`../kern/docs/snapshots.md`).
  No self-grading: correctness and response time derive the rating.
  Multiple choice on a keyboard-less device is a deliberate concession to the
  recall-first rule, with the latency curve compensating for it.
  Answers return as events; the phone reschedules against real timestamps and re-pushes.
- **A word too long for a tile is a phone word.** The wrist carries only what four tiles
  can hold at a readable size (kern `MAX_TEXT_CHARS`); a longer phrase is never pushed and
  never offered as somebody else's distractor. This costs the watch about a quarter of the
  phrases and no single word at all, and it is a gain rather than a loss: a four-way pick
  between sentences is exposure, not recall, and exposure is what the phone's own card
  already does better. What the watch drops, it drops from BOTH the entries and the option
  pool, from one predicate — the two can never disagree about what fits.
- **Every answer answers back, on three channels.** A haptic shaped like the derived rating
  (affirming, a double tap for slow-but-right, a failure buzz for a miss), the rating itself
  badged on the tapped tile as an emoji, and — on a miss only — the tile and a brief
  full-screen wash in red. **Red is the wrist's alone**: the phone keeps wrong off its cards
  because the learner stays there and can correct it, while a glance-long wrist answer has
  no second face to be gentle on. The badge is a tell, never a label: naming the grade would
  invite playing to the latency the grade is measuring. Reduce Motion keeps every color and
  the badge and drops only the movement. No sound — the wrist is a silent surface, and two
  channels already carry it.
- **The picture arrives with the answer.** A card that has an emoji shows it on the watch's
  prompt line once a tile is tapped, never before: on a recognition question the picture
  depicts the very meaning being asked for. That reveal moment is also why the wire now
  carries held-back pictures at all — the emoji cue picks which KEY the picture travels
  under rather than whether it travels (`../kern/docs/snapshots.md`), so no surface can show one early
  by reading the wrong field. It joins the prompt line instead of taking a slot of its own,
  so an answered card never reflows under the thumb.
- Two runs, and only one of them ends: the **due batch** is a counter that reaches its
  end and returns to the start screen by itself, while **free practice** takes the words
  closest to slipping, lap after lap, carrying the answer streak in place of a total.
  Practice has no end screen — a run the learner ends when they like has nothing to
  celebrate.
