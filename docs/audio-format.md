# Why the recordings are mp3

What the bundled audio costs, why the codec is not ours to pick,
and what switching it would actually buy — measured 2026-08-22.

Whose the recordings are and what each license obliges is `audio-licensing.md`;
this doc states the format decision, not the legal posture.
The engine rule is `../kern/docs/audio.md`, the manifest schema `../catalog/audio/README.md`.

## What ships

3606 tracked files, **74.4 MB** of bytes, 72.5 minutes total, median clip 1.14 s.
Every file is 44.1 kHz stereo, VBR spanning 81–231 kbps with a median of 142.

`du` reports 80 MiB: 3606 files averaging 20.6 KB pad to 4 KiB blocks,
so roughly 6 MB of the apparent size is allocation, not payload.

## The codec is Wikimedia's choice, not ours

Commons publishes each source file plus its own transcodes,
and mp3 is the only transcode it offers — `.opus`, `.ogg` and `.oga` all 404
against `upload.wikimedia.org/wikipedia/commons/transcoded/`.
Since what ships is the untouched Commons transcode (`audio-licensing.md` §3),
the format follows from that decision rather than from any judgment about compression.

Shipping a different codec means WE produce the adaptation.
That is affordable — it costs a credits wording change and a share-alike offer on the derived
audio, neither of which reaches the app — but it demotes the `sha256` gate
from a provenance proof to an integrity check,
and it removes the reason the loudness correction is a measurement rather than an edit.

## The stereo is already free — do not "fix" it

Every Commons source is mono; the transcoder emits stereo.
This looks like a doubled payload and is not one.
All 3606 files are joint stereo, mid/side on 97.9% of frames,
and 99.4% of them measure a `(L−R)/2` side signal at the −91 dB decoder floor —
the second channel is a side signal of zeros.

Repacking the corpus as mono recovers **2.8 MB, 3.76%**,
of which 2.6 MB is per-frame `side_info` shrinking 32→17 bytes
and only 0.17 MB is audio data.
23 files are genuinely stereo (17 de, 3 it, 2 eo, 1 sw; loudest `it/fridge.mp3`, `it/sofa.mp3`).

## What a transcode would save, and what it would cost

Opus 32 kbps mono from the Commons original, measured over 71 files across all nine packs,
lands at **26% of the shipped mp3**. Sourcing matters per pack, not uniformly:
where the original is lossless (eo, es, it, sw, de articles — roughly 2200 files)
encoding from it costs the same as from the mp3 and skips a generation,
while for Vorbis originals (de, fr, uk, it articles) the mp3 is the better source —
Vorbis artifacts cost Opus bits (uk 28% from the original vs 25% from the mp3).

The obstacle is the container, not the codec.

| | iOS 17.0 | Android 26 | one pipeline | vs mp3 |
|---|---|---|---|---|
| Ogg Opus | no | yes | — | 26% |
| CAF Opus | yes | no | — | 29% |
| Opus, container per platform | yes | yes | **no — two encodes** | ~22 MB |
| AAC-LC `.m4a` | yes | yes | **yes** | ~31 MB |

iOS 17.5 has the Opus **decoder** but no Ogg demuxer:
`AVAudioFile(forReading:)` rejects Ogg with `kAudioFileUnsupportedFileTypeError`
while the same bitstream in CAF opens at an identical frame count.
`Oggf` is the single entry separating iOS 26.5's readable types from 17.5's,
and there is no public `kAudioFileOggType` to feature-check against.
Android decodes Opus in Ogg from 5.0, nine API levels below `minSdk = 26`, and cannot read CAF.

Splitting the container is not a remux: `ffmpeg` refuses to mux Opus into CAF,
`afconvert` only encodes, and its Opus encoder largely ignores `-b`
(6 kbps requested still emits 28), so iOS and Android would need separate encodes
from the same source and separate output trees.

## The decision

mp3 stays. Opus saves ~52 MB against two encode pipelines, two container trees,
a rewritten converter and lint gate, and a dependency on an undocumented container path.

If bundle size becomes the binding constraint, the first lever is delivery, not codec:
per-language on-demand packs are 7–13 MB each and change nothing about provenance.
If the corpus is ever transcoded anyway, AAC-LC `.m4a` is the better target than Opus —
one pipeline, both platforms, no undocumented API, 74 → ~31 MB.

The iOS half of this expires when `project.yml`'s deployment target rises past the release
that added Ogg. The check is whether `kAudioFileGlobalInfo_ReadableTypes` reports `Oggf`
on the lowest supported runtime.
