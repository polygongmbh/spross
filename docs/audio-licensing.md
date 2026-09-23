# Audio & content licensing

The record for the ship/legal questions the bundled audio raises:
what is in the app, whose it is, what each license asks for, and where the app answers it.

The engine rule is `../kern/docs/audio.md`, the file format `../catalog/audio/README.md`,
and the per-file truth is the per-language manifests themselves --
this doc states the posture, not the schema.
Why the recordings are mp3 is `audio-format.md`.
The pack research (how each source was found, what was rejected, the coverage gaps)
lives outside the repo in `data/reference/audio/README.md`.

## 1. What ships, and under what

Every bundled recording is a Wikimedia Commons transcode under CC BY-SA, CC BY, CC0
or a public-domain dedication --
no NC and no ND clause ships, in audio or anywhere else.

How many files each pack holds, which licenses they carry and who spoke them
are in the manifests;
`scripts/audio-coverage.py --credits` prints them.
The app says the same thing per file --
`Catalog.audioCredits()` feeds the credits screen on both platforms --
and that, not a table, is where a speaker's name is discharged.
`--check` fails where a manifest names a file git does not track.

| License | What it obliges |
|---|---|
| CC BY-SA (4.0, 3.0, 2.5, 2.0) | name the speaker and link the deed; a derivative carries the same terms |
| CC BY (4.0, 3.0, 3.0 us, 2.0 fr) | name the speaker and link the deed |
| CC0 | nothing; credited anyway |
| Public domain | nothing, and there is no deed to link |

The `articles/` sets are where a word may ship TWICE:
the bare file for the learner's own language, the article one for the target
(`../catalog/audio/README.md`).
Five words ship only the article recording, which answers both ways.
Both carry their own author and license row.

