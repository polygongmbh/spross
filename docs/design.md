# Spross — app design (v2)

The product thesis and phase plan live in `../../docs/roadmap.md` (repo-external, project-level).
This doc is the build contract for the APP layer: screens, review UX, profile, persistence wiring.
The engine (scheduling, growth, sessions, grading, snapshots) is owned by `../kern/README.md` (the kern contract) —
link, don't restate.
Product frame: any source (known) / target (learning) language pair from the catalog;
no user-facing direction concept; progress tracked per target language.

## North star

Every screen answers "What do I do right now?" with zero ambiguity.
The app composes the work; the user never browses for what to study.

## Architecture

Strict dependency direction (App → SprossKern, never the reverse):

```
kern   SprossKern — Kotlin Multiplatform engine (contract: kern/README.md)
App/       SwiftUI iOS app — views, observable AppModel, file-backed store actor
Shared/ Watch/ Widgets/ WatchWidgets/   decode-only Swift snapshot surfaces
android/   Jetpack Compose app — core loop on the same engine (§ Android below)
```

- ONLY the app target links the Kotlin framework;
  watch/widget targets are pure Swift over phone-built snapshots (see below).
- `App/Sources/KernBridge.swift` is the boundary file:
  `Date ↔ epochMillis`, `tzId`, `Identifiable`/`Equatable` conformances, `Int32` bridging.
- **Time discipline**: every kern call passes `nowEpochMillis` + `tzId`
  (the boundary contract: kern README §7).
- Persistence: `BoxStore` actor over the kern store facade
  (document-per-target layout + schema: kern README §7).
  Atomic writes; save debounced ≥ 5 s after answers,
  immediate at session end, config mutations, and scene-background
  (which also folds partial session stats and pushes snapshots).
  **Calibration is re-applied from the build on every load**
  (`BoxState.withProductCalibration`): learning steps, retention and caps are
  decisions the app version makes, so a box written months ago must not go on
  answering to the numbers that shipped with it. `maxUnsettled` is the exception
  and survives — it is the one figure the learner sets themselves.
- Swift 6 strict concurrency.

## Profile & onboarding

- Profile = (source, target) catalog languages;
  targets come from `Catalog.availableTargets(source)` (joinability threshold: kern README §1).
- Default source = device language when covered, else en.
  Either picker may hold the other side's language — picking it swaps the pair.
- Picker rows carry the flag, the language's own name, and the English exonym
  ("🇺🇦 Українська · Ukrainian"): a flag beside an unreadable script is easy to
  mistake for a neighbouring language. Settings shows the pair as two dropdowns
  side by side; only their collapsed labels shorten to the exonym alone.
- UI chrome renders in the KNOWN language when chrome exists (de/en today), otherwise en.
  Onboarding follows the source being PICKED — device language first, re-rendering on
  each tap — so the greeting is already in the user's language.
- Chrome strings are SYMBOLIC catalog keys (`settings.source.title`), never source
  text in either language: copy edits then never detach a translation, and a new
  chrome language is additive in `Localizable.xcstrings`.
  Arguments stay in the key (`heute.session.reviews %@`) so resolution keeps running
  through `LocalizedStringKey` against the environment locale — `String(localized:)`
  would read the device language instead. They are pre-formatted at the call site
  (`\(due.formatted())`, never a bare `\(due)`): Xcode's index-based extractor writes
  `%@` for every argument, so an Int interpolation — `%lld` to the compiler — leaves the
  two disagreeing, and opening the project rewrites the catalog with dead twins.
  `extractionState: "stale"` on a key is cosmetic — the same index-based extractor misses
  keys the compiler finds (computed `LocalizedStringKey` properties, our own
  `LocalizedStringKey` parameters, `Label`/`accessibilityLabel`), and flagged keys still
  compile into every `.lproj`: clear the flags (`scripts/strings.py --fix`), never the keys.
  `scripts/strings.py --built` diffs the catalog against what the compiler actually emitted
  (after a build with `SWIFT_EMIT_LOC_STRINGS=YES`) — the check that catches real drift.
  Errors take the same path: `AppModel` reports a `LoadFailure` case, the view
  turns it into `Text`.
- Area titles and area emoji both come from the catalog (kern README §8);
  the app only picks the title for the source language and carries no emoji map of its own.

