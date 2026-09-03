# Spross — app design (v2)

This doc is the build contract for the REVIEW LOOP: the card, the typing, Home, the Box.
Two app domains have their own pages — `surfaces.md` (drills, the wrist, Android) and
`read-aloud.md` (what speaks, and when).
The product thesis and phase plan live in `../../docs/roadmap.md`;
the engine — scheduling, growth, sessions, grading, snapshots — in `../kern/README.md`.

**What belongs here** are the foundations: the decisions a rewrite must not lose,
and the reasons behind them.
Everything else belongs wherever it is answered faster — the running app for timings,
colors and screen inventories, the code for the mechanics of a rule already stated here.
A negation earns its line only where the opposite is what would otherwise happen.
Cross-links stay rare on purpose: a fact should sit in the one place it is needed,
and a doc that keeps pointing elsewhere is telling you something is filed wrong.

Product frame: any source (known) / target (learning) language pair from the catalog;
no user-facing direction concept; progress tracked per target language.

## North star

Every screen answers "What do I do right now?" with zero ambiguity.
The app composes the work; the user never browses for what to study.

The voice follows from that: copy states what is on the table,
never that something waits for the learner
("Lust auf neue Wörter?", not "Neue Wörter warten auf dich";
"Morgen sind %@ Karten dran", not "warten auf dich").
This is a box the learner tends, not a streak app that nags,
and material personified into waiting turns an offer into an obligation —
the learner who comes back late then meets a reproach instead of their words.
Every prompt, empty state, call to action and forecast line gets the same read:
where the sentence puts the learner under an expectation,
rephrase it as an availability statement or a question.

## Boundaries & persistence

Strict dependency direction: App → SprossKern, never the reverse.

- ONLY the app target links the Kotlin framework;
  watch and widget targets are pure Swift over phone-built snapshots.
- `KernBridge.swift` is the boundary every kern type crosses, and the app hands the
  engine its clock (`nowEpochMillis` + `tzId`) rather than letting it read one.
- Persistence: a `BoxStore` actor, one document per target language, atomic writes,
  saved after answers, at session end, and when the app goes to the background —
  an answered review is never only in memory.
- **Calibration is re-applied from the build on every load**: learning steps, retention
  and caps are decisions the app version makes, so a box written months ago must not go
  on answering to the numbers that shipped with it. Nothing survives it — growth pacing
  is the engine's opinion, not a figure the learner tunes (`docs/growth-evidence.md`).
- Swift 6 strict concurrency.

## Profile & onboarding

- Profile = (source, target) catalog languages;
  the catalog decides which targets a source can reach.
- Default source = device language when covered, else en.
  Either picker may hold the other side's language — picking it swaps the pair.
- The first round teaches itself: it carries one line per moment —
  give the word a moment, answer honestly, write the one you missed — in the quiet line
  every other aside uses, and the grade line takes the standing question's slot rather
  than stacking under it. Copy only: no step is added and nothing waits on it.
  The lines last that whole round, whatever "keep practicing" adds to the same run included,
  and closing the round spends them. The flag arms with the round onboarding opens —
  never ahead of one nothing can open — and lives in memory,
  so an app killed mid-round comes back to a quiet screen.
- Onboarding is three pages, and ends INSIDE the first round: the pair, what the box is for,
  then what a round asks of you — and the button on the last page joins the box
  and opens the session. Only that page commits, so the join happens once,
  behind something worth reading, and every page before it is free to go back from;
  the first round is never something the learner has to go and find on Home.
  It is the first-run path alone — a later language change is the box's own settings,
  which take none of the pages and open no session over the screen you were on.
- One list is open at a time; the other stands folded on its pick and opens at a tap,
  and picking a source hands the screen to the target. Onboarding opens with the known
  side folded — the device language is a good guess already — so most of the page fits
  without scrolling; a long enough open list (the catalog already reaches 8 targets)
  can push the name field and button below the fold, and the page scrolls to reach
  them like any other.
- The chooser is the FIRST-LAUNCH screen — it is what a device with no profile yet opens on.
  Afterwards the pair is changed on the box's own two pickers, beside everything else the box
  is configured by, so there is one place to change it and the chooser never reopens.
- Picker rows carry the flag, the language's own name, and the English exonym
  ("🇺🇦 Українська · Ukrainian"): a flag beside an unreadable script is easy to
  mistake for a neighboring language.
  The swap row counts the SWAPPED pair — that is the pair the tap would join, and it
  differs from the count on screen wherever one side realizes a feminine the other knows
  only through its base.
