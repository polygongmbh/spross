# Audio
The bundled recordings' file format, naming and provenance fields.
Neighbors: when and how a recording is heard `../../docs/audio.md`, whose they are `../../docs/audio-licensing.md`.

Bundled pronunciation recordings, one folder per language, **generated** by `app/scripts/audio-catalog.py` out of the workspace's packs —
edit packs, not this directory.
A full convert builds a language that ships no manifest yet and refuses one that does.

**`--fill [--section S]` is how a language catches up with the catalog**, in any section:
it ADDS forms the section lacks and leaves every entry it already has byte-identical —
file, digest and credit —
so the German pack's reseated rows (the fix for its hiss) and several packs' hand-curated tails survive whatever the workspace has moved on to.
A fill also refuses a row whose spoken form a SHIPPED row already claims under different bytes:
two files for one sound leave the runtime nothing to pick, so filling one word would silence another.
German `poor` is "arm", which the body part already speaks;
French `entrance` and `fresh` are the same story.
A row whose bytes already ship in that section is that entry, under the key it was moved to.
`--articles`, `--calendar` and `--countries` ship their section's staged pack swaps (`--ship --from-packs`),
then fill it.

The packs (`pack-<lang>-words`, `pack-<lang>-articles` and their siblings) are keyed like this manifest, by the form a recording speaks, never by the concept that shows it:
a concept's text, a synonym and a tagged form (a plural, a feminine, an agreement form) are one kind of row,
so a form moving between those roles keeps its recording.
Every form is looked for bare in the words pack and,
where a card shows it with an article, with that article in the articles pack;
a form Commons has no take of is looked for without its leading citation prefix or detachable particle (`../languages.json`),
the form playback falls back to.
Where each language's recordings are on Commons is one table, `../../scripts/audio_routes.py`,
and every request goes through one client, `../../scripts/audio_commons.py`,
which waits out a dropped network or a throttling Commons for up to 15 minutes before an item counts as failed.

After authoring words, per language and one language at a time (Commons throttles parallel fetches), a matter of seconds:

```sh
scripts/audio-packs.py resolve words <lang>                  # resolves only what the pack lacks
scripts/audio-packs.py resolve articles <lang>               # the same forms with their article
scripts/audio-catalog.py --lang <lang> --fill
scripts/audio-catalog.py --lang <lang> --articles
scripts/audio-coverage.py --missing <lang>                   # what nobody has recorded
```

