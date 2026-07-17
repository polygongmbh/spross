# DuoLernen — v1 design

The product thesis and phase plan live in `../../docs/roadmap.md` (repo-external, project-level).
This doc is the build contract for v1: architecture, module APIs, and behavioral rules.
The Lovable prototype and its brief are inspiration only; this is a fresh base.

## North star

Every screen answers "What do I do right now?" with zero ambiguity.
The app composes the work; the user never browses for what to study.

## Architecture

Two layers, strict dependency direction (App → Kern):

```
Kern/    Swift package "DuoKern" — pure logic, zero UI/IO deps, fully unit-tested
  FSRS/     scheduler (FSRS-5 port)
  Model/    domain types (Types.swift, owned by root, changes require coordination)
  Content/  seed JSON importer + phrase→word linking
  Box/      growth engine (what enters the box, when)
  Session/  session composer (what today's session contains)
App/     SwiftUI iOS app — views, persistence (file-backed store actor), app lifecycle
```

- Kern is platform-agnostic (`swift test` on macOS is the fast gate).
- **Time discipline**: Kern never calls `Date()`/`Calendar.current`. Every engine API takes
  `now: Date` and `calendar: Calendar` from the caller. Day key = `calendar.startOfDay(for: now)`
  formatted `yyyy-MM-dd` in that calendar. A card is due iff `due <= now`.
- Persistence is a file-backed JSON store actor in App (atomic write,
  **one `BoxState` document per LanguagePair**, path keyed by pair).
  Save cadence: debounced after answers (≥5 s), at session end, and on scene-background.
  No SwiftData/CoreData in v1 — testability and zero migration pain beat ORM comfort. // why: personal-scale data (<10k cards) loads in ms; swapping later is behind StoreProtocol.
- Swift 6 strict concurrency; all Kern types Sendable.

## Domain model (Model/Types.swift — already written, build against it)

