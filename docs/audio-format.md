# Why the recordings are mp3

What the bundled audio costs, why the codec is not ours to pick,
and what switching it would actually buy -- measured 2026-08-22.

Whose the recordings are and what each license obliges is `audio-licensing.md`;
the engine rule is `../kern/docs/audio.md`,
the manifest schema `../catalog/audio/README.md`.

## What ships

Every file is 44.1 kHz stereo, VBR spanning 81-231 kbps with a median of 142,
median clip 1.14 s.
How many files and what they weigh is `scripts/audio-coverage.py --credits`,
counted from the manifests.

## The codec is Wikimedia's choice, not ours

Commons publishes each source file plus its own transcodes,
and mp3 is the only transcode it offers --
`.opus`, `.ogg` and `.oga` all 404 against `upload.wikimedia.org/.../transcoded/`.
Since what ships is the untouched Commons transcode (`audio-licensing.md` section 3),
the format follows from that decision.

Shipping a different codec means WE produce the adaptation.
That is affordable -- it costs a credits wording change and a share-alike offer --
but it demotes the `sha256` gate from a provenance proof to an integrity check,
and it removes the reason the loudness correction is a measurement rather than an edit.

## The stereo is already free -- do not "fix" it

Every Commons source is mono; the transcoder emits stereo.
Every file measured is joint stereo, mid/side on 97.9% of frames,
and 99.4% measure a side signal at the -91 dB decoder floor --
the second channel is a side signal of zeros.

Repacking the corpus as mono recovers **2.8 MB, 3.76%**,
of which 2.6 MB is per-frame `side_info` shrinking 32 to 17 bytes
and only 0.17 MB is audio data.
23 files are genuinely stereo (17 de, 3 it, 2 eo, 1 sw).

## What a transcode would save, and what it would cost

Opus 32 kbps mono from the Commons original, measured over 71 files across all nine packs,
lands at **26% of the shipped mp3**.
Where the original is lossless (eo, es, it, sw, de articles -- roughly 2200 files)
encoding from it skips a generation;
for Vorbis originals (de, fr, uk, it articles) the mp3 is the better source --
Vorbis artifacts cost Opus bits (uk 28% from the original vs 25% from the mp3).

The obstacle is the container, not the codec.

| | iOS 17.0 | Android 26 | one pipeline | vs mp3 |
|---|---|---|---|---|
| Ogg Opus | no | yes | -- | 26% |
| CAF Opus | yes | no | -- | 29% |
| Opus, container per platform | yes | yes | **no -- two encodes** | ~22 MB |
| AAC-LC `.m4a` | yes | yes | **yes** | ~31 MB |

iOS has the Opus **decoder** but no Ogg demuxer:
`AVAudioFile(forReading:)` rejects Ogg with `kAudioFileUnsupportedFileTypeError`
while the same bitstream in CAF opens.
`Oggf` is the single entry separating iOS 26.5's readable types from 17.5's,
and there is no public `kAudioFileOggType` to feature-check against.
Android decodes Opus in Ogg from API 21, nine levels below `minSdk = 26`, and cannot read CAF.

Splitting the container is not a remux:
`ffmpeg` refuses to mux Opus into CAF,
`afconvert` only encodes and its Opus encoder largely ignores `-b`,
so iOS and Android would need separate encodes and separate output trees.

## The decision

mp3 stays.
Opus saves roughly three quarters of the audio bytes
against two encode pipelines, two container trees,
a rewritten converter and lint gate,
and a dependency on an undocumented container path.

If bundle size becomes the binding constraint,
the first lever is delivery, not codec:
per-language on-demand packs are a sixth to a fifth of the corpus each.
If the corpus is ever transcoded anyway,
AAC-LC `.m4a` is the better target --
one pipeline, both platforms, no undocumented API, ~40% of the mp3 bytes.

The iOS half of this expires when `project.yml`'s deployment target rises
past the release that added Ogg.
The check is whether `kAudioFileGlobalInfo_ReadableTypes` reports `Oggf`
on the lowest supported runtime.
