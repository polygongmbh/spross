# Where a rule lives, and how it is written

`CLAUDE.md`, a `docs/` page and a gate all carry rules,
and which one a rule lands in decides whether it holds.
`CLAUDE.md` is the index every session pays for,
`docs/` is read when its topic comes up,
and a check runs whether or not anybody read anything.

## A rule belongs in the repo, not in an agent's memory

An assistant's private memory store travels with one machine and one tool.
A teammate, a CI run, a fresh clone and a different assistant all see a repo
where the rule does not exist.
It is not reviewed, diffed, or versioned.

When something is worth remembering, write it into the repo --
the owning `docs/` page, or a gate if it is checkable.
Let the memory file hold at most a pointer.
What genuinely belongs there is the operator's own preferences.

## A checkable rule earns a check, not another sentence

Prose is advisory to an agent that never re-reads it mid-task; a hook runs every time.
When proposing an invariant, ask first whether it is checkable, and write the check.
Keep the sentence too -- the gate says what failed, the sentence says why --
and give the check a `--fix` and a per-line waiver
(`// layer-ok: <reason>`, `// card-parity: <why>`).

The class that most needs a gate is the one that never shows up as a red:
whether code was put in the right place surfaces weeks later as a consolidation commit.
`LayerBoundaryTest` is that class made checkable.

## CLAUDE.md states the rule and stops

One terse line per rule, matching its neighbors.
No inline examples, no before/after pairs, no "see X" asides.
It is loaded into every session, so every line is a tax paid on every task;
a `docs/` page costs nothing until its topic comes up.
A bullet that needs a second line to be understood belongs somewhere else.

Architecture never earns those lines --
module maps, folder inventories, "what lives where".
A compressed restatement is a lossy duplicate;
`rg` or the module doc answers the question faster.
Only what changes an agent's behavior on every edit
(dependency direction, hard invariants) earns default context.

## Docs hold what is true now; git holds what changed

A doc records the rule that holds, never the story of how it got there.
Completed migrations, port inventories, comparisons to a superseded version
and test-suite changelogs get deleted.
Git answers "what changed" precisely and for free.

The sharp line: code that was built and later removed leaves a diff,
so cut the doc entry -- `git log -S'<symbol>'` finds it.
A design rejected before it was written leaves no diff, so it stays.
Keep "X used to do Y, and it was wrong because Z"
wherever Z stops someone re-adding X.

## A narrow rule is provisional until the user rules on it again

Most hyper-specific lines in `docs/` were written to pin one bug's fix.
They read as standing law to a later reader.

When a documented rule would block or contort a proposed design,
name the rule and where it came from,
propose the design that ignores it, and let the user rule.
When one is released, rewrite or strike it in the same series.
