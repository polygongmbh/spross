# Spross — app design
The app-layer rules both phones answer to: what the app composes, how an answer is asked, graded and shown, the window and the visual language; copy is `../scripts/strings.py`'s, and one screen's layout, element order, sizes, timings and inventories are the code's, not this page's.
Neighbors: the review card `review.md`, drills `drills.md`, the wrist and the tiles `watch-and-widgets.md`, audio `audio.md`, engine `../kern/README.md`.

## Persistence

- One document per target language, saved after answers, at session end and on background —
  an answered review is never only in memory.
- Calibration is the build's, never a figure the learner tunes (`snapshots.md`).

## Profile & onboarding

- Onboarding ends inside the first round, with no way back once its last page commits (`OnboardingPage`).
  That first round teaches itself, one line per moment, in the quiet aside line,
  and the lines last that round only.
- Onboarding copy teaches where things are and what to do there, not what the app believes:
  the tour names each stop by its own screen's name (`OnboardingTourStop`).
- Settings' import takes a word list (Anki, Quizlet, CSV) where the file is no backup,
  reviewed like the companion's way back before it goes in as own words.
- Importing a backup on a first run skips onboarding:
  the backup brings its own pair and nothing is replaced, so it asks no confirmation.

## Chrome & copy

- Chrome copy, its keys and how it speaks are `../scripts/strings.py`'s.
  Where the layout differs the reader composes — the catalog holds the caption,
  each phone sets the form beside or below it.
- **A screen reader speaks what learning needs** — prompts, answers, verdicts, corrections, controls —
  and stays silent on visual cues, counters, progress and decoration.
  A sound beside a word is an action on the word, never a stop of its own.

## Presentation model in the UI

**Practice means typing.**
Writing the word is the recall the box schedules;
revealing is the way out for four cases:
a first exposure, a recognition turn, a settled word's recalled produce turn (no field at all),
or a learner who does not want to type.
Grade buttons live on that path only.

- Which turn a card asks, and what it earns, is `review.md`'s.
- Self-grading has three outcomes; Easy is earned by answering fast, never picked.
  Buttons name what the learner knows, never FSRS rating names.
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

- How a verdict looks, sounds and feels is kern's, one table both phones read:
  the field's ink and checkmark (`FieldVerdict`), the chime and the tap (`ToneKind`),
  when the card opens (`Question.opens`) and when a beat may run (`Cue.ArmAdvance`).
- Only one hint stands on the card at a time.
- The text field is focused the moment it appears, and every step keeps a way out.

## Counts & sessions

- Sessions are composed, never configured.
  **The plan is the whole run**: the bar on screen is a promise,
  so nothing joins mid-session unless in endless mode.
- **A round too long for the evening can be taken short:**
  due work alone, a round's worth, no first sights,
  offered only while it differs from the full round.
- The summary's hero is the tree kern raises (`RoundSummary.shownTree`, `TreeRise`),
  or where none stands, one glyph springing in as the screen arrives;
  nothing else decorates it, and Reduce Motion draws the finished tree at once.
- **A record is named, a number is only counted.**
- **A finished stretch is celebrated, record or not** — confetti and the cheer, as one,
  on every round summary and wherever kern's `celebrated` says.
  On a summary screen a tap anywhere but its buttons replays both;
  a screen coming back into view does not.
- **Stopping is the default at round end**, and going on the secondary choice.
  A day going badly says so and says why stopping is the better call.

## App structure

- **Three peer sections**: Home, Box, Settings.
  The tab bar stands on those three and on nothing else.
- **Home** carries the day: the round, listening, the drills, the companion, the trees.
  - Where the day's card leads with something else (`DayLead`), it says why, and the round is a smaller button under it.
  - The streak flame is one grade (`BoxStatistics.streakHealth`), merged across every language,
    and every surface that draws a flame reads it — the 🔥 itself, worn at the opacity and saturation that grade names.
  - The round card names what the round is led by (due work or new-word offer),
    and its headline calls to act ("Ran an die Wörter!"), so it never reads like the greeting's question.
    What it promises is what the round will hand over — the cap, never the pile.
    A day not worked is never called done;
    a day with work still returning is not either (`../kern/README.md` §6).
- **The companion**: the box briefs a chat assistant
  and reads the conversation's answer back as own words (`Briefing`, `Harvest`),
  pasted or from a file (`WordListFile`); every box briefs, one no round has opened as a beginner's talk.
  It stands under the day's card, never in it.
  The prompt text is never shown, only the loop it runs.
  Of what comes back, only the new group arrives ticked (`HarvestKind`).
- **The trees**: one tree per area, catalog order.
  Answers how the box is shaped and which corners have never been opened.
  **The unit is the area, not the word.**
  How a tree grows and is drawn is kern's (`TreePicture`); merely queued hangs nothing, and a suspended word gets no space.
  Accessibility: canvas hidden, each tree has a tap target speaking its area name,
  blossom differs from leaf in shape before color, figures spelled out beneath for the eye.
  Nothing moves.
- **Box**: browse by area, queue words, unqueue, revive suspended.
  The area is the unit for queuing and unqueuing
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
- **Reminders** are off until the learner turns them on in Settings:
  at their usual times (learned, `../kern/README.md` §6) or at times they set, as many a day as they choose.
  The system's permission is asked for at that switch and nowhere else;
  a reminder is a normal alert whose priority the system settings own.
- **Mid-round**, report and suspend are offered only once the answer is out
  (`TurnState.answerOut`).
  A report carries the typed answer along without asking about it,
  and reopens on the existing report.
  Suspending (`SessionIntent.SuspendCurrent`) asks no rating and shrinks the round total.
- **Android** renders THIS contract with Compose on the same engine facades, so the turn and the drill runs follow the same rules by construction, not by porting discipline.
  The hub ships there whole (`drills.md`).
  Back mirrors ✕ everywhere — inside a run the reference panel eats Back first —
  and the summary's tree rises, and confetti falls, only while the system's animations are on.

## Wide windows

- How a window splits into panes and how large its type grows are kern's (`WindowPanes`, `TypeScale`).
- **Navigation and a screen's own controls stand where the platform's guidance puts them** on a wide window (`CLAUDE.md`).
- Each of Home's panes (`HomeSection`) scrolls on its own.
- The Box puts its areas in one pane and the chosen area's words in the other.
- A sheet follows the same hinge: on a half-open book it stands in the trailing half,
  lying flat it keeps the platform's placement; an alert stays centered.
- **Only a symbol that says what it does goes into a bar** (✕, ✓, ▶, send — `BarSymbol`);
  an action that needs its words, like a summary's Chat about them and Keep practicing, stays a labeled button in the content.
- **A session's card keeps the whole width**; its close, progress and read-aloud stand where the platform puts a task's controls,
  and the status line stays over the card.

## Design language

A noun's article wears its gender's hue (`Palette.der`/`die`/`das`, `Gender`);
a noun taking both genders (l'élève) stays neutral.
Colors come from `Palette` only.
