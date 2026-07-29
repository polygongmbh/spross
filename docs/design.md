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
- **RECOGNIZE**: reveal + self-grade ONLY (Unbekannt/Schwer/Gut/Einfach) — no input field.
  The four sit in a 2×2 pad, not a row: quality rises up and to the left, which puts the two
  opposite verdicts diagonally apart and leaves the bottom-right corner — under a resting
  thumb — to Unbekannt, the common answer at first exposure and the cheapest one to undo.
  The label says what the learner knows, not what the scheduler will do;
  the FSRS rating behind it is still Again (kern README §5).
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
- **A missed word is written out once before the session moves on.**
  A reveal followed by a single tap gives a word almost no encoding, which is how it comes
  back later and passes on being "that new one";
  missing a word that has not settled (kern README §5) asks for it to be typed, with the
  answer in view.
  Encoding only, never a grade: the rating the self-grade buttons already chose is held and
  applied unchanged, so self-grading still owns the schedule.
  Only Again takes the path — Easy/Good/Hard advance straight away, so a word already known
  still costs one tap — the copy is checked by the same typo-tolerant normalizer as a real
  answer (kern README §6), and skipping is always one tap away.
- A clean correct answer auto-advances after ~1.2 s; Enter advances when revealed.
- "Aufdecken" fills the answer field with the correct answer.
- Answer-colored progress bar: one segment per answer — green right, amber tough, brick wrong.
- Never punishing: no red flashes; streak survives one missed day.

## Counts & sessions

- Every user-facing count (due ring, "x neu", active cards, widget stats) is
  **concept-denominated** (kern README §4).
- Sessions are composed, never configured:
  plan from `BoxEngine`, drain loop, extra round, endless mode — semantics in kern README §6.
  Session end = summary with confetti and streak;
  its "gefestigt" tally counts words that crossed into settled during the run
  (kern README §5), not a phase edge — with one learning step a word reaches Review while
  its stability is still tiny.

## App structure (single screen)

- **Heute** is the only root screen:
  session card (due-count ring + streak flame, or done state),
  trainer hub, condensed Fortschritt section (14-day strip, gefestigt/frisch split).
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