- UI chrome renders in the KNOWN language when chrome exists (de/en today), otherwise en.
  Onboarding follows the source being PICKED — device language first, re-rendering on
  each tap — so the greeting is already in the user's language.
- Chrome strings are SYMBOLIC keys (`settings.known.title`), never source text in either
  language: copy edits then never detach a translation, and a new chrome language is
  additive. How a key is written and kept honest: `scripts/strings.py`.
  A key's first level is the SURFACE it renders on, one namespace apiece — never a
  namespace per widget, and never two for one screen. Where a surface holds several
  KINDS of thing the second level says which (`box.card` vs `box.shelf` vs `box.area`,
  `home.offer` vs `home.done`), and where a rule reaches every surface the namespace is
  the channel instead (`a11y`, `common`). It names the domain's word, not the code's —
  known and learning over source and target, Sprosse over rung or level, pack over enqueue — and no
  key is also the stem of a family that means something else.
- The String Catalog is the ONE home for that copy, on both phones: Android's tables are
  generated from it (`scripts/chrome.py`), and a pre-commit check
  refuses a catalog edit that leaves them behind. A field's NAME is its key, camelCased
  (`box.card.due` → `boxCardDue`), so the binding is derived rather than tabulated and a
  field cannot come to name a key that says something else; only the few fields reading
  SEVERAL keys are written out, in `FAMILIES`. A counted key's form follows the field's
  own name — `<field>One` takes the `one` form, the bare name the general `other` one.
  Which keys Android does not read is declared beside it and checked against the whole
  catalog: `IOS_ONLY` for what Android has no use for, `ANDROID_TODO` for what it still owes.
  What a string MEANS is its catalog entry's `comment`, where the other phone and whoever
  translates it read the same sentence; a field's KDoc says only what Kotlin owns —
  the shape of a list or map, the kern enum indexing it, the placeholders owed.
  Strings only Android says live there too,
  named in `strings.py`'s `ANDROID_ONLY` so the iOS drift check knows no Swift will ask.
  Where the layout differs the READER composes — the catalog holds the caption
  ("Fast! Korrekte Schreibweise"), and each phone sets the form beside or below it.
- Area titles and area emoji both come from the catalog; the app carries no map of its own.
  A title is a plain name, so it can serve as the produce prompt's area cue unedited;
  the catalog's optional area subtitle is the flavor line, shown under the title
  in the box and nowhere a cue is wanted.

## Presentation model in the UI

**Practice means typing.** Writing the word is the recall the box is scheduling, so that
is what a card asks for. Revealing is the way out of it, for the three cases where typing
is not the question: a word met for the first time (heard and read, its picture waiting for
the reveal), a target form to be recognized rather than produced, and a learner who does not
want to type right now — one hand on the phone is reason enough.
The grade buttons live on that path only; they are the way back to the deck,
never the shape of the app.

- The role of each review comes from the engine, alternating produce and recognize.
- **PRODUCE**: typed answer in the target language, graded by the kern normalizer.
  "Aufdecken" is the no-typing fallback, and self-grades.
- **RECOGNIZE**: reveal and self-grade ONLY — no input field, so no schedule is ever
  graded against a language the word was not learned with.
  The learner reports one of three outcomes, and the engine turns that plus how long the
  recall took into the rating: Easy is earned by answering fast, never picked.
  The buttons name what the learner knows, never what the scheduler will do —
  so none of them wears an FSRS rating's name, and the row stands under the question
  it answers, in the same quiet line every other post-reveal hint uses.
  The recall clock runs from the prompt appearing to "Aufdecken" — the time spent choosing
  a button afterwards is thumb travel, not memory.
  The reveal carries the source meaning plus the full synonym family.
- A picture on a card — a word's emoji, a country's flag — sits in a slot the CARD places,
  from what the surface is: beside the words where the screen is shared with an input, a
  button and a keyboard, above them where the card owns the screen and horizontal room is
  what the words are short of. Which is which is `VocabCardView`'s own parameter and not a
  caller's choice, because the one time it was a size flag a run picked the wrong one and
  drew a picture wide enough to hyphenate a six-letter word.
  Either way a card's visible text stays CENTERED in it, vertically and horizontally, and
  the picture is mirrored on the opposite edge so it never shifts the words off that center.
  Which is also why the slot is held whether or not the picture is in it: one withheld until
  the reveal fades into a space already kept for it, rather than reflowing the words to
  make room.
  A picture that would ANSWER the question is **withheld as a hint, never dropped**: it is
  held back while the answer is owed and appears at the reveal, so the learner always ends
  up seeing what they were asked about. The slot itself keeps this (`CardEmoji.Cue`) —
  there is no way for a card face to say "hide it for good".