## Presentation model in the UI

Roles, synonym rotation, and emoji placement are engine rules (kern README §3);
the app renders them:

- The role of each review comes from the engine (`presentationRole`),
  alternating produce/recognize.
- **PRODUCE**: typed answer in the target language; placeholder "In ⟨target⟩ …";
  grading via the kern answer normalizer (contract: kern README §6).
  "Aufdecken" remains the no-typing fallback that self-grades.
- **RECOGNIZE**: reveal + self-grade ONLY — no input field.
  The learner reports one of three outcomes,
  and the engine turns that plus how long the recall took into the FSRS rating
  (`SelfGrading`, kern README §6) — so Easy is earned by answering fast rather than picked.
  The three run best to worst,
  so the miss ends up under a resting thumb
  and the two opposite verdicts are kept apart by the middle one.
  They are `SessionOutcome`, the same three the progress bar draws and in the same colors:
  the button pressed is the segment it produces.
  Labels name what the learner knows rather than what the scheduler will do
  (`rating.unknown` for the miss).
  The recall clock runs from the prompt appearing to "Aufdecken";
  the time spent choosing a button afterwards is thumb travel, not memory.
  The prompt shows the engine's rotated form;
  the reveal shows the source meaning plus the full synonym family ("auch: …").
  The first exposure is no exception: the word is prompted before it is taught,
  so a learner who already knows it gets the moment to recall it —
  with the emoji beside it as the cue
  (engine cue rule; the only recognition prompt that carries one from the start).
- The emoji sits in a fixed slot **beside the headword**, never above it:
  vertical space is the scarce axis (card + input + button + keyboard share one screen).
  The slot is reserved for the card's whole life and mirrored on the trailing edge,
  so the word stays centred and an `onReveal` picture fades in without moving anything —
  a stronger guarantee than the reveal's grow-downward rule, not an exception to it.
  Cards without an emoji (verbs, phrases) drop both slots and centre on the word.
- Ambiguous prompts (engine-flagged `Card.promptAmbiguous`, i.e. the target merges two
  source concepts) carry an **area label** above the headword. Produce only — on a
  recognition prompt a cue precise enough to disambiguate would reveal the answer.
  Never graded.
- Card rendering: grammar display (target-side plural line, inline article color) per kern README §2;
  prompt/answer styling is role-based (prompt neutral, reveal accent), not per-language.

## Review UX rules (spec)

- Wrong answer reveals **inline**, expanding the card DOWNWARD (animated);
  no space is reserved pre-reveal.
- **The answer is never on screen twice.** The card expands only when the word was
  not produced — a wrong answer or "Aufdecken";
  anything graded correct leaves the card closed and is narrated at the input field,
  where the learner's own attempt already stands.
- A typo counts as correct but does NOT auto-advance:
  the typed text stays visible with the proper spelling and a "Weiter" tap —
  that correction line is the only place the spelling shows.
- A wrong answer that IS another word of the catalog **names it** under the field
  ("Übrigens: … heißt …", styled like the typo correction — both explain what became
  of the answer). Such a word never earns typo credit (kern README §6), so two words
  a learner needs told apart can never grade each other correct.
- **A produce miss keeps the field open for a retry, not a self-grade tap.**
  Reveal trims the field to the whole words already right — kern
  `AnswerNormalizer.matchingPrefixWordCount` — dropping only the wrong tail, so the
  learner finishes the word against the answer standing on the card rather than
  retyping what was already correct. Completing it exactly grades **Hard** (recalled
  with the answer in view, not a clean Good); a small "Überspringen", the same escape
  hatch as the copy step below, gives up honestly as **Again**.
- **A missed word is written out once before the session moves on.**
  A reveal followed by a single tap gives a word almost no encoding, which is how it comes
  back later and passes on being "that new one";
  missing a word that has not settled (kern README §5) asks for it to be typed, with the
  answer in view.
  Encoding only, never a grade: the rating the self-grade buttons already chose is held and
  applied unchanged, so self-grading still owns the schedule.
  Only Again takes the path — Easy/Good/Hard advance straight away, so a word already known
  still costs one tap. There is **no confirm button**: the word finishing IS the action,
  so the card flips a beat (~0.45 s) after it stands complete, and nothing but the field
  and a small "Überspringen" is on screen.
