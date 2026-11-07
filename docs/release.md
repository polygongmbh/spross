# Release — cutting a version and its changelog
How a release is cut, how its number is picked, and what earns a changelog entry and how it is worded.
Neighbors: what the tag sets off and how builds reach phones `distribution.md`.

## Cutting a release

```sh
# CHANGELOG.md: what moved for the LEARNER, under '## Unreleased'
scripts/release.sh 5.7.2             # heading, version, gates, commit, tag, push
scripts/deploy-devices.sh --launch   # your own phones — a separate build, from here
```

The script owns the mechanical half:
it renames `## Unreleased` to `## <version> — <today>` and opens a fresh one, writes `MARKETING_VERSION`, regenerates `Spross.xcodeproj` —
generated and gitignored, so it keeps stamping the old number until something regenerates it —
runs the text lints, the Kotlin gates and an unsigned simulator build, and only then commits, tags and pushes.
`--check` stops before the commit, `--no-app` drops the Xcode gate on a Linux session.
Two things stay yours: which number, and what the entries say.
Between releases a new entry lands anywhere in `## Unreleased` and existing ones stay put;
the section is reviewed once, right before the cut, against the rules below:
prune and merge, reword for the learner,
then order the bullets from what most learners meet to what fewest do.
An empty `## Unreleased` stops the script.

The number is a judgment call; `release.sh` prints commit counts as guidance.

- A major introduces a non-reversible breaking storage format change.
- A minor adds something the learner can do or learn that was not there: a new area, drill, screen or mode.
- A patch refines what was there — fixes, wording, layout, look, recordings — however many learners meet it.
- The counts are a prompt, not a rule:
  a feature reached from one button in a corner is a patch, however many `feat:` commits it took.

Every gate runs BEFORE the tag exists, because the tag is the trigger and the version:
the workflow strips the leading `v` and hands the rest to both surfaces, so nothing is bumped twice —
and a red found after the push costs the release rather than a rerun, since the publish step creates the GitHub release and will not overwrite one that already stands.
`MARKETING_VERSION` in `project.yml` is what a local build shows —
Gradle reads it too rather than keeping a second number, and `scripts/release-notes.sh` fails the run when the changelog has no section under that heading.

Android's `versionCode` is derived from the name:
`4.1.2` → `40102` so it rises on its own as long as minor and patch stay under 100.

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
- A bullet is one plain sentence about what the learner sees and hears, in their words:
  no pipeline terms (ship, take, prompt, verdict), no bold lead, no explanation clause.
