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
- Persistence is a file-backed JSON store actor in App (atomic write, single `BoxState` document).
  No SwiftData/CoreData in v1 — testability and zero migration pain beat ORM comfort. // why: personal-scale data (<10k cards) loads in ms; swapping later is behind StoreProtocol.
- Swift 6 strict concurrency; all Kern types Sendable.

## Domain model (Model/Types.swift — already written, build against it)

- `Card`: id, kind (noun/verb/phrase), German side (headword, article?, plural?, emoji?),
  translation keyed by pair, optional note (literal gloss), area, componentIDs (phrase → word cards).
- `CardScheduling`: phase (new/learning/review/relearning), MemoryState (stability, difficulty),
  due date, review log. Attached to a card per-direction (forward/reverse are independent schedules).
- `LanguagePair`: `de-sw`, `de-uk`. Direction: `.deToTarget` / `.targetToDe` — card model is direction-agnostic.
- `BoxState`: the single persisted aggregate — active cards, scheduling, config, history.

## FSRS module (Kern/FSRS)

- Port **FSRS-5** from the open-spaced-repetition algorithm spec (fetch the official
  algorithm wiki + a reference implementation, e.g. ts-fsrs or py-fsrs, for golden test vectors).
- API: `struct FSRS: Scheduler` — `review(state:rating:elapsed:) -> MemoryState`,
  `nextDue(state:desiredRetention:) -> TimeInterval`, `retrievability(state:elapsed:) -> Double`.
- Default parameters = published FSRS-5 defaults; desired retention default 0.9 (configurable).
- Learning steps for new cards (e.g. 1 min, 10 min) handled by the scheduler's short-term memory
  path per the spec; no separate hand-rolled learning queue.
- Tests: golden vectors from the reference implementation (fetched, not invented),
  plus property tests (stability monotonicity on Good, difficulty bounds, retrievability ∈ (0,1]).

## Box engine (Kern/Box) — the growth rules

The box grows only while material sits:

1. Daily new-card budget: default 5/day (configurable 0–20).
2. Health gate: introduce new cards only if
   (a) today's due count after estimated reviews < `dueSoftCap` (default 30), and
   (b) share of active cards in relearning < 20%.
3. **Phrase unlock**: a phrase card becomes eligible only when all its componentIDs
   are in `.review` phase with stability ≥ `phraseUnlockStability` (default 3 days).
   Phrases jump the new-card queue when they unlock (composition is the payoff).
4. User agency: user can enqueue a topic pack or a single word ("add to box") —
   enqueued items respect the health gate but take priority over automatic ordering.
5. Automatic ordering within an area: nouns/verbs by seed order, then phrases by unlock.

## Session composer (Kern/Session)

A session is composed, never configured:

1. All due reviews (oldest due first), capped at `sessionCap` (default 30).
2. Newly unlocked phrases (if any budget).
3. New cards per the box engine.
4. Failed cards cycle back at session end until answered correctly (retry loop).

## Review UX rules (App) — carried over from prototype refinement, treat as spec

- Typed answer, not flip-and-self-grade, for `.targetToDe` production;
  recognition (reveal + self-grade Again/Hard/Good/Easy) for `.deToTarget` in v1.
- Answer normalization before comparison: lowercase, trim, strip leading article
  (de: der/die/das/ein/eine; en-style "the/to" not needed), strip punctuation, collapse whitespace.
- Wrong answer reveals **inline** — the card stays visually stable, never flips or jumps.
- Correct answers auto-advance after ~800 ms; Enter advances when revealed.
- Never punishing: no red flashes, streak survives one missed day.

## App structure (SwiftUI, three tabs)

- **Heute**: the composed session (or "done for today" + tiny preview of tomorrow).
- **Box**: browse areas/cards, see phase/stability, add topic packs or single words, pair/direction settings.
- **Fortschritt**: active card count over time, retention estimate, streak, per-area sitting/learning split.

Design language: warm, card-centric, emoji as illustration (from seed data), article color coding
der=blue / die=pink-red / das=green (text carries meaning, color reinforces — colorblind-safe).

## Content pipeline

- Seed JSONs in `content/` (copied from project `data/`, schema documented in
  `../../docs/sprachposter-learnings.md`).
- Importer parses areas → Cards; phrase componentIDs resolved by normalized lemma matching
  of area words inside the phrase text (naive contains-match on normalized forms is acceptable v1;
  unresolved components = phrase depends only on resolved ones; log unresolved count).
- Importer is deterministic: same JSON → same card IDs (stable hash of pair+area+headword).

## Testing & gates

- `cd Kern && swift test` — must be green before every commit.
- App builds via XcodeGen (`project.yml`) + `xcodebuild -scheme DuoLernen -destination 'iPhone 17 Pro' build`.
- Behavior-level tests: box growth scenarios, session composition, importer round-trip, FSRS vectors.

## Not in v1

Couple mode, accounts/sync, listening/writing steps, watch app (Phase 2.5+ — but keep Kern
watch-ready: no UIKit imports, session composer can emit micro-sessions).