- `Card`: id, kind (noun/verb/phrase), German side (headword, article?, plural?, emoji?),
  a single translation string (the card's pair determines the language), optional note (literal gloss),
  area, componentIDs (phrase → word cards).
- `CardScheduling`: phase (new/learning/review/relearning), MemoryState (stability, difficulty),
  due date, lapses, suspended flag, review log. Attached per-direction (forward/reverse are independent).
  Invariant: `phase == .new ⟺ memory == nil ⟺ due == nil`.
- `LanguagePair`: `de-sw`, `de-uk`. Direction: `.deToTarget` / `.targetToDe` — card model is direction-agnostic.
- `BoxState`: the persisted aggregate for ONE pair — cards, scheduling, config, `dailyStats`.
  All engine computations are scoped to `config.direction`'s scheduling entries.
  Direction switch: cards reviewed in the old direction re-enter the new direction
  through the normal new-card budget (it *is* new learning); old-direction state is kept untouched.
  `newIntroduced` is pruned to the trailing 60 days; `dailyStats` (reviews, introduced, active count)
  is appended at session end and is the only input to streak/Fortschritt (never scan logs at render).

## FSRS module (Kern/FSRS)

- Port **FSRS-5** from the open-spaced-repetition algorithm spec (fetch the official
  algorithm wiki + a reference implementation, e.g. ts-fsrs or py-fsrs, for golden test vectors).
- API: `struct FSRS` — `initialState(rating:) -> MemoryState` (first review, w₀–w₅ path),
  `nextState(state:elapsedDays:rating:) -> MemoryState` (elapsed < 1 day takes the short-term path),
  `retrievability(state:elapsedDays:) -> Double`, `nextIntervalDays(stability:desiredRetention:) -> Double`,
  `nextPhase(current:rating:) -> CardPhase`.
- First answer of a `.new` card calls `initialState`; all later answers call `nextState` with
  `elapsedDays` computed from the last log entry's date (never from `due`).
- Default parameters = published FSRS-5 defaults; desired retention default 0.9.
  Changing `desiredRetention` (or any config) applies at each card's next review — no bulk re-dating.
- Learning steps for new cards (e.g. 1 min, 10 min) handled by the scheduler's short-term memory
  path per the spec; no separate hand-rolled learning queue.
- Tests: golden vectors from the reference implementation (fetched, not invented),
  plus property tests (stability monotonicity on Good, difficulty bounds, retrievability ∈ (0,1]).

## Box engine (Kern/Box) — the growth rules

The box grows only while material sits:

1. Daily new-card budget: default 5/day (configurable 0–20). Unlocked phrases CONSUME this
   budget (they are new cards), ordered ahead of new words; at most `newPerDay` introductions
   of any kind per day.
2. Health gate: introduce new cards only if
   (a) projected post-session backlog `dueCount − min(dueCount, sessionCap) < dueSoftCap`
   (evaluated at composition time), and
   (b) share of active (non-suspended) cards in relearning < 20% — this sub-gate applies
   only once active count ≥ 10 (else it passes; day-one bootstrap).
3. **Phrase unlock** (evaluated per `config.direction`): a phrase becomes eligible only when
   ALL its componentIDs have scheduling in `.review` phase with stability ≥
   `phraseUnlockStability` (default 3 days). A component with no scheduling, or suspended,
   counts as not-stable. Phrases with ZERO resolved components follow normal seed order —
   never the unlock fast path.
4. User agency: user can enqueue a topic pack or a single word ("add to box") —
   enqueued items respect the health gate but take priority over automatic ordering.
   Enqueuing a phrase's area auto-prioritizes its missing component words.
5. Automatic ordering within an area: nouns/verbs by seed order, then phrases by unlock.
6. **Introduction = first answer**: `CardScheduling` is created and `newIntroduced[day]`
   incremented when the card is first *rated*, not when composed. Composition selects
   candidates purely functionally (same inputs → same candidates).
7. **Leeches**: `lapses ≥ 8` → auto-suspend. Suspended cards are excluded from due counts,
   relearning share, and sessions; surfaced in the Box screen for manual revive.

## Session composer (Kern/Session)

A session is composed, never configured:

1. Due reviews (oldest due first), capped at `sessionCap − min(newBudgetRemaining, 5)` —
   slots are reserved so a full due queue can't starve growth; unlocked phrases fill
   reserved slots first, then new cards per the box engine.
2. **Drain loop**: after the composed queue empties, keep pulling any card with
   `due <= now` (learning/relearning steps land here) until none remain — only then
   is the session done. Failed cards thus cycle back naturally.
3. **Every answer event is an FSRS review** — retries and learning steps go through the
   FSRS-5 short-term path with real elapsed time; nothing is "UI-only".
4. Typed answer → Rating mapping (v1): wrong → `.again`,
   correct after reveal → `.hard`, correct → `.good`; `.easy` is unreachable via typing.
   Revealing without typing falls back to four-button self-grading (both directions).
5. **Extra round** (`composeExtraSession`): always available on demand —
   due cards, then explicitly enqueued new cards (these bypass the daily budget
   and health gate at answer time; user agency), then review-ahead by soonest due,
   capped at `sessionCap`. Automatic seed-order cards never enter an extra round;
   introductions still count into `newIntroduced`, so tomorrow's automatic budget sees them.

## Review UX rules (App) — carried over from prototype refinement, treat as spec

- Typing first in BOTH directions (recall beats recognition): input field + "Prüfen",
  with "Aufdecken" as the no-typing fallback that self-grades via Again/Hard/Good/Easy.
  Typed grading compares against the German side (production) or the translation
  (recognition; seed alternatives separated by "/" all count as correct).
- Answer normalization before comparison: lowercase, trim, strip leading article
  (de: der/die/das/ein/eine; en-style "the/to" not needed), strip punctuation, collapse whitespace.
- Wrong answer reveals **inline**, expanding the card DOWNWARD (animated) —
  existing content never moves or flips; no space is reserved pre-reveal.
- German side renders as ONE line, article inline in its color ("der Kühlschrank");
  the plural line appears only for learners OF German (it's noise otherwise).
- Typo tolerance: ~10% of letters (Damerau-Levenshtein, min word length 5, minimum 1 edit
  from 5 letters up) — diacritic slips like "Kuhlschrank" ride the same rule.
  A typo still counts as correct, but does NOT auto-advance: the typed text stays visible
  with the proper spelling and a "Weiter" tap, so the learner reviews the slip.
- A clean (exact) correct answer auto-advances after ~1.2 s (long enough to read the
  confirmed answer); Enter advances when revealed.
- "Aufdecken" fills the answer field with the correct answer (no empty box left beside it).
- Never punishing: no red flashes, streak survives one missed day.

## App structure (SwiftUI, single screen)

- **Heute** is the only root screen, top to bottom:
  session card (due-count ring + streak flame, or "done for today" + tiny preview of tomorrow),
  trainer hub, condensed Fortschritt section (14-day activity strip, active card count, retention estimate).
- **Box** (pushed via the 📦 toolbar icon): browse areas/cards, see phase/stability,
  add topic packs or single words, pair/direction settings; suspended cards surface here.

Design language: warm, card-centric, emoji as illustration (from seed data), article color coding
der=blue / die=pink-red / das=green (text carries meaning, color reinforces — colorblind-safe).

## Content pipeline

- Seed JSONs in `content/` (copied from project `data/`, schema documented in
  `../../docs/sprachposter-learnings.md`).
- Importer parses areas → Cards; phrase componentIDs resolved by normalized lemma matching
  of area words inside the phrase text (naive contains-match on normalized forms is acceptable v1;
  unresolved components = phrase depends only on resolved ones; log unresolved count).
- Importer is deterministic: same JSON → same card IDs (stable slug of pair+area+kind+headword).
  IDs never contain `|` (scheduling keys are `id|direction`); importer asserts this.

## Testing & gates

- `cd Kern && swift test` — must be green before every commit.
- App builds via XcodeGen (`project.yml`) + `xcodebuild -scheme DuoLernen -destination 'iPhone 17 Pro' build`.
- Behavior-level tests: box growth scenarios, session composition, importer round-trip, FSRS vectors.

## Not in v1

Couple mode, accounts/sync, listening/writing steps, watch app (Phase 2.5+ — but keep Kern
watch-ready: no UIKit imports, session composer can emit micro-sessions).

- Note: the daily new-card budget (`newIntroduced`) is day-keyed only — shared across directions by design.
