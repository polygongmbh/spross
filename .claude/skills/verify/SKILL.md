---
name: verify
description: Runtime-verify Spross iOS changes on the simulator — build, install, drive with idb taps, screenshot evidence.
---

# Verify Spross on the iOS Simulator

Build (also the commit gate):

```sh
xcodebuild -project Spross.xcodeproj -scheme Spross \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

Install + launch (app binary lands in DerivedData `Build/Products/Debug-iphonesimulator/Spross.app`):

```sh
xcrun simctl boot "iPhone 17 Pro"; open -a Simulator
xcrun simctl install booted <path>/Spross.app
xcrun simctl launch booted net.spross.app            # clean install ⇒ onboarding
xcrun simctl uninstall booted net.spross.app         # reset to clean state
```

DEBUG launch-arg hooks (read in `AppModel.start()`):
`-uitest-source de -uitest-target sw` (skip onboarding with that profile),
`-uitest-screen settings` (push Settings), `-uitest-autostart 1`.

Screenshots: `xcrun simctl io booted screenshot /tmp/x.png` (1206x2622 px = 402x874 pt on iPhone 17 Pro; divide px by 3 for tap points).

## Taps/swipes — use idb, NOT cliclick/osascript

cliclick and System Events need accessibility permission (not granted).
fb-idb drives CoreSimulator HID directly:

```sh
brew install facebook/fb/idb-companion
idb_companion --udid <UDID> --only simulator &        # prints grpc_port (e.g. 10882)
python3.13 -m venv /tmp/idbenv13 && /tmp/idbenv13/bin/pip install fb-idb
export IDB_COMPANION=localhost:<port>
/tmp/idbenv13/bin/idb ui tap <x> <y>                  # logical points
/tmp/idbenv13/bin/idb ui swipe 200 750 200 150 --duration 0.05   # flick-scroll
```

Gotcha: fb-idb breaks on python 3.14 (`asyncio.get_event_loop`) — use python@3.13.

Box documents (per-target, progress evidence) live in the shared app group:
`~/Library/Developer/CoreSimulator/Devices/<UDID>/data/Containers/Shared/AppGroup/*/box/box-<target>.json`
(`source`/`target` top-level keys carry the join stamp).

Flows worth driving: onboarding (clean install), Heute's forest
(~4 flick-swipes down from the top; a grove tap opens its area).
