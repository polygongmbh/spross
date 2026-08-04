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
- Picker rows carry the flag, the language's own name, and the English exonym
  ("🇺🇦 Українська · Ukrainian"): a flag beside an unreadable script is easy to
  mistake for a neighbouring language.
- UI chrome renders in the KNOWN language when chrome exists (de/en today), otherwise en.
  Onboarding follows the source being PICKED — device language first, re-rendering on
  each tap — so the greeting is already in the user's language.
- Chrome strings are SYMBOLIC keys (`settings.source.title`), never source text in either
  language: copy edits then never detach a translation, and a new chrome language is
  additive. How a key is written and kept honest: `scripts/strings.py`.
- Area titles and area emoji both come from the catalog; the app carries no map of its own.

## Presentation model in the UI

**Practice means typing.** Writing the word is the recall the box is scheduling, so that
is what a card asks for. Revealing is the way out of it, for the three cases where typing
is not the question: a word met for the first time (with the emoji as its cue), a target
form to be recognized rather than produced, and a learner who does not want to type right
now — one hand on the phone is reason enough.
The grade buttons live on that path only; they are the way back to the deck,
never the shape of the app.

- The role of each review comes from the engine, alternating produce and recognize.
- **PRODUCE**: typed answer in the target language, graded by the kern normalizer.
  "Aufdecken" is the no-typing fallback, and self-grades.
- **RECOGNIZE**: reveal and self-grade ONLY — no input field, so no schedule is ever
  graded against a language the word was not learned with.
  The learner reports one of three outcomes, and the engine turns that plus how long the
  recall took into the rating: Easy is earned by answering fast, never picked.
  The buttons name what the learner knows, never what the scheduler will do.
  The recall clock runs from the prompt appearing to "Aufdecken" — the time spent choosing
  a button afterwards is thumb travel, not memory.
  The reveal carries the source meaning plus the full synonym family.
- The emoji sits in a fixed slot **beside the headword**, never above it:
  vertical space is the scarce axis — card, input, button and keyboard share one screen —
  and the slot is held for the card's whole life, so a reveal moves nothing.
- Ambiguous prompts (the target merges two source concepts) carry an **area label**.
  Produce only: on a recognition prompt a cue precise enough to disambiguate would give
  the answer away. Never graded.
- Grammar — plural line, article color — renders TARGET-side only;
  prompt and reveal are styled by role, not by language.

## Review UX rules

- A wrong answer reveals **inline**, expanding the card DOWNWARD;
  no space is reserved for it beforehand.
- **The answer is never on screen twice.** The card expands only when the word was not
  produced — a wrong answer or "Aufdecken"; anything graded correct leaves the card
  closed and is narrated at the field, where the learner's own attempt already stands.
- A typo counts as correct but does NOT auto-advance: the typed word stays with the
  proper spelling beside it, so the slip is seen before the card goes.
- A wrong answer that IS another word of the catalog **names that word** instead of
  forgiving it, so two words a learner needs told apart can never grade each other correct.
- **A word you have consolidated is sometimes asked by ear alone**: the prompt is the
  replay glyph and nothing else, and what is typed is what was heard. The card is still a
  produce card and still books its review — only the prompt side moved. It withholds the
  meaning, so the reveal owes it back, and it needs the STRICTER consolidated bar rather
  than the settled one every other presentation rule uses: taking a word's only cue away
  while it is still landing is not support. A synonym typed here is amber, never wrong —
  the reveal itself teaches those forms, it simply was not the one that played. Where the
  word cannot be heard right now — no recording and no voice, reading aloud off, a screen
  reader running — the card falls back to its source prompt rather than blocking, because
  unlike the letter drill review has another way to ask the same question.
- **A produce miss keeps the field open for a retry, not a self-grade tap.** The reveal
  trims the field back to the words already right, so the learner finishes the word
  against the answer standing on the card. Reaching it counts as recalled-with-help;
  giving up is an honest miss.
- **A missed word is written out once before the session moves on.** A reveal followed by
  a single tap gives a word almost no encoding, which is how it comes back later and
  passes for new again; a word that has not settled is typed with the answer in view.
  Encoding only, never a grade — the rating the self-grade already chose is applied
  unchanged, so self-grading still owns the schedule.
  Production asks for it, and so does a first exposure — the review that teaches the word,
  written once as it is met. A later recognition miss does not: the target has stood in
  the prompt since the first frame, so copying it teaches nothing the reading did not,
  and the next review asks for the word properly.
  One write-out per miss, never two: giving up on a produce retry ends the card, because
  that field already was the write-out.
- **Finishing the word IS the answer**, when producing, when writing out, and in the
  trainer drills alike:
  the field confirms itself the moment the letters line up and the card flips a beat
  later, so there is **no confirm button** to reach for. Backing out of a finished word
  takes the confirmation with it. Every step keeps a way out — a step you cannot leave
  is a trap.
