# Parked area — `reproduction`

An area written, sourced and deliberately NOT shipped.
It sits in the catalog like any other and is absent from `areas.json` on purpose,
which `CatalogLintTest.parkedAreas` records so the gap reads as a decision rather
than a hole. The loader reads the manifest, so nothing here takes a seed index,
joins a card or reaches a box. What the format means by parking is `../../README.md`.

## Why it is parked

The words are ready; the guards around them are not.
Shipping them now means a release where they are unconditionally visible and
unconditionally spoken, which is the one state to avoid.
Two pieces have to exist first:

- **A quiet flag** — concept-level, owned by kern and read by both platforms:
  never autoplay, excluded from listening playlists, still speakable on an
  explicit tap. Nothing in the catalog can say this today
  (`docs/read-aloud.md` states that words are read aloud, full stop).
- **A settings toggle** that hides the area, default off, on both platforms.
  `BoxEngine.dequeueArea` takes cards out of the queue but the seed order still
  walks every area, so there is no mechanism to hide one yet.

A settings toggle, not an age check: Apple's Declared Age Range API needs
iOS 26 against a deployment target of 17.0, reports a *declared* range rather
than a verified one, and has no counterpart on the Android channel, which ships
through Obtainium with no store review at all. "Opt-in and off by default"
answers a reviewer better than a partial age signal does.

## Activating it

1. Add it to `catalog/areas.json` — last inside the `healthcare` group, so it
   lands several hundred cards into the seed order rather than in a beginner's
   first weeks. It needs an `emoji` there; the concepts deliberately carry none.
2. Drop `"reproduction"` from `CatalogLintTest.parkedAreas`.
3. `scripts/catalog-format.py --check` and `./gradlew :kern:jvmTest -Psweeps`.
   The content lints see this area for the first time here, so expect them to have
   something to say — that is the review, and it is the point of doing it in one step.
4. Delete this README.

## What the register rule is, and why

`text` carries the clinical word in every language and nothing else.
The crude Swahili forms the corpus also holds — `mboo`, `zubu`, `dhakari`,
`nyundo` for the penis, `kuma` for the vagina, which kaikki glosses plainly as
"cunt" — are **deliberately absent**, and they must not be added as `synonyms`:
a synonym is prompt-worthy and grades as correct, so listing one teaches it as
an equal. The same holds for German; `Scheide` is in because it is an ordinary
second word, not a coarse one.

`uume` and `uke` literally mean manhood and womanhood, so each carries a note
saying it is the respectful word — the fact a learner most needs here.

No emoji, no phrases, no audio: no honest picture exists, bare vocabulary is
easier to defend than sentences, and a recording would be read aloud by the
listening drill.

## Open before shipping

- sw `testicle` ships `pumbu`, which kaikki glosses "testicle" (pl. mapumbu).
  `korodani` appears in the ipa-dict wordlist and may be the better clinical
  register, but no vendored source glosses it — worth one native check.
- de `Vaginen` is the plural given by Duden; wikidata records no plural for
  `Vagina` at all, so it is the one form here with a single source.
