# Store & snapshots
The persisted box document and the watch/widget snapshots the phone precomputes.

Engine contract: `../README.md`.

- One document per TARGET: `box-<target>.json` (schema version 2, `store/StoreDocument.kt`)
  in App Group `group.net.spross.app`. Only one language is ever active, so a save encodes and
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
  `enqueued`, `ownWords` (the document's only content, `../README.md` §6) and `reportedIssues`
  carry the rest.
  Timestamps are epoch seconds, and the engine floors every stamp it mints (`box/Time.kt`,
  `stampOf`), so a live box equals its own reloaded self.
  `BoxState.rekeyingPrefixedVerbs()` still runs on load, a temporary migration (delete at 7.0+)
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
- Engine boundary time: `nowEpochMillis: Long` + `tzId: String` (kotlinx-datetime 0.8 has
  no Swift-Date bridging; Instant/TimeZone are constructed inside). TimeZone = device-current
  per call. Day keys are ISO regardless of device calendar
  (DST + non-Gregorian vectors in the test suite).
- **WidgetSnapshot** (NEW): the phone precomputes it on the IMMEDIATE saves — session end,
  a config change, the app leaving the screen — never per answer: building it walks the
  exposure ranking, the active cards and every day the box has tallied, and the tile's worth
  is long-term exposure, which a round's staleness does not touch. A widget decodes and
  draws (it cannot run the join: no catalog in its bundle, ~30 MB extension memory cap vs
  33 MB measured Kotlin debug framework). Contents: pre-resolved exposure
  entries (target-side text, emoji, `article?`, `gender?`), per-card `{due}` for render-time
  `dueCount(now)`, the settled-card count (`allSettledCount`, resolved phone-side —
  it does not move with the clock), the strip's length (`activityWindowDays`, kern's `ACTIVITY_WINDOW_DAYS`),
  the answer counts of the activity strip's fortnight
  plus the day before it (which decides whether the strip's oldest empty day is bridged),
  `streakByDay`, `chromeLanguage`, `schemaVersion` (9).
  Built by `WidgetSnapshotBuilder.build`, written by the app.
  **Both sides of the wire are kern's answer, nowhere re-derived.**
  `streakByDay` is the streak resolved for every day a widget may render on
  (`snapshot/WidgetStreak.kt`): `{streak, health}` per ISO day,
  from the build day through the first day with no run left —
  an answer only reaches a widget through a fresh build, so the run can only age after it,
  and `StreakHealth.NoRun` holds past the last entry.
  A widget looks its render day up (a day past the end reads the last entry,
  one before the start the first), so a widget rendered days after the app last ran
  shows the streak and flame kern would show, and a run of any length arrives whole.
  `WidgetSnapshotBuilder.decode` returns a public `WidgetSnapshotView` — the rows, plus
  `dueCount`/`streak`/`streakHealth`/`activityWindow` over that lookup and that tail,
  so the Android Glance widget (which links Kotlin) reads the schema rather than guessing at it,
  and rejects anything but the current `schemaVersion`.
  The iOS extension links no Kotlin and does the same lookup in
  `Widgets/Sources/WidgetSnapshot.swift`; `health` decodes into the widget's Swift `StreakHealth`
  (`Widgets/Sources/StreakHealth.swift`): kern's cases in Swift's casing, read off the serialized case name.
  The app uses kern's `StreakHealth` itself.
- **WatchSnapshot v7**: direction/pair/`german` are gone — one entry per CARD with BOTH
  sides pre-resolved: `{cardId, sourceText, targetText, emoji?, revealEmoji?, article?, gender?,
  femMarker, due, nextRole, promptForm, distractors[], optionForm?}`
  + `chromeLanguage` + `schemaVersion`.
  The watch refuses any other version whole and waits for the phone's next push, as the widget does.
  **The wire carries only what a surface draws**: v4 dropped `accepted[]` (the full target
  family), which was shipped for a reveal the quiz does not have — the watch answers by
  picking a tile, so there is no second face to list alternates on. Should the watch ever
  grow the phone's "auch: …" line, the field comes back with the surface that reads it,
  not ahead of it.
  `distractors` (v3) are the multiple-choice tiles for that entry, picked by
  `session/MultipleChoice` and read on THIS entry's option side —
  so the watch only shuffles and cannot put the two languages in one question.
  Nothing but MEANING may separate the answer from its company, and five rules keep it so:
  same word class first (a lone verb among nouns is answerable off its `ku` alone),
  then the same `sentenceShape` (a lone question mark among full stops is answerable
  without the tile being read; the closing mark names the shape in every catalog language,
  since Spanish never writes `¿`/`¡` without its partner, and every single word is `Bare`),
  then, for an answer not yet growing, company not yet growing either
  (the one unrecognized tile among familiar words is the answer by elimination) —
  whenever such company exists, the shortlist is cut to it plus two others,
  so any three the watch draws hold one,
  then same area (four kitchen words test the kitchen),
  then shape (length gap + a heavy part-count penalty).
  All five RANK and none filters, so a thin box still fills four tiles.
  The pool is every SCHEDULED card, not the capped entry list — the cap is a wire budget,
  and a pool that small leaves a question no same-class company to keep;
  unscheduled cards stay out, since a word first met as somebody else's wrong answer
  is no longer new when it arrives. Up to ten per entry, omitted when the box has
  nothing else to offer.
  Every tile, the answer's included, stands in one of its card's forms —
  `text` and its `teaches`, rotating with that card's review count like a prompt form —
  and no bound stem stands while a real form can (`-ako` → `yako`, `lako`, …).
  Where a class marker survives the ranking anyway, the writing gives it up:
  a stem without forms loses its dash and a verb its citation prefix
  (`-husika` → `husika`, `kupika` → `pika`).
  `optionForm` is the entry's own option wherever it differs from `text`,
  which the reveal shows either way.
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
  **v6** (with widget v4) ships the gender beside the article, and the chrome language:
  - `gender` (`masculine`/`feminine`/`neuter`) is `articleGender` resolved against the TARGET
    language, and every decode-only surface tints from it, never from the article word —
    fr `le` and it `le` are one string and two genders, and only the phone knows which language wrote it.
    `article` is the word the surface prints in front of the target text.
  - `chromeLanguage` is `LanguageChoices.chromeLanguage` of the box's known language —
    the language the app's own chrome follows, which the watch, the complication and the
    iOS widget cannot ask the phone's model for.
  The cap of 60 entries (the ~60 KB `updateApplicationContext` limit) fills **due-first**,
  so a due card is never evicted by a non-due one.
  The entries it keeps ship weakest first (`Urgency.weakestFirst`):
  that order is the watch's practice lap, and the due batch and the complication follow it too.
  A second cap is a LEGIBILITY budget rather than a wire one: `MAX_TEXT_CHARS` (24) keeps a
  card off the watch entirely when any form it can render — both sides, plus the target
  `teaches` a rotated `promptForm` reaches for — runs longer than a tile in a 2×2 grid holds.
  It gates the option pool as well as the entries, from the one predicate, so a distractor
  can never overflow a tile an answer could not have. It drops ~9% of a pair's cards, all of
  them long sentences: a four-way pick between those is exposure rather than recall, and the
  phone gives exposure better, on a card with room for it.
  `make` lives phone-side; watch stays pure Swift.
