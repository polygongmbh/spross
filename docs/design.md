# Spross — app design (v2)

This doc is the build contract for the REVIEW LOOP: the card, the typing, Heute, the Box.
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
  the first round is never something the learner has to go and find on Heute.
  It is the first-run path alone — a later language change is the box's own settings,
  which take none of the pages and open no session over the screen you were on.
- One list is open at a time; the other stands folded on its pick and opens at a tap,
  and picking a source hands the screen to the target. Onboarding opens with the known
  side folded — the device language is a good guess already — so the picker is one
  screen with one open question, no scrolling.
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
- Chrome strings are SYMBOLIC keys (`settings.source.title`), never source text in either
  language: copy edits then never detach a translation, and a new chrome language is
  additive. How a key is written and kept honest: `scripts/strings.py`.
- The String Catalog is the ONE home for that copy, on both phones: Android's tables are
  generated from it (`scripts/chrome.py`, keyed by its `MAPPING`), and a pre-commit check
  refuses a catalog edit that leaves them behind. Strings only Android says live there too,
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
- A picture on a card — a word's emoji, a country's flag — sits in a fixed slot
  **beside the words**, never above them, and that holds for every card face the app has:
  vertical space is the scarce axis — card, input, button and keyboard share one screen —
  and the slot is held for the card's whole life, so a reveal moves nothing.
  A picture that would ANSWER the question is **withheld as a hint, never dropped**: it is
  held back while the answer is owed and appears at the reveal, so the learner always ends
  up seeing what they were asked about. The slot itself keeps this (`DLCardEmoji.Cue`) —
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
- **A word you have consolidated is sometimes asked by ear alone**: the prompt is the
  replay glyph and nothing else, and what is typed is what was heard. The card is still a
  produce card and still books its review — only the prompt side moved. It withholds the
  meaning, so the reveal owes it back, and it waits for the landed bar the emoji reads from
  the other side: taking a word's only cue away
  while it is still landing is not support. A synonym typed here is amber, never wrong —
  the reveal itself teaches those forms, it simply was not the one that played. Where the
  word cannot be heard right now — no recording and no voice, reading aloud off, a screen
  reader running — the card falls back to its source prompt rather than blocking, because
  unlike the letter drill review has another way to ask the same question.
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

- **Heute** is the only root screen:
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
  The card's flame is graded, not decorative — the 🔥 emoji at full strength, paled,
  or drained to gray by what today still owes the run
  (`../kern/docs/reports.md`, `BoxStatistics.streakHealth`).
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
- **The forest** at the foot of Heute is a picture of the box, never a way around it:
  one tree per area, in catalog order, on ground its whole row shares. It answers the one
  question a count cannot — how the box is SHAPED, and which corners of the language have
  never been opened — and it hands anything actionable to the Box screen by opening it at
  the area a tree names.
  **The unit is the area, not the word.** Five hundred plants can only be read as texture,
  and drawing each as an object made packing an area — forty words at once, a normal
  move — look like a spilled bag rather than like sowing. One tree can be looked at.
  **A tree is one organism its whole life**, never swapped for another at some rung: the
  trunk is what the area has grown, the canopy IS its words that have landed, and blossom
  and fruit appear ON that canopy. So the picture never starts over, and has no top rung
  at which it stops. A word is a leaf — a thing believed many of without being counted;
  which rung of `GrowthStage` (`../kern/docs/reports.md`) becomes which mark is decided in
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
- **Box** (📦 from Heute): browse the catalog by area, pack words in, revive suspended
  ones; settings live here — profile, reset. A tree in the forest opens it already
  unfolded at that area, exactly as a search hit does.
  - **Search** (🔍 in the bar) reaches a word without knowing its shelf. The two result
    kinds offer what they ARE: an area unfolds itself back on the Box screen and scrolls
    into reach, a word can be heard and packed on its own — a learner who went looking for
    one word by name should not have to take the shelf around it.
  - **Own words** are what a search with no answer leads to: the learner has just proved
    the catalog has no word for what they need, so the empty state is where writing one
    down belongs, and nowhere else. Both languages are asked for, because a word is only
    studiable as a pair; the known side arrives prefilled from the query, since a search
    box is far more often used to name what one wants to be able to SAY than a form met in
    the wild. They land in an area of their own, after every catalog area — which is the
    whole point of the arrangement (`../kern/README.md` §6): a growing catalog can never
    collide with them, and a box reset never takes them. Deleting one is a long-press on
    its row, and it is the only deletion in the app: a catalog word can be put to sleep,
    never removed.
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
the home-screen widget, and the whole of Sprossen — all three drills on kern's rules,
each behind its overview page with the generated numbers reference, the alphabet table
and the joined atlas (`surfaces.md`) — but no forest canvas or growth headline;
a new record celebrates in words and a cheer, without confetti.
