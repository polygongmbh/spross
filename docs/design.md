# Spross — app design
The app-layer rules every screen answers to: what the app composes, how an answer is asked, graded and shown, how copy speaks, the visual language; one screen's layout, element order, sizes, timings and inventories are the code's, not this page's.
Neighbors: drills `drills.md`, listening/wrist/Android `surfaces.md`, audio `read-aloud.md`, engine `../kern/README.md`.

## North star

Every screen answers "What do I do right now?" with zero ambiguity.
The app composes the work; the user never browses for what to study.

Copy states what is on the table, never personifies waiting
("Lust auf neue Wörter?", not "Neue Wörter warten auf dich";
"Morgen sind %@ Karten dran", not "warten auf dich").
Every prompt, empty state, call to action and forecast line gets the same read:
where the sentence puts the learner under an expectation,
rephrase it as an availability statement or a question.

## Persistence

- One document per target language, saved after answers, at session end and on background —
  an answered review is never only in memory.
- **Calibration is re-applied from the build on every load**:
  learning steps, retention and caps are the app version's decisions,
  so a box written months ago answers to the current numbers, not its own.
  Nothing survives it — growth pacing is the engine's opinion,
  not a figure the learner tunes (`growth-evidence.md`).
- Drill records live outside the box: a drill run touches no card and no schedule,
  so losing one costs a climb, never learning history.

## Profile & onboarding

- Profile = (source, target) catalog languages;
  the catalog decides which targets a source can reach.
- Default source = device language when covered, else en.
  Either picker may hold the other side's language — picking it swaps the pair.
- A language is named by its endonym (`LanguageChoices.name`);
  pickers add the English exonym.
- Onboarding ends inside the first round, and only its last page commits
  (joins the box and opens the session).
  That first round teaches itself, one line per moment, in the quiet aside line,
  and the lines last that round only.
- Importing a backup on a first run skips onboarding:
  the backup brings its own pair and nothing is replaced, so it asks no confirmation.

## Chrome & copy

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
  What a string means is its catalog entry's `comment`.
  Where the layout differs the reader composes — the catalog holds the caption,
  each phone sets the form beside or below it.
- The watch, the complication and the iOS widget never see that catalog,
  so they carry their own (`Shared/Resources/Glance.xcstrings`, same rules)
  and resolve it in the snapshot's chrome language — never the device's.
- Area titles and area emoji both come from the catalog; the app carries no map.
  A title is a plain name usable as the produce prompt's area cue;
  the catalog's optional subtitle is the flavor line, never shown where a cue is wanted.

## Presentation model in the UI

**Practice means typing.**
Writing the word is the recall the box schedules;
revealing is the way out for three cases:
a first exposure, a recognition turn, or a learner who does not want to type.
Grade buttons live on that path only.

- The role alternates between produce and recognize.
- **Produce**: typed answer in the target language, graded by the kern normalizer.
  The reveal is the no-typing fallback, self-graded.
- **Recognize**: reveal and self-grade only — no input field.
  Three outcomes; Easy is earned by answering fast, never picked.
  Buttons name what the learner knows, never FSRS rating names.
  The recall clock runs from prompt to reveal.
  The reveal carries the source meaning plus the full synonym family.
- A card's text stays centered whatever else it carries.
  A picture's slot is held whether or not the picture is in it, so a reveal never reflows the words.
  A picture that would answer the question is withheld until the reveal (`CardEmoji.Cue`).
- Ambiguous prompts carry an area label, produce only, never graded.
- Grammar (plural, article color) renders target-side only.
- Drill cards wear the same face (`drills.md`).

## Review UX rules

These bind every surface that asks for an answer, drills included.
A new surface that asks the way an existing one does IS that component with a parameter,
never a second cut.
What licenses a second component is a parameter attempted and found not to carry.

- **A right answer's feedback is the subtlest the surface has, because it comes all the time:**
  never a buzz beyond the lightest tap, never a chime as loud as the miss's.
- **The answer is never on screen twice, and never in the field.**
  Correct → card stays closed, narrated at the field.
  Wrong → card expands onto the answer.
  Near miss → correction box under the field.
  Either way the owed form stands at a readable size with its speaker beside it.
- **Near miss runs amber** — field edge, checkmark and box agree.
  Green stays the clean answer's alone.
  Its chime is the correct one's first note alone; a merged meaning, full credit, sounds correct.
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
  A card asked by ear has no retry: its miss shows the reveal and a Next button.
- **A missed word is written out once before the session moves on.**
  Encoding only, never a grade.
  Production and first exposures ask for it; recognition misses do not.
  One write-out per miss.
- **Finishing the word IS the answer:**
  the field confirms itself when the letters line up, the card flips a beat later.
  Backing out takes the confirmation with it.
  Every step keeps a way out.
- The text field appears only where there is something to type, focused immediately.
- **Auto-advance** has two tiers, live (typed exactly right) and explicit (a check or tile tap),
  both from kern's `AdvanceTier`.
  Under VoiceOver or Switch Control no timer runs; an explicit next step stands instead.

## Counts & sessions

