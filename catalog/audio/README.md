# Audio

The bundled recordings: file format, naming and provenance fields.
What the engine does with them is `../../kern/docs/audio.md`,
when one is heard `../../docs/read-aloud.md`,
and whose they are `../../docs/audio-licensing.md`.

Bundled pronunciation recordings, one folder per language, **generated** by
`app/scripts/audio-catalog.py --packs <workspace>` — edit packs, not this directory.
`--articles` and `--calendar` rebuild only their own section of what already ships, which
is how a late pack lands without re-deriving the others from a workspace whose word mp3s a
renamed slug has already outlived.
The packs (Wikimedia Commons transcodes plus a `manifest.tsv` of provenance) are
unversioned research input; what is committed here is the shipped bytes and the
license record that has to travel with them. Both apps bundle the whole tree as it
stands (iOS folder reference, the Android catalog sync), so nothing needs registering.

```json
{ "language": "uk",
  "authors": { "Галя Раптова, Nicolas Vion": "CC BY 3.0 us", "Tabrus": "CC BY-SA 4.0" },
  "licenses": { "CC BY 3.0 us": "https://creativecommons.org/licenses/by/3.0/us/",
                "CC BY-SA 4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
                "CC BY 2.0 fr": "https://creativecommons.org/licenses/by/2.0/fr/" },
  "words": {
    "office": { "file": "office.mp3", "matches": "установа",
                "author": "Галя Раптова, Nicolas Vion",
                "source": "Uk-установа.ogg", "sha256": "ca9d…" },
    "prescription": { "file": "prescription.mp3", "matches": "рецепт",
                      "license": "CC BY 2.0 fr",
                      "author": "Галя Раптова, Nicolas Vion",
                      "source": "Uk-рецепт.ogg", "sha256": "91d9…" } },
  "letters": {
    "ж": { "file": "letters/u0436.mp3",
           "author": "Tabrus", "source": "Жж – ukrainian.ogg", "sha256": "77b0…",
           "gain": 20.0, "lead": 1069 } },
  "articles": {
    "address": { "file": "articles/address.mp3", "matches": "die Adresse", "word": "Adresse",
                 "author": "Natschoba",
                 "source": "LL-Q188 (deu)-Natschoba-die Adresse.wav", "sha256": "a15c…",
                 "gain": 8.0, "gainPhone": 3.9, "lead": 240 } },
  "calendar": {
    "Montag": { "file": "calendar/montag.mp3", "matches": "Montag",
                "author": "Jeuwre", "source": "De-Montag.ogg", "sha256": "fa2d…",
                "gain": -5.4, "gainPhone": -4.1, "lead": 439 } } }
```

- `language` must equal the folder name, and a folder for a language `languages.json`
  does not declare is never read — adding one is dropping a directory in, nothing else.
- `authors` maps every credited speaker to the license they record under, `licenses`
  every license the pack uses to its deed URL — `null` for `Public domain`, the one
  license with nothing to point a reader at. Provenance is authored ONCE per speaker
  rather than once per file: a license is effectively a property of the voice, and across
  all 3992 shipped recordings four depart from their own author's. Those four carry a
  `license` of their own, which is the only place an entry names one; the deed is never
  written on an entry at all, being derivable from the license. There is deliberately no
  default AUTHOR, though one voice covers 476 of the 477 Swahili files — a missing key
  would then read as a credit to whoever recorded the most, and a misattribution by
  omission is the one thing a BY notice may not do.
  Every row of both maps has to be used by some recording (lint), so they describe the
  pack rather than accumulating its history.
- `words` is keyed by concept slug, `letters` (optional, uk only today) by lowercase
  glyph. Every field is required except `license`, present only on the entries that
  depart from their author's, `gain`/`cap`/`capPhone`/`lead`, absent where they would be
  zero, and `gainPhone` — present on every word and article entry (0.0 when no
  correction) but absent on letters and texts.
- `articles` (optional, de and it today) is keyed by slug like `words` and holds
  recordings that speak an ARTICLE and then the word — `die Adresse`, files under
  `articles/<slug>.mp3`. `matches` is the whole spoken form, `word` the bare form inside
  it, and the entry is indexed by BOTH: the spoken form answers a card asking with its
  article (the string `spokenTargetForm` builds), the bare word answers one asking
  without, last, so a recording of exactly what the card shows always wins.
  `word` is carried rather than derived — cutting a leading article off is a guess, and
  an elided one (`l'acqua`) has no space to cut at.
  The article must be the realization's own `grammar.gender`, because a recording is the
  only thing that teaches a gender aloud and a wrong one teaches it wrong; the `word` may
  be any form the realization carries, so a file saying "der Großvater" ships as a
  recording of the variant it actually says.
  Usually an addition beside a bare `words` entry — the source side reads the learner's
  own language, where the article is not what is being taught — but not dependent on one:
  five words ship the article recording alone and it answers both asks.
