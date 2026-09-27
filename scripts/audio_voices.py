"""Which recordings a listener's ear stands behind, and how high the bar is per language.

The noise measurement (`audio_measure.noise_margin`) does not rank recordings the way the
ear does (`../docs/audio-verdicts.tsv`), so two rulings sit above it: voices heard as
consistently great are trusted unheard, and the bar a recording must clear follows what
the learner hears without it.
"""

# GOLD voices: heard as consistently great, best first. Every picker takes their take of a
# word first (the workspace's `build-audio-pack.py`, `lingualibre.pick`, `requalify-pack.py`,
# `consolidate-pack.py --preferred`), and no score refuses or replaces one of their takes
# short of clearly bad (`is_trusted`).
GOLD_VOICES = {
    "de": ["Holunder Gabriel", "Natschoba"],
}

# NOISY voices: heard as noisy across their takes, not in one file. Every take of theirs is
# reconsidered whatever it measures and gives way to any passable take of the word; where the
# device voice is good (WELL_VOICED) and nothing else exists, the voice takes over
# (`audio-catalog.py --prune-noisy`).
NOISY_VOICES = {
    "de": ["MiER", "Wikimedia Commons user Jakob.scholbach"],
}

# Languages whose device voice is good on both platforms: without a recording the card still
# speaks, so a recording has to beat that voice rather than silence.
WELL_VOICED = frozenset({"de", "es", "fr", "it", "uk"})

# The quietest `snr` a recording may ship at. Where the device voice is missing (eo, sw) the
# bar only refuses the clearly bad: a listening pass heard 38.1–39.2 as noisy. Where it is
# good, the bar is higher and a word under it is left to the voice — at the cost of some takes
# the ear rated good but the score undersells (39.6, 39.7, 43.5), which is the listener's
# chosen trade.
NO_VOICE_SNR_FLOOR_DB = 39.5
VOICED_SNR_FLOOR_DB = 45.0


def snr_floor(lang):
    return VOICED_SNR_FLOOR_DB if lang in WELL_VOICED else NO_VOICE_SNR_FLOOR_DB


def is_gold(lang, author):
    return author in GOLD_VOICES.get(lang, [])


def is_trusted(lang, author, snr):
    """A gold take is exempt from every score — unless it measures clearly bad, under the
    bar that refuses the clearly bad anywhere, where the ear's ruling on the voice is no
    longer evidence about the file (Holunder Gabriel's great Karotte measures 42.9)."""
    return is_gold(lang, author) and (snr is None or snr >= NO_VOICE_SNR_FLOOR_DB)


def is_noisy(lang, author):
    return author in NOISY_VOICES.get(lang, [])
