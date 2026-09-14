# Early history — July 2026 (v0.1 → v1.1)

How Spross went from zero to v1.1 in nine days, built largely by orchestrated subagent teams.

## Starting point

A sprachposter session (July 5–11) produced verified DE–SW and DE–UK poster vocabulary
and a content-QA methodology: generate → per-language adversarial verification
(explicit anti-Russism / anti-calque / agreement lenses, review-only, corrections-with-reasons)
→ human spot-check. Every sweep found real errors — tense, false friends, Russisms,
feminine-numeral leaks — and the methodology became the permanent content pipeline.

A Lovable web prototype contributed hard-won UX findings:
typed answers beat flip-and-self-grade, wrong answers reveal inline (card stays stable),
normalize before comparison, missed cards cycle at session end, Enter advances,
correct answers auto-advance after ~800 ms.
It also prototyped procedural slot trainers (numbers, clock incl. the Swahili saa system),
couple/trainer mode (the sleeper differentiator, deferred to post-v1), and
exam-aligned Goethe-A1 topic tracks.

Product thesis: a growing digital box of cards, FSRS-scheduled, expanding only while material
sits, with native non-English pairs (DE–SW, DE–UK) as the wedge nobody else serves.
Priority call (July 16): core loop first — SRS + incremental growth + phrases.
Every screen answers "What do I do right now?" with zero ambiguity.

## v0.1 — first working version (July 17)

Kern + app built by parallel agents against `docs/design.md`
(hardened by an adversarial design review — 14 findings, 4 blockers, all folded in).

Growing box with 233 verified cards per pair from the poster dataset.
FSRS-5 scheduling (golden-vector-tested against ts-fsrs v4.7.1).
Composed sessions: due reviews with reserved growth slots, drain loop for learning steps,
recognition (4-button self-grade) and typed production with inline reveal.
Three screens: Heute, Box (areas, pack-into-box, leech revive), Fortschritt (streak, stats).
Offline, file-backed, one JSON per language pair.

## v0.2–v0.6 — rapid feature stacking (July 17)

All shipped the same day, in per-feature commits:

- **v0.2**: typed-first both directions, Extra-Runde (cards bypass the daily budget),
  normalizer into kern, hyphen/apostrophe-insensitive matching.