- `calendar` (optional) is keyed by the FORM it speaks, like `texts` and for the same
  reason: no concept covers a weekday, so there is no slug to key one by. Files are
  `calendar/<ascii stem>.mp3`. It holds the weekday and month names of `../dates/`, the
  synonyms beside them included — a card may show `Sonnabend`, and a recording is only
  ever played for the form it actually says. `abbr` is never recorded: it is a written
  short form the prompt wears, and nothing says it aloud.
  It carries `gainPhone` where `texts` does not — these are words spoken on a drill card,
  beside the very vocabulary the phone-speaker plane was measured for, while the
  alphabet's reference rows stay flat.
- `matches` — the surface form the recording actually SPEAKS, and the lookup key:
  playback is keyed by what stands on the card, never by the slug the file was fetched
  for, so a rotated synonym nobody recorded falls through to the app's own voice
  instead of playing the canonical word. It may differ from `text` in case
  (`unterlagen` / "Unterlagen"), edge punctuation (`hallo` / "Hallo!") or the citation
  dash (`zuri` / "-zuri") — the engine folds those away (`../../kern/docs/audio.md`).
  Letters carry no `matches`: they speak a name, and that string belongs to the
  alphabet file.
- `source` — the original Commons filename; the credits screen links `File:<source>`,
  which is what keeps attribution checkable rather than merely present.
- Word files are `<slug>.mp3`, article files `articles/<slug>.mp3`, and text and calendar
  files their form's ASCII stem under `texts/` and `calendar/`; letter files are
  `letters/u<codepoint>….mp3`, one
  `u` + four lowercase hex digits PER CODEPOINT, never glyph-named — `й`/`ї` decompose
  under NFD on APFS and a Unicode filename has to survive git, Gradle sync and AAPT
  unchanged. A sequence rather than one codepoint because a named row may be a digraph
  (es `ch`), which a single codepoint would file under `c`. The manifest maps the glyph,
  so the name is purely internal.
- **The mp3 bytes are the Commons transcode untouched**, renamed and nothing else:
  re-encoding (including loudness normalization, so packs differ in loudness) is an
  adaptation under BY-SA. `sha256` is the digest the generator verified after the copy
  and lint re-hashes what was committed, which makes it a gate rather than a promise.
- `gain` (dB) and `lead` (ms) are the generator's own MEASUREMENT of those untouched
  bytes. `gain` is the full-range plane and `gainPhone` the phone-speaker plane
  (absent on letters and texts, where no phone plane was measured, and present on
  calendar entries, which are drill-card words); `lead` is how much
  dead air to start past. The files stay unmodified and only the player corrects them,
  picking the plane by its output route. What was measured, against which target,
  is `../../scripts/audio-catalog.py`'s `ANALYSIS`.
- `cap` (dB) and `capPhone` are what the peak ceiling held back from each plane's gain:
  a boost is capped at the headroom the file's own peak leaves, so a quiet word with sharp
  peaks ships under the loudness target rather than clipping. That ceiling is only true at
  full volume — a listening run's bedtime ramp attenuates ahead of the boost and opens the
  headroom again — so a player under a fade hands back as much of the deficit as the ramp
  has taken off, and no more (`fadedGainDb`). It binds 5-25% of every pack but sw, which
  is the loud one and is capped almost nowhere.
- `snr` (dB) is a third measurement of the same bytes — peak minus noise floor, how far
  the word stands above the hiss under it — but nothing plays it. It is carried so lint
  can see the SHAPE of a pack and refuse a rebuild that quietly reintroduces noise an
  earlier sweep removed. A floor per file would be dishonest: some words have nothing
  cleaner on Commons, so the rule is on the median and the size of the bad tail.

**Replacing bytes a learner hears** — a speaker consolidation, a re-fetch, a fresh pack —
earns an independent measurement pass before it ships,
run by a separate agent from the raw files with its own script,
never trusting the numbers the generating tool printed,
plus a folder staged under `../../../data/` whose filename order alternates current -> proposed
(speaker and license in the name, an INDEX naming the word) so the pairs simply play through.
A tool's own guard only measures what it was built to measure:
the consolidation guarded on noise floor, which is blind to bandwidth,
and passed a Spanish swap 23 dB duller above 6 kHz —
scored *clean* precisely because a lopped-off recording has no hiss up there.
The audit caught it and the tool then grew `snr` above.
Flagged items go back to the user rather than being closed by the auditor:
measurement can bracket plausibility, but it cannot confirm which word was spoken.

Lint (`CatalogAudioLintTest`) holds the rest: every entry names a slug its language
realizes and a form some card can show, no two entries claim one spoken form with
different bytes, every file ships and is referenced exactly once, every `authors` and
`licenses` row is used, and no author is a placeholder like "Own work" — BY and BY-SA
both require naming somebody.
