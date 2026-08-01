# Spross — app design (v2)

This doc is the build contract for the APP layer: screens, review UX, profile, persistence wiring.
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

## Architecture

Strict dependency direction (App → SprossKern, never the reverse):

```
kern       SprossKern — Kotlin Multiplatform engine (contract: kern/README.md)
App/       SwiftUI iOS app — views, observable AppModel, file-backed store actor
Shared/ Watch/ Widgets/ WatchWidgets/   decode-only Swift snapshot surfaces
android/   Jetpack Compose app — core loop on the same engine (§ Android below)
```

- ONLY the app target links the Kotlin framework;
  watch and widget targets are pure Swift over phone-built snapshots.
- `KernBridge.swift` is the boundary every kern type crosses, and the app hands the
  engine its clock (`nowEpochMillis` + `tzId`) rather than letting it read one.
- Persistence: a `BoxStore` actor, one document per target language, atomic writes,
  saved after answers, at session end, and when the app goes to the background —
  an answered review is never only in memory.
- **Calibration is re-applied from the build on every load**: learning steps, retention
  and caps are decisions the app version makes, so a box written months ago must not go
  on answering to the numbers that shipped with it. `maxUnsettled` survives — it is the
  one figure the learner sets themselves.
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
- **A produce miss keeps the field open for a retry, not a self-grade tap.** The reveal
  trims the field back to the words already right, so the learner finishes the word
  against the answer standing on the card. Reaching it counts as recalled-with-help;
  giving up is an honest miss.
- **A missed word is written out once before the session moves on.** A reveal followed by
  a single tap gives a word almost no encoding, which is how it comes back later and
  passes for new again; a word that has not settled is typed with the answer in view.
  Encoding only, never a grade — the rating the self-grade already chose is applied
  unchanged, so self-grading still owns the schedule.
- **Finishing the word IS the answer**, when producing and when writing out alike:
  the field confirms itself the moment the letters line up and the card flips a beat
  later, so there is **no confirm button** to reach for. Backing out of a finished word
  takes the confirmation with it. Every step keeps a way out — a step you cannot leave
  is a trap.
- A field is on screen only where there is something to type, and is focused the moment
  it is there — typing never costs a tap first.
- Progress bar: one segment per answer, colored by its outcome.
- A miss is stated where the learner is already looking;
  the streak survives a missed day, but not two in a row.
- **Words are read aloud, and a recording is only played for the word it actually says.**
  Kern matches recordings by the FORM on screen, never by concept, so a rotated synonym is
  never answered with the canonical word; anything unmatched falls to the device's own
  voice speaking exactly what stands there, and a target with neither — Swahili has no iOS
  voice at all — stays silent rather than be read in the wrong one.
- **Only the headword is ever spoken.** Article, ♀ badge, plural line and alternates are
  grammar decoration: gender is taught by the article color, and the recordings say bare
  words — speaking the article in the synthesized branch alone would make a word's
  pronunciation depend on which branch happened to answer.
- **Audio may never give the answer away**: whether a card's target may be heard is the
  engine's cue, the audio twin of the emoji cue — a recognition prompt carries the target
  from frame one and speaks at once, a produce card owes that very form and waits for the
  reveal. Both apps consume that one cue instead of each deciding for itself.
- **Autoplay fires only where the card holds the learner.** A clean correct answer flips in
  0.45–1.2 s, less than a word lasts, and a word cut off every time teaches worse than one
  not played — the tap and the next recognition of the card both say it in full. Produce
  fires wait a beat so the feedback chime is out of the way first; chimes are never ducked
  for them, and no fire ever delays a flip. One fire per card, whichever path reaches it.

| on screen | speaks? | what is said |
|---|---|---|
| recognition prompt | yes, at once | the prompted form — the rotated synonym, never the canonical word |
| recognition reveal, write-it-out step | no | already said once |
| produce answered correctly, typed or checked | no — the card is already flipping | — |
| produce typo accepted (waits for a tap) | yes, after the chime | the correction line's proper spelling |
| produce revealed — Aufdecken, wrong, other word | yes, after the chime | the bare target word |

- **Tapping a word says it again**, and says it even when reading aloud is switched off:
  a tap is a request, and mute has to stay usable as the accessibility affordance. The
  gesture is disclosed by the settings row's hint line, never by a mark on the card — the
  hit area sits on every headword whether or not it can be heard, so no card changes size
  between reviews because a synonym rotation landed on an unrecorded form.
