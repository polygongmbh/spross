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
# bar only refuses the clearly bad; where it is good, the bar is higher and a word under it
# is left to the voice. Both sit where they refuse nothing a listener rated passable: the
# lowest-scoring take heard at all (es `litro`, 1.51) was rated okay.
NO_VOICE_MOS_FLOOR = 1.5
VOICED_MOS_FLOOR = 1.5


def mos_floor(lang):
    return VOICED_MOS_FLOOR if lang in WELL_VOICED else NO_VOICE_MOS_FLOOR


_verdicts = None


def verdict(sha256):
    """What a listener rated these bytes — bad, mediocre, okay, good, great — or None."""
    global _verdicts
    if _verdicts is None:
        with open(VERDICTS, encoding='utf-8') as handle:
            _verdicts = {row['sha256']: row['verdict']
                         for row in csv.DictReader(handle, delimiter='\t')}
    return _verdicts.get(sha256)


def is_rejected(sha256):
    """Heard as bad: never ships, whatever it scores."""
    return verdict(sha256) == 'bad'


def is_doubted(sha256):
    """Heard as bad or mediocre: every other take of the word is tried against it."""
    return verdict(sha256) in ('bad', 'mediocre')
