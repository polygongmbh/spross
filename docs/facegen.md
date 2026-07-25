# facegen — vocabulary cards for the Apple Watch Photos face

`tools/FaceGen` is a macOS command-line tool
that renders Spross vocabulary cards as poster-style PNG images,
sized for the Apple Watch **Photos watch face**.

## Why the Photos face

The Photos face accepts an album of up to **24 photos**
and shuffles to a different photo on **every wrist raise**.
That turns each glance at the time into a passive vocabulary exposure —
no app, no complication, no interaction.

Because the watch overlays the **time at the top** of the photo,
facegen keeps the top portion of every image empty
(default 28 % of the height, tunable via `--time-safe-top`);
the emoji sits just below that line
and the headword lands in the middle band of the screen.

## Usage

```sh
cd tools/FaceGen
swift run facegen --seed ../../content --pair de-sw --count 24 --out ~/Desktop/faces
```

Flags:

| Flag | Default | Meaning |
| --- | --- | --- |
| `--seed <dir>` | required | directory with `vocab-*.json` seed files |
| `--box <file>` | — | a persisted `BoxState` JSON; selects attention-worthy cards instead of seed order |
| `--pair de-sw\|de-uk` | `de-sw` | which pair to render from seed (ignored with `--box`) |
| `--count <n>` | 24 | number of cards (the Photos-face album cap is 24) |
| `--out <dir>` | `faces` | output directory |
| `--size WxH` | `1170x1521` | pixel size of each image |
| `--time-safe-top <p>` | `0.28` | proportion of the height kept empty for the watch's time overlay |

Without `--box`, the first `count` cards of the pair in seed order are rendered.
With `--box`, cards are ranked the way the widget ranks exposure cards:
learning/relearning-phase cards first,
then review cards by lowest stability,
deterministic tiebreak by card id —
so the album shows the words that currently need attention.

Output: `face-01.png … face-NN.png`
plus a `manifest.json` listing card ids → filenames.

## Getting the images onto the watch

1. Run the command above.
2. AirDrop the output folder to the iPhone
   (or drop it into iCloud Drive and save the images to Photos).
3. On the iPhone, put the images into a dedicated album (e.g. "Vokabeln").
4. Watch app (or long-press the watch face) → add a **Photos** face →
   choose that album.

Re-running facegen weekly (ideally with `--box` against the current box document)
and replacing the album keeps the words fresh
as the box grows and stabilities shift.

## Current limitation

Album sync is manual:
there is no automation from facegen output into the Photos album —
each refresh means re-transferring the images and updating the album by hand.
