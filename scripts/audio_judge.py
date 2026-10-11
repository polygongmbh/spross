"""May this take ship — one answer for every path that ships a recording or keeps one.

A take is a dict `lang section key source author license path` (plus `sha256` where known):
the Commons file it came from, who is credited under what, and its bytes on disk.
`judge` answers with the take's `mos` and four findings, and each caller's policy is one line
over them — a fill and a ship take what is not `refused`, a prune removes what is.

  · `refused` — (reason, detail) of the first of: license refused or unknown, author names
    nobody, a Lingua Libre name that is not its speaker saying its form, decodes to silence,
    heard as bad or by a POOR voice, under the language's floor, one-sided, gain past the limit;
  · `hot` — squashed by a limiter at the source;
  · `doubted` — heard as mediocre or worse, by a POOR voice, or under the reconsider bar;
  · `vouched` — heard as okay or better.

The source checks need no bytes (`source_refusal`).
"""
import collections

import audio_index
import audio_measure
from audio_gates import (LICENSE_URLS, UNSHIPPABLE_LICENSES, digest_of, named_by_its_file,
                         names_nobody)
from audio_voices import (RECONSIDER_MOS, is_doubted, is_one_sided, is_rejected, is_squashed,
                          is_vouched, mos_floor)

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


def judge(take, index=False):
    """`Judged` for [take]; [index] is its playback index where already measured
    (None for a take that decodes to silence), else the bytes are measured here."""
    lang, author, path = take['lang'], take['author'], take['path']
    digest = take.get('sha256') or digest_of(path)
    if index is False:
        index = audio_index.analyze([path])[path]
    mos = index.get('mos') if index else None
    refused = source_refusal(take)
    if not refused and index is None:
        refused = 'silent', 'decodes to silence'
    if not refused and is_rejected(digest, lang, author):
        refused = 'poor', 'heard as bad'
    if not refused and mos is not None and mos < mos_floor(lang):
        refused = 'poor', 'mos %.2f, floor is %.2f' % (mos, mos_floor(lang))
    if not refused:
        balance = audio_measure.side_balance(audio_index.FFMPEG, path)
        if is_one_sided(balance):
            refused = 'one-sided', '%.1f dB between its sides' % balance
    if not refused and abs(index.get('gain', 0)) > audio_index.GAIN_LIMIT_DB:
        refused = 'unmeasurable', 'gain %.1f dB is past ±%.0f — clipped' % (index['gain'],
                                                                          audio_index.GAIN_LIMIT_DB)
    return Judged(mos=mos, refused=refused,
                  hot=is_squashed(*audio_measure.peak_and_loudness(audio_index.FFMPEG, path)),
                  doubted=is_doubted(digest, lang, author) or (mos is not None and mos < RECONSIDER_MOS),
                  vouched=is_vouched(digest))
