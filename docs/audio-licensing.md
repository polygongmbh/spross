# Audio & content licensing

The record for the ship/legal questions the bundled audio raises:
what is in the app, whose it is, what each license asks for, and where the app answers it.
Successor to `docs/pronunciation-plan.md`, deleted once the feature shipped.

The engine rule is `../kern/docs/audio.md`, the file format `../catalog/README.md` § Audio,
and the per-file truth is the per-language manifests themselves — this doc states the posture,
not the schema.
The pack research (how each source was found, what was rejected, the coverage gaps)
lives outside the repo in `data/reference/audio/README.md`.

## 1. What ships, and under what

3597 mp3 files, ~71 MB, all of them Wikimedia Commons transcodes:
**2094 CC BY-SA · 988 CC BY · 515 CC0**.

| Pack | Files | Source | Licenses | Speakers | Obligation |
|---|---|---|---|---|---|
| `audio/de/` | 570 words | Commons `De-*.ogg` | BY-SA 4.0 371 · BY-SA 3.0 199 | 8 credit groups, Jeuwre 356 | credit + share-alike, whole pack |
| `audio/de/letters/` | 8 letters | Commons `De-<letter>.ogg` | BY-SA 4.0 6 · BY-SA 3.0 2 | Jeuwre 6, T.Voekler 2 | credit + share-alike |
| `audio/de/texts/` | 2 words | Commons `De-*.ogg` | BY-SA 4.0 1 · BY-SA 3.0 1 | Jeuwre 1, joni 1 | credit + share-alike |
| `audio/es/` | 488 words | Lingua Libre, via Commons | BY-SA 4.0 363 · CC0 88 · BY 4.0 37 | 15 credit groups (AdrianAbdulBaha 195, Marreromarco 88) | credit all but the 88 CC0; share-alike on 363 |
| `audio/es/letters/` | 6 letters | Lingua Libre, via Commons | CC0 2 · BY 4.0 2 · BY-SA 4.0 2 | 4 groups | credit all but the CC0 |
| `audio/es/texts/` | 4 words | Lingua Libre, via Commons | CC0 2 · BY 4.0 1 · BY-SA 4.0 1 | 4 groups | credit all but the CC0 |
| `audio/sw/` | 467 words | Commons `Sw-ke-*.flac` | BY-SA 4.0, whole pack | Waithera Were 466, Goethe-Institut Cameroon 1 | credit + share-alike, whole pack |
| `audio/uk/` words | 490 words | Commons `Uk-*.ogg` (Shtooka) | BY 3.0 us 489 · BY 2.0 fr 1 | Галя Раптова / Nicolas Vion 476, 2 further groups | attribution only |
| `audio/uk/letters/` | 33 letters | Commons `Аа – ukrainian.ogg`, one Lingua Libre | BY-SA 4.0 | Tabrus 32, Tohaomg 1 | credit + share-alike |
| `audio/uk/texts/` | 1 word | Commons `Uk-*.ogg` | BY 3.0 us | Галя Раптова / Nicolas Vion | attribution only |
| `audio/it/` | 437 words | Lingua Libre + Wiktionary `It-*.ogg`, via Commons | BY-SA 4.0 234 · CC0 184 · BY 3.0 us 10 · BY 4.0 5 · BY-SA 3.0 3 · BY-SA 2.0 1 | 14 credit groups (LangPao 232, XANA000 105) | credit all but the 184 CC0; share-alike on 238 |
| `audio/it/letters/` | 10 letters | Lingua Libre, via Commons | CC0 | XANA000 | none — a dedication |
| `audio/it/texts/` | 4 words | Commons | CC0 3 · BY-SA 4.0 1 | 4 groups | credit the one BY-SA |
| `audio/fr/` | 511 words | Commons `Fr-*.ogg` (Shtooka Paris) + Lingua Libre | **BY 2.0 fr 442 — attribution only** · CC0 45 · BY-SA 4.0 22 · BY-SA 3.0 2 | 15 credit groups (Vion Nicolas 442, Poslovitch 36) | credit all but the 45 CC0; share-alike on the 24-word tail only |
| `audio/fr/letters/` | 5 letters | Lingua Libre, via Commons | BY-SA 4.0 | Sartus85 | credit + share-alike |
| `audio/fr/texts/` | 2 words | Lingua Libre, via Commons | BY-SA 4.0 1 · CC0 1 | 2 groups | credit the BY-SA |
| `audio/eo/` | 529 words | Lingua Libre + the Commons Esperanto phrasebook | BY-SA 4.0 349 · CC0 180 | 12 credit groups (Lepticed7 341, Poslovitch 100) | credit all but the 180 CC0; share-alike on 349 |
| `audio/eo/letters/` | 28 letters | Lingua Libre word recordings | BY-SA 4.0 | Lepticed7 | credit + share-alike |
| `audio/eo/texts/` | 2 words | Lingua Libre, via Commons | BY-SA 4.0 | Lepticed7, NMaia | credit + share-alike |

Share-alike reaches the **audio files only**.
CC carries no GPL-style linking clause,
so bundling BY-SA recordings leaves the Kotlin core, the UI and the catalog data unaffected.
The Ukrainian WORDS are the one pack free of share-alike entirely, and that is one
recording deep: a single BY-SA file taken for a gap the alphabet needed would end it,
as one already did once before being re-cut away. Nothing else depends on the property,
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

- **Provenance is versioned per file.** Every manifest entry carries `license`, `licenseUrl`,
  `author`, the original Commons filename as `source`, and the `sha256` of the shipped bytes.
  The unversioned pack workspace is research input; `catalog/audio/` is the record that ships.
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
Each entry carries two optional numbers our own generator measured off the shipped bytes —
`gain` (dB from the catalog's analysis target) and `lead` (dead air at the head, ms) —
and only a player ever applies them (iOS through an EQ node, Android through `LoudnessEnhancer` and a seek).
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
