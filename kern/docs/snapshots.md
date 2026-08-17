# Store & snapshots

The persisted box document, and the watch/widget snapshots the phone precomputes.
Engine contract: `../README.md`.

- One document per TARGET: `box-<target>.json` in App Group `group.net.spross.app`.
  `BoxDocument { schemaVersion: 1, target, source, config, scheduling, enqueued,
  newIntroduced, dailyStats, ownWords }` — scheduling keys are card ids;
  `ownWords` is the document's only content (`../README.md` §6), defaulted so a box written before the
  learner could author any decodes as one who has authored none;
  the stored `config` is a record of the calibration a box was written under, never an input —
  `BoxState.withProductCalibration()` re-applies the build's (`../README.md` §4) to every box that loads;
  kotlinx.serialization; dates as ISO-8601 UTC strings via explicit `kotlin.time.Instant`
  serializers; facade encodes with **sorted keys** (deterministic bytes).
  All `@Serializable` types are `internal`; the public surface is a narrow facade
  (`encode/decode` — no `migrate()` until a schema v2 exists) — keeps the ObjC header
  small (probe showed serialization internals otherwise flood it).
- Engine boundary time: `nowEpochMillis: Long` + `tzId: String` (kotlinx-datetime 0.8 has
  no Swift-Date bridging; Instant/TimeZone are constructed inside). TimeZone = device-current
  per call. Day keys are ISO regardless of device calendar
  (DST + non-Gregorian vectors in the test suite).
- **WidgetSnapshot** (NEW): the phone precomputes on every persist; a widget decodes and
  draws (it cannot run the join: no catalog in its bundle, ~30 MB extension memory cap vs
  33 MB measured Kotlin debug framework). Contents: pre-resolved exposure
  entries (target-side text, emoji, article tint), per-card `{due}` for render-time
  `dueCount(now)`, the consolidated-card count (`consolidatedCount`, resolved phone-side —
  it does not move with the clock), dailyStats tail
  (~70 days) for the streak walk, `schemaVersion`. Built by a KMP `SnapshotBuilder`,
  written by the app.
  **Both sides of the wire are kern's, except the one that cannot be.**
  `WidgetSnapshotBuilder.decode` returns a public `WidgetSnapshotView` — the rows, plus
  `dueCount`/`streak`/`streakHealth`/`activityWindow` delegating to `Statistics`, so the
  Android Glance widget reads the schema rather than guessing at it, and rejects anything
  but the current `schemaVersion`. The iOS extension links no Kotlin at all, so
  `Widgets/Sources/WidgetSnapshot.swift` stays a hand-written mirror of that same
  contract — a DELIBERATE duplicate, and the only one the widget wire is allowed.
- **WatchSnapshot v5**: direction/pair/`german` are gone — one entry per CARD with BOTH
  sides pre-resolved: `{cardId, sourceText, targetText, emoji?, revealEmoji?, articleTint?,
  femMarker, due, stability, nextRole, promptForm, distractors[], optionForm?}` + `schemaVersion`.
  **The wire carries only what a surface draws**: v4 dropped `accepted[]` (the full target
  family), which was shipped for a reveal the quiz does not have — the watch answers by
  picking a tile, so there is no second face to list alternates on. Should the watch ever
  grow the phone's "auch: …" line, the field comes back with the surface that reads it,
  not ahead of it.
  `distractors` (v3) are the multiple-choice tiles for that entry, picked by
  `session/MultipleChoice` and read on THIS entry's option side —
  so the watch only shuffles and cannot put the two languages in one question.
  Nothing but MEANING may separate the answer from its company, and four rules keep it so:
  same word class first (a lone verb among nouns is answerable off its `ku` alone),
  then the same `sentenceShape` (a lone question mark among full stops is answerable
  without the tile being read; the closing mark names the shape in every catalog language,
  since Spanish never writes `¿`/`¡` without its partner, and every single word is `Bare`),
  then same area (four kitchen words test the kitchen),
  then shape (length gap + a heavy part-count penalty).
  All four RANK and none filters, so a thin box still fills four tiles.
  The pool is every SCHEDULED card, not the capped entry list — the cap is a wire budget,
  and a pool that small leaves a question no same-class company to keep;
  unscheduled cards stay out, since a word first met as somebody else's wrong answer
  is no longer new when it arrives. Up to ten per entry, omitted when the box has
  nothing else to offer.
  Where a class marker survives the ranking anyway, the writing gives it up:
  `optionForm` is the entry's own option with a bound stem's dash and a verb's
  citation prefix dropped (`-zuri` → `zuri`, `kupika` → `pika`), absent when it would
  equal the taught form, which the reveal shows either way.
  The prefixes come from `languages.json` via the builder's `citationPrefixes` —
  an empty map simply leaves every verb whole.
  The shortlist is the variety knob: three of the ten reach a question, so the
  same card keeps offering the same handful until the next push.
  It is also the first thing to cut if the snapshot ever crowds the ~60 KB cap —
  a full 60-entry de→sw snapshot measured ~18 KB, ~7 KB of it distractors (taken while
  `accepted[]` was still aboard, so v4 sits under it);
  shipping card ids instead of texts would recover most of that, at the price of
  making the watch resolve the option side again (the v2 bug's home).
  The phone resolves `nextRole` and the rotated `promptForm` from the log count at build
  time; presentation is the app layer's.
  **v5** carries the held-back picture as well: the emoji cue (`../README.md` §3) no longer decides WHETHER the
  picture ships but which KEY it ships under — `emoji` for one the learner may see from
  frame one, `revealEmoji` for one that may only be seen once a tile has been tapped. Exactly
  one is ever set. v4 omitted the second outright, on the grounds that the watch had no
  reveal face to hang it on; the graded feedback window is that face, so the picture now has
  an honest moment and no longer has to be withheld to stay honest. Two keys rather than one
  key plus a flag, so a surface that reads `emoji` and draws it immediately — the
  complication does exactly this — cannot leak a reveal-side picture by forgetting the flag.
  Ranking is **due-first** (a due card is never evicted by a non-due lower tier), then
  exposure tiers, capped at 60 entries (the ~60 KB `updateApplicationContext` limit).
  A second cap is a LEGIBILITY budget rather than a wire one: `MAX_TEXT_CHARS` (24) keeps a
  card off the watch entirely when any form it can render — both sides, plus the target
  synonyms a rotated `promptForm` reaches for — runs longer than a tile in a 2×2 grid holds.
  It gates the option pool as well as the entries, from the one predicate, so a distractor
  can never overflow a tile an answer could not have. It drops ~9% of a pair's cards, all of
  them long sentences: a four-way pick between those is exposure rather than recall, and the
  phone gives exposure better, on a card with room for it.
  `make` lives phone-side; watch stays pure Swift.
