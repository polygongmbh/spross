# Where a rule lives, and how it is written

`CLAUDE.md`, a `docs/` page and a gate all carry rules,
and which one a rule lands in decides whether it holds.
`CLAUDE.md` is the index every session pays for, `docs/` is read when its topic comes up,
and a check runs whether or not anybody read anything.

## A checkable rule earns a check, not another sentence

The repo ran the A/B without meaning to.
The String Catalog's layout rule is backed by `scripts/hooks/pre-commit` and holds;
the `catalog/` layout rule lived only in `CLAUDE.md`,
and 78% of catalog commits shipped off it until the same hook grew that check
(2026-08-21, `e232842a`).
Card parity went the same way: nine divergences shipped and were corrected again,
eight of them across a session boundary, before `scripts/card-parity.py` ran on every commit.

Prose is advisory to an agent that never re-reads it mid-task; a hook runs every time.
So when proposing an invariant, ask first whether it is checkable, and write the check.
Keep the sentence too — the gate says what failed, the sentence says why —
and give the check a `--fix` and a per-line waiver
(`// layer-ok: <reason>`, `// card-parity: <why>`),
so it is never the reason a commit is hard.

The class that most needs a gate is the one that never shows up as a red or a correction:
whether code was PUT in the right place surfaces weeks later as a consolidation commit,
not as a failed search. `LayerBoundaryTest` is that class made checkable.

## CLAUDE.md states the rule and stops

One terse line per rule, matching its neighbours:
no inline examples, no before/after pairs, no "see X" asides.
It is loaded into every session, so every line is a tax paid on every task,
while a `docs/` page costs nothing until its topic comes up.
A bullet that needs a second line to be understood is a bullet that belongs somewhere else.

Architecture never earns those lines — module maps, folder inventories, "what lives where" —
not even compressed to one row per module.
A compressed restatement is a LOSSY duplicate:
each row says less than the owning doc's own paragraph on the same rule,
so an agent trusting the short version gets the weaker fact,
and `rg` or the module doc answers the question faster anyway.
Ask what changes an agent's behavior on EVERY edit (dependency direction, hard invariants)
against what it can look up when it gets there (where a type lives);
only the first earns default context.

Before claiming a fact has no other home, grep the candidate doc's BODY, not its headings:
`kern/README.md`'s section titles said the module map was unique, and were wrong on every row.
After deleting a section, grep for what pointed at it —
"see Architecture below" outlived the section it named.

## Docs hold what is true now; git holds what changed

A doc records the rule that holds, never the story of how it got there.
Completed migrations, port inventories, "ported 1:1" framing,
comparisons to a superseded version and test-suite changelogs get DELETED,
not moved to an archive doc: an archive is still a doc that gets loaded, skimmed and drifts,
while git already answers "what changed" precisely and for free.

The sharp line is whether git can actually find it.
Code that was BUILT and later removed leaves a diff, so cut the doc entry —
`git log -S'<symbol>' -- '*.kt'` finds it.
A design REJECTED before it was written leaves no diff, so it stays;
that is the cut behind kern's "Rejected designs", which kept `homonymOf`/`disambiguator`
(zero code commits) and dropped `maxUnsettled`/`dueSoftCap`/`variantOf` (four to twelve each).
Keep "X used to do Y, and it was wrong because Z" wherever Z is what stops someone re-adding X:
that is a live constraint wearing a past tense, not a history record.

## A narrow rule is provisional until the user rules on it again

Most hyper-specific lines in `docs/` were written to pin one bug's fix in place.
They read as standing law to a later reader,
and their author does not remember writing half of them —
"those are not load bearing anymore I honestly dont know why we have such oddly particular
constraints", on `design.md`'s "each delta is its own tile's news"
and `roadmap.md`'s "no streak gamification beyond a simple counter".

So when a documented rule would block or contort a proposed design, do not quietly design around it:
name the rule and where it came from in one sentence, propose the design that ignores it,
and let the user rule.
When one is released, rewrite or strike it in the same series
rather than leaving the next reader to hit it again.
