# DuoLernen

A personal "growing box" vocabulary app for German ↔ Swahili and German ↔ Ukrainian.
Native iOS (SwiftUI, iOS 17+), fully offline.

The box only grows while your material sits:
new cards enter on a daily budget behind a health gate,
phrases unlock once their component words are stable,
and everything is scheduled by a golden-vector-tested FSRS-5 port.
Seed content is the verified trilingual dataset from the sprachposter project
(96 nouns, 73 verbs, 64 phrases with literal glosses per pair).

## Structure

- `Kern/` — DuoKern Swift package: FSRS-5, domain model, seed importer, box engine, session composer.
  Pure logic, time injected, fully unit-tested (`swift test`).
- `App/` — SwiftUI app: design system (poster-derived theme), file-backed store, screens
  (Heute / Box / Fortschritt).
- `content/` — bundled seed JSON (copies of the project-level `data/`).
- `docs/design.md` — the build contract; read before changing behavior.

## Build

```sh
brew install xcodegen        # once
xcodegen generate
xcodebuild -project DuoLernen.xcodeproj -scheme DuoLernen \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

Tests: `cd Kern && swift test`
