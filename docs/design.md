# Spross — app design (v2)

Build contract for the review loop: the card, the typing, Home, the Box.
Drills: `drills.md`; listening/wrist/Android: `surfaces.md`;
audio: `read-aloud.md`; budgets: `performance.md`.
Engine: `../kern/README.md`.

## North star

Every screen answers "What do I do right now?" with zero ambiguity.
The app composes the work; the user never browses for what to study.

Copy states what is on the table, never personifies waiting
("Lust auf neue Wörter?", not "Neue Wörter warten auf dich";
"Morgen sind %@ Karten dran", not "warten auf dich").
Every prompt, empty state, call to action and forecast line gets the same read:
where the sentence puts the learner under an expectation,
rephrase it as an availability statement or a question.

## Boundaries & persistence

Strict dependency direction: App → SprossKern, never the reverse.

- Only the app target links the Kotlin framework;
  watch and widget targets are pure Swift over phone-built snapshots.
- `KernBridge.swift` is the boundary; the app hands the engine its clock
  (`nowEpochMillis` + `tzId`) rather than letting it read one.
- Persistence: `BoxStore` actor, one document per target language, atomic writes,
  saved after answers, at session end, and on background —
  an answered review is never only in memory.
- **Calibration is re-applied from the build on every load**:
  learning steps, retention and caps are the app version's decisions,
  so a box written months ago answers to the current numbers, not its own.
  Nothing survives it — growth pacing is the engine's opinion,
  not a figure the learner tunes (`docs/growth-evidence.md`).
- Swift 6 strict concurrency.

## Profile & onboarding

- Profile = (source, target) catalog languages;
  the catalog decides which targets a source can reach.
- Default source = device language when covered, else en.
  Either picker may hold the other side's language — picking it swaps the pair.
- The first round teaches itself with one line per moment,
  in the quiet line every other aside uses.
  The grade line takes the standing question's slot.
  The lines last that whole round and closing the round spends them.
  The flag arms with the round onboarding opens and lives in memory.
- Onboarding is three pages, ending inside the first round:
  the pair, what the box is for, what a round asks.
  Only the last page commits (joins the box and opens the session).
  The settings carry a restart-tutorial row.
- One picker list open at a time; the other folds on its pick.
  Onboarding opens with the known side folded.
- The chooser is the first-launch screen.
  Afterwards the pair is changed on the box's own pickers.
- Picker rows: flag, own name, English exonym ("🇺🇦 Українська · Ukrainian").
  The swap row counts the swapped pair.
- UI chrome renders in the known language when chrome exists (de/en today), else en.
  Onboarding follows the source being picked, re-rendering on each tap.
- **English chrome title-cases names** — screens, drills, exercises, modifiers, sections.
  Sentences stay sentence case.
  German is left alone — it has no title-case convention.
- Chrome strings are symbolic keys (`settings.known.title`), never source text.
  How a key is written and kept honest: `scripts/strings.py`.
  A key is a literal, never built by interpolation
  (`"dates.sprosse.\(n)"` looks up `dates.sprosse.%lld`, localizes nothing).
  Key namespaces: first level = surface, second level = kind within it
  (`box.card` vs `box.shelf`, `home.offer` vs `home.done`);
  cross-surface keys go to `common.`, accessibility-only keys to `a11y.`.
  Keys name the domain's word, not the code's —
  known/learning over source/target, Sprosse over rung, pack over enqueue.
  No key is also the stem of a family that means something else.
- The String Catalog is the one home for copy on both phones:
  Android's tables are generated from it (`scripts/chrome.py`),
  and a pre-commit check refuses a catalog edit that leaves them behind.
  A field's name is its key, camelCased (`box.card.due` → `boxCardDue`).
  `IOS_ONLY` / `ANDROID_TODO` / `ANDROID_ONLY` track platform scope.
  What a string means is its catalog entry's `comment`.
  Where the layout differs the reader composes — the catalog holds the caption,
  each phone sets the form beside or below it.
- Area titles and area emoji both come from the catalog; the app carries no map.
  A title is a plain name usable as the produce prompt's area cue;
  the catalog's optional subtitle is the flavor line,
  shown under the title in the box and nowhere a cue is wanted.

## Presentation model in the UI

**Practice means typing.**
Writing the word is the recall the box schedules;
revealing is the way out for three cases:
a first exposure, a recognition turn, or a learner who does not want to type.
Grade buttons live on that path only.

- The role alternates between produce and recognize.
- **Produce**: typed answer in the target language, graded by the kern normalizer.
  "Aufdecken" is the no-typing fallback, self-graded.
- **Recognize**: reveal and self-grade only — no input field.
  Three outcomes; Easy is earned by answering fast, never picked.
  Buttons name what the learner knows, never FSRS rating names,
  and stand under the question in the quiet hint line.
  The recall clock runs from prompt to "Aufdecken".
  Reveal carries source meaning plus the full synonym family.
