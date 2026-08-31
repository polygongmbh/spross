# What may run when

The engine recomputes every derivation of the box from scratch. No kern entry point holds
a `BoxState`, a capability flag or any other answer across calls, and none decides for
itself when to refresh one — that decision is the platform's, because only the platform
knows what moved: a mutation, a foreground, a voice arriving from Settings while the app
slept. So **where an answer is held is the platform's question**, and there is exactly one
right place to hold it.

Separately, kern never reads a clock: `nowEpochMillis` and `tzId` are parameters, which is
what makes a rule testable at a pinned moment. That is a rule about the determinism of
inputs, not about holding state; the two are easy to fuse and they protect different things.

What kern may still keep is the resolution of an input that carries its own cache key and
cannot go stale under it — `box/Time.kt`'s `zoneOf`, which holds the last time zone
resolved by name because resolving one reads the platform's zone database off disk, and
`Catalog`'s per-instance lazy indices. Those are not derivations of the box: a miss is
never wrong, only slow.

Both apps are shaped the same way: a screen reads values off the model, and the model
takes those values when something moves. A screen that asks kern a question directly is
asking it once per redraw, and SwiftUI and Compose both redraw far more often than
anything in the box changes.

## The budgets

**Per frame** — nothing that touches the box or the catalog. A body or a composable may
read a stored value, format it, and lay it out. It may not compose a round, count the
backlog, walk the cards, or ask the catalog anything.

**Per answer** — one FSRS step and the queue's own bookkeeping. Not a re-composition of
the round, not the day's report, not a serialization of the document, not a rebuild of
the grading index. The document is written by the store, off the main thread, coalesced.

**Per activation** — the catalog parse, the join, the stored document's decode, the
per-pair drill content. All of it off the main thread; a profile switch is the only time
any of it is allowed to run.

## Where derived state is taken

`AppModel.refreshStats()`, on both platforms, is the one place anything derived from the
box goes stale and the one place it is taken again. Everything that can move the box ends
there: a mutation, a language switch, a booked day, a foreground.

It holds the statistics, the growth ladder, the day's standing (`HeuteStanding` — the
offer, the day's report, what tomorrow holds, whether another round would yield
anything), the forest, the activity strip, the browser's shelves and their pack counts,
and it retires the typed-answer grader so the next turn rebuilds it against the box
standing then.

Two things sit outside it because they are not box questions:

- **Catalog content for the pair** — the country atlas, the sentence frames. Taken when
  the profile changes (`refreshTrainerContent` / `activate`).
- **What this device can say aloud** — the listening pool and the letter drill's sweep.
  Taken on every foreground, because a voice may be installed in Settings while the app
  sleeps, and off the main thread; only the voice table itself is read on it.

## Asking kern for less

Where only the SIZE of something is wanted, there is a counting entry point that does not
compose an order — `BoxEngine.dueCount` rather than `dueNow().size`. Where a screen draws
one number per area, there is one that answers for every area in a walk —
`BoxBrowser.shelfCounts` rather than `enqueueableCount` per shelf. Reach for those before
reaching for a cache.

## Compose

`remember` every kern call and every value derived from one, keyed on what actually moves
it — the box, the profile, the day. An animated value is read in the phase that uses it
(`graphicsLayer` for a transform), never in composition, or the whole calling composable
recomposes once per frame for the length of the animation.
