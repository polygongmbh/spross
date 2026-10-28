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
An empty `## Unreleased` is refused rather than cut.

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

`CHANGELOG.md` is what moved for the LEARNER, curated, never a commit log.
"User-observable" is too low a bar; the rules below are stricter.

- The delta has to be in what the app DOES, not in how it looks or reads.
  Cut in one pass:
  a picker row gaining its native name, onboarding switching to the user's language, a full English localization, error messages becoming localized.
  What stays is content, scheduling and capability —
  new areas, phrases unlocking differently, progress surviving a card-id change.
  A change that only alters language, wording, layout, order, naming or a celebration's thresholds is carried by its commit message.
  The test: does the learner need to know it to use the app? If not, no entry.
  A release where nothing clears this bar still gets short entries, one line per screen the learner sees changed.
- Content earns an entry by the SHELF, not by the CARD.
  A new area, a pass across a language, a sweep that re-cuts a whole shelf is a change the learner meets;
  a single concept added, split or reworded is not,
  nor a correction scattered across cards (a batch of emoji swapped, a word's recording replaced by a clearer take),
  and stays in its commit message.
- One entry per sweep, not one per finding.
  Before adding a second bullet from the same work, ask whether the learner would call it a second change;
  if not, fold it in —
  one headline, the sharpest example or two.
- A bullet is ONE plain sentence, written for a release-notes reader, not a maintainer.
  Read it as a learner who has never seen the code:
  say what they see and hear, never the pipeline's terms (ship, take, prompt, produce, verdict).
  No bold lead, no explanation clause after it: name what the learner can now do.
  Reasoning, mechanism and examples belong in the commit message.
  An explanation earns its place only where the sentence means nothing without it.
- No raw counts, with one exception.
  A count measures the catalog rather than the learner's experience, and goes stale on the next commit.
  Write what widened and for whom.
  The exception is a major cut for accumulated growth, whose bullets summarize everything since the last one:
  there a growth figure is the point, as a percentage rather than a pair of totals.
