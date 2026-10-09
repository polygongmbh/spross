"""How high the bar is per language, and where a listener's ear overrules the score.

The quality score (`audio_measure.mos`) ranks voices the way the ear does, but single files
stray from their voice by a few tenths, so it only refuses the clearly bad; picking the best
of several takes is where it does its work. Where a listener has heard a file, their verdict
(`../docs/audio-verdicts.tsv`, by sha256) decides instead.
"""
import csv
import os

VERDICTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs',
                        'audio-verdicts.tsv')

# Languages whose device voice is good on both platforms: without a recording the card still
# speaks, so a recording has to beat that voice rather than silence.
WELL_VOICED = frozenset({"de", "es", "fr", "it", "uk"})

# The lowest `mos` a recording may ship at. Where the device voice is missing (eo, sw) the
# bar only refuses the clearly bad; where it is good, the bar is a notch higher and a word
# under it is left to the voice. Under 1.4 a listener heard only bad takes (it Francyskus);
# from 1.42 up voices split by speaker, not by score (es Marreromarco okay at 1.42 and good
# at 1.46, uk Галя Раптова good throughout 2.2–2.4), so the score cannot draw a higher line.
NO_VOICE_MOS_FLOOR = 1.3
VOICED_MOS_FLOOR = 1.4


def mos_floor(lang):
    return VOICED_MOS_FLOOR if lang in WELL_VOICED else NO_VOICE_MOS_FLOOR


# The least a take's sample peak may stand over its integrated loudness. Speech keeps its
# stress and consonant peaks well above its average; a take limited at the source has had
# them flattened into a harsh, screamy sound the score does not hear and no gain undoes
# (sw `kuuma`: 1.6 dB). Every voice of every pack keeps 8.5 dB or more at its 5th
# percentile, while Waithera Were's sw takes sit at a median 7.6 (measured 2026-10-05).
SQUASH_FLOOR_DB = 8.0


def is_squashed(peak, loudness):
    """Limited at the source: sample peak dBFS under SQUASH_FLOOR_DB over integrated LUFS."""
    return peak is not None and loudness is not None and peak - loudness < SQUASH_FLOOR_DB


# POOR voices: heard as bad across their takes, not in one file — what the score cannot be
# trusted to catch (it Francyskus, all 7 heard bad for background noise, scores up to 2.3).
# Every take of theirs counts as heard bad, except a file a listener passed on its own.
POOR_VOICES = {
    "it": ["Francyskus"],
}

_verdicts = None


def verdict(sha256):
    """What a listener rated these bytes — bad, mediocre, okay, good, great — or None."""
    global _verdicts
    if _verdicts is None:
        with open(VERDICTS, encoding='utf-8') as handle:
            _verdicts = {row['sha256']: row['verdict']
                         for row in csv.DictReader(handle, delimiter='\t')}
    return _verdicts.get(sha256)


def _poor(sha256, lang, author):
    return author in POOR_VOICES.get(lang, ()) and not is_vouched(sha256)


def is_rejected(sha256, lang=None, author=None):
    """Heard as bad, or by a POOR voice: never ships, whatever it scores."""
    return verdict(sha256) == 'bad' or _poor(sha256, lang, author)


def is_doubted(sha256, lang=None, author=None):
    """Heard as bad or mediocre, or by a POOR voice: every other take of the word is tried
    against it."""
    return verdict(sha256) in ('bad', 'mediocre') or _poor(sha256, lang, author)


def is_vouched(sha256):
    """Heard as okay or better: kept whatever the score or the squash measure says of it."""
    return verdict(sha256) in ('okay', 'good', 'great')
