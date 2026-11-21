# Store & snapshots
The persisted box documents, the backup file, and the wire between the phone and the watch and widget snapshots it precomputes; each document's fields and encoding are the KDoc on `StoreDocument`, `BoxBackup`, `WidgetSnapshotBuilder` and `WatchSnapshotBuilder`.
Neighbors: the engine contract `../kern/README.md`, what the watch and widgets draw `watch-and-widgets.md`.

## The box documents

- One document per TARGET, `box-<target>.json`, in the App Group `project.yml` names (`APP_GROUP_ID`):
  only one language is ever active, so a save encodes and writes that one alone.
  kern encodes and decodes (`StoreCodec`); each platform's store only reads and writes the bytes.
- Calibration is the BUILD's: the file holds no configuration, and `StoredBox.join` applies `BoxConfig.product()` on every load.
- The backup carries boxes only;
  the name, the audio choice and the drill Sprossen are device settings and stay behind.

## The snapshots

- The phone precomputes both snapshots on the saves whose `SaveScope` carries them, never per answer;
  a surface decodes and draws, and never runs the join (no catalog in its bundle, ~30 MB extension memory cap against a 33 MB Kotlin debug framework).
- **Both sides of the wire are kern's answer, nowhere re-derived.**
  Everything clock-free is resolved phone-side;
  what ages with the clock ships raw (per-card `due`) or resolved per render day (`streakByDay`, `activityByDay`),
  so a widget rendered days after the app last ran shows the streak, flame and strip kern would show.
- Who decodes how:
  the Android Glance widget links Kotlin and reads `WidgetSnapshotBuilder.decode`'s `WidgetSnapshotView`;
  the iOS extension links no Kotlin and does the same lookup in `Widgets/Sources/WidgetSnapshot.swift`,
  decoding `health` off the serialized case name and reading the flame's grade the snapshot carries per day,
  since it cannot ask the Kotlin enum;
  the watch is pure Swift.
  Every surface refuses any other `schemaVersion` whole and waits for the phone's next push.
- **The wire carries only what a surface draws**:
  no `accepted[]` family, because the watch answers by picking a tile and has no face to list alternates on —
  should it grow the phone's "auch: …" line, the field comes back with the surface that reads it.
- **The picture ships under the key of its cue** (`../kern/README.md` §3):
  `emoji` for one the learner may see from frame one, `revealEmoji` for one seen only once a tile has been tapped,
  exactly one of them set.
  Two keys rather than one plus a flag,
  so a surface that draws `emoji` immediately — the complication does — cannot leak a reveal-side picture by forgetting the flag.
- **`gender`** is `articleGender` resolved against the TARGET language,
  and every decode-only surface tints from it, never from the article word —
  fr `le` and it `le` are one string and two genders, and only the phone knows which language wrote it.
  `chromeLanguage` is `LanguageChoices.chromeLanguage` of the box's known language,
  which the watch, the complication and the iOS widget cannot ask the phone's model for.
- **`distractors`** ship ranked and on the entry's own option side (`MultipleChoice`), so the watch only shuffles.
  The pool is every SCHEDULED card, not the capped entry list;
  unscheduled cards stay out, since a word first met as somebody else's wrong answer is no longer new when it arrives.
  The shortlist is the first thing to cut if the snapshot ever crowds the ~60 KB cap;
  shipping card ids instead of texts would recover most of it,
  at the price of making the watch resolve the option side again.
- The watch's entry cap (`ENTRY_CAP`, for `updateApplicationContext`'s ~60 KB) fills **due-first**,
  so a due card is never evicted by a non-due one,
  then **soonest-due**, so the watch counts tomorrow's cards in on its own through a night without the phone.
  The entries it keeps ship weakest first (`Urgency.weakestFirst`):
  that order is the watch's practice lap, and the due batch and the complication follow it too.
