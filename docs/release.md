# Release — cutting a version and its changelog
How a release is cut, how its number is picked, and what earns a changelog entry and how it is worded.
Neighbors: what the tag sets off and how builds reach phones `distribution.md`.

## Cutting a release

```sh
# CHANGELOG.md: what moved for the LEARNER, under '## Unreleased'
scripts/release.sh 5.7.2             # heading, version, gates, commit, tag, push
xdevice --release --all              # your own devices ($APPLE_DEVICES) — a separate build, from here
```

The script owns the mechanical half (`--help`);
two things stay yours: which number, and what the entries say.
Between releases a new entry lands anywhere in `## Unreleased` and existing ones stay put;
the section is reviewed once, right before the cut, against the rules below:
prune and merge, reword for the learner,
then order the bullets from what most learners meet to what fewest do.

The number is a judgment call; `release.sh` prints commit counts as guidance.

- A major introduces a non-reversible breaking storage format change.
- A minor adds something the learner can do or learn that was not there: a new area, drill, screen or mode.
- A patch refines what was there — fixes, wording, layout, look, recordings — however many learners meet it.
- The counts are a prompt, not a rule:
  a feature reached from one button in a corner is a patch, however many `feat:` commits it took.

## What earns a changelog entry

`CHANGELOG.md` lists what the learner can newly do or learn.
Strike every line a learner would not miss;
when the owner strikes one, sharpen this rule.

- An entry names something new: a mode, a drill, a screen, an area.
  A refinement of what was there stays in its commit message —
  a card added or corrected, a recording, wording, layout, a setting, a tolerance, a fallback.
  A fix gets a line only where the learner saw something break, such as a crash or lost progress.
  A release where nothing clears this bar gets one short line per screen the learner sees changed.
- One piece of work is one bullet.
- A version with one entry is its heading alone, the entry inlined after the date.
- A bullet is one plain sentence about what the learner sees and hears, in their words:
  no pipeline terms (ship, take, prompt, verdict), no bold lead, no explanation clause.
