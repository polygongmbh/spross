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

## Keep it quiet

`scripts/run-sim.sh --mute` (and `--shot`, which implies it) launches with
`-readAloud off`; `scripts/run-emu.sh` does the same through `--es readAloud off`.
Autoplay is silenced for that launch only — nothing is stored, so the in-app
toggle turns sound back on for whoever picks the device up, and a hand-launched
app opens with the setting the learner last chose.

Drive a session muted unless the change under test IS the audio: a produce card
that would have asked by ear falls back to its source prompt, which is the
screenshot evidence that no audio path was taken. The verdict chimes are NOT
covered — deliberately, on both platforms — but they only fire on an answer, so
a plain `--shot` run is silent either way.

## Drive with idb; reach for a flag only where it cannot

idb drives a finger. Anything a thumb could do — typing, tapping, scrolling — goes
through idb, against an element read out of `describe-all`. The `-uitest-*` flags are
for the two things no finger reaches:

- **seeding state** the UI cannot author — `-uitest-source de -uitest-target sw`
  (skip onboarding with that profile), `-uitest-streak N`, `-uitest-level N`,
  `-uitest-misses N`, `-uitest-record 1`, and the Watch's `-uitest-snapshot`;
- **instrumentation** — `-uitest-sound 1` and `-uitest-pronounce <form>` print WHICH
  branch played, which no screenshot shows.

`-uitest-screen box`, `-uitest-autostart 1`, `-uitest-reveal 1`, `-uitest-close 1` and
the rest are navigation: idb reaches them all, and the flag only saves taps. Prefer the
flag on a long path (Box settings is ~20 flick-swipes), idb on a short one.

Screenshots: `xcrun simctl io booted screenshot /tmp/x.png` (1206x2622 px = 402x874 pt on iPhone 17 Pro; divide px by 3 for tap points).

## Taps/swipes — use idb, NOT cliclick/osascript

cliclick and System Events need accessibility permission (not granted).
fb-idb drives CoreSimulator HID directly:

```sh
brew install facebook/fb/idb-companion
idb_companion --udid <UDID> --only simulator &        # prints grpc_port (e.g. 10882)
export IDB=~/.local/share/idbenv13/bin/idb           # built once; /tmp is wiped between sessions
[ -x $IDB ] || { python3.13 -m venv ~/.local/share/idbenv13 && ~/.local/share/idbenv13/bin/pip install fb-idb; }
export IDB_COMPANION=localhost:<port>
$IDB ui tap <x> <y>                                   # logical points
$IDB ui text 'hallo'                                  # types into the focused field
$IDB ui swipe 200 750 200 150 --duration 0.05         # flick-scroll
$IDB ui describe-all                                  # the whole accessibility tree
$IDB ui describe-point <x> <y>                        # one element under a point
```

`describe-all` is the one to reach for first: it returns every element as JSON with
its label, value and frame, so a run taps what it can NAME rather than a coordinate
guessed off a screenshot, and asserts on a value (`Aussprache vorlesen` = `an`/`aus`)
instead of on pixels. A tap costs ~0.2 s, `ui text` ~0.2 s.

`ui text` is how a typed answer gets in — the sim takes no host keystrokes without an
Accessibility grant. Read the prompt out of `describe-all`, then type the answer that
fits it: that ordering is why an unseeded drill RNG is no problem for a typed answer,
and why the old `-uitest-input`/`-uitest-submit` pair, which had to prefill before the
prompt was known, is gone.

Gotcha: fb-idb breaks on python 3.14 (`asyncio.get_event_loop`) — use python@3.13.

Box documents (per-target, progress evidence) live in the shared app group:
`~/Library/Developer/CoreSimulator/Devices/<UDID>/data/Containers/Shared/AppGroup/*/box/box-<target>.json`
(`source`/`target` top-level keys carry the join stamp).

Flows worth driving: onboarding (clean install), Box screen settings section
(~20 flick-swipes down from top of Box screen).
