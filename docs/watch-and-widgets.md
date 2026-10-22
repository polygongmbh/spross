# Watch & widgets — the wrist and the home-screen tiles
The watch's multiple-choice loop and the tiles on both phones: what each holds, how it rotates and how the wrist grades; every one decodes the snapshots the phone precomputes.
Neighbors: the snapshots they decode `snapshots.md`, the review card on the phone `review.md`.

- The phone precomputes both snapshots on every persist;
  the surfaces decode and draw, and never compute what the phone could pre-resolve.
- **iOS's three home-screen families differ in kind, not in row count.** WidgetKit offers three fixed sizes and no shape between them, so each is its own answer.
  Small is one word with its picture and a single stats line.
  Medium is a short list whose rows meet at a fixed emoji column —
  word right of one edge, meaning left of the other, both touching the picture —
  so a pair is read in place instead of scanned across the tile.
  Large is a poster of stacked cells rather than a longer list:
  equal rows have no hierarchy, so a glance reads none of them, and a cell gives each side the full column width that a shared line denies it.
  The two lock-screen families carry the rotating word alone —
  rectangular as word over meaning, inline as one line of picture, article and word —
  and no stats.
- **Whatever a tile holds, it holds it shortest pair first, over what today owes.** Which cards travel is kern's attention ranking, where they land is the tile's.
  Every home-screen tile states the run and the due count, and adds the fortnight's review bars where the width carries them —
  which is the header, since the bottom of a tile has no such room.
- **A widget with no readable snapshot draws the sprout, never sample words.** A placed tile only ever shows the learner's own —
  so when the store holds no snapshot this build can decode (what an app update leaves behind until the app next runs), the tile says where the words come from instead of inventing a box, and the launch that follows writes the snapshot and pushes the tile a redraw.
  Only the picker advertises with a sample box, and only where a picker can hold one:
  iOS previews the gallery entry from a made-up box, while the Android widget picker gets the app's own mark rather than a second layout written to say what a real tile says better.
  Every family names its tap destination, so the tile opens the app in that state too.
- **A tile rotates when it is redrawn, not on a schedule of its own.** The head of the window is read off the clock, epoch-aligned (kern `WidgetRotation`),
  so a reload picks the rotation up where it stands rather than starting it over;
  how long a step lasts is the host's.
  iOS hands its host a timeline of quarter-hour entries.
  A Glance tile has no timeline to hand over:
  its head moves every half hour, which is the shortest refresh the platform will schedule, and the app pushes a redraw itself on every persist —
  so the numbers are as fresh as the last answer, and the words turn over on the half hour whether or not anyone opened the app.
- Watch: one graded **multiple-choice** loop —
  the watch never types, and the options arrive ranked from kern so that nothing but meaning tells the answer from its company:
  word class, then how the sentence closes, then area, then string shape (`snapshots.md`).
  No self-grading: correctness and response time derive the rating.
  Every question shows its prompt alone first, for as long as the Good window gives the prompt's own characters,
  so the answer is recalled before options can be spotted; a tap shows them early, the response clock starts when they appear,
  and VoiceOver skips the pause.
  Multiple choice on a keyboard-less device is a deliberate concession to the recall-first rule, with the latency curve compensating for it.
  Answers return as events; the phone reschedules against real timestamps and re-pushes.
  Until it does, the watch keeps each card's last rating;
  after it, a miss returns when the phone's schedule brings it due.
  A round is the due cards plus the misses, one counted run, offered on the home screen while they number at least `WatchModel.roundFloor`;
  below it the screen reads all done, since a miss would return within three questions of its reveal, and practice asks what it has not asked (due cards first), then its misses, then what it got right, each in the phone's order.
- **A word too long for a tile is a phone word.** The wrist carries only what four tiles can hold at a readable size (kern `MAX_TEXT_CHARS`);
  a longer phrase is never pushed and never offered as somebody else's distractor.
  This costs the watch about a quarter of the phrases and no single word at all, and it is a gain rather than a loss:
  a four-way pick between sentences is exposure, not recall, and exposure is what the phone's own card already does better.
  What the watch drops, it drops from BOTH the entries and the option pool, from one predicate —
  the two can never disagree about what fits.
- **The last two answers are nobody's distractor.** A word just tapped as right is ruled out on sight,
  so the watch draws its three tiles from the rest of the phone's shortlist and falls back on a recent answer only when nothing else is left (`WatchPracticeGenerator`).
- **Every answer answers back, on three channels.** A haptic (a light click for any right answer, the retry buzz for a miss), a quick right answer's speed marked on the tapped tile (⚡⚡ Easy, ⚡ Good), and — on a miss only — the tile and a brief full-screen wash in red.
  **Red is the wrist's alone**: the phone keeps wrong off its cards because the learner stays there and can correct it, while a glance-long wrist answer has no second face to be gentle on.
  The mark names what earned the rating, never the grade: a learner sees why it came.
  Reduce Motion keeps every color and the mark and drops only the movement.
  No sound — the wrist is a silent surface, and two channels already carry it;
  the right answer's feedback is the subtlest the wrist has.
- **The picture arrives with the answer.** A card that has an emoji shows it on the watch's prompt line once a tile is tapped, never before:
  on a recognition question the picture depicts the very meaning being asked for.
  The emoji cue picks which KEY the picture travels under (`snapshots.md`), so no surface can show one early by reading the wrong field.
  It joins the prompt line instead of taking a slot of its own, so an answered card never reflows under the thumb.
- Two runs, and only one of them ends:
  the **due batch** is a counter that reaches its end and returns to the start screen by itself, while **free practice** takes the words weakest first in the order the phone ships, lap after lap, carrying the answer streak in place of a total.
  Practice has no end screen — a run the learner ends when they like has nothing to celebrate.

## The Android tile

The home-screen tile ships there too, in Glance, and it is ONE grid sized to the tile rather than iOS's three home-screen families (above): an Android tile is dragged to any shape, so a bucket boundary would change what the tile IS over a cell of width nobody can see.
Columns and rows come off the size the host hands over, against a minimum readable cell and a bound the platform's ten-children container forces —
the smallest tile is one word, and every shape up to that bound fills with as many as fit.
Only the cell's TYPE moves with it, and it moves in steps, off the cell's own box:
the picture over the pair, beside it where a tile one row tall leaves no third line.
The tile is app-private like the box, and one the launcher has just placed shows the platform's own loading face until the first composition lands.

## Evidence

Holding the options back while the learner tries to recall improved delayed retention
(van den Broek et al. 2023, "Delayed display of answer alternatives enhances vocabulary learning", *Journal of Educational Psychology*).
The study held them 4 s; the watch holds less on purpose, a middle ground that keeps a wrist answer quick (user ruling 2026-10-19).
The same study, and Little, Bjork, Bjork & Angello (2012), *Psychological Science*,
found competitive, hard-to-guess options improve retention — the distractor ranking.
