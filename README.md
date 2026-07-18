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

- `Kern/` — DuoKern Swift package: FSRS-5, domain model, catalog importer, box engine, session composer.
  Pure logic, time injected, fully unit-tested (`swift test`).
- `App/` — SwiftUI app: design system (poster-derived theme), file-backed store, screens
  (Heute / Box / Fortschritt).
- `catalog/` — a relative **symlink** to the single master content catalog
  (`../data/catalog/`); bundled as the app's content resource. The app therefore
  references `../data/catalog` and is **not standalone-cloneable** on its own —
  deliberate (local-iteration-first). Schema: `../docs/content-format.md`.
- `docs/design.md` — the build contract; read before changing behavior.

## Build

```sh
brew install xcodegen        # once
xcodegen generate
xcodebuild -project DuoLernen.xcodeproj -scheme DuoLernen \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

Tests: `cd Kern && swift test`