- Ambiguous prompts (the target merges two source concepts) carry an **area label**.
  Produce only: on a recognition prompt a cue precise enough to disambiguate would give
  the answer away. Never graded.
- Grammar — plural line, article color — renders TARGET-side only;
  prompt and reveal are styled by role, not by language.
- A drill card wears this same face — same slot, same reveal
  (the rule's home is `surfaces.md` § Trainers).

## Review UX rules

These bind every surface that asks the learner for an answer, the drills with the review
loop (`surfaces.md`): they are rules about the asking, not about the box behind it.
A new surface reads what it owes here, and reads what is already filed against whatever
it was copied from (`backlog.md`) —
a clone inherits its sibling's gaps, never the rules.

- **The answer is never on screen twice, and never in the field** —
  the field carries what the LEARNER typed and nothing else.
  The card expands only when the word was not produced — a wrong answer or "Aufdecken";
  anything graded correct leaves the card closed and is narrated at the field,
  where the learner's own attempt already stands.
  So how close the answer came decides where the owed form is read:
  a miss opens the CARD onto it, a near miss gets a box under the field.
  Either way it stands at a size worth reading, with the speaker that says it beside it —
  the form the learner owed is the one word on screen most needing to land,
  and a caption squeezed under the field is not how it lands.
- **A near miss runs amber throughout** — field edge, checkmark and box agree.
  A typo and a dictation's other form are graded correct and keep the checkmark;
  the color says how cleanly, never whether the answer counted,
  so the green glow stays the clean answer's alone.
  Neither counts as clean for FSRS either: both book Hard, same as a finished
  retry, because neither came back on the first, unaided try.
- A near miss does NOT auto-advance: the typed word stays in the field
  with the form it missed in the box below, so the slip is seen before the card goes.
- A wrong answer that IS another word of the catalog **names that word** instead of
  forgiving it, so two words a learner needs told apart can never grade each other correct.
- **A word asked for its MEANING accepts every meaning it has**, where the language being
  learned merges two of the learner's own words into one of its own. It is right, and it
  counts in full — and the card then holds on the meaning it teaches, because that is the
  word the learner has still not said. The reveal names the other meanings in the same quiet
  line the note uses, and **only one of the two is ever on the card**: a card with something
  of its own to say says that, and a second hint under the first is a line nobody reads.
- **A word you have consolidated is sometimes asked by ear alone**: the prompt is the
  replay glyph and nothing else, and what is typed is what the word MEANS, in your own
  language — hearing a word and writing it back down is transcription, and translating it
  is what the box is for. The field's placeholder names that language, as it always names
  the one an answer is owed in. The card is still a produce card and still books its
  review — only the side it asks from moved. It withholds the meaning, so the reveal owes
  it back, and it waits for the landed bar the emoji reads from the other side: taking a
  word's only cue away while it is still landing is not support.
  Where the word cannot be heard right now — no recording and no voice, reading aloud off,
  the device turned down or muted, a screen reader running — the card falls back to its
  source prompt rather than blocking, because unlike the letter drill review has another
  way to ask the same question.
- **"Can't listen right now?" stands under the primary action of every card asked by ear**,
  and puts the word on the card as text for the rest of that turn. A learner in a meeting
  or on a silent phone cannot hear the one thing the card consists of, and the alternative
  to reading it is answering blind. The question is otherwise unchanged — same answer, same
  language, same rating; only the channel it arrives through moves, and the next card asked
  by ear asks by ear again. The same word appears the moment the answer is out, whether it
  was asked for or not: the spelling is what the reveal owes, and it stands with its
  article, its plural line and the speaker that says it, above the meaning the card opens
  onto. iOS cannot read the ring/silent switch — there is no API — so on a phone silenced
  that way this button is the whole remedy, which is why it is on screen from frame one
  rather than offered after a silence.
- **A produce miss keeps the field open for a retry, not a self-grade tap.** 
  The reveal trims the field back to the words already right, 
  so the learner finishes the word against the answer standing on the card.
  Reaching it counts as recalled-with-help; giving up is an honest miss.
- **A missed word is written out once before the session moves on.** 
  A reveal followed by a single tap gives a word almost no encoding,
  which is how it comes back later and passes for new again;
  a word that has not landed is typed with the answer in view.
  Encoding only, never a grade — the rating the self-grade already chose is applied unchanged,
  so self-grading still owns the schedule.
  Production asks for it, and so does a first exposure — the review that teaches the word,
  written once as it is met. A later recognition miss does not: the target has stood in
  the prompt since the first frame, so copying it teaches nothing the reading did not,
  and the next review asks for the word properly.
  One write-out per miss, never two:
  giving up on a produce retry ends the card, because that field already was the write-out.
- **Finishing the word IS the answer**, 
  when producing, when writing out, and in the trainer drills alike:
  the field confirms itself the moment the letters line up and the card flips a beat later, so there is no need to confirm.
  Backing out of a finished word takes the confirmation with it. 
  Every step keeps a way out — a step you cannot leave is a trap.
- The textfield is on screen only where there is something to type, 
  and is focused the moment it is there — typing never costs a tap first.
- Progress bar: one segment per answer, colored by its outcome.
- A miss is stated where the learner is already looking;
  the streak survives a missed day, but not two in a row.

## Counts & sessions

- Sessions are composed, never configured.
  **The plan is the whole run**: the counter on screen is a promise, 
  so nothing joins a session already under way unless in endless mode.
  Session end is a summary that celebrates, carrying the streak and what the run consolidated.
  It also carries the TREE of the area the round worked hardest, rising out of the ground as
  the screen arrives — the one place a tree may move, because it is the one place where
  something just happened, and the only part of that screen that is about this learner's own
  box rather than about having finished. Which area is kern's answer (`SessionRunState`
  records what the run touched); ties walk catalog order, so a round split evenly names the
  same area every time it is shown.
  It is drawn far larger than in the forest but off the same growth curve, and the BOX it is
  given is what carries that — the tree fills whatever box it gets, so a fixed one drew a
  first-day sprout at the height of a thoroughly learned area: a bare stem the length of the
  screen, claiming a standing the area has not got.