- Picture slot: beside words when the screen has an input/keyboard,
  above them when the card owns the screen.
  `VocabCardView`'s own parameter, not a caller's choice.
  Text stays centered; the picture mirrors on the opposite edge.
  Slot is held whether or not the picture is in it.
  A picture that would answer the question is withheld until reveal (`CardEmoji.Cue`).
- Ambiguous prompts carry an area label, produce only, never graded.
- Grammar (plural, article color) renders target-side only.
- Drill cards wear the same face (`drills.md`).

## Review UX rules

These bind every surface that asks for an answer, drills included.
A new surface that asks the way an existing one does IS that component with a parameter,
never a second cut.
What licenses a second component is a parameter attempted and found not to carry.

- **The answer is never on screen twice, and never in the field.**
  Correct → card stays closed, narrated at the field.
  Wrong → card expands onto the answer.
  Near miss → correction box under the field.
  Either way the owed form stands at a readable size with its speaker beside it.
- The field's text is centered on the card's axis;
  its checkmark is mirrored by an empty slot so a verdict never shifts it.
- **Near miss runs amber** — field edge, checkmark and box agree.
  Green stays the clean answer's alone.
  Books Hard (same as a finished retry).
- Near miss does not auto-advance.
- A wrong answer that is another catalog word **names that word** (`Match.OtherWord`).
- **Meaning answers accept every meaning the word has**,
  where the target merges two source concepts.
  The card holds on the meaning it teaches.
  Only one hint on the card at a time.
- **Sound-prompted production** for words past the growing bar:
  prompt is the replay glyph, answer is the meaning in the known language.
  Falls back to source prompt when the word cannot be heard.
- **"Can't listen right now?"** under every sound-prompted card:
  puts the word on the card as text for that turn.
  Same answer, same rating; only the channel moves.
- **A produce miss keeps the field open for a retry.**
  The reveal trims to the words already right;
  finishing counts as recalled-with-help, giving up is an honest miss.
- **A missed word is written out once before the session moves on.**
  Encoding only, never a grade.
  Production and first exposures ask for it; recognition misses do not.
  One write-out per miss.
- **Finishing the word IS the answer:**
  the field confirms itself when the letters line up, the card flips a beat later.
  Backing out takes the confirmation with it.
  Every step keeps a way out.
- The textfield appears only where there is something to type, focused immediately.
- Progress bar: one segment per answer, colored by outcome.

## Counts & sessions

- Sessions are composed, never configured.
  **The plan is the whole run**: the counter on screen is a promise,
  so nothing joins mid-session unless in endless mode.
- Session end: summary with streak and consolidation counts.
  The tree of the hardest-worked area rises out of the ground
  (`SessionRunState` records it; ties walk catalog order).
  Drawn far larger than in the forest but off the same growth curve,
  and the box it is given carries the scale.
- **A round too long for the evening can be taken short:**
  due work alone, a round's worth, no first sights.
  Quiet button below the primary, shown only while the two offers differ.
- **The tree rises whatever the round did to the counts.**
  New marks land one by one after the rise.
  Only the round's own marks move; the rest holds still.
  Reduce Motion draws the finished tree at once.
- **A record is named, a number is only counted.**
  Day streak at its longest says so on the finish screen.
  A drill run that beats its stored best earns confetti and the cheer.
  Drill records live outside the box.

## App structure (three tabs)

- **The companion**: the box briefs a chat assistant
  and reads the conversation's answer back as own words (`Briefing`, `Harvest`).
  Leads the Box tab's own-content panel;
  stands on Home under the hub, wearing the listening card's face.
  Under the day's card, never in it.
  The finished round offers it under the celebration.
  The sheet shows the loop in three numbered moves, never the prompt text.
  Copy and Share both carry the same text.
  What comes back is shown whole and sorted, never filtered:
  every pair in new/near/held groups (`HarvestKind`), only the new group arrives ticked.
- **Three peer sections** behind the tab bar: Home, Box, Settings.
  Bar is up on those three, gone for everything else.
  Each item carries a glyph and its section's name.
- **Home**: the day's line, session card (streak flame + counts or done state),
  listening card, trainer hub, companion card, 14-day strip, forest.
  Listening card under the round and above the trainers.
  The line over the card carries the language being learned in words that fit the hour
  (`../kern/docs/reports.md`, `dayPart`/`partVariant`).
  Two registers: the target speaking for itself, or the known language asking about it.
  The spoken lines lead and may address the learner by name
  (`Greetings.addressed`); morning/night have an address,
  midday/evening do not.
  Every chrome line asks — never states.
  Holds through a render, moves on by the next opening.
  No section titles over the strip and forest (each heads itself).
  The card's flame is graded (`BoxStatistics.streakHealth`):
  full 🔥, half-cooled (drained of color, not faded), or gray.
  Strip badge, widget flame and streak headline all read from the same grade,
  merged across every language.
  The card names what the round is led by (due work or new-word offer).
  What it promises is what the round will hand over — the cap, never the pile.
  A day not worked is never called done;
  a day with work still returning is not either (`../kern/README.md` §6).
  Done state: mark (= streak badge), headline, day's figures, way on, fine print.
