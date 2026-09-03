# Audio & content licensing

The record for the ship/legal questions the bundled audio raises:
what is in the app, whose it is, what each license asks for, and where the app answers it.
Successor to `docs/pronunciation-plan.md`, deleted once the feature shipped.

The engine rule is `../kern/docs/audio.md`, the file format `../catalog/audio/README.md`,
and the per-file truth is the per-language manifests themselves — this doc states the posture,
not the schema.
Why the bundled recordings are mp3, and what a different codec would cost, is `audio-format.md`.
The pack research (how each source was found, what was rejected, the coverage gaps)
lives outside the repo in `data/reference/audio/README.md`.

## 1. What ships, and under what

5828 mp3 files, ~129 MB, all of them Wikimedia Commons transcodes:
**3566 CC BY-SA · 1326 CC BY · 930 CC0 · 6 public domain**.

The per-pack rows below are DERIVED, not typed: `scripts/audio-coverage.py --credits`
emits them from the shipped manifests, and `--check` fails where one names a file git does
not track. Coverage itself is the bare `audio-coverage.py`.

| Pack | Files | Source | Licenses | Speakers | Obligation |
|---|---|---|---|---|---|
| `audio/de/` | 767 | Commons `De-*.ogg` | CC BY-SA 4.0 490 · CC BY-SA 3.0 268 · CC BY 3.0 us 7 · Public domain 1 · CC BY-SA 2.5 1 | Jeuwre 467, Kampy 194 | credit; share-alike on the BY-SA |
| `audio/de/letters/` | 8 | Commons `De-<letter>.ogg` | CC BY-SA 4.0 6 · CC BY-SA 3.0 2 | Jeuwre 6, T.Voekler 2 | credit + share-alike |
| `audio/de/texts/` | 2 | Commons `De-*.ogg` | CC BY-SA 4.0 1 · CC BY-SA 3.0 1 | Jeuwre 1, joni 1 | credit + share-alike |
| `audio/de/articles/` | 221 | Lingua Libre, via Commons | CC BY-SA 4.0 221 | Natschoba 221 | credit + share-alike |
| `audio/de/calendar/` | 13 | Commons `De-*.ogg` | CC BY-SA 3.0 7 · CC BY-SA 4.0 5 · CC BY-SA 2.5 1 | joni 5, Jeuwre 5 | credit + share-alike |
| `audio/de/countries/` | 127 | Commons `De-*.ogg` | CC BY-SA 4.0 103 · CC BY-SA 3.0 19 · CC BY 3.0 5 | Jeuwre 101, Hedwig von Ebbel 7 | credit + share-alike |
| `audio/eo/` | 717 | Lingua Libre + the Commons Esperanto phrasebook | CC BY-SA 4.0 494 · CC0 222 · CC BY 4.0 1 | Lepticed7 483, Poslovitch 117 | credit; share-alike on the BY-SA |
| `audio/eo/letters/` | 28 | Lingua Libre word recordings | CC BY-SA 4.0 28 | Lepticed7 28 | credit + share-alike |
| `audio/eo/texts/` | 2 | Lingua Libre, via Commons | CC BY-SA 4.0 2 | NMaia 1, Lepticed7 1 | credit + share-alike |
| `audio/eo/calendar/` | 3 | Lingua Libre, via Commons | CC0 1 · CC BY-SA 4.0 1 · Public domain 1 | Balamutick 1, Lepticed7 1 | credit; share-alike on the BY-SA |
| `audio/eo/countries/` | 74 | Lingua Libre, via Commons | CC BY-SA 4.0 69 · CC0 5 | Castelobranco 56, Lepticed7 8 | credit; share-alike on the BY-SA |
| `audio/es/` | 665 | Lingua Libre, via Commons | CC BY-SA 4.0 483 · CC0 126 · CC BY 4.0 56 | AdrianAbdulBaha 257, Marreromarco 133 | credit; share-alike on the BY-SA |
| `audio/es/letters/` | 6 | Lingua Libre, via Commons | CC BY-SA 4.0 2 · CC0 2 · CC BY 4.0 2 | Marreromarco 2, Emanuelps27 2 | credit; share-alike on the BY-SA |
| `audio/es/texts/` | 4 | Lingua Libre, via Commons | CC0 2 · CC BY 4.0 1 · CC BY-SA 4.0 1 | Rodrigo5260 1, Precision27 1 | credit; share-alike on the BY-SA |
| `audio/es/calendar/` | 19 | Lingua Libre, via Commons | CC0 9 · CC BY-SA 4.0 7 · CC BY 4.0 3 | GlyphEnjoyer 8, Eavqwiki 3 | credit; share-alike on the BY-SA |
| `audio/es/countries/` | 75 | Lingua Libre + Commons | CC BY-SA 4.0 44 · CC0 18 · CC BY 4.0 6 · CC BY-SA 3.0 6 · Public domain 1 | Rodelar 39, Rodrigo5260 11 | credit; share-alike on the BY-SA |
| `audio/fr/` | 692 | Commons `Fr-*.ogg` (Shtooka Paris) + Lingua Libre | CC BY 2.0 fr 442 · CC0 192 · CC BY-SA 4.0 51 · CC BY-SA 3.0 5 · CC BY 4.0 2 | Vion Nicolas 442, Poslovitch 168 | credit; share-alike on the BY-SA |
| `audio/fr/letters/` | 5 | Lingua Libre, via Commons | CC BY-SA 4.0 5 | Sartus85 5 | credit + share-alike |
| `audio/fr/texts/` | 2 | Lingua Libre, via Commons | CC BY-SA 4.0 1 · CC0 1 | Sartus85 1, Poslovitch 1 | credit; share-alike on the BY-SA |
| `audio/fr/calendar/` | 19 | Shtooka + Lingua Libre | CC BY 2.0 fr 12 · CC0 7 | Vion Nicolas 12, Poslovitch 7 | credit all but the CC0 |
| `audio/fr/countries/` | 120 | Lingua Libre + Shtooka | CC0 71 · CC BY-SA 4.0 27 · CC BY-SA 3.0 14 · CC BY 2.0 fr 6 · Public domain 2 | Jules78120 39, Poslovitch 16 | credit; share-alike on the BY-SA |
| `audio/it/` | 600 | Lingua Libre + Wiktionary `It-*.ogg` | CC BY-SA 4.0 327 · CC0 231 · CC BY 4.0 28 · CC BY 3.0 us 10 · CC BY-SA 3.0 3 · CC BY-SA 2.0 1 | LangPao 325, XANA000 129 | credit; share-alike on the BY-SA |
| `audio/it/letters/` | 10 | Lingua Libre, via Commons | CC0 10 | XANA000 10 | none — a dedication |
| `audio/it/texts/` | 4 | Commons | CC0 3 · CC BY-SA 4.0 1 | XANA000 1, DanielParoliere 1 | credit; share-alike on the BY-SA |
| `audio/it/articles/` | 54 | Commons `It-<article> <word>.ogg` (Shtooka) | CC BY 3.0 us 54 | Marta Carbone, Association Shtooka 54 | attribution only |
| `audio/it/calendar/` | 19 | Lingua Libre + Shtooka | CC BY-SA 3.0 13 · CC BY 3.0 us 5 · CC0 1 | GerardM 11, Marta Carbone, Association Shtooka 5 | credit; share-alike on the BY-SA |
| `audio/it/countries/` | 69 | Lingua Libre + Commons | CC BY-SA 4.0 41 · CC0 24 · CC BY-SA 3.0 3 · Public domain 1 | Francyskus 41, Ciampix 24 | credit; share-alike on the BY-SA |
| `audio/sw/` | 623 | Commons `Sw-ke-*.flac` | CC BY-SA 4.0 623 | Waithera Were 622, Goethe-Institut Cameroon 1 | credit + share-alike |
| `audio/sw/calendar/` | 19 | Commons `Sw-ke-*.flac` | CC BY-SA 4.0 19 | Waithera Were 19 | credit + share-alike |
| `audio/sw/countries/` | 74 | Commons `Sw-ke-*.flac` | CC BY-SA 4.0 74 | Waithera Were 74 | credit + share-alike |
| `audio/uk/` | 664 | Commons `Uk-*.ogg` (Shtooka) | CC BY 3.0 us 663 · CC BY 2.0 fr 1 | Галя Раптова, Nicolas Vion 649, Женя Музика, Nicolas Vion 8 | attribution only |
| `audio/uk/letters/` | 33 | Commons `Аа – ukrainian.ogg` | CC BY-SA 4.0 33 | Tabrus 32, Tohaomg 1 | credit + share-alike |
| `audio/uk/texts/` | 1 | Commons `Uk-*.ogg` | CC BY 3.0 us 1 | Галя Раптова, Nicolas Vion 1 | attribution only |
| `audio/uk/calendar/` | 19 | Commons `Uk-*.ogg` (Shtooka) | CC BY 3.0 us 19 | Галя Раптова, Nicolas Vion 19 | attribution only |
| `audio/uk/countries/` | 70 | Lingua Libre, via Commons | CC BY-SA 4.0 63 · CC0 5 · CC BY 3.0 us 2 | Tohaomg 61, Renvoy 3 | credit; share-alike on the BY-SA |

