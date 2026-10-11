"""The ANALYSIS INDEX: what the players correct a recording by, measured off its untouched bytes.

`gain`/`cap`/`lead`/`gate` plus `mos` per entry, re-derived by `audio-catalog.py --reindex`
and written by every path that ships a take (`audio_manifest.py`).
"""
import math
import os

import audio_measure

FFMPEG = os.environ.get('FFMPEG', 'ffmpeg')

# The PLAYBACK ANALYSIS INDEX (user ruling 2026-08-01). The packs were recorded by
# different people on different equipment and do not share a loudness; the uk letters are
# both the quietest and the latest to start speaking. Re-encoding them is out — that is an
# adaptation under BY-SA, and it would break the untouched-transcode gate — so what
# corrects them is our own MEASUREMENT of the untouched bytes, carried in the manifest and
# applied by the player. Measurement data carries no license of its own, the credits'
# "unmodified" claim stays true, and `sha256` keeps meaning exactly what it says.
#
# ONE GAIN for every output, `gain` = `target_phon` minus the recording's perceived loudness
# (`audio_measure.perceived_loudness`, ISO 532-1 N5). The target is set so the catalog-wide
# median word gain is where the LUFS target (-18) had it; only the balance between voices moved
# (2026-10), and it moved by ear as well: sw, a squashed, band-limited voice, plays ~2 dB under
# where flat R128 energy put it. A second, phone-speaker gain measured through a home-made roll-off
# was dropped (2026-10): no standard phone weighting exists, and on the device it turned the
# sw pack down far past where the ear put it.
#
# The rule was ≤ 6 dB → attenuate everything down to the quietest class; past that the
# whole app would whisper, so the scheme is BOOST against the pack median: letters take
# up to +20 dB and the players need a boost path. (Letters also open with a median 1077 ms
# of dead air, against 173 ms for words.)
#
# A boost is also a CLIPPING risk, so the loudness number never decides a gain alone: the
# player adds it to samples that already peak where they peak, and past full scale iOS's EQ
# hard-clips while Android's `LoudnessEnhancer` compresses — one number, two sounds, neither
# the recording. Every gain is therefore CAPPED at the headroom its own file has, measured on
# the same decode: `gain = min(loudness gain, PEAK_CEILING_DBFS - peak)`, floored to the
# decimal it ships at so rounding can never spend the margin. The cap only ever lowers; the
# files it binds land under the loudness target instead of distorting: user ruling
# 2026-08-01, quiet is the lesser loss.
#
# What the cap held back ships beside the gain as `cap`, because the cap is only
# true at FULL VOLUME. A listening run's bedtime ramp attenuates before the boost is applied
# and opens exactly that much headroom again, so a player under a fade can hand the deficit
# back — as much of it as the ramp has already taken off — and the word lands on the loudness
# target after all (`fadedGainDb`). It binds 5-25% of every pack but sw, which is the loud
# one and is never capped at all, so without this the fade made a run's levels DRIFT APART
# by pack rather than merely fall.
ANALYSIS = {
    'scheme': 'boost',
    'target_phon': 86.1,
    'ffmpeg': 'ffmpeg version 9.0.2',
}

# A recording's first 50 ms of near-silence is its attack, not dead air — starting past it
# clips the consonant off the front. Everything before that the player may skip.
LEAD_KEEP_MS = 50
# ±20 dB is 10× amplitude and the point where a measurement is likelier broken than the
# recording. uk `ж` (-37.4 LUFS, +20.7 measured) is the one entry the clamp catches today.
GAIN_LIMIT_DB = 20.0
# The ceiling a boosted sample may reach. 1 dB of full scale stays unspent: the sample peak
# we measure is not the inter-sample peak a resampler reconstructs, and the players' gain
# stages add their own ringing on top of it.
PEAK_CEILING_DBFS = -1.0


# How far above its measured noise a recording's playback gate opens. Enough that the
# noise's own swing does not flutter the gate, little enough that the word is never under it.
GATE_MARGIN_DB = 6.0
# kern `Playback.GATE_FLOOR_DB`: below it a file is digital silence and needs no gate.
GATE_FLOOR_DB = -100.0

# The fields [playback_index] writes onto an entry.
INDEX_FIELDS = ('gain', 'cap', 'lead', 'gate', 'mos')


def playback_index(loudness, leading, peak, noise, loudest, mos, perceived):
    """The optional `gain`/`cap`/`lead`/`gate` plus `mos` for one entry — absent when there is nothing to say.

    `mos` is how good the take sounds (`audio_measure.mos`). Unlike the other fields it changes
    no playback — it is carried so the lint can see the SHAPE of a pack and refuse a rebuild
    that quietly reintroduces the noise a previous one removed, and so a fill or prune can
    refuse the clearly bad. Measured, never applied: filtering the file would be an adaptation
    under BY-SA and would break the sha256 that pins it.
    """
    full = round(min(GAIN_LIMIT_DB, max(-GAIN_LIMIT_DB, ANALYSIS['target_phon'] - perceived)), 1)
    # why: floor, never round — a gain rounded up to the shipped decimal spends the safety
    # margin it was granted, and the file it was granted for is the one already near clipping.
    headroom = math.floor((PEAK_CEILING_DBFS - peak) * 10) / 10
    gain = min(full, headroom)
    index = {}
    if gain:
        index['gain'] = gain
    # What the ceiling held back, for a player that has attenuated its way to the headroom
    # again (see [ANALYSIS]). Absent means the loudness number stood as measured.
    cap = round(full - gain, 1)
    if cap:
        index['cap'] = cap
    lead = max(0, round(leading * 1000) - LEAD_KEEP_MS)
    if lead:
        index['lead'] = lead
    if mos is not None:
        index['mos'] = round(mos, 2)
    if noise is not None:
        # why: the noise sits `noise` dB under the loudest frame; a gate a few dB above it
        # quiets the hiss in the pauses and never reaches the word (kern `Playback`).
        gate = round(loudest - noise + GATE_MARGIN_DB, 1)
        if GATE_FLOOR_DB <= gate < 0:
            index['gate'] = gate
    return index


def analyze(paths):
    """{path: playback index, or None for a file that decodes to silence} — one batched pass."""
    measured = audio_measure.measure_all(FFMPEG, list(paths))
    return {path: None if levels[0] is None or levels[2] is None else playback_index(*levels)
            for path, levels in measured.items()}