- **Finishing the word IS the answer**, in the copy step and in typed production alike:
  the field turns green with its checkmark the moment the letters line up — every time,
  so a retry after a miss confirms itself the same way the first run does — and the card
  flips a beat later. Backing out of a finished word takes the green with it, so typing
  past the answer never commits it.
  That completion check is EXACT, unlike Return, which still accepts a near miss
  (kern README §6): the typo budget would fire a letter early and snatch the card away
  mid-word, and a real typo has to pause on its correction anyway.
- Submitting by hand still works: Return or "Prüfen" grades typo-tolerantly, a clean
  correct answer auto-advances after ~1.2 s, and Enter gives up (Again) once revealed —
  the retry field itself, not a button, is what confirms a completed retype.
- "Aufdecken" fills the answer field with the correct answer.
- **A field on screen is always a focused field; the keyboard follows it.**
  Keeping the keyboard up across fieldless steps was tried and does not work:
  iOS drops it for a field that is hidden, however it is hidden, so a field
  collapsed to zero height bought a keyboard that came and went on its own
  schedule and a focus request that could land on nothing.
  A field is now mounted only where there is something to type — produce before
  its blank self-grade, and the write-it-out step — and **each field claims focus
  from its own `.onAppear`**, never from the action that opened it: a request
  made in the frame before the field exists is simply dropped, which is what
  left "Unbekannt" opening the write-it-out step with the keyboard down.
  `focusAnswerField()` re-asserts once a frame later for mounts that race a card
  transition, and the card switch calls it for a field carried over unchanged
  from the previous card (no remount ⇒ no `.onAppear`).
- Answer-colored progress bar: one segment per answer — green right, amber tough, brick wrong.
- A miss is stated where the learner is already looking;
  the streak survives one missed day.

## Counts & sessions

- Every user-facing count (due ring, "x neu", active cards, widget stats) is
  **concept-denominated** (kern README §4).
- Sessions are composed, never configured:
  plan from `BoxEngine`, extra round, endless mode — semantics in kern README §6.
  **The plan is the whole run**: the "12/30" on screen is a promise, so nothing joins a
  session already under way — a word whose learning step matures mid-sitting waits for the
  summary rather than pushing the finish line back. "Weiter üben" is where it comes in,
  and only there does the denominator move.
  Session end = summary with falling confetti, an emoji burst, the cheer, and the streak
  (tap anywhere to replay all three);
  its "gefestigt" tally counts words that crossed into settled during the run
  (kern README §5), not a phase edge — with one learning step a word reaches Review while
  its stability is still tiny.

## App structure (single screen)

- **Heute** is the only root screen:
  session card (streak flame + the round's counts, or done state),
  trainer hub, condensed Fortschritt section (14-day strip, gefestigt/frisch split).
  The card names what the round is led by rather than calling everything "a session":
  due work, or an offer of new words when nothing is due.
  Copy for the second is an OFFER, never a summons —
  the words are on the table, they are not waiting on the learner.
  Each part of the round is named for what it is (due · new · pulled forward),
  the counts describe the reviews THIS run takes (the cap, not the pile),
  and due cards the cap holds back are named rather than dropped from the number.
  No progress ring: a growing box sets no daily quota, so an arc can only divide
  work done by work still queued — both climb through the day, leaving it near-full
  from the second round on and fullest exactly when a capped backlog is worst.
  A short round never reaches the screen as two cards: the kern fills it out
  (`SESSION_FLOOR_CARDS`, kern README §6) and the summary says what it pulled forward.
  A day the learner has not answered anything in never shows a closed box either —
  the same rule hands it a round — so "nothing due right now" is now the
  unreachable fallback, kept only so a state that should not occur cannot
  congratulate someone for a day they never worked.
  The done state carries the day's own gain (reviews · new · settled, from
  `TodayReport`), and the Fortschritt tiles carry today's movement under the
  standing totals: totals say where the box stands, deltas say that today moved it.
- **Stopping is the default at the end of a round**: the round that was planned is done,
  so "Fertig" is the primary button and "Weiter üben" the quiet one below it — an earned
  break needs no arguing for, and a learner who wants another round still has it one tap on.
  When `TodayReport.recallStrained` holds, the summary additionally says why stopping is
  the better call; nothing is taken away either way.
