# Audio (`catalog/audio/`)

The bundled recordings: file format, naming and provenance fields.
What the engine does with them is `../../kern/docs/audio.md`,
when one is heard `../../docs/read-aloud.md`,
and whose they are `../../docs/audio-licensing.md`.

Bundled pronunciation recordings, one folder per language, **generated** by
`app/scripts/audio-catalog.py --packs <workspace>` — edit packs, not this directory.
`--articles` rebuilds only the `articles` section of what already ships, which is how an
article pack lands without re-deriving three sections from a workspace whose word mp3s a
renamed slug has already outlived.
The packs (Wikimedia Commons transcodes plus a `manifest.tsv` of provenance) are
unversioned research input; what is committed here is the shipped bytes and the
license record that has to travel with them. Both apps bundle the whole tree as it
stands (iOS folder reference, the Android catalog sync), so nothing needs registering.

```json
{ "language": "uk",
  "words": {
    "office": { "file": "office.mp3", "matches": "установа",
                "license": "CC BY 3.0 us",
                "licenseUrl": "https://creativecommons.org/licenses/by/3.0/us/",
                "author": "Галя Раптова, Nicolas Vion",
                "source": "Uk-установа.ogg", "sha256": "1c44…" } },
  "letters": {
    "ж": { "file": "letters/u0436.mp3", "license": "CC BY-SA 4.0",
           "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
           "author": "Tabrus", "source": "Жж – ukrainian.ogg", "sha256": "77b0…",
           "gain": 20.0, "lead": 1069 } },
  "articles": {
    "address": { "file": "articles/address.mp3", "matches": "die Adresse",
                 "license": "CC BY-SA 4.0", "licenseUrl": "…", "author": "Natschoba",
                 "source": "LL-Q188 (deu)-Natschoba-die Adresse.wav", "sha256": "a15c…",
                 "gain": 8.0, "gainPhone": 3.9, "lead": 240 } } }
```

- `language` must equal the folder name, and a folder for a language `languages.json`
  does not declare is never read — adding one is dropping a directory in, nothing else.
- `words` is keyed by concept slug, `letters` (optional, uk only today) by lowercase
  glyph. Every field is required except `licenseUrl`, which is absent exactly for
  public-domain files, having no deed to link, `gain`/`lead`, absent where they
  would be zero, and `gainPhone` — present on every word and article entry (0.0 when no
  correction) but absent on letters and texts.
- `articles` (optional, de and it today) is keyed by slug like `words` and holds
  recordings that speak the card's ARTICLE and then the word — `die Adresse`, files
  under `articles/<slug>.mp3`. Its `matches` is that whole spoken form, which is the
  string `spokenTargetForm` builds for the target side, so it is the only key such an
  entry can be reached by. It is an ADDITION, never a replacement: the bare `words`
  entry keeps shipping and is what the source side reads, where the article is not what
  is being taught, and lint fails an article entry whose slug has no bare twin.
  An article entry must speak its realization's own `grammar.gender` in front of its
  canonical `text` — a synonym rotates the article away on screen, so a recording of
  one could never play.
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
- Word files are `<slug>.mp3`, article files `articles/<slug>.mp3`; letter files are `letters/u<codepoint>….mp3`, one
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
  (absent on letters and texts, where no phone plane was measured); `lead` is how much
  dead air to start past. The files stay unmodified and only the player corrects them,
  picking the plane by its output route. What was measured, against which target,
  is `../../scripts/audio-catalog.py`'s `ANALYSIS`.
- `snr` (dB) is a third measurement of the same bytes — peak minus noise floor, how far
  the word stands above the hiss under it — but nothing plays it. It is carried so lint
  can see the SHAPE of a pack and refuse a rebuild that quietly reintroduces noise an
  earlier sweep removed. A floor per file would be dishonest: some words have nothing
  cleaner on Commons, so the rule is on the median and the size of the bad tail.
- No `README.md` inside `audio/` — the Android sync only excludes one at the catalog
  root, so a nested one would ship in the APK. Audio schema docs live here.

**Replacing bytes a learner hears** — a speaker consolidation, a re-fetch, a fresh pack —
earns an independent measurement pass before it ships,
run by a separate agent from the raw files with its own script,
never trusting the numbers the generating tool printed,
plus a folder staged under `../../data/` whose filename order alternates current -> proposed
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
different bytes, every file ships and is referenced exactly once, and no author is a
placeholder like "Own work" — BY and BY-SA both require naming somebody.