The `articles/` sets are where a word may ship TWICE, and deliberately: the bare file is
what the learner's own language is read with, the article one what the target is heard as
(`../catalog/audio/README.md`). Five words ship only the article recording, which then answers
both ways it is asked rather than leaving the word silent. Both carry their own author and license row, so the credits
screen names Natschoba beside the German pack's eight groups and Marta Carbone beside the
Italian's fourteen — a second voice on 221 and 54 cards, not a replacement of the first.
Nothing else on Commons could have been used: article-form recordings exist for German
(one speaker) and Italian (one set), and for Spanish and French they do not exist at all —
the research and its counts are `data/reference/audio/README.md`'s.

**Three licenses are refused outright**, and the drills' own vocabularies are where Commons
first offered them (`scripts/audio-catalog.py`'s `UNSHIPPABLE_LICENSES`). None is a CC
license and none is shaped for media:

- **GFDL** obliges shipping the full license text and keeping a "Transparent copy"
  available — a document's terms, which a credits screen linking a deed does not meet.
  It cost two German month and weekday names.
- **GPLv3** is a software license whose copyleft reaches the work as a whole, and whose
  anti-tivoization and Installation Information terms are irreconcilable with App Store
  distribution. It cost sixteen of the nineteen Esperanto names, all one uploader
  (Kurso de Esperanto) — which is why that row reads 3 and not 19.
- **"Attribution"**, Commons' legacy bare template, asks for credit and names no versioned
  license, so there are no stated terms and no deed to link. Public domain also has no deed,
  but that is a waiver; this is a claim of rights without terms. It cost the Spanish `Chile`.

A refused row is a printed decision and the word falls to the device voice; an
*unlisted* license remains a hard stop, because the difference is whether anyone has
looked at it. Refusing only ever ships less, which is the safe side of the question —
but the Esperanto cost is large enough to be worth revisiting deliberately.

Share-alike reaches the **audio files only**.
CC carries no GPL-style linking clause,
so bundling BY-SA recordings leaves the Kotlin core, the UI and the catalog data unaffected.
The Ukrainian WORDS are the one pack free of share-alike entirely, and that is one
recording deep: a single BY-SA file taken for a gap the alphabet needed would end it,
as one already did once before being re-cut away.
That property is the `words` section's alone and stays intact — the fill added 174 words
under the same Shtooka attribution-only terms — but the uk FOLDER is now share-alike in
three places rather than one: the letters, and since the atlas landed, 63 of its 70 country
names. Lingua Libre is where Ukrainian toponyms exist at all (the Shtooka convention found
2 of 140), so this was the price of the atlas speaking Ukrainian, spent knowingly. Nothing else depends on the property,
since every other pack carries the obligation anyway — but it is worth spending
deliberately rather than by accident.
The French words are the near-miss beside it: 442 of 511 are one Shtooka voice under
BY 2.0 fr, attribution only, and each of those file pages states its own origin
("Male voice. Speaker from Paris, France.") — the only pack that can name its accent;
share-alike enters only through the 24-word Lingua Libre tail and the letters.
The eo letters cost no extra credit because the letter names ARE ordinary lexemes
(`bo`, `ĉo`, `ŭo`) — a phonemic orthography makes the word recording and the
letter-name recording the same sound, so the alphabet rides the word pack's main voice.
Ten of the eo word rows are the first PHRASE recordings any pack carries, matched
exactly (edge punctuation folded, nothing fuzzy) against the Commons phrasebook.

## 2. How the obligations are discharged

- **Provenance is versioned per file.** Every manifest entry carries its `author`, the
  original Commons filename as `source`, and the `sha256` of the shipped bytes; its license
  and that license's deed come from the manifest's own `authors` and `licenses` maps, which
  is where a voice's terms are authored once instead of on each of its hundreds of files
  (`../catalog/audio/README.md`). Factored, not thinned: every recording still resolves to a
  named speaker and a linked license, and the parser refuses a manifest whose maps do not
  cover what it credits. The unversioned pack workspace is research input; `catalog/audio/`
  is the record that ships.
- **Credits derive from the shipped manifests**, never from a hand-kept list:
  `Catalog.audioCredits()` groups per (language, author, license) with per-file rows,
  rendered by `App/Sources/Screens/CreditsView.swift` (sheet off Box settings)
  and `android/.../ui/AboutScreen.kt` (About screen) from that one API.
  A group expands to its recordings, each linking `File:<source>` on Commons,
  so attribution is checkable rather than merely present.
  BY and BY-SA groups are separate rows by construction — there is no blanket notice,
  and the screen can neither credit something not bundled nor miss something that is.
- **The untouched-transcode gate is a test, not a promise.**
  `CatalogAudioLintTest.audioFilesMatchTheirManifestHashes` re-hashes every committed mp3
  against its manifest entry; the converter verified the same digest right after the byte-copy.
  Re-encoding — loudness normalization included — would be an adaptation under BY-SA,
  so the gate is what keeps the packs' loudness differences a playback problem (§3) rather than a license one.
- **No file ships without a nameable author.**
  `noAudioAuthorIsUnattributable` rejects the placeholder set (`Own work`, `myself`, empty),
  which BY and BY-SA both make useless,
  and Commons' `… assumed (based on copyright claims)` wording with it:
  that names a bot's inference about the uploader, which reads as a credit and is a guess.
  `everyAudioFileShipsAndIsReferencedExactlyOnce` keeps uncredited bytes out of the bundle.
- **The converter drops rather than guesses** (`scripts/audio-catalog.py` + `scripts/audio_gates.py`,
  every decision printed): rows whose slug the catalog does not realize;
  rows whose recording speaks a different word than any visible form;
  rows colliding on one spoken form with differing bytes;
  and rows still unattributable after resolution against the Commons API
  (`extmetadata.Artist`, else the uploader as "Wikimedia Commons user X").

## 3. The analysis index — the "unmodified" claim stays true

The packs were recorded by different people on different equipment and share no loudness:
the word packs sit at a median −18.0 LUFS, the uk letters at −31.4,
and those letters open with about a second of dead air before they speak.

The correction is **not applied to the files**.
Each entry carries the numbers our own generator measured off the shipped bytes —
`gain` (dB from the full-range analysis target) and `gainPhone` (the phone-speaker plane, absent on letters and texts),
plus `lead` (dead air at the head, ms) —
and only a player ever applies them, picking the plane by its output route
(iOS through an EQ node, Android through `LoudnessEnhancer` and a seek).
Consequences, and this was the deciding argument:

- The shipped mp3 bytes remain **byte-identical Commons transcodes**, so **no adaptation is distributed**
  and share-alike is never triggered by anything we did.
- The credits' "Aufnahmen unverändert übernommen" / "Recordings shipped unmodified" line stays **accurate**.
- The `sha256` gate keeps meaning exactly what it says.
- A measurement of a file is our own factual data:
  it carries no license of its own, and it grants nobody anything.

What was measured, against which target and under which scheme is `scripts/audio-catalog.py`'s `ANALYSIS`.
The player-side mechanics are the platforms' business, not this doc's.

## 4. Text-to-speech: live only, never an asset

TTS covers what no recording exists for — every phrase, and the unrecorded half of de.
It is synthesized and spoken **at the moment the card asks**, and never written to a file:

- Apple's System Voices are Apple IP; the macOS SLA §2(F) confines their output to personal,
  non-commercial use and DTS is explicit that they cannot be commercialized as one's own.
  Live synthesis through `AVSpeechSynthesizer` is ordinary sanctioned API use; pre-rendering is not.
  No synthesis-to-file API is referenced anywhere in `App/Sources/Audio/`.
- iOS speaks only voices the user has installed, and has no Swahili voice at any tier,
  so an unrecorded Swahili word is silent rather than read in the wrong language.
- Android pins Google's engine (`com.google.android.tts`, offline Swahili included)
  and likewise only ever calls `speak()`.
  Its licensing question is the same shape as Apple's and is answered the same way.

The moment a synthetic voice were ever **bundled** instead of spoken live,
this posture would change (see §6.3).

## 5. Catalog data posture

**No non-commercial source was used anywhere, deliberately** — the app has product ambition,
and NC would foreclose it.
That cost the two richest morphology sources (VESUM for Ukrainian, Helsinki for Swahili).

Only one licensed source's output actually **ships**: Wikidata noun gender and plural, **CC0**,
chosen because it is CC0 — no attribution, no share-alike, no burden on catalog content.
Everything else was consulted as evidence and left no expression behind:
FreeDict (CC-BY-SA 3.0) generated candidate lists that were then re-picked and largely re-authored,
frequency lists broke ties, Tatoeba attested phrase wording.
Single dictionary headwords are facts, not expression — and the rule holds only while it holds:
a future language that *derives* `text` or `notes` from a BY-SA dictionary
would put the whole catalog under share-alike, a far larger commitment than a per-pack audio notice.
The evidence-only accounting for the newest language is
`data/orchestration/audio-langs-2026-07/es-content/final/REPORT.md` §7.

Notable rejections, one line each:

- **Forvo** — non-commercial terms, and API URLs expire after two hours; unbundleable either way.
- **PanLex** — relicensed from CC0 to CC BY-NC-SA; treated as rejected, kept only as a historical pointer.
- **Tatoeba audio** — 76.6% CC BY-NC-ND; the text corpus is fine, the recordings are not.
- **Meta MMS-TTS** — weights CC-BY-NC 4.0, which blocks build-time use too.
- **Coqui XTTS-v2** — CPML licenses "a model and its outputs" non-commercially, and the company is gone.
- **eSpeak NG / piper1-gpl / sherpa-onnx v1.x** — GPL-3.0, or statically linking something that is;
  fatal for a closed App Store binary (build-time phonemizing is fine, shipping it is not).
- **Piper `sw_CD-lanfrica`** — finetuned from research-only voice data over a non-profit-restricted audio Bible.
- **OPUS OpenSubtitles** — no explicit license at all; gray however good the volume.

## 6. Open items for the owner

1. **BY-SA §2(a)(5)(B) versus App Store DRM — the pre-submission gate.**
   The license forbids applying "Effective Technological Measures" to the shared material,
   and every App Store binary is FairPlay-encrypted;
   2094 of the 3597 files are BY-SA — all 467 sw, all 580 de, 33 of 524 uk, 366 of 498 es,
   239 of 451 it, 30 of 518 fr, 379 of 559 eo.
   Attribution, the other obligation, is already covered (§2).
   **Mitigation on record:** additionally publish the same recordings at a public un-DRM'd URL
   under the same licenses, in **separate per-language files** —
   the `catalog/audio/<lang>/` split already prepares exactly that shape.
   This needs a legal read before the first submission, not an engineering one.
2. **The es accent caveat.**
   The catalog and the alphabet file are authored in Peninsular Spanish (distinción),
   and the pack **does not claim to match it**: Commons states a license, a speaker and a recorder per file
   and no country or variety, so nothing about accent is asserted anywhere.
   Read each es file as "a native speaker says this word".
   The consequence is audible where the two meet — an es-MX voice speaks /s/ where the alphabet's c and z rows
   promise /θ/ — which is a content decision tracked in `backlog.md`, not a license one.
3. **Azure S0 terms, if the gaps are ever filled by synthesis.**
   Azure AI Speech is the only provider covering every target language including native `sw-KE`
   with an express commercial grant to the output (~$1–4 for the whole catalog, one-off).
   Conditions if it is ever used: the **paid S0 tier only** (F0 carries no commercial grant),
   archive the dated Product Terms with the release, and disclose synthetic voices in-app.
   Google Cloud TTS would additionally need counsel on Service Terms §20(d),
   which bars generative-AI services in products likely accessed by under-18s — a learning app.

Coverage gaps, install size and the index's missing peak term are engineering debts,
filed in `backlog.md` rather than here.