**Three licenses are refused outright**
(`scripts/audio-catalog.py`'s `UNSHIPPABLE_LICENSES`):

- **GFDL** obliges shipping the full license text and a "Transparent copy" --
  a document's terms, which a credits screen linking a deed does not meet.
- **GPLv3** is a software license whose copyleft reaches the work as a whole,
  and whose anti-tivoization terms are irreconcilable with App Store distribution.
  It cost sixteen of the nineteen Esperanto names (one uploader, Kurso de Esperanto).
- **"Attribution"**, Commons' legacy bare template, names no versioned license
  and has no stated terms.

A refused row is a printed decision and the word falls to the device voice;
an *unlisted* license remains a hard stop.

## Share-alike reach

CC carries no GPL-style linking clause,
so bundling BY-SA recordings leaves the Kotlin core, the UI and the catalog data unaffected.
Share-alike reaches the **audio files only**.

Per-pack share-alike status:
- **uk words**: free of share-alike entirely (Shtooka attribution-only terms).
  The uk FOLDER is share-alike in three places: the letters, and 63 of 70 atlas country names
  (Lingua Libre, the only source where Ukrainian toponyms exist at scale).
- **fr words**: 442 of 511 are one Shtooka voice under BY 2.0 fr (attribution only);
  share-alike enters through the 24-word Lingua Libre tail and the letters.
- **eo letters**: cost no extra credit because the letter names ARE ordinary lexemes
  (`bo`, `co`, `uo`) -- the word recording and the letter-name recording are the same sound.
- **eo phrases**: ten rows, the first PHRASE recordings any pack carries,
  matched exactly against the Commons phrasebook.
- Every other pack carries BY-SA anyway.

## 2. How the obligations are discharged

- **Provenance is versioned per file.**
  Every manifest entry carries `author`, the original Commons filename as `source`,
  and the `sha256` of the shipped bytes;
  license and deed come from the manifest's `authors` and `licenses` maps
  (`../catalog/audio/README.md`).
  The parser refuses a manifest whose maps do not cover what it credits.
- **Credits derive from the shipped manifests**, never from a hand-kept list:
  `Catalog.audioCredits()` groups per (language, author, license) with per-file rows,
  rendered by `App/Sources/Screens/CreditsView.swift` and `android/.../ui/AboutScreen.kt`
  from that one API.
  A group expands to its recordings, each linking `File:<source>` on Commons.
  BY and BY-SA groups are separate rows by construction.
- **The untouched-transcode gate is a test.**
  `CatalogAudioProvenanceTest.audioFilesMatchTheirManifestHashes` re-hashes every committed mp3
  against its manifest entry.
  Re-encoding (loudness normalization included) would be an adaptation under BY-SA,
  so the gate keeps the packs' loudness differences a playback problem (section 3)
  rather than a license one.
- **No file ships without a nameable author.**
  `noAudioAuthorIsUnattributable` rejects the placeholder set
  (`Own work`, `myself`, empty),
  and Commons' `assumed (based on copyright claims)` wording with it.
  `everyAudioFileShipsAndIsReferencedExactlyOnce` keeps uncredited bytes out of the bundle.
- **The converter drops rather than guesses**
  (`scripts/audio-catalog.py` + `scripts/audio_gates.py`):
  rows whose slug the catalog does not realize;
  rows whose recording speaks a different word;
  rows colliding on one spoken form with differing bytes;
  and rows unattributable after resolution against the Commons API.

## 3. The analysis index -- the "unmodified" claim stays true

The packs share no loudness:
the word packs sit at a median -18.0 LUFS, the uk letters at -31.4.

The correction is **not applied to the files**.
Each entry carries the numbers measured off the shipped bytes --
`gain` (dB from the full-range analysis target)
and `gainPhone` (phone-speaker plane, absent on letters and texts),
plus `lead` (dead air at the head, ms) --
and only a player ever applies them
(iOS through an EQ node, Android through `LoudnessEnhancer` and a seek).
Consequences:

- The shipped mp3 bytes remain **byte-identical Commons transcodes**,
  so no adaptation is distributed and share-alike is never triggered.
- The credits' "Recordings shipped unmodified" line stays accurate.
- The `sha256` gate keeps meaning exactly what it says.
- A measurement of a file is our own factual data: it carries no license of its own.

What was measured, against which target and under which scheme
is `scripts/audio-catalog.py`'s `ANALYSIS`.

## 4. Text-to-speech: live only, never an asset

TTS covers what no recording exists for.
It is synthesized and spoken **at the moment the card asks**, never written to a file:

- Apple's System Voices: the macOS SLA confines their output to personal, non-commercial use.
  Live synthesis through `AVSpeechSynthesizer` is ordinary sanctioned API use;
  pre-rendering is not.
  No synthesis-to-file API is referenced anywhere in `App/Sources/Audio/`.
- iOS speaks only voices the user has installed, and has no Swahili voice at any tier,
  so an unrecorded Swahili word is silent.
- Android pins Google's engine (`com.google.android.tts`, offline Swahili included)
  and likewise only calls `speak()`.

The moment a synthetic voice were ever **bundled** instead of spoken live,
this posture would change (see section 6.3).

## 5. Catalog data posture

**No non-commercial source was used anywhere, deliberately** --
the app has product ambition, and NC would foreclose it.

Only one licensed source's output actually **ships**: Wikidata noun gender and plural, **CC0**.
Everything else was consulted as evidence and left no expression behind:
FreeDict generated candidate lists that were re-picked and largely re-authored,
frequency lists broke ties, Tatoeba attested phrase wording.
Single dictionary headwords are facts, not expression --
and the rule holds only while it holds:
a future language that *derives* `text` or `notes` from a BY-SA dictionary
would put the whole catalog under share-alike.

Notable rejections:

- **Forvo** -- non-commercial terms, and API URLs expire after two hours.
- **PanLex** -- relicensed from CC0 to CC BY-NC-SA; treated as rejected.
- **Tatoeba audio** -- 76.6% CC BY-NC-ND; the text corpus is fine, the recordings are not.
- **Meta MMS-TTS** -- weights CC-BY-NC 4.0, which blocks build-time use too.
- **Coqui XTTS-v2** -- CPML licenses model and outputs non-commercially, and the company is gone.
- **eSpeak NG / piper1-gpl / sherpa-onnx v1.x** -- GPL-3.0 or statically linking it;
  fatal for a closed App Store binary.
- **Piper `sw_CD-lanfrica`** -- finetuned from research-only voice data.
- **OPUS OpenSubtitles** -- no explicit license at all.

## 6. Open items for the owner

1. **BY-SA section 2(a)(5)(B) versus App Store DRM -- the pre-submission gate.**
   The license forbids "Effective Technological Measures" on the shared material,
   and every App Store binary is FairPlay-encrypted;
   2094 of the 3597 files are BY-SA.
   **Mitigation on record:** additionally publish the same recordings at a public un-DRM'd URL
   under the same licenses, in **separate per-language files** --
   the `catalog/audio/<lang>/` split already prepares that shape.
   This needs a legal read before the first submission.
2. **The es accent caveat.**
   The catalog is authored in Peninsular Spanish (distincion);
   the pack does not claim to match it.
   Commons states a license, a speaker and a recorder per file and no country or variety.
   The consequence is audible where the two meet --
   an es-MX voice speaks /s/ where the alphabet's c and z rows promise /theta/.
   Tracked in `../catalog/backlog.md`.
3. **Azure S0 terms, if the gaps are ever filled by synthesis.**
   Azure AI Speech is the only provider covering every target language including native `sw-KE`
   with an express commercial grant (~$1-4 for the whole catalog, one-off).
   Conditions: the **paid S0 tier only** (F0 carries no commercial grant),
   archive the dated Product Terms with the release,
   and disclose synthetic voices in-app.
   Google Cloud TTS would additionally need counsel on Service Terms section 20(d),
   which bars generative-AI services in products likely accessed by under-18s.

Coverage gaps, install size and the index's missing peak term are engineering debts,
filed in `../catalog/backlog.md` and `backlog.md`.
