# Watch answers travel in a batch, not one push per tap

## Context
The pair-switch fix is committed (`cff5ac662`).
Remaining concern: every watch answer costs the phone a snapshot build.
Chain today: `WatchModel.swift:199` → `connectivity.send` (one `transferUserInfo` per answer) → phone `applyWatchAnswers` → `save(.changed)` = `BOX_AND_SNAPSHOTS` → `pushWatchSnapshot` (ranks the whole box).

The phone side is already as intended — no change there:
- a phone run saves per answer as `SaveScope.BOX` (`SessionRun.kt:100,124`), snapshots only at the run's end (`:205`);
- leaving the app saves with `BoxChange.leaving` and pushes (`AppModel+Persistence.swift:12-15`, `SprossApp.swift:34`).

## Change (watch only)
The watch holds its answer events and sends them in one `transferUserInfo` when the app leaves the foreground or a round ends.
`WatchAnswerEvent.userInfo(events:)` and the phone's `decode` / `applyWatchAnswers` already take arrays, so one batch = one apply = one push.

- `Watch/Sources/WatchConnectivityClient.swift`
  - `send(_:)` appends to a pending list persisted in `UserDefaults` (stored as `userInfoEntry` dicts, decoded with `WatchAnswerEvent.decode`) so a killed app loses nothing; an `NSLock` guards it (the WC delegate callbacks run off the main thread).
  - new `flush()`: if pending is non-empty, `transferUserInfo(WatchAnswerEvent.userInfo(events: pending))`, then clear. Called from `activationDidCompleteWith` as well, to ship leftovers of a killed run.
  - new `discardPending()`.
  - update the file header comment (queues → batches).
- `Watch/Sources/SprossWatchApp.swift`: `@Environment(\.scenePhase)`; `.onChange` → `model.connectivity.flush()` when the phase is not `.active`.
- `Watch/Sources/WatchModel.swift`
  - `endSession()` (`:230`) calls `connectivity.flush()`, so a finished round reaches the phone while the app stays open.
  - `receiveSnapshot` pair-change branch also calls `connectivity.discardPending()`: unsent old-pair answers must not be applied to the new pair's box (card ids are shared across pairs).
  - doc comment at `:9` ("queued to the phone") stays true; no change.
- No schema, phone, or kern change. `PhoneConnectivity.swift:8` comment already says "receives decoded answer events".

## Not changing
`pushWatchSnapshot` call sites and `SaveScope`; an answer reaches the phone only when the watch backgrounds or a round ends — by design.

## Verification
- `xcodebuild -project Spross.xcodeproj -scheme Spross -destination 'platform=iOS Simulator,name=iPhone 17' build` (embeds the watch target). It is currently red in `DrillPauseView.swift` from someone else's in-flight `DrillPacing` edit — if still red, confirm no error is in `Watch/` or `Shared/` and say so.
- Kotlin untouched → no `jvmTest`. No UI added → no screenshot.
- Manual on paired simulators: answer several cards on the watch with the app foregrounded, phone Box unchanged; background the watch app, phone applies all of them once; finish a round, phone updates without backgrounding; kill the watch app mid-round, relaunch, the leftovers arrive.
- Commit `enhance(watch): answers go to the phone in one batch when the watch app leaves the foreground`, `--only` the three Watch files.
