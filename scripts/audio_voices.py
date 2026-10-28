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
WELL_VOICED = frozenset({"de", "en", "es", "fr", "it", "uk"})

# The lowest `mos` a recording may ship at. Where the device voice is missing (eo, sw) the
# bar only refuses the clearly bad: under 1.4 a listener heard only bad takes (it Francyskus).
# Where it is good, a recording has to beat that voice, and a mediocre one does not: under 1.8
# most takes were heard bad or mediocre (17 of 25 in the well-voiced languages, 2026-10-27),
# over it most passed. A take a listener passed ships whatever it scores.
NO_VOICE_MOS_FLOOR = 1.3
VOICED_MOS_FLOOR = 1.8


def mos_floor(lang):
    return VOICED_MOS_FLOOR if lang in WELL_VOICED else NO_VOICE_MOS_FLOOR


# Under this `mos` a shipped take goes up for a sweep: every other take of its word is fetched
# and the best one may replace it. Well over every floor, because here the score only ranks.
RECONSIDER_MOS = 2.8

# What a challenger has to beat a passable incumbent by: a swap costs a voice change and a
# re-credit, and a single file's score strays by a few tenths. A consolidation gives up strictly
# less for a tidier credits screen, so neither picker undoes the other.
SWAP_MARGIN = 0.3

# Takes a voice ships in a language before it counts as established: every new speaker is another
# voice change on the cards and another credit group, so a picker takes an established voice's
# take over a slightly better stranger's. A voice with ONE_OFF_TAKES or fewer is moved onto an
# established one wherever it can be.
ESTABLISHED_TAKES = 4
ONE_OFF_TAKES = 3

# The most energy above 6 kHz (`audio_measure.brightness`) a consolidation may give up: a take lopped
# off at 7 kHz has no hiss because it has nothing up there, scores clean and sounds like a telephone.
DULLNESS_DB = 12.0


# The least a take's sample peak may stand over its integrated loudness. Speech keeps its
# stress and consonant peaks well above its average; a take limited at the source has had
# them flattened into a harsh, screamy sound the score does not hear and no gain undoes
# (sw `kuuma`: 1.6 dB). Every voice of every pack keeps 8.5 dB or more at its 5th
# percentile, while Waithera Were's sw takes sit at a median 7.6 (measured 2026-10-05).
SQUASH_FLOOR_DB = 8.0


def is_squashed(peak, loudness):
    """Limited at the source: sample peak dBFS under SQUASH_FLOOR_DB over integrated LUFS."""
    return peak is not None and loudness is not None and peak - loudness < SQUASH_FLOOR_DB


# The loudest a breath burst (`audio_measure.pop`) may sound against the word's own loudest stretch.
# Of 13 takes heard for pops, the 4 at +1 dB or over all pop (fr nom de famille +5.2, de lieben +5.9),
# the 5 heard clean all stay under (fr enseignante, sw samaki +0.4); 4 heard popping escape the measure
# (sw kwaheri, de die Brücke, sw mboga, de pullover) (2026-10-28).
POP_DB = 1.0


def is_popping(pop):
    """Breath strikes the microphone: `audio_measure.pop` at POP_DB or over."""
    return pop is not None and pop >= POP_DB


# POOR voices: heard as bad across their takes, not in one file — what the score cannot be
# trusted to catch (it Francyskus, all 7 heard bad for background noise, scores up to 2.3).
# Every take of theirs counts as heard bad, except a file a listener passed on its own.
POOR_VOICES = {
    "it": ["Francyskus"],
}

# DOUBTED voices: heard as mediocre across their takes, and still preferred over the device voice
# (es Marreromarco, echoey on headphones and fine on a phone speaker, 2026-10-22). A sweep swaps
# their takes for another voice's that is not clearly worse, and never picks one of theirs, except a
# file a listener passed on its own.
DOUBTED_VOICES = {
    "es": ["Marreromarco"],
}

# The most one side of a take may stand over the other. A recorder that took the voice on
# one channel only plays it in one ear on headphones: fr Clément Bucco-Lechat's country names
# ran 13–25 dB lopsided, and es UofG Language Modules was heard one-sided at 7–8 dB, while
# every other shipped take stays under 2.5 (measured 2026-10-22). Speakers mix the sides in the
# room, so a lopsided take heard on them passes for good (UofG llave at 8.3 dB): headphones decide.
ONE_SIDED_DB = 5.0


# The longest a take's speech (`audio_measure.speech_span`) may run for the text it is filed under:
# a base for the breath around any word plus a time per letter. Past it a take says more than its
# key — es Arca's "Argentina" and "Uruguay" are a sentence each — or its hum and clicks reach so far
# into the file that the span cannot find the word. 6 of 9074 shipped takes ran past it, all heard as
# wrong or in that run, the next one 0.03 s under (2026-10-22).
SPEECH_BASE_S = 1.5
SPEECH_PER_LETTER_S = 0.15


def is_overlong(speech, text):
    """Speaks longer than [text] takes to say: [speech] seconds over SPEECH_BASE_S plus SPEECH_PER_LETTER_S
    a letter."""
    return speech > SPEECH_BASE_S + SPEECH_PER_LETTER_S * sum(char.isalpha() for char in text)


def is_one_sided(balance):
    """One side near silent: `audio_measure.side_balance` over ONE_SIDED_DB."""
    return balance is not None and balance > ONE_SIDED_DB


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
    return author in POOR_VOICES.get(lang, ()) and not is_passed(sha256)


def is_rejected(sha256, lang=None, author=None):
    """Heard as bad, or by a POOR voice: never ships, whatever it scores."""
    return verdict(sha256) == 'bad' or _poor(sha256, lang, author)


def is_voice_doubted(sha256, lang, author):
    """By a DOUBTED voice, and not passed on its own."""
    return author in DOUBTED_VOICES.get(lang, ()) and not is_passed(sha256)


def is_doubted(sha256, lang=None, author=None):
    """Heard as bad or mediocre, or by a POOR voice: every other take of the word is tried
    against it."""
    return verdict(sha256) in ('bad', 'mediocre') or _poor(sha256, lang, author)


def heard_over(sha256, other):
    """A listener rated [sha256] above [other]: a challenger heard worse than the take it would replace."""
    ranks = ['bad', 'mediocre', 'okay', 'good', 'great']
    mine, theirs = verdict(sha256), verdict(other)
    return mine in ranks and theirs in ranks and ranks.index(mine) > ranks.index(theirs)


def is_passed(sha256):
    """Heard as okay or better: judged as itself rather than as its POOR or DOUBTED voice."""
    return verdict(sha256) in ('okay', 'good', 'great')


def is_fair(sha256):
    """Heard as okay, which leans to mediocre: it ships, and a sweep tries every other take of its word."""
    return verdict(sha256) == 'okay'