- **Reading aloud is on by default.** It is switched at the session's top bar — constant
  chrome, so the card below never moves for it — and in the Box settings. One flag for the
  device: not per target language, and not in the box, where the product calibration would
  reset it. It governs the spoken words only; the feedback chimes are their own matter, and
  both follow the ring/silent switch.
- VoiceOver never gets autoplay talking over it. The headword is labeled with the language
  it is written in instead, so the screen reader says it in the right voice, and the replay
  is an action ON the word rather than a button around it.

## Counts & sessions

- Every user-facing count is **concept-denominated**: a word is one word to the learner,
  whichever way it is being asked.
- Sessions are composed, never configured.
  **The plan is the whole run**: the counter on screen is a promise, so nothing joins a
  session already under way — a word maturing mid-sitting waits for the summary rather
  than pushing the finish line back. Practising on is where it comes in.
  Session end is a summary that celebrates, carrying the streak and what the run settled.
- **A record is named, a number is only counted.** A day streak standing at its longest
  ever (`BoxStatistics.longestStreak`) says so on the finish screen; a drill run that beats
  its own stored best says so too, and is the only thing in a drill that earns confetti and
  the cheer — a drill can be closed a dozen times an evening, and a screen that celebrates
  every close celebrates nothing. Drill records live outside the box: a run touches no card,
  so it is not box state.

## App structure (single screen)

- **Heute** is the only root screen:
  session card (streak flame + the round's counts, or done state),
  trainer hub, condensed Fortschritt section (14-day strip, gefestigt/frisch split).
  The card names what the round is led by rather than calling everything "a session":
  due work, or an offer of new words when nothing is due.
  Copy for the second is an OFFER, never a summons —
  the words are on the table, they are not waiting on the learner.
  What the card promises is what the round will really hand over — the cap it will take,
  never the pile behind it.
  A day the learner has not worked is never called done.
  The done state and the Fortschritt tiles carry the day's own movement under the
  standing totals: totals say where the box stands, deltas say that today moved it.
- **Stopping is the default at the end of a round**: the round that was planned is done,
  so "Fertig" is the primary button and going on the quiet one below it — an earned break
  needs no arguing for, and another round is still one tap away.
  A session summary and a drill's end through the same pair of buttons, so they cannot
  drift apart on which way out is the default.
  A day that is going badly says so, and says why stopping is the better call.
- **Box** (📦 from Heute): browse the catalog by area, pack words in, revive suspended
  ones; settings live here — profile, unsettled cap, reset.
- **Trainers**: number, clock and phrase drills, registry-driven from kern, so the hub
  offers only languages with authored content. Drills grade word by word and ramp with
  the learner instead of sitting at one level.

Design language: warm, card-centric, emoji as illustration, article color coding
der=blue / die=berry / das=green — degrading to neutral for languages without gendered
articles. Palette: stone-and-moss paper, clay headline, ocean and forest as secondaries
(growing-box theme), every pairing clearing WCAG AA in both schemes.
`App/Sources/Design/Theme.swift` holds the values and the rules that keep them there.

## Android companion (core loop)

`android/` renders THIS contract with Compose — same engine facades, same review UX
rules. Platform deltas only: the catalog ships as APK assets synced from `catalog/`,
and the box is app-private and written after every answer rather than debounced.
Its scope is § Not yet.

## Watch & widgets (decode-only)

- The phone precomputes both snapshots on every persist; the surfaces decode and draw,
  and never compute what the phone could pre-resolve.
- Watch: one graded **multiple-choice** loop — the watch never types, and the options
  arrive ranked from kern so that nothing but meaning tells the answer from its company.
  No self-grading: correctness and response time derive the rating.
  Multiple choice on a keyboard-less device is a deliberate concession to the
  recall-first rule, with the latency curve compensating for it.
  Answers return as events; the phone reschedules against real timestamps and re-pushes.
- Two runs, and only one of them ends: the **due batch** is a counter that reaches its
  end and returns to the start screen by itself, while **free practice** takes the words
  closest to slipping, lap after lap, carrying the answer streak in place of a total.
  Practice has no end screen — a run the learner ends when they like has nothing to
  celebrate.

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

Couple mode, accounts/sync, sw/uk UI chrome (those sources fall back to en),
en trainer content.
Android has the core loop and ONE trainer, the letter drill (a chip on Heute — the
platform has no trainer hub) — no Box browse, other trainers, alphabet sheet, widget,
14-day strip or write-it-out step, and its settings are a language switch and an About
screen (version, read-aloud, credits — where iOS carries the read-aloud row in Box
settings).