The fill's own drops are the quality check:
`poor` (under its language's floor, or heard as bad), `unmeasurable` (clipped), `one-sided` (one channel near silent), `overlong` (speech running past 1.5 s plus 0.15 s a letter of its key: it says more, or the word drowns in its noise), `misnamed`, `unreachable` and `shipped-collision` each name the row and why;
what a take must pass on its own is the take judge's (`../../scripts/audio_judge.py`).
Quality is `mos`, a predicted listener score (`../../scripts/audio_measure.py`),
and the floor follows the device voice (`../../scripts/audio_voices.py`):
where it is missing (eo, sw) only the clearly bad are refused,
where it is good a recording has to beat it,
and `--prune-poor` hands anything shipped that the judge now refuses to it.

A full sweep also re-checks what already ships, in any language:
anything not clearly good has every other take of its word fetched and compared, and the best one wins.
Where none of a whole form wins, the takes of its bare form (de `sich irren` → `irren`) compete under the same bar,
unless the bare form ships a take of its own;
a winner ships under the bare form in the whole form's place, its pack row re-keyed with it,
so neither a fill nor a resolve brings the whole form's take back.
The score ranks voices the way the ear does,
but a single file strays by a few tenths,
so it picks among takes and refuses only the clearly bad.

```sh
scripts/audio-packs.py consolidate <lang>... --apply        # voices with ≤3 takes onto established ones
scripts/audio-packs.py sweep <lang>... --apply              # poor, doubted, squashed, one-sided or refused: the best other take
scripts/audio-catalog.py --ship --lang <lang> --dry-run     # the staged swaps, judged
scripts/audio-catalog.py --ship --lang <lang>               # ships them
scripts/audio-catalog.py --lang <lang> --prune-poor         # what the judge still refuses
```

A sweep runs for hours: every run prints its `tail -f` line first and tees each pack's log to `$W/logs/<date>-<command>/`, beside a one-line-per-pack `summary.log`.
One run at a time — Commons throttles a second.
`--only <forms>` reconsiders named forms whatever they score, and a rerun resumes:
fetches and measures are cached in the workspace, and a key already staged against what ships is passed over.
An item Commons stays unreachable for keeps its take, and the run exits nonzero with the count.

A take whose peak stands less than `audio_voices.SQUASH_FLOOR_DB` over its loudness was limited at its source:
the score hears it as clean, the ear hears it squashed, and no gain undoes that.
A sweep reconsiders it like a poor take and never swaps one in that no listener passed.

Both pickers only print until given `--apply`, and never write a pack:
a swap is a row in `$W/staged.tsv`, its bytes in `$W/staged/`,
and a form that ships nothing yet is staged against nothing.
`--ship` replaces a take only while the one it was chosen over still ships,
and only once the take judge passes the replacement;
a refused swap leaves the old recording standing.
Each applied swap is appended to `$W/shipped.tsv`;
`--from-packs` first stages every pack row naming another Commons file than what ships under its form.
A new speaker is another voice change on the cards and another credit group,
so every picker takes an established voice's take over a slightly better stranger's,
and a voice with three recordings or fewer is moved onto an established one wherever it can be —
never onto a take the sweep would refuse.
A second voice earns its place with a chunk of words, never a scattering of one-offs:
where the main voice is poor, a few good voices each take every word they say well.

One entry comes back out of git, or goes, without a pack;
a restore checks the bytes against the `sha256` the manifest pinned at that revision:

```sh
scripts/audio-catalog.py --restore <lang> <section> <key> --from <rev>
scripts/audio-catalog.py --remove <lang> <section> <key>
```

**Every verdict a listener gives on a recording goes into `../../docs/audio-verdicts.tsv`** with its source, sha256 and `mos`,
so the next revision of the measure is tested against the ear rather than tuned to a handful of files.
A verdict also overrules the score for those bytes:
bad never ships, and bad or mediocre has every other take of its word tried.
A voice heard as bad across its takes is a POOR voice (`../../scripts/audio_voices.py`),
and every take of it counts as bad unless a listener passed that file.
A listening set is takes beside a TSV naming them, rated one key per take with `../../scripts/audio-listen.py`,
which writes the verdicts itself;
`audio-listen.py sample NAME --from shipped|journal|voices` draws one out of unrated takes —
the lowest scorers that ship, the staged and shipped swaps most likely to be wrong,
or one take of each voice nobody heard yet.

The packs (Wikimedia Commons transcodes plus a `manifest.tsv` of provenance) are unversioned research input;
what is committed here is the shipped bytes and the license record that has to travel with them.
Both apps bundle the whole tree as it stands (iOS folder reference, the Android catalog sync),
so nothing needs registering.

**`app/scripts/audio-coverage.py --check` runs on every commit that stages a recording** (`scripts/hooks/pre-commit`),
and by hand after adding one.
The lint that verifies every file ships walks the WORKING TREE,
so a recording fetched but never staged is indistinguishable from one that ships —
the manifests once named 517 files that existed on one machine and in no checkout;
the script asks `git ls-files` instead.

```json
{ "language": "uk",
  "authors": { "Галя Раптова, Nicolas Vion": "CC BY 3.0 us", "Tabrus": "CC BY-SA 4.0" },
  "licenses": { "CC BY 3.0 us": "https://creativecommons.org/licenses/by/3.0/us/",
                "CC BY-SA 4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
                "CC BY 2.0 fr": "https://creativecommons.org/licenses/by/2.0/fr/" },
  "words": {
    "установа": { "file": "words/u0443u0441u0442u0430u043du043eu0432u0430.mp3", "matches": "установа",
                "author": "Галя Раптова, Nicolas Vion",
                "source": "Uk-установа.ogg", "sha256": "ca9d…" },
    "рецепт": { "file": "words/u0440u0435u0446u0435u043fu0442.mp3", "matches": "рецепт",
                      "license": "CC BY 2.0 fr",
                      "author": "Галя Раптова, Nicolas Vion",
                      "source": "Uk-рецепт.ogg", "sha256": "91d9…" } },
  "letters": {
    "ж": { "file": "letters/u0436.mp3",
           "author": "Tabrus", "source": "Жж – ukrainian.ogg", "sha256": "77b0…",
           "gain": 20.0, "lead": 1069 } },
  "articles": {
    "die Adresse": { "file": "articles/die_adresse.mp3", "matches": "die Adresse", "word": "Adresse",
                 "author": "Natschoba",
                 "source": "LL-Q188 (deu)-Natschoba-die Adresse.wav", "sha256": "a15c…",
                 "gain": 8.0, "lead": 240 } },
  "calendar": {
    "Montag": { "file": "calendar/montag.mp3", "matches": "Montag",
                "author": "Jeuwre", "source": "De-Montag.ogg", "sha256": "fa2d…",
                "gain": -5.4, "lead": 439 } } }
```

- `language` must equal the folder name, and a folder for a language `languages.json` does not declare is never read —
  adding one is dropping a directory in, nothing else.
- `authors` maps every credited speaker to the license they record under, `licenses` every license the pack uses to its deed URL —
  `null` for `Public domain`, the one license with nothing to point a reader at.
  Provenance is authored ONCE per speaker rather than once per file:
  a license is effectively a property of the voice,
  and across every shipped pack only a handful of entries depart from their own author's.
  Those carry a `license` of their own, which is the only place an entry names one;
  the deed is never written on an entry at all, being derivable from the license.
  There is deliberately no default AUTHOR, though one voice covers all but one Swahili file —
  a missing key would then read as a credit to whoever recorded the most,
  and a misattribution by omission is the one thing a BY notice may not do.
  Every row of both maps has to be used by some recording (lint),
  so they describe the pack rather than accumulating its history.
- `words` is keyed by the FORM it speaks, like every section but `letters` (optional),
  which is keyed by lowercase glyph.
  No key or file names the concept a recording was fetched for,
  so merging, renaming or moving a concept never touches audio:
  a recording stays reachable while some card shows its form.
  Every field is required except `license`, present only on the entries that depart from their author's,
  and `gain`/`cap`/`lead`/`end`/`gate`, absent where they would be zero or unmeasured.
- `articles` (optional, de and it today) is keyed by the whole spoken form like `words` and holds recordings that speak an ARTICLE and then the word —
  `die Adresse`, files under `articles/`.
  `matches` is the whole spoken form, `word` the bare form inside it, and the entry is indexed by BOTH:
  the spoken form answers a card asking with its article (the string `spokenTargetForm` builds), the bare word answers one asking without, last,
  so a recording of exactly what the card shows always wins.
  `word` is carried rather than derived —
  cutting a leading article off is a guess, and an elided one (`l'acqua`) has no space to cut at.
  The article must be one a card shows in front of that word —
  a realization's own `grammar.article`, or the article a tagged form was authored with (`die Lehrerin`) —
  because a recording is the only thing that teaches a gender aloud and a wrong one teaches it wrong;
  the `word` may be any form the realization carries,
  so a file saying "der Großvater" ships as a recording of the form it actually says.
  Usually an addition beside a bare `words` entry —
  the source side reads the learner's own language, where the article is not what is being taught —
  but not dependent on one: five words ship the article recording alone and it answers both asks.
- `texts` (optional) holds the `exampleText` rows of `../alphabet/`.
  A key no alphabet row cites is
  refused by lint: it could never be reached and its mp3 would ship for nothing.
- `calendar` (optional) holds the weekday and month names of `../dates/`, the `teaches` beside them included —
  a card may show `Sonnabend`, and a recording is only ever played for the form it actually says.
  `abbr` is never recorded: it is a written short form the prompt wears, and nothing says it aloud.
- `countries` (optional) is `calendar`'s twin for `../countries/`, holding the country and the nationality name of every atlas row —
  `Deutschland` and `Deutsche`.
  `accepts` are not recorded on either half, being accept-only and never displayed,
  so a recording keyed by one could never be reached.
- `matches` —
  the surface form the recording actually SPEAKS, repeated from the key, and the lookup key:
  playback is keyed by what stands on the card, never by the concept that shows it,
  so a rotated synonym nobody recorded falls through to the app's own voice instead of playing the canonical word.
  It may differ from `text` in case (`unterlagen` / "Unterlagen"), edge punctuation (`hallo` / "Hallo!") or the citation dash (`zuri` / "-zuri") —
  the engine folds those away (`../../docs/audio.md`).
  Letters carry no `matches`: they speak a name, and that string belongs to the alphabet file.
- `source` — the original Commons filename;
  the credits screen links `File:<source>`,
  which is what keeps attribution checkable rather than merely present.
- `alternates` (optional, on an entry in any section) lists further takes of the same key, mostly by other voices.
  The entry itself stays the key's first take;
  an alternate carries a take's own fields (`file`, `author`, `source`, `sha256`, a departing `license`, the measurements)
  and never `matches` or `word`, which belong to the key.
  `scripts/audio-catalog.py --add-voice <lang> <section> <speaker>` ships a Lingua Libre speaker's takes of a section,
  beside each key another voice says and as the first take where none ships.
  Which take plays is the engine's choice (`../../docs/audio.md`);
  sweeps and consolidation judge the first take only,
  and `scripts/audio-catalog.py --remove <lang> <section> <key> --take <source>` drops one alternate.
- A form-keyed file is its form's ASCII stem under its section's folder —
  `[a-z0-9-]` stays, a space becomes `_`, every other character `u<hex>`,
  so `die Adresse` is `articles/die_adresse.mp3`;
  byte-identical recordings of one form ship once.
  Letter files are `letters/u<codepoint>….mp3`, one `u` + four lowercase hex digits PER CODEPOINT, never glyph-named —
  `й`/`ї` decompose under NFD on APFS and a Unicode filename has to survive git, Gradle sync and AAPT unchanged.
  A sequence rather than one codepoint because a named row may be a digraph (es `ch`),
  which a single codepoint would file under `c`.
  The manifest maps the glyph, so the name is purely internal.
  An alternate's file is its key's with `.<n>` before `.mp3`, n from 2 — no stem carries a dot.
- **The mp3 bytes are the Commons transcode untouched**, renamed and nothing else:
  re-encoding, loudness normalization included, is ruled out by `../../docs/audio-licensing.md` §3,
  so packs differ in loudness.
  `sha256` is the digest the generator verified after the copy and lint re-hashes what was committed,
  which makes it a gate rather than a promise.
- `gain` (dB), `lead` and `end` (ms) are the generator's own MEASUREMENT of those untouched bytes, one gain for every output route;
  `lead` is how much dead air to start past, `end` where to stop once the speech is over,
  so neither a click nor the hum a word sits in plays around it.
  Both come from the speech span (`../../scripts/audio_measure.py`), which looks past clicks and over a steady edge noise.
  The files stay unmodified and only the player corrects them.
  What was measured, against which target,
  is `../../scripts/audio_index.py`'s `ANALYSIS`.
- `cap` (dB) is what the peak ceiling held back from the gain:
  a boost is capped at the headroom the file's own peak leaves,
  so a quiet word with sharp peaks ships under the loudness target rather than clipping.
  That ceiling is only true at full volume —
  a listening run's bedtime ramp attenuates ahead of the boost and opens the headroom again —
  so a player under a fade hands back as much of the deficit as the ramp has taken off, and no more (`fadedGainDb`).
  It binds 5-25% of every pack but sw, which is the loud one and is capped almost nowhere.
- `mos` (1 to 5) is a third measurement of the same bytes —
  how good the take sounds, as predicted by the DNSMOS model (`scripts/audio_measure.py`) —
  but nothing plays it.
  It is carried so lint can see the SHAPE of a pack and refuse a rebuild that quietly reintroduces noise an earlier sweep removed.
  The lint holds the median and the size of the bad tail, never a floor per file:
  some words have nothing better on Commons.
- `gate` (dB, negative) is the file's own noise level plus a small margin, in dBFS of the raw decoded bytes before any gain;
  a player's downward expander sits there and quiets the hiss in pauses and between syllables while the word plays untouched.
  Absent means no gate: the file measured as digital silence, or nothing was measured.

Lint (`CatalogAudioLintTest`, `CatalogAudioProvenanceTest`) holds the rest:
every entry is keyed by the form it speaks and some card of its language shows it, no two entries claim one spoken form with different bytes, every file ships and is referenced exactly once, every `authors` and `licenses` row is used,
and no author is a placeholder like "Own work" —
BY and BY-SA both require naming somebody.