- **v0.3**: slot trainers (numbers/years/clock in de/sw/uk, golden-vectored against
  the prototype's own JS), word-of-the-moment widget (App Group, 15-min rotation).
- **v0.4**: watchOS companion (snapshots down / answer events up over WatchConnectivity,
  phone reschedules with real timestamps), facegen CLI, phrase-slot templates
  ("Der Zug fährt um {slot} Uhr ab." → sentence drills with generated values).
- **v0.5**: mixed-direction practice (ONE memory per card, direction alternates per review),
  typo tolerance (Damerau-Levenshtein ~10%, min word length 5), endless drills with streaks,
  review-feel polish (instant keyboard, card flip, sounds+haptics, morphing button).
- **v0.6**: user's own restructure — single-screen Heute root, Box pushed,
  Fortschritt inlined. Answer-colored progress bar. All content packs merged
  (343 cards/pair); `BoxEngine.reconcileSeed` lets existing boxes absorb updates.

## v0.7–v0.12 — polish and content (July 17–19)

- Adaptive drills: numbers ramp by digit count, clock from full hours to five-minute forms,
  drill counter reads richtig/gesamt, hints on new place values.
- Widgets: 2×2, 4×2, 4×4 sizes, lock-screen inline widget, rotation biased toward attention-worthy cards.
- App chrome localized (de/en), interface follows the language you already know.
- Wrong article counts as typo not miss, Swahili verbs accepted without ku- prefix.
- Card layout tightened, first exposure always shows the unknown word, glosses moved to reveal notes.
- Numbers drill extended to billions in all three languages.

## Flutter port (July 18) — transient

A Fable orchestrator + 9 subagent runs ported the full app to Flutter/Dart.
Pure-Dart kern mirrored DuoKern 1:1 (145 tests, FSRS golden vectors byte-identical,
card IDs pinned so a Swift-written box loads in Dart).
The Swift app stayed canonical; the Flutter port was later superseded by the KMP rewrite.

## Catalog v2 and KMP rewrite (July 18–22)

Two coupled moves landed together on branch `content-catalog-v2`:

**Catalog v2**: factored concepts + per-language realizations, now in-repo at `catalog/`
with its format spec (`catalog/README.md`).

**Full engine rewrite in Kotlin Multiplatform**: SprossKern (`net.spross.kern`),
jvm + iOS targets, 251 jvmTest as the fast gate.
FSRS-6 replaced FSRS-5 (21 weights, decay learnable; golden vectors from
ts-fsrs v5.4.1 / py-fsrs v6.3.1 with provenance).
Engine contract: `kern/README.md`.
Swift DuoKern/DuoKernTrainer deleted.

Presentation-model rulings (user, July 22):

1. **One schedule per card** — production/recognition alternate on one memory;
   per-role scheduling dropped.
2. **Recognition is reveal + self-grade, never typed** — phrases alternate too.
3. **No in-session lapse retry** — a lapsed card returns next session (breadth over depth).
4. **`variantOf` dropped** — near-duplicate phrase twins unified onto base slugs;
   synonyms/variants became a display-vs-accept distinction.

First exposure is always recognition with emoji (teaching moment),
second review always production. Switching source language keeps every schedule.

iOS wiring: app renamed **Spross** (`net.spross.app`, `Spross.xcodeproj`).
App target links SprossKern via a pre-build Gradle phase (`scripts/build-kern.sh`).
Watch + widgets are decode-only Swift over phone-built snapshots (no Kotlin on watch).

## Android (July 24–25)

`:android` module — Jetpack Compose on the same SprossKern engine.
KMP `androidLibrary` target (AGP 9.3.0, compileSdk 36 / minSdk 26).
Core loop shipped: onboarding, Heute, alternating sessions, extra round, endless, summary.
Box browse, trainers, and widgets remained iOS-only.

## v1.0 release (July 25)

Three adversarial audits (engine vs contract, app vs design.md, release readiness) surfaced
and fixed real flaws: "eins Uhr"→"ein Uhr" apocope, drills accepting wrong articles,
mangled typo corrections, missing first-exposure flip, typo budget bridging long number words.

Kern module flattened to `kern/`, engine contract recast as `kern/README.md`,
`docs/backlog.md` created as the out-of-scope-discovery home.
Content at 358 concepts, 352–356 cards per German-source pair.

Key user-facing changes shipping for the first time in 1.0:
learn any language pair from the catalog, the watch became a single multiple-choice loop
that feeds the schedule (faster answers score higher), and "Pack in die Box" enrolls areas
at the normal drip rate instead of dumping dozens.

## v1.1 — the first real polish pass (July 25)

> The progress line counts what has taken root: the retention percentage — a prediction
> that sat in the same narrow band whatever you did — is gone, replaced by the
> gefestigt/frisch split. Struggling words now count in the picture instead of falling out.

> Onboarding speaks your language: the first-run sheet opens in your device language
> and follows every tap.

> The reveal's small print can actually be read: plural, alternates, literal gloss and
> typo correction were set too small and too faint. They are now sized to be read.
> The palette moved with them, re-grounded on the growing box, and every color
> carries enough contrast for both light and dark.

> A new word asks before it teaches: the first exposure waits behind "Aufdecken"
> with the emoji as cue, so a word you already know can be recalled.

Content grew by half: every area gained its missing everyday words (the kitchen got food,
the doctor got body parts, the office got Pass/Bescheid/Gebühr), a new
"Das Wichtigste im Alltag" area opened with bedrock vocabulary,
phrases wait for their component words, and near-duplicates were merged.

Swahili nouns now carry their plurals (kiti/viti, mlango/milango),
Ukrainian shows the plurals regular declension doesn't hand you (ніж→ножі, день→дні),
and homonym prompts show the area label so there's one right answer.

Reorganizing content no longer costs progress — card IDs changed one last time
to make area moves schedule-preserving (pre-production, so existing boxes start fresh).

## Gotchas that cost time once

- xcodegen: new files need `xcodegen generate`.
- Extension bundle IDs must be prefixed by the host app ID.
- Parallel agents building for the simulator: use distinct `-derivedDataPath` per agent.
- `git reset --soft` keeps everything staged — unstage before selective recommits.
- The kern never reads the clock (tests depend on injected `nowEpochMillis`/`tzId`).
- The real catalog is linted by `CatalogLintTest` on every jvmTest run,
  so catalog format breaks fail the fast gate, not the app build.