- Sessions are composed, never configured.
  **The plan is the whole run**: the counter on screen is a promise,
  so nothing joins mid-session unless in endless mode.
- **A round too long for the evening can be taken short:**
  due work alone, a round's worth, no first sights,
  offered only while it differs from the full round.
- **The session end raises the tree of the hardest-worked area,
  whatever the round did to the counts.**
  Only the round's own marks move; Reduce Motion draws the finished tree at once.
- **A record is named, a number is only counted.**
  A day streak at its longest says so on the finish screen;
  a drill run that beats its stored best is celebrated.
- **The finish screen reports the round, not the box:**
  one claim over the tree, one line of what the round did,
  and the streak only when it just became a record — its count is Home's.
- **Stopping is the default at round end**, and going on the secondary choice.
  A day going badly says so and says why stopping is the better call.

## App structure

- **Three peer sections**: Home, Box, Settings.
  The tab bar stands on those three and on nothing else.
- **Home** carries the day: the round, listening, the drills, the companion, the trees.
  - The line over the round carries the language being learned in words that fit the hour
    (`../kern/docs/reports.md`, `dayPart`/`partVariant`).
    Two registers: the target speaking for itself, or the known language asking about it.
    The spoken lines lead and may address the learner by name (`Greetings.addressed`).
    Every chrome line asks — never states.
    Holds through a render, moves on by the next opening.
  - Once the day has answered more than is still due, the day's card leads with ONE drill and why,
    opening what that drill's chip opens, the round a smaller button under it
    (`drills.md` § The suggestion).
  - The streak flame is one grade (`BoxStatistics.streakHealth`), merged across every language,
    and every surface that draws a flame reads it.
  - The round card names what the round is led by (due work or new-word offer).
    What it promises is what the round will hand over — the cap, never the pile.
    A day not worked is never called done;
    a day with work still returning is not either (`../kern/README.md` §6).
- **The companion**: the box briefs a chat assistant
  and reads the conversation's answer back as own words (`Briefing`, `Harvest`).
  It stands under the day's card, never in it.
  The prompt text is never shown, only the loop it runs.
  What comes back is shown whole and sorted, never filtered:
  every pair in new/near/held groups (`HarvestKind`), only the new group arrives ticked.
- **The trees**: one tree per area, catalog order.
  Answers how the box is shaped and which corners have never been opened.
  **The unit is the area, not the word.**
  **A tree is one organism its whole life** — trunk is growth, canopy is landed words,
  blossom and fruit appear on it.
  Which `GrowthStage` stands in which tier is kern's `growthByArea`;
  where each tree stands is kern's `TreesLayout`, its size and its wood kern's `AreaTree`;
  a tier is one mark — arriving a bud, growing a leaf, matured a blossom, long held fruit.
  What the round summary claims over its tree is kern's `growthHeadline`.
  A tree forks further the more words it carries, each limb continued by a lead
  with side branches turning well away from it, and a branch dipping below level grows short;
  its marks spread along its finer wood, never the trunk or first limbs, fruit and blossom on the levelest limbs,
  no two of them touching while the crown has room, and only wood carrying a shown mark is drawn —
  a bud hangs beside a grown mark, never on a twig of its own.
  A met word hangs as a bud until it settles into a leaf; merely packed hangs nothing.
  Height comes from how many words the area has met, never from catalog count.
  An unopened area is one faded seedling.
  A lapse drops leaves, never shrinks the tree.
  A suspended word gets no space.
  Accessibility: canvas hidden, each tree has tap target + spoken split,
  blossom differs from leaf in shape before color, figures spelled out beneath.
  Nothing moves.
- **Box**: browse by area, pack words, unpack, revive suspended.
  The area is the unit for packing and unpacking
  (`../kern/README.md` §6 owns the mechanics).
  - **Own content**: a reported own word appears once, among the words;
    the reports list catalog cards only.
    Clearing empties suggestions and reports and keeps word pairs;
    behind an export it needs no confirmation.
    Catalog matches offer every own word the catalog now has for a merge
    (`CatalogMatches`, `../kern/README.md` §6),
    ticked where both halves agree and left to the learner where one differs.
  - **Own words** are what a search with no answer leads to,
    known side prefilled from the query.
    One side alone is taken as a suggestion.
    A growing catalog never collides with them; a box reset never takes them.
    Rewriting keeps the id; deleting is the only deletion in the app.
- **Mid-round**, report and suspend are offered only once the answer is out
  (`TurnState.answerOut`).
  A report carries the typed answer along without asking about it,
  and reopens on the existing report.
  Suspending (`SessionIntent.SuspendCurrent`) asks no rating and shrinks the round total.

## Design language

Warm, card-centric, emoji as illustration.
Article colors: der=blue, die=berry, das=green;
two-gender languages fold onto the same two hues,
and an article marking both genders (`l'`, fr `les`) stays neutral.
The rendered article is always the one `grammar.gender` names, prepended.
Palette: stone-and-moss paper, clay headline, ocean/forest secondaries,
every pairing clearing WCAG AA in both schemes.

## Not yet

Couple mode, accounts/sync (`plans/sync.md`),
UI chrome past de/en (every other source falls back to en).
