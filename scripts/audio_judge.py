"""May this take ship — one answer for every path that ships a recording or keeps one.

A take is a dict `lang section key source author license path` (plus `sha256` where known):
the Commons file it came from, who is credited under what, and its bytes on disk.
`judge` answers with the take's `mos` and four findings, and each caller's policy is one line
over them — a fill and a ship take what is not `refused`, a prune removes what is.

  · `refused` — (reason, detail) of the first of: license refused or unknown, author names
    nobody, a Lingua Libre name that is not its speaker saying its form, decodes to silence,
    heard as bad or by a POOR voice, under the language's floor, one-sided, speaking longer than its
    key takes to say, gain past the limit;
  · `hot` — squashed by a limiter at the source;
  · `doubted` — heard as mediocre or worse, by a POOR voice, or under the reconsider bar;
  · `vouched` — heard as okay or better.

The source checks need no bytes (`source_refusal`); `measure` reads everything else a judgement
needs off the bytes in one process, so a picker measures its takes in a pool and caches them.
"""
import collections

import audio_index
import audio_measure
from audio_gates import (LICENSE_URLS, UNSHIPPABLE_LICENSES, digest_of, named_by_its_file,
                         names_nobody)
from audio_voices import (RECONSIDER_MOS, is_doubted, is_one_sided, is_overlong, is_rejected,
                          is_squashed, is_vouched, mos_floor)

Judged = collections.namedtuple('Judged', 'mos refused hot doubted vouched')


def license_refusal(license):
    """(reason, detail) for a license that may not ship, or None."""
    if license in UNSHIPPABLE_LICENSES:
        return 'license', license
    if license not in LICENSE_URLS:
        return 'license', 'unknown "%s" — rule on it in audio_gates.py' % license
    return None


def source_refusal(take):
    """(reason, detail) out of the take's Commons record alone, or None."""
    refused = license_refusal(take['license'])
    if refused:
        return refused
    if names_nobody(take['author']):
        return 'unattributable', '%s credits nobody' % take['source']
    # why: a letter's take says the letter's name, which its glyph key never spells.
    if take['section'] != 'letters' and not named_by_its_file(take['source'], take['author'], take['key']):
        return 'misnamed', '%s is not %s saying "%s"' % (take['source'], take['author'], take['key'])
    return None


def measure(path, scored=True):
    """What [judge] reads off a take's bytes short of its playback gain, in one process:
    `balance`, `peak`, `loudness`, `speech` seconds, `pop` and — [scored] — `mos` and `silent`."""
    if scored:
        levels = audio_measure.measure(audio_index.FFMPEG, path)
        loudness, span, peak = levels[0], levels[1], levels[2]
        out = dict(mos=None if levels[5] is None else round(levels[5], 2), silent=loudness is None or peak is None)
    else:
        peak, loudness = audio_measure.peak_and_loudness(audio_index.FFMPEG, path)
        span = audio_measure.speech_span(audio_index.FFMPEG, path)
        out = {}
    return dict(out, balance=audio_measure.side_balance(audio_index.FFMPEG, path), peak=peak, loudness=loudness,
                speech=round(span[1] - span[0], 2), **measure_pop(path, span))


def measure_pop(path, span=None):
    """`{'pop': dB}` of `audio_measure.pop`, for [measure] and for a take cached before it read pops."""
    pop = audio_measure.pop(audio_index.FFMPEG, path, span)
    return dict(pop=None if pop is None else round(pop, 1))


def judge(take, index=False, measured=None):
    """`Judged` for [take]; [index] is its playback index where already measured
    (None for a take that decodes to silence), [measured] its [measure] where that is known instead,
    else the bytes are measured here."""
    lang, author, path = take['lang'], take['author'], take['path']
    digest = take.get('sha256') or digest_of(path)
    if measured is not None:
        index = None if measured.get('silent') else {'mos': measured.get('mos', take.get('mos'))}
    elif index is False:
        index = audio_index.analyze([path])[path]
    mos = index.get('mos') if index else None
    refused = source_refusal(take)
    if not refused and index is None:
        refused = 'silent', 'decodes to silence'
    if not refused and is_rejected(digest, lang, author):
        refused = 'poor', 'heard as bad'
    if not refused and mos is not None and mos < mos_floor(lang) and not is_vouched(digest):
        refused = 'poor', 'mos %.2f, floor is %.2f' % (mos, mos_floor(lang))
    if not refused:
        balance = (measured or {}).get('balance')
        if balance is None:
            balance = audio_measure.side_balance(audio_index.FFMPEG, path)
        if is_one_sided(balance):
            refused = 'one-sided', '%.1f dB between its sides' % balance
    # why: a letter's take says the letter's name, which its glyph key never spells.
    if not refused and take['section'] != 'letters':
        speech = (measured or {}).get('speech')
        if speech is None:
            start, end, _ = audio_measure.speech_span(audio_index.FFMPEG, path)
            speech = end - start
        if is_overlong(speech, take['key']):
            refused = 'overlong', '%.1f s of speech for "%s"' % (speech, take['key'])
    if not refused and abs(index.get('gain', 0)) > audio_index.GAIN_LIMIT_DB:
        refused = 'unmeasurable', 'gain %.1f dB is past ±%.0f — clipped' % (index['gain'],
                                                                          audio_index.GAIN_LIMIT_DB)
    levels = ((measured['peak'], measured['loudness']) if measured is not None
              else audio_measure.peak_and_loudness(audio_index.FFMPEG, path))
    return Judged(mos=mos, refused=refused, hot=is_squashed(*levels),
                  doubted=is_doubted(digest, lang, author) or (mos is not None and mos < RECONSIDER_MOS),
                  vouched=is_vouched(digest))