- **A round too long for the evening can be taken short.** Where the day's round runs well
  past a sitting, the card offers a second way in beside "Los geht's!" — its due work alone,
  a round's worth of it, no first sights and nothing dragged forward. An abandoned round is
  worse than a small one: it leaves the day unworked and the streak unpaid, and a learner
  with five minutes had no move but to start something they could not finish. It is the same
  round stopped early rather than a different pile of words, and it is a round's worth on
  purpose, so the short sitting still closes the day (`../kern/README.md` §6).
  The quiet button below the primary one, and up only while the two are really different —
  a second button handing over what the first one does is a choice made for nothing.
- **The tree rises whatever the round did to the counts**, and what the round CHANGED lands
  on it afterwards — new marks out of nothing, a matured word swelling where it already hung,
  one after another rather than all at once. Two motions, because they answer two questions:
  a round that promoted nothing still moved the box, and a learner holding a hard area steady
  has earned the tree standing up. Tied to the counts alone the picture was simply frozen on
  those days. Only the round's own marks move; the rest of the crown holds still, which is
  what makes the new leaf the thing the eye goes to. Reduce Motion draws the finished tree at
  once, both motions included.
- **A record is named, a number is only counted.** A day streak standing at its longest
  ever (`BoxStatistics.longestStreak`) says so on the finish screen; a drill run that beats
  its own stored best says so too, and is the only thing in a drill that earns confetti and
  the cheer — a drill can be closed a dozen times an evening, and a screen that celebrates
  every close celebrates nothing. Drill records live outside the box: a run touches no card,
  so it is not box state.

## App structure (single screen)

- **The companion**: the box briefs a chat assistant the app does not host, and reads its
  answer back into own words — `companion.md` owns the whole of it, screens included.
