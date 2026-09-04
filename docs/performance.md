# What may run when

Cache what is expensive, and key the cache on everything the answer depends on.

Most kern entry points nonetheless recompute:
they take `nowEpochMillis` as an input, so a correct key would include it and never hit.
What knows when an answer really needs to change is the platform —
a mutation, a foreground, a booked day —
so **that is where box-derived answers are held**, keyed on the event rather than the clock.
Where an answer does not depend on the clock, kern keeps it:
`box/Time.kt`'s `zoneOf` holds the last time zone resolved by name,
because resolving one reads the platform's zone database off disk,
and `Catalog` holds lazy indices over its own immutable contents.

Two things kern does not do.
It never reads a clock — `nowEpochMillis` and `tzId` are parameters,
which is what makes a rule testable at a pinned moment.
And it never decides for itself when to refresh something whose truth is the platform's:
a voice arriving from Settings while the app slept is not an event kern can observe,
so `LetterDrillAvailability` and `ListeningPool` answer freshly every time
and the platform owns the rebuild trigger (`kern/docs/turns.md`).

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

It holds the statistics, the growth ladder, the day's standing (`HomeStanding` — the
offer, the day's report, what tomorrow holds, whether another round would yield
anything), the forest, the activity strip, the browser's shelves and their pack counts,
and it retires the typed-answer grader so the next turn rebuilds it against the box
standing then.

Two things sit outside it because they are not box questions:

- **Catalog content for the pair** — the country atlas, the sentence frames. Taken when
  the profile changes (`refreshTrainerContent` / `activate`).
- **What this device can say aloud** — the letter drill's sweep. Taken on every foreground,
  because a voice may be installed in Settings while the app sleeps; only the voice table
  itself is read on the main thread.
  Listening's own sweep is NOT among them, on either count. Dealing its playlist walks the
  whole join, and a run is the only thing that needs it, so it waits for one to be opened
  (`AppModel.startListening` / `ListeningDriver`) — and its entry card asks nothing at all,
  standing on the box holding words. There is no third state to sweep for: every catalog
  language but `en` ships several hundred recordings and `en` is spoken by every device
  there is, so a joined box with nothing sayable in it does not occur.

## Asking kern for less

Where only the SIZE of something is wanted, there is a counting entry point that does not
compose an order — `BoxEngine.dueCount` rather than `dueNow().size`. Where a screen draws
one number per area, there is one that answers for every area in a walk —
`BoxBrowser.shelfCounts` rather than `enqueueableCount` per shelf. Prefer these to caching
a more expensive answer: an answer cheap enough to just ask for is one nothing has to
remember to invalidate.

## Compose

`remember` every kern call and every value derived from one, keyed on what actually moves
it — the box, the profile, the day. An animated value is read in the phase that uses it
(`graphicsLayer` for a transform), never in composition, or the whole calling composable
recomposes once per frame for the length of the animation.
