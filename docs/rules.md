# Where a rule lives, and how it is written
Which home a rule takes — `CLAUDE.md`, a `docs/` page or a gate — and the shape each is written in; the rules themselves live in those homes.
Neighbors: the rules every edit pays for `../CLAUDE.md`.

## Pick the home

- Checkable → a gate, run by the pre-commit hook or a test, with a `--fix` where it can and a per-line waiver (`// layer-ok: <reason>`).
  The sentence stays beside it: the gate says what failed, the sentence why.
- Changes behavior on every edit → one terse line in `CLAUDE.md`; anything needing a second line goes to a doc.
- Everything else → the `docs/` page owning the topic, read when the topic comes up.
- Never an agent's private memory: it reaches no teammate, CI run or other assistant; at most a pointer.

## Markdown head

Line 1 the heading, line 2 one unbroken line of what the file holds, how it is laid out and what it leaves out, line 3 `Neighbors:` and the files owning the rest.
Nothing describing the file goes below them; `scripts/doc-header.py` holds the shape, plans and the archive are exempt.

## What a doc keeps

The rule as it holds now: a removed thing leaves a diff, so its doc entry goes;
a design rejected before it was built leaves none, so it stays wherever it stops someone re-adding it.
A standing doc never links a plan; the plan links into the doc.