- **Home** is the only root screen:
  the day's line, session card (streak flame + the round's counts, or done state),
  the listening card, trainer hub, then the 14-day strip and the forest.
  The listening card sits under the round and above the trainers because that is its
  standing: not what the box asks of the learner, and not a skill to climb, but the way in
  that needs no hands — up whenever this device can actually say both sides of enough words
  (`surfaces.md` § Listening).
  The line over the card carries the LANGUAGE being learned, in words that fit the hour
  (`../kern/docs/reports.md`, `dayPart`/`partVariant`) — the screen's own name spent the
  largest type on the page on what the learner already knew, and the language is the one
  piece of standing nothing else here says.
  Two registers, and every line is in one of them: the language speaking for itself
  ("Habari za asubuhi", "Tayari kujifunza?"), or the known language asking about it
  ("Ein Feierabend mit Suaheli?").
  The spoken lines lead, because they are the only ones that both greet and teach — and the
  only ones that can address the learner, since a name inside a sentence is that language's
  business (`Greetings.addressed`), not a slot the chrome can fake.
  The address is the learner's name, or the word the hour lends when none is set:
  morning and night have one, midday and evening do not, so the pool simply carries no
  addressed line there.
  Every chrome line ASKS — the words are on the table, and a statement over a card the
  learner has not opened yet claims their day for them.
  It holds through a render and has moved on by the next opening: a line that re-rolls
  while it is being read is a glitch, one that never moves stops being read.
  Those two carry no section title: the strip heads itself and the forest is captioned,
  so one above them says the word a third time.
  The card's flame is graded, not decorative — the 🔥 emoji at full strength,
  half-cooled while today still owes the run, or drained to gray once a miss would end it
  (`../kern/docs/reports.md`, `BoxStatistics.streakHealth`).
  The half-cooled mark is drained of some of its color, not faded: a washed-out flame
  reads as disabled, while one losing its heat asks to be re-lit.
  The card is up exactly while that work is still owed, so the mark carries the warning
  without spending a word on it — and the strip's badge and the widget's flame
  read off the same grade, so no two surfaces say different things about one day.
  The card names what the round is led by rather than calling everything "a session":
  due work, or an offer of new words when nothing is due.
  What the card promises is what the round will really hand over — the cap it will take,
  never the pile behind it.
  A day the learner has not worked is never called done —
  and neither is one with work still coming back, which the engine answers by composing
  the round rather than the finish (`../kern/README.md` §6).
  A screen that celebrates and is overturned minutes later teaches the learner not to believe it.
  The done state is ordered like every other celebration in the app —
  mark, headline, what the day bought, the way on, then fine print —
  and its mark IS the streak badge: two elements sandwiched the prose between them,
  and a card that both cheers and counts the run says one thing, not two.
  The done state carries the day's own movement under the standing totals:
  totals say where the box stands, the day's figures say that today moved it.
- **Stopping is the default at the end of a round**: the round that was planned is done,
  so "Fertig" is the primary button and going on the quiet one below it — an earned break
  needs no arguing for, and another round is still one tap away.
  That is the review session's shape; a drill has no end screen at all — it hands its
  figures to the page that started it (`surfaces.md`), where the way on is the button
  that was already there.
  A day that is going badly says so, and says why stopping is the better call.
- **The forest** at the foot of Home is a picture of the box, never a way around it:
  one tree per area, in catalog order, on ground its whole row shares. It answers the one
  question a count cannot — how the box is SHAPED, and which corners of the language have
  never been opened — and it hands anything actionable to the Box screen by opening it at
  the area a tree names.
  **The unit is the area, not the word.** Five hundred plants can only be read as texture,
  and drawing each as an object made packing an area — forty words at once, a normal
  move — look like a spilled bag rather than like sowing. One tree can be looked at.
  **A tree is one organism its whole life**, never swapped for another at some Sprosse: the
  trunk is what the area has grown, the canopy IS its words that have landed, and blossom
  and fruit appear ON that canopy. So the picture never starts over, and has no top Sprosse
  at which it stops. A word is a leaf — a thing believed many of without being counted;
  which Sprosse of `GrowthStage` (`../kern/docs/reports.md`) becomes which mark is decided in
  `AreaTrees` and nowhere else.
  **A word the learner has MET hangs on the tree from its first answer on**, as a bud until
  it settles into a leaf — ochre and a third a leaf's size, so a word leafing out always
  reads as a gain. Drawn as nothing until it consolidated, a first round in an area moved
  the picture not at all, which is the one round with the most to show. A word merely packed
  still hangs nothing: it has not been met, and it is only why the tree is growing.
  **Size comes from what has grown, never from what the catalog holds.** Sized by catalog
  count, every area would draw the same on install day and a year in — and the one thing a
  growing box's picture owes the learner is a shape that changes. A row is therefore a
  skyline, measured against a ground line its trees roughly share: the ground rolls a few
  points under each of them, and a tree standing further back is drawn first so the one in
  front overlaps it. Equal cells on one exact baseline read as a plantation, and the box is
  not one — but the roll stays a fraction of the height range, because heights are still
  what the row is for.
  **An area nobody has opened draws nothing at all** — not even ground. A mark per
  untouched word turns a catalog the learner did not choose into five hundred things they
  have not done; the dimmed area emoji already says the place exists.
  A lapse drops leaves at the foot and never shrinks the tree: the engine expects about a
  fifth of reviews to miss, and a picture that shrank for a routine Tuesday would overstate
  what a lapse costs. A suspended word is owed no space at all — waking it is the box's.
  The picture is never the only telling — the canvas is hidden from accessibility, each
  tree carries the tap target and the spoken split as one element, blossom differs from
  leaf in shape before color, and the standing figures are spelled out beneath.
  Nothing in it moves: a box grows over weeks, and motion would claim a change the
  picture is not showing.