- **Box** (pushed via the 📦 toolbar icon): browse areas/cards —
  areas grouped under their areas.json groups
  (source-language titles, en fallback, manifest order; empty groups drop out);
  rows lead with the TARGET realization; phase/stability, pack-into-box,
  suspended cards surface for revive; settings (profile, unsettled cap, reset).
- **Trainers**: registry-driven from kern (registry + templates: kern README §9) —
  the hub hides languages with no trainer content (en unauthored).
  Slot drills are stateless.
  Drill answers grade through the kern normalizer in strict drill mode (kern README §6);
  accepted-with-typo pauses with the proper spelling.
  All drills ramp: two-wins-up / one-miss-down levels,
  the sentence drill drawing leveled slot values (kern README §9).

Design language: warm, card-centric, emoji as illustration, article color coding
der=blue / die=berry / das=green — degrades to neutral for languages without
gendered articles.

Palette: stone-and-moss paper, clay headline, ocean and forest as the secondaries
(growing-box theme). Every pairing clears WCAG AA in both schemes — 4.5:1 for text,
3:1 for controls — under two rules the token file states and enforces by construction:
accents are cut at ink strength (readable on their own 14 % wash, the tightest case
they face), and text drawn ON an accent fill uses `dlOnColor`, never `.white`.
Values live in `App/Sources/Design/Theme.swift`; decorative hairlines are exempt
and say so there.

## Android companion (core loop)

`android/` renders THIS contract with Compose — same engine facades,
same review UX rules; deltas only where the platform differs:

- Catalog: bundled as APK assets by a per-variant Gradle sync task from `catalog/`
  (single-source rule); read through an `AssetManager`-backed `CatalogSource`.
- Store: `box-<target>.json` in the app-private files dir (no App Group);
  persisted after every answer (atomic temp-then-rename, non-cancellable write)
  instead of iOS's debounce.
- Typed produce grading maps Exact → Good, Typo → Hard, Wrong → Again;
  "Aufdecken" self-grades — identical to the intent above.
- Not ported yet: Box browse, trainers, widget, 14-day strip, confetti/haptics,
  the write-it-out step; settings = language switch only.

## Watch & widgets (decode-only)

- The phone precomputes **WatchSnapshot v3** and **WidgetSnapshot** on every persist;
  wire formats, ranking, and caps in kern README §7.
- Watch: one graded **multiple-choice** loop (the watch never types), role-aware per card;
  the tiles are picked in kern (`session/MultipleChoice`) and shipped per entry,
  ranked so nothing but meaning tells the answer from its company — word class,
  then area, then shape — and all on the side the prompt asks for;
  the watch only shuffles, so it can't mix the two languages.
  No self-grading — correctness + response time derive the FSRS rating (`WatchGrading`).
  Multiple choice on a keyboard-less device is a deliberate concession to the phone's
  recall-first rule, with the latency curve compensating.
  Answers return as events; the phone reschedules with real timestamps and re-pushes.
- Two watch runs, one progress indicator each, in the title slot:
  the **due batch** is exactly the cards due now — a counter that reaches its end,
  then a brief burst with the tally and back to the start screen on its own;
  **free practice** takes the words closest to slipping first, lap after lap — how far a card
  has run through its own stability, not how soon it falls due in days, since forgetting
  tracks the ratio; jittered only narrowly enough to reorder words standing at the same
  point — so it has no total to count and carries the answer streak instead.
  Practice has no end screen: a run the learner ends when they like has nothing to celebrate.
- iOS widget: pure Swift decode; only the due count and the streak walk run at
  render time, everything clock-independent is pre-resolved phone-side (kern README §7).

## Content pipeline

- `catalog/` is the in-repo master (v2 format, spec in `catalog/README.md`),
  bundled as a folder resource;
  the app derives cards from the (source, target) join at load (kern README §2).
- `CatalogLintTest` guards format rules on every kern test run.
- Content changes go through verification sweeps before shipping
  (`../../docs/sprachposter-learnings.md`).

## Testing & gates

Commands and gates: `../CLAUDE.md` § Commands;
engine gates and the behavioral test inventory: kern README §§9–10.

## Not yet

Couple mode, accounts/sync, Android beyond the core loop
(Box browse / trainers / widget — see § Android companion),
sw/uk UI chrome (sources fall back to en), en trainer content.