- **Stopping is the default at round end.**
  "Fertig" is primary; going on is the quiet button below it.
  A day going badly says so and says why stopping is the better call.
- **The forest** at the foot of Home: one tree per area, catalog order,
  on ground its whole row shares.
  Answers how the box is shaped and which corners have never been opened.
  Tapping a tree opens the Box at that area.
  **The unit is the area, not the word.**
  **A tree is one organism its whole life** — trunk is growth, canopy is landed words,
  blossom and fruit appear on it.
  Which `GrowthStage` becomes which mark: `AreaTrees` and nowhere else.
  A met word hangs as a bud until it settles into a leaf.
  Merely packed hangs nothing.
  Size comes from what has grown, never from catalog count.
  Rows are skylines over gently rolling ground.
  An unopened area: one faded seedling on bare ground.
  A lapse drops leaves, never shrinks the tree.
  A suspended word gets no space.
  Accessibility: canvas hidden, each tree has tap target + spoken split,
  blossom differs from leaf in shape before color, figures spelled out beneath.
  Nothing moves.
- **Box** (middle tab): browse by area, pack words, unpack, revive suspended.
  A forest tree opens it at that area.
  The area is the unit for packing and unpacking
  (`kern/README.md` §6 owns the mechanics).
  - **Search** (🔍): area results unfold on the Box screen;
    word results can be heard and packed individually.
  - **Eigene Inhalte** closes the Box, under the shelves.
    Carries the write-a-word button; the other way in is a search with no results.
    Three blocks: words in two+ languages, suggestions, notes.
    A reported own word appears once in the words block;
    the reported block lists catalog cards only.
    Export offers everything, only new, or only suggestions+reports.
    Whole copy marks itself taken.
    Clear empties suggestions and reports, keeping word-pair cards.
    Behind an export, clear needs no confirmation.
    Catalog matches: every own word the catalog now has,
    with a merge action (`CatalogMatches`, `../kern/README.md` §6).
    Ticked rows: both halves agree; unticked: one half differs, learner decides.
    Each row leads with the catalog's writing, learner's under it wearing the section's pen.
    The three one-word actions share a line;
    the merge takes the line under them.
    Ticked-word lists use one row per platform (same as companion harvest).
  - **Own words** are what a search with no answer leads to:
    the empty state offers to write one, known side prefilled from the query.
    One side alone is taken as a suggestion.
    A growing catalog never collides with them; a box reset never takes them.
    Rewriting keeps the id; deleting is the only deletion in the app.
- **Settings** (third tab): pair, name, read-aloud toggle, backup,
  restart-tutorial, reset, recording credits footer.
  **About** reached from its footer only.
- **Long press on a Box row**: pack/unpack/sleep/wake/forget,
  make own word, report, delete (last, irreversible).
  Above: also-words ("auch: …"), note, stability in days.
  Each line drawn only where the word has it.
  **Long press mid-round**: report and suspend only,
  available after the answer is out (`TurnState.answerOut`).
  Report sheet carries no suspend switch;
  the typed answer rides along without being asked about.
  Reopened on an existing report, the form opens on it.
- **Taking a word out** (`SessionIntent.SuspendCurrent`):
  no rating asked, round total shrinks.
  Named "Nicht mehr abfragen", not the 💤.
- **Picture field**: two characters max, kind glyphs offered as quick picks
  (`OwnWords.QUICK_EMOJI`, `OwnWords.MAX_EMOJI`).
  Language fields wear their flag with a swap button between them.
  Form opens unfocused.
- **Language naming**: always the endonym
  (`LanguageChoices.name` — "Deutsch", "Kiswahili").
  Pickers show both endonym and exonym (`pickerRow`).
- **Auto-advance timing**: 450 ms live (typing exactly correct),
  1200 ms explicit (Check/Enter/tile tap).
  One source: `AdvanceTier` in kern.
  Under VoiceOver/Switch Control: no timer, explicit "Weiter" instead.
  The letter drill's typed/dictation stage is the deliberate holdout
  with no live tier (`backlog.md` § App & UX).

Design language: warm, card-centric, emoji as illustration.
Article colors: der=blue, die=berry, das=green;
two-gender languages fold onto the same two hues
(es el/los/un blue, la/las/una berry;
fr and it the same way, with `le` read by language;
an article marking both genders — `l'`, fr `les` — neutral).
The rendered article is always the one `grammar.gender` names, prepended.
Palette: stone-and-moss paper, clay headline, ocean/forest secondaries,
every pairing clearing WCAG AA in both schemes.
`Palette.kt` (kern) holds values; `Theme.swift` holds rules.

## Content pipeline

`catalog/` is the in-repo master, bundled as a folder resource;
cards are derived from the (source, target) join at load.
Format rules guarded by lint on every kern test run.

## Testing & gates

Commands and gates: `../CLAUDE.md` § Commands;
engine gates and behavioral tests: kern README.

## Not yet

Couple mode, accounts/sync (`sync.md`),
UI chrome past de/en (every other source falls back to en).
Android: no forest canvas, no growth headline
(`surfaces.md` § Android companion).