- **Box** (📦 from Home): browse the catalog by area, pack words in and take a
  shelf's queue back out before a round has met it, revive suspended ones;
  settings live here — profile, reset. A tree in the forest opens it already
  unfolded at that area, exactly as a search hit does. **The area is the unit for
  packing and unpacking alike**, same as the forest's own picture — a word acted
  on by itself is only ever the one a search reached by name, never a shelf
  listing (the mechanics and the row/shelf color agreement this rests on are
  kern's own contract, `kern/README.md` §6).
  - **Search** (🔍 in the bar) reaches a word without knowing its shelf. The two result
    kinds offer what they ARE: an area unfolds itself back on the Box screen and scrolls
    into reach, a word can be heard and packed on its own — a learner who went looking for
    one word by name should not have to take the shelf around it.
  - **Eigene Inhalte** closes the Box, below the shelves and above the settings, and it is
    the one section always drawn: it carries the button that writes a word, and the only
    other way in is a search that found nothing. Everything the learner put into the box
    themselves is here — the words they wrote, and the problems they filed — and nothing
    the catalog brought. It is deliberately NOT a shelf: own words are packed the moment
    they are written, so an area head offering to pack them would say nothing, and a
    progress bar over a handful of words is furniture.
    A reported own word appears ONCE, in the words block wearing its flag; the reported
    block lists catalog cards only, because naming the same word twice in one section
    reads as two different problems.
    Both ways out — clipboard and mail — carry the same one text, and each offers
    everything or only what is new, the latter only once a copy has ever been taken: before
    that the two would be the same list, so the control asks nothing.
    Each also offers to EMPTY the section behind the export, and a button beside them does
    it alone: suggestions and reports are an outbox, and a learner who sends theirs on
    regularly otherwise watches the section only grow. What a clear leaves is the words
    written in both languages (`kern/README.md` §6) — those are cards with progress on
    them, not notes. Only the bare button asks first; behind an export there is nothing
    left to lose, the lot having just landed on the clipboard or in a draft.
  - **Own words** are also what a search with no answer leads to: the learner has just
    proved the catalog has no word for what they need, so the empty state offers to write
    one, with the known side prefilled from the query — a search box is far more often used
    to name what one wants to be able to SAY than a form met in the wild. One side alone is
    still taken, as a SUGGESTION — the learner has the half they came with and the catalog
    owes the other — and it is never asked, since there is nothing to ask yet. A growing
    catalog can never collide with them and a box reset never takes them
    (`../kern/README.md` §6). Rewriting one keeps its id, and with it the progress made on
    it; deleting one is the only deletion in the app, since a catalog word can be put to
    sleep but never removed.
- **A long press on a Box row offers everything that can be done to one word; the same
  press mid-round offers two things only.** In the Box the menu is where the word stands
  (pack, unpack, sleep, wake, forget its progress), then what can be MADE of it (an own
  word from it, or editing one), then what is wrong with it, and deleting last because it
  is the entry that cannot be taken back. Above those, where the word carries one, the
  catalog's note — the gloss a session card only hands over at the reveal, which nothing
  in the Box turns over; the row itself has no width for a sentence.
  A round is no place to reorganize the box, so
  the session card keeps just reporting and stopping the word being asked.
  Those two are unrelated on purpose: reporting a problem changes nothing about what gets
  asked, and taking a word out files no complaint (`kern/README.md` §6), which is why
  the report sheet carries no suspend switch. What the learner typed rides along without
  being asked about — the answer the catalog rejected is usually the report itself, and a
  box to tick is a box to miss. In a session, only after the reveal: a menu over a prompt
  is a menu over a question they have not been answered yet.
- **Taking a word out of a round does not grade it on the way out**
  (`SessionIntent.SuspendCurrent`). The card leaves on the spot with no rating asked —
  demanding one for a word the learner just said should never be asked is the busywork the
  action removes — and the round's total shrinks with it, since the count on screen is a
  promise and a word taken out was never owed. The control is named for what it does,
  "Nicht mehr abfragen", not for the 💤 the Box draws afterwards: the Box shows a STATE and
  can afford the metaphor, a menu entry is a verb and cannot.
- **The picture field takes two characters and offers a few by tap.** Written by hand it is
  a keyboard trip for something optional, so a short row of pictures sits under its label;
  the cap is two so a flag can pair with a thing while a pasted sentence cannot land there
  (`OwnWords.QUICK_EMOJI`, `OwnWords.MAX_EMOJI`). Those pictures are the KIND glyphs the box
  already draws — a learner writing a word rarely has a picture in mind but does know what
  kind of word it is, and those five are vocabulary the app has already taught them.
  The two language fields wear their flag, and a button between them swaps what is typed in
  each, for the learner who filled them in the wrong way round.
- **A language is named the same everywhere, by its own name for itself**
  (`LanguageChoices.name` — "Deutsch", "Kiswahili"). One choice, kern's, because the
  alternative already drifted: one phone read a localized exonym out of the string catalog
  while the other read the endonym, so the same language answered to two names. The endonym
  is what `languages.json` carries for every language rather than the handful someone wrote
  chrome for, and in an app about languages it is the better default anyway. Pickers still
  show both (`pickerRow`), where the point is recognizing a row rather than naming it.
- **Two clean-correct beats, one home: 450 ms live, 1200 ms explicit.** The 0.45–1.2 s a
  clean answer waits above is those two tiers — 450 ms when the typing itself went exactly
  correct, 1200 ms when the learner tapped Check, Enter or a tile. Vocab review, the
  trainer drills and the letter drill's tile and explicit-submit stages all schedule off
  the one tier kern names (`AdvanceTier`, `../kern/src/commonMain/kotlin/net/spross/kern/session/Turn.kt`)
  rather than each screen carrying its own timer, so neither number can be minted a second
  time — nor differ between the platforms. The accessibility guard rides with it in each
  platform's scheduling helper: under VoiceOver and Switch Control no timer runs at all and
  an explicit "Weiter" takes its place. The letter drill's typed and dictation stage is the
  deliberate holdout with no live tier (`backlog.md` § App & UX).

Design language: warm, card-centric, emoji as illustration, article color coding
der=blue / die=berry / das=green — degrading to neutral for languages without gendered
articles. A two-gender language folds onto those same two hues rather than minting its own
(es el/los/un blue, la/las/una berry, the neuter never reached). The article rendered is
always the one `grammar.gender` names, prepended — never a word sliced off the front of the
text, which carries the bare word in every language.
Palette: stone-and-moss paper, clay headline, ocean and forest as secondaries
(growing-box theme), every pairing clearing WCAG AA in both schemes.
`App/Sources/Design/Theme.swift` holds the values and the rules that keep them there.

## Content pipeline

- `catalog/` is the in-repo master (format spec in `catalog/README.md`), bundled as a
  folder resource; cards are derived from the (source, target) join at load.
- Format rules are guarded by a lint test on every kern test run.
- Content changes go through verification sweeps before shipping
  (method: `../../docs/sprachposter-learnings.md`).

## Testing & gates

Commands and gates: `../CLAUDE.md` § Commands;
engine gates and the behavioral test inventory: kern README.

## Not yet

Couple mode, accounts/sync, UI chrome past de/en (every other source falls back to en).
Android has the core loop on the full kern turn (write-out, retry, the earned Easy),
Box browse with search, own words and the settings block, the 14-day activity strip,
the home-screen widget, and the whole of Sprossen — all four drills on kern's rules,
each behind its overview page with the generated numbers reference, the alphabet table,
the joined atlas and the joined calendars (`surfaces.md`) — but no forest canvas or growth headline;
a new record celebrates in words and a cheer, without confetti.
