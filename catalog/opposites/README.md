# Opposite pairs
The opposites drill's pairs — which two concepts it asks for each other, and what earns a pair.
Neighbors: the concepts they name `../areas/README.md`, what the drill does with them `../../docs/drills-words.md`.

Drill-only — a pair never joins a card, so editing one never restamps a running box.
No `pairs.json`, no drill.

`pairs.json` is language-neutral, one pair per line, in no meaningful order:

```json
[ ["white", "black"],
  ["to-move-in", "to-move-out"] ]
```

- **A pair names two concepts the catalog already has** — the drill teaches no word of its own.
  A pair wanting a word the catalog lacks adds that word to its area first, in every language that has it.
- **Both sides are words of one kind** (`OppositePairLintTest`) —
  a phrase has no opposite to type, and a verb's opposite is a verb.
- **A pair is the PLAIN antonym in every language that realizes both sides.**
  A language that realizes only one side, or both alike, simply never asks it.
- **A slug may stand in several pairs** (`old` with `new` and with `young`):
  the prompt then has several right answers, and that is the point, not a slip.
  The same happens without authoring anything where a language writes two concepts alike
  (de `ausziehen` is moving out and taking off; sw `kulia` is right and crying) —
  a collision pinned in `CatalogCollisionLintTest` is fair game here.
