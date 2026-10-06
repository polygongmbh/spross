# Store & snapshots
The persisted box document, the backup file, and the watch/widget snapshots the phone precomputes.
Neighbors: the engine contract `../README.md`, what the watch and widgets draw `../../docs/surfaces.md`.

- One document per TARGET: `box-<target>.json` (schema version 2, `store/StoreDocument.kt`)
  in the App Group `project.yml` names (`APP_GROUP_ID`). Only one language is ever active, so a save encodes and
  writes that one alone; each file carries its own `schemaVersion` beside its fields.
  `source` names the known language the box was last studied under — the target names the
  file and this names the other half of the pair, so a document that travels re-opens the
  pair its progress was made in rather than whichever one the receiving device is set to;
  a file written before it carries none and the device's own setting stands.
  A card is `[dueEpochSeconds, [[answerEpochSeconds, rating], …]]` and nothing else:
  memory, phase and step are REPLAYED from that log as it decodes
  (`box/Answer.kt`, `replayed`), so the file carries only what a replay cannot give back.
  `due` is stored for exactly that reason — a retention or ladder change then moves future
  answers rather than reshuffling every pending date.
  Calibration is the BUILD's: `StoredBox.join` applies `BoxConfig.product()`, and the file
  holds no configuration at all.
  A word suspended before it was ever answered is an id in `suspended` and has no card entry;
  `queued`, `ownWords` (the document's only content, `../README.md` §6) and `reportedIssues`
  carry the rest.
  Timestamps are epoch seconds, and the engine floors every stamp it mints (`box/Time.kt`,
  `stampOf`), so a live box equals its own reloaded self.
  `BoxState.rekeyingPrefixedVerbs()` still runs on load (`BoxEngine.open`), a temporary migration (delete at 7.0+)
  moving progress stored under a bare verb slug onto the `to-` prefixed card the 2026-09-03
  ruling renamed it to.
  kotlinx.serialization; the facade encodes with **sorted keys** (deterministic bytes).
  All `@Serializable` types are `internal`; the public surface is a narrow facade
  (`encode` / `decode` / `load`) — keeps the ObjC header small (probe showed serialization
  internals otherwise flood it).
- **A v1 document converts in place** (`store/LegacyStore.kt`, `store/LegacyDocument.kt`):
  `StoreCodec.load` routes on the version the file declares, replays a v1 box's logs, keeps its
  stored `due`, and lifts the leech-era suspensions the removed 2026-09-01 rule left behind (by
  the lapse count v1 wrote, not a replayed one). The file keeps its NAME, so a conversion is a
  rewrite and never a move: the platform writes back whatever `load` reports as converted, and
  an interrupted migration simply runs again. Both files go once no device can hold a v1 box.
- **The backup** (`store/BoxBackup.kt`) — the settings' export and import file — is every
  language in ONE envelope under a single `schemaVersion`
  — `{"schemaVersion": 2, "boxes": {<target>: …}}`, sorted keys.
  Only the languages with something in them ride along (`StoredBox.hasContent`), and the
  export offers the one language on screen instead (`BoxBackup.encode`'s `only`): a box the
  learner merely opened would land as an emptiness over a real box on the other phone.
  A restore replaces every language the file carries and leaves the rest alone
  (`StoredBoxes.restoring`); one box it cannot read refuses the whole file.
  Boxes only — name, audio choice and drill Sprossen are device settings and stay behind;
  the pair does not, since each box names the known language it was studied under.
- **WidgetSnapshot** (`WidgetSnapshotBuilder`): the phone precomputes it on the saves whose `SaveScope` carries
  the snapshots — never per answer; which saves those are, and why, is that type's.
  A widget decodes and draws; it cannot run the join
  (no catalog in its bundle, ~30 MB extension memory cap against a 33 MB Kotlin debug framework).
  Everything clock-free is resolved phone-side;
  what ages with the clock ships raw (per-card `due`, the activity strip's answer counts, `streakByDay`).
  **Both sides of the wire are kern's answer, nowhere re-derived.**
  `streakByDay` (`snapshot/WidgetStreak.kt`) is the streak resolved for every day a widget may render on,
  from the build day through the first day with no run left:
  an answer only reaches a widget through a fresh build, so the run can only age after it.
  A widget looks its render day up, so one rendered days after the app last ran
  shows the streak and flame kern would show.
  `WidgetSnapshotBuilder.decode` returns a public `WidgetSnapshotView` over that lookup,
  so the Android Glance widget (which links Kotlin) reads the schema rather than guessing at it,
  and rejects anything but the current `schemaVersion`.
  The iOS extension links no Kotlin and does the same lookup in `Widgets/Sources/WidgetSnapshot.swift`,
  decoding `health` into its own Swift `StreakHealth` off the serialized case name;
  each day also carries the flame's grade for that health (`flameOpacity`, `flameSaturation`),
  which the extension cannot ask the Kotlin enum for.
- **WatchSnapshot** (`WatchSnapshotBuilder`): one entry per CARD with BOTH sides pre-resolved,
  plus `chromeLanguage` and `schemaVersion`;
  the watch refuses any other version whole and waits for the phone's next push, as the widget does.
  The phone resolves `nextRole` and the rotated `promptForm` from the log count at build time;
  presentation is the app layer's, and `make` lives phone-side — the watch stays pure Swift.
  **The wire carries only what a surface draws**:
  no `accepted[]` family, because the watch answers by picking a tile and has no face to list alternates on —
  should it grow the phone's "auch: …" line, the field comes back with the surface that reads it.
- **The picture ships under the key of its cue** (`../README.md` §3):
  `emoji` for one the learner may see from frame one, `revealEmoji` for one seen only once a tile has been tapped,
  exactly one of them set.
  Two keys rather than one plus a flag,
  so a surface that draws `emoji` immediately — the complication does — cannot leak a reveal-side picture by forgetting the flag.
- **`gender`** (`masculine`/`feminine`/`neuter`) is `articleGender` resolved against the TARGET language,
  and every decode-only surface tints from it, never from the article word —
  fr `le` and it `le` are one string and two genders, and only the phone knows which language wrote it.
  `chromeLanguage` is `LanguageChoices.chromeLanguage` of the box's known language,
  which the watch, the complication and the iOS widget cannot ask the phone's model for.
- **`distractors`** are the multiple-choice tiles for an entry, picked by `session/MultipleChoice`
  and read on THIS entry's option side, so the watch only shuffles and cannot put the two languages in one question.
  Nothing but MEANING may separate the answer from its company:
  the ranking prefers the same word class, the same `sentenceShape`,
  company as unfamiliar as the answer, the same area, then a near string shape —
  all five RANK and none filters, so a thin box still fills four tiles.
  The pool is every SCHEDULED card, not the capped entry list;
  unscheduled cards stay out, since a word first met as somebody else's wrong answer is no longer new when it arrives.
  Every tile, the answer's included, stands in one of its card's forms (`text` and its `teaches`, rotating like a prompt form),
  and no bound stem stands while a real form can (`-ako` → `yako`, `lako`, …).
  Where a class marker survives the ranking, the writing gives it up:
  a stem without forms loses its dash and a verb its citation prefix
  (`-husika` → `husika`, `kupika` → `pika`, prefixes from `languages.json` via `citationPrefixes`);
  `optionForm` is the entry's own option wherever it differs from `text`.
  The shortlist is the variety knob and the first thing to cut if the snapshot ever crowds the ~60 KB cap;
  shipping card ids instead of texts would recover most of it,
  at the price of making the watch resolve the option side again.
- The entry cap (`updateApplicationContext`'s ~60 KB) fills **due-first**,
  so a due card is never evicted by a non-due one,
  then **soonest-due**, so the watch counts tomorrow's cards in on its own through a night without the phone.
  The entries it keeps ship weakest first (`Urgency.weakestFirst`):
  that order is the watch's practice lap, and the due batch and the complication follow it too.
  A second cap is a LEGIBILITY budget: `MAX_TEXT_CHARS` keeps a card off the watch entirely
  when any form it can render runs longer than a tile in a 2×2 grid holds,
  and gates the option pool from the same predicate, so a distractor can never overflow a tile an answer could not have.
  What it drops is long sentences: a four-way pick between those is exposure rather than recall,
  and the phone gives exposure better.