- A field is on screen only where there is something to type, and is focused the moment
  it is there — typing never costs a tap first.
  **A pause that waits for a tap gives the keyboard back**, everywhere one exists — the
  amber holds (a typo's spelling, a dictation's other form) end in a button, and a keyboard
  left standing covers the button being waited for. A beat that advances on its own keeps
  it: a keyboard that drops and returns within the 1.2 s is worse than one that never moved.
- Progress bar: one segment per answer, colored by its outcome.
- A miss is stated where the learner is already looking;
  the streak survives a missed day, but not two in a row.
## Counts & sessions

- Sessions are composed, never configured.
  **The plan is the whole run**: the counter on screen is a promise, 
  so nothing joins a session already under way unless in endless mode.
  Session end is a summary that celebrates, carrying the streak and what the run consolidated.
- **A record is named, a number is only counted.** A day streak standing at its longest
  ever (`BoxStatistics.longestStreak`) says so on the finish screen; a drill run that beats
  its own stored best says so too, and is the only thing in a drill that earns confetti and
  the cheer — a drill can be closed a dozen times an evening, and a screen that celebrates
  every close celebrates nothing. Drill records live outside the box: a run touches no card,
  so it is not box state.

## App structure (single screen)

- **Heute** is the only root screen:
  session card (streak flame + the round's counts, or done state),
  trainer hub, then the Fortschritt section — 14-day strip and the forest.
  The card names what the round is led by rather than calling everything "a session":
  due work, or an offer of new words when nothing is due.
  Copy for the second is an OFFER, never a summons —
  the words are on the table, they are not waiting on the learner.
  What the card promises is what the round will really hand over — the cap it will take,
  never the pile behind it.
  A day the learner has not worked is never called done —
  and neither is one with work still coming back, which the engine answers by composing
  the round rather than the finish (`../kern/README.md` §6).
  A screen that celebrates and is overturned minutes later teaches the learner not to
  believe it.
  The done state is ordered like every other celebration in the app —
  mark, headline, what the day bought, the way on, then fine print —
  and its mark IS the streak badge: two elements sandwiched the prose between them,
  and a card that both cheers and counts the run says one thing, not two.
  The done state carries the day's own movement under the standing totals:
  totals say where the box stands, the day's figures say that today moved it.
- **Stopping is the default at the end of a round**: the round that was planned is done,
  so "Fertig" is the primary button and going on the quiet one below it — an earned break
  needs no arguing for, and another round is still one tap away.
  A session summary and a drill's end through the same pair of buttons, so they cannot
  drift apart on which way out is the default.
  A day that is going badly says so, and says why stopping is the better call.
- **The forest** at the foot of Heute IS the box: one plant per word, clustered into a
  grove per area, in catalog order so adjacency still says which group an area belongs to.
  The box had a screen of its own for as long as it was a list; a picture of what has
  grown can be looked at from where the learner already is, and the browsing hangs off it.
  A grove opens its area.
  How far a word has come is `GrowthStage`'s to say (`../kern/README.md` §6) and this
  layer's to draw: which rung takes which plant, and whether two rungs share one, is
  decided in `ForestSection` alone. Growth reads UP the ladder — seed, sprout, stem,
  leaf, flower, tree — so the flower is the word that has landed and the tree the one
  that has stopped needing tending. Species is the word's kind.
  Plants stand where a hash of their card id puts them, never where their place in seed
  order would: the box grows in seed order, and a patch that showed it would pile
  everything the learner has reached into one corner.
  The picture is never the only telling — the canvas is hidden from accessibility and
  each grove carries the tap target and the spoken split as one element, every stage is
  a silhouette before it is a colour, and the standing figures are spelled out beneath.
  Nothing in it moves: a box grows over weeks, and motion would claim a change the
  picture is not showing.
  - **Area** (a grove, or a search hit): the words in it and the one control that packs
    the rest of them in. It opens on its words — the learner said which area they meant
    by tapping it.
  - **Search** (🔍 in Heute's bar) reaches a word without knowing its shelf. The two
    result kinds offer what they ARE: an area opens itself, a word can be heard and
    packed on its own — a learner who went looking for one word by name should not have
    to take the shelf around it.
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
  the one shared helper (`App/Sources/Design/AutoAdvance.swift`) rather than each screen
  carrying its own timer, so both numbers and the accessibility guard — under VoiceOver
  and Switch Control no timer runs at all and an explicit "Weiter" takes its place — live
  in a single place. The letter drill's typed and dictation stage is the deliberate
  holdout with no live tier (`backlog.md` § App & UX).

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

Couple mode, accounts/sync, sw/uk UI chrome (those sources fall back to en).
Android has the core loop and ONE trainer, the letter drill (a chip on Heute — the
platform has no trainer hub) — no Box browse, other trainers, alphabet sheet, widget,
14-day strip or write-it-out step, and its settings are a language switch and an About
screen (version, read-aloud, credits — where iOS carries the read-aloud row in Box
settings).
