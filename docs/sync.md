# Sync — one box across devices

## What it has to serve

- **Two phones, one box.** An iPhone and an Android device without Play services,
  which rules out iCloud as the only path (no iCloud client exists for Android)
  and Firebase as the push channel.
- **A way to charge without paywalling the language.**
  The app, the catalog and self-hosting stay free; hosted sync is the paid convenience.
  Charging for language packs would sell the breadth the product is built on,
  and an open repo makes a content paywall forkable anyway.
- **Couple mode later.** The account a sync server mints is what a second learner
  would eventually be reachable through (`design.md` § Not yet).

## What already holds

The box was built toward this without the word being used:

- `CardScheduling.log` appends **every** answer — rating, timestamp, elapsed days —
  and is never pruned.
- `replayed()` (`kern/.../box/Answer.kt`) rebuilds the schedule a log implies,
  through the same step a live answer takes, and `ReplayTests` pins that the two agree.
  Everything but `due` is derived from the log.
- `answerDays()` (`kern/.../box/AnswerDays.kt`) counts the day tallies off the logs
  rather than keeping a counter beside them.
- `BoxBackup` (`kern/docs/snapshots.md`) is the file format already carrying every target's
  document, and the settings screens already write and read it.
- The App Group container is **device-local** — the widget and watch read it, nothing syncs it.
  A device backup carries it; that is Apple's or Google's function, not a sync path.

So the merge does not need a new history. It needs the one rule that reads the history twice.

## The merge rule

Kern owns it (`store/BoxMerge.kt`), pure, no network, no platform.
Per card id, for every schedule the two documents share:

- **Union the logs**, dropping entries equal in date and rating — the same answer
  arriving from both sides is one answer.
- **Order by date.** A single device replays in RECORDED order, because a watch answer can
  arrive dated before the one already logged (`Answer.kt`); a merged history has no single
  recorded order, so date is the only order it has.
- **Replay the union.** The FSRS state, the phase, the lapse count and the day tallies
  all fall out of it; none of them is merged directly.
- **`due`** is what the replayed log implies. Nothing mints a due outside the answer path
  today, so a stored due that disagrees with its own log does not exist yet — if one ever
  does (a postpone), the later of the two wins and the rule is written down here.
- **`suspended`** has no timestamp, so a merge cannot tell a fresh unsuspend from a stale
  suspend. Give it a `suspendedAt` when this lands (a `!` commit — it changes the stored
  shape) rather than letting "suspended wins" quietly bury an unsuspend.

Box-level, once the schedules are merged:

- `enqueued` — the local order first, then the remote ids it does not already hold.
- `ownWords` — union by `id`, the later `addedAt` winning a clash.
- `reportedIssues` — union by `cardId`, the later `reportedAt` winning.
- `lastExportAt` — the later of the two.
- the day counts — **read off the merged logs** (`answerDays`), never merged as counters,
  which would double-count every answer both sides recorded.
  `consolidatedToday` is the one counter the box still keeps, it holds today alone, and it
  is local to a device: a merge keeps the local one rather than adding the two.
- `config`, `cards`, `joinStamp` — not merged at all: the calibration is re-applied and
  the join re-derived on load.

What the tests have to say: two boxes from one origin, each answered offline, merge to the
same box whichever way round they are merged, and merging a second time changes nothing.

## The steps

1. **Merge in kern, and import that uses it.** `BoxMerge` plus its tests, then restore
   merges instead of replacing (`BoxBackup` and `kern/docs/snapshots.md` both state the
   replace rule and change with it). This already earns its keep with no server at all:
   export on the iPhone, import on the Android phone, keep both.
2. **One sync step in the app layer**, transport-agnostic: read the remote document,
   merge, write locally, push the result. Written once per platform against one kern rule.
3. **A file transport** — the backup file in a folder that Syncthing, Nextcloud or any
   WebDAV server keeps in step. Free, no server of ours, and it works on a device without
   Play services, which is the fastest honest answer to the two-phone problem.
4. **The spross.net server.** It stores opaque encrypted documents per target and never
   merges anything: the devices do. Optimistic concurrency (ETag / `If-Match`), and a
   rejected push means fetch, merge, push again. No email and no password — a random sync
   id plus a key, paired to the second device by QR, which keeps the privacy posture the
   app already has. Sync runs on foreground and at session end; there is no push channel
   without Play services and this app does not need one.
5. **iCloud last**, as an Apple-only transport (CloudKit private database) for people whose
   devices are all Apple. It costs nothing to run and serves none of the Android half,
   which is why it is not the foundation.

## Decisions before step 4

- **What is paid and what is not.** Self-hosting and the file transport stay free;
  the hosted service is the product. Expect it to sell narrowly, so a one-time supporter
  purchase alongside it is worth pricing at the same time.
- **The payment rails.** A web checkout serves the Android and direct installs.
  Apple generally requires in-app purchase for a subscription sold inside the iOS app,
  and the rules on linking out moved in 2025 and differ by region — verify them against
  the current guidelines before a paywall is built, not after.
- **The privacy policy promises no account** (`web/privacy.html`), and the first server
  changes that page and the privacy manifests with it.
- **End-to-end encryption** keeps the server out of the data, which keeps the GDPR surface
  and the breach surface small. The key never leaves the devices: losing it loses the sync,
  not the box, which is still on the phone.
- **The source license** is its own decision, with its own constraints: `source-license.md`.

Out of scope here: server-side merging, per-answer live sync, and anything that reaches
across learners.
