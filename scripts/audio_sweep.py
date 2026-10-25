"""The two pickers: a sweep finds a better take where what ships is poor, a consolidation moves a
voice's few takes onto an established one. Both only ever CHOOSE another untouched Commons transcode —
denoising would be an adaptation under BY-SA and break the sha256 the catalog pins.

SWEEP: an incumbent goes up when the take judge refuses it from its record, a listener doubted it,
it scores under `RECONSIDER_MOS`, it is squashed, one-sided or overlong — unless a listener vouched
for it, which only one-sidedness and overlength overrule. A take by a DOUBTED voice (`audio_voices`) goes up
too, and is beaten like a squashed one. A challenger the judge refuses, or finds squashed unless a
listener vouched for it, or a listener doubted, never wins.
The best one has to beat the incumbent by `SWAP_MARGIN`, because a swap costs a voice change and a
single file's score strays by a few tenths; against a squashed incumbent it only must not be
clearly worse, and against a refused, doubted, one-sided, overlong or under-floor one any passable take
wins. A voice the language already ships `ESTABLISHED_TAKES` of beats a slightly better stranger.
Where no take of a word's whole form wins, the takes of its bare form (`bare_form`: de `sich irren` →
`irren`, es `ponerse` → `poner`) compete under the same bar, unless that
bare form ships a recording of its own; a winner is staged to ship under the bare form, which playback
falls back to, in place of the whole form's entry.

CONSOLIDATE: a shipped take whose voice ships `ONE_OFF_TAKES` or fewer in its language moves onto
an established voice's take of the same form — smallest license harm first (CC0 and public domain,
then attribution, then share-alike), then the biggest voice, then the catalog's casing — unless
that take is refused, squashed (unless a listener vouched for it), doubted by a listener,
scores `SWAP_MARGIN` or more under the incumbent (a sweep would win that back, and the two would
trade the take forever), or gives up
more than `DULLNESS_DB` above 6 kHz (a telephone-band take scores clean and sounds dull).
"""
import collections

import audio_commons
import audio_judge
import audio_measure
from audio_forms import language_info
from audio_gates import bare_form, digest_of, speech_key
from audio_index import FFMPEG
from audio_lingualibre import license_rank
from audio_manifest import audio_dir, read_manifest
from audio_pick import (candidate_refusal, challengers, credit_groups, fetch, incumbents, judged, stage,
                        staged_keys, voices)
from audio_voices import (DULLNESS_DB, ESTABLISHED_TAKES, ONE_OFF_TAKES, RECONSIDER_MOS, SWAP_MARGIN,
                          is_doubted, is_one_sided, is_overlong, is_squashed, is_voice_doubted, is_vouched,
                          mos_floor)


def candidates(lang, takes):
    """{key: [(name, record)]} passing the record checks, the keys whose probe failed, and the
    new refusals each named."""
    options, failed = challengers(lang, takes)
    records = audio_commons.meta(sorted({name for names in options.values() for name in names}))
    kept = {}
    for take in takes:
        kept[take['key']] = []
        for name in sorted(options[take['key']]):
            if name.replace('_', ' ') == take['source'].replace('_', ' '):
                continue
            why = candidate_refusal(take, name, records.get(name, {}))
            if why and why[0] in ('says more', 'titled as a name', 'unattributable'):
                print('  next   %-18s (%s %s)' % (take['key'], name, why[0]))
            if not why:
                kept[take['key']].append((name, records.get(name, {})))
    return kept, failed


def pending(lang, section, catalog, measures):
    """This section's incumbents, measured, minus shared bytes and keys already staged against them."""
    held = staged_keys()
    takes = [take for take in incumbents(lang, section, catalog) if not take['shared']
             and (lang, section, take['key'], take['sha256'] if take['shipped'] else '') not in held]
    futures = [measures.submit(take['sha256'], take['path'], scored=take['mos'] is None) for take in takes]
    ready = []
    for take, future in zip(takes, futures):
        measured = measures.result(take['sha256'], future)
        if measured is None:
            continue
        take['mos'] = take['mos'] if take['mos'] is not None else measured.get('mos')
        take['lopsided'] = is_one_sided(measured['balance'])
        speech = measured.get('speech')
        if speech is None and section != 'letters':
            start, end, _ = audio_measure.speech_span(FFMPEG, take['path'])
            speech = end - start
        take['overlong'] = section != 'letters' and is_overlong(speech, take['key'])
        take['squashed'] = is_squashed(measured['peak'], measured['loudness'])
        take['ear'] = is_doubted(take['sha256'], lang, take['author'])
        take['voice'] = is_voice_doubted(take['sha256'], lang, take['author'])
        take['refused'] = audio_judge.source_refusal(take)
        ready.append(take)
    return [take for take in ready if take['mos'] is not None]


def sweep(lang, section, catalog, measures, only, apply):
    """→ (swaps, failures) for one section."""
    takes = pending(lang, section, catalog, measures)
    if only:
        noisy = [take for take in takes if take['key'] in only]
    else:
        noisy = [take for take in takes
                 if (not is_vouched(take['sha256']) or take['lopsided'] or take['overlong'])
                 and (take['mos'] < RECONSIDER_MOS or take['ear'] or take['voice'] or take['squashed'] or take['lopsided']
                      or take['overlong'] or take['refused'])]
    print('%d below %.2f, squashed, one-sided, overlong or refused' % (len(noisy), RECONSIDER_MOS))
    if not noisy:
        return 0, 0
    results, errors = judged_options(lang, noisy, measures)
    floor, weight = mos_floor(lang), voices(lang)
    picks = {take['key']: best_option(take, results.get(take['key'], []), lang, floor, weight) for take in noisy}
    # why: only where the whole form has no winner does its bare form compete, so a take of
    # `sich irren` always beats one of `irren`; a bare form shipping its own take already answers.
    ships = {speech_key(key) for key in read_manifest(audio_dir(lang)).get(section, {})}
    bare = [dict(take, key=stem, whole=take['key']) for take in noisy if section == 'words' and not picks[take['key']]
            for stem in [bare_form(take['key'], language_info(lang))] if stem and speech_key(stem) not in ships]
    if bare:
        print('%d without a winner, trying their bare form' % len(bare))
        found, failures = judged_options(lang, bare, measures)
        errors += failures
        for take in bare:
            picked = best_option(take, found.get(take['key'], []), lang, floor, weight)
            picks[take['whole']] = picked and picked + (take['key'],)
    swaps = 0
    for take in sorted(noisy, key=lambda take: take['mos']):
        best = picks[take['key']]
        if not best:
            print('  keep   %-18s %5.2f  (no challenger survives)' % (take['key'], take['mos']))
            continue
        name, record, path, after, *speaks = best
        print('  SWAP   %-18s %5.2f -> %5.2f  %s (%s)%s' % (take['key'], take['mos'], after, name,
                                                     record.get('author', '?'), ' as %s' % speaks[0] if speaks else ''))
        swaps += 1
        if apply:
            stage(take, name, record, path, after, 'sweep', reason(take), *speaks)
    print('\n%d swap(s) of %d noisy rows%s' % (swaps, len(noisy), '' if apply else ' — dry run, nothing written'))
    return swaps, errors


def judged_options(lang, takes, measures):
    """{key: [(name, record, path, Judged)]} of every challenger of [takes], fetched and judged, and the
    count of failures."""
    options, failed = candidates(lang, takes)
    fetched, errors = [], len(failed)
    for take in takes:
        if take['key'] in failed:
            continue
        for name, record in options[take['key']]:
            path = fetch(name, record, take['key'])
            errors += path is False
            if path:
                fetched.append((take, name, record, path, measures.submit(digest_of(path), path)))
    print('judging %d fetched takes…' % len(fetched))
    results = collections.defaultdict(list)
    for take, name, record, path, future in fetched:
        verdict = judged(take, name, record, path, measures.result(digest_of(path), future))
        results[take['key']].append((name, record, path, verdict))
    measures.save()
    return results, errors


def best_option(take, options, lang, floor, weight):
    """(name, record, path, mos) of the challenger in [options] that beats [take], else None."""
    before, best, stranger = take['mos'], None, None
    for name, record, path, verdict in options:
        if verdict.mos is None and not verdict.refused:
            continue
        sha = digest_of(path)
        why = verdict.refused[1] if verdict.refused else 'squashed' if verdict.hot and not verdict.vouched else \
            'heard as mediocre' if is_doubted(sha, lang, record.get('author')) else \
            'by a doubted voice' if is_voice_doubted(sha, lang, record.get('author')) else None
        if why:
            print('  next   %-18s (%s %s)' % (take['key'], name, why))
            continue
        needed = before + SWAP_MARGIN
        if take['squashed'] or take['lopsided'] or take['voice']:
            needed = before - SWAP_MARGIN
        if take['ear'] or take['lopsided'] or take['overlong'] or take['refused']:
            needed = floor
        elif before < floor:
            needed = min(needed, max(floor, before + 0.01))
        if verdict.mos < needed:
            continue
        pick = (name, record, path, verdict.mos)
        if weight[record.get('author')] >= ESTABLISHED_TAKES:
            best = pick if best is None or verdict.mos > best[3] else best
        elif stranger is None or verdict.mos > stranger[3]:
            stranger = pick
    return best or stranger


def reason(take):
    return ('refused: %s' % take['refused'][0] if take['refused'] else 'one-sided' if take['lopsided']
            else 'overlong' if take['overlong'] else 'squashed' if take['squashed'] else 'heard as doubtful' if take['ear'] else 'a doubted voice' if take['voice'] else 'mos %.2f' % take['mos'])


def brightness(measures, sha, path):
    """`audio_measure.brightness`, cached with the take's other measures."""
    known = measures.known.setdefault(sha, {})
    if 'bright' not in known:
        known['bright'] = audio_measure.brightness(FFMPEG, path)
    return known['bright']


def consolidate(lang, section, catalog, measures, only, apply):
    """→ (swaps, failures) for one section."""
    weight = credit_groups(lang)
    held = staged_keys()
    takes = [take for take in incumbents(lang, section, catalog) if take['shipped'] and not take['shared']
             and (lang, section, take['key'], take['sha256']) not in held]
    tail = ([take for take in takes if take['key'] in only] if only else
            [take for take in takes if weight[(take['author'], take['license'])] <= ONE_OFF_TAKES])
    print('%s %s: %d rows in credit groups of %d or fewer, %d groups total'
          % (lang, section, len(tail), ONE_OFF_TAKES, len(weight)))
    if not tail:
        return 0, 0
    options, failed = candidates(lang, tail)
    swaps, errors, after_weight = 0, len(failed), collections.Counter(weight)
    for take in tail:
        if take['key'] in failed:
            continue
        if take['mos'] is None:
            take['mos'] = (measures.result(take['sha256'], measures.submit(take['sha256'], take['path'])) or {}).get('mos')
        was_bright = brightness(measures, take['sha256'], take['path'])
        order = audio_commons.casings(take['key'].lower())
        ranked = sorted((license_rank(record.get('license', '?')), -weight[(record.get('author'), record.get('license'))],
                         next((i for i, cased in enumerate(order) if cased in name), len(order)), name, record)
                        for name, record in options[take['key']]
                        if (record.get('author'), record.get('license')) != (take['author'], take['license'])
                        and weight[(record.get('author'), record.get('license'))] >= ESTABLISHED_TAKES)
        chosen = None
        for *_, name, record in ranked:
            path = fetch(name, record, take['key'])
            errors += path is False
            if not path:
                continue
            sha = digest_of(path)
            verdict = judged(take, name, record, path, measures.result(sha, measures.submit(sha, path)))
            why = verdict.refused[1] if verdict.refused else 'squashed' if verdict.hot and not verdict.vouched else \
                'heard as mediocre' if is_doubted(sha, lang, record.get('author')) else \
                'by a doubted voice' if is_voice_doubted(sha, lang, record.get('author')) else None
            if why:
                print('  next   %-18s (%s %s)' % (take['key'], record.get('author', '?'), why))
                continue
            if take['mos'] is not None and verdict.mos is not None and round(take['mos'] - verdict.mos, 2) >= SWAP_MARGIN:
                print('  next   %-18s %5.2f  (%s only %.2f)' % (take['key'], take['mos'], record.get('author', '?'), verdict.mos))
                continue
            is_bright = brightness(measures, sha, path)
            if was_bright is not None and is_bright is not None and is_bright < was_bright - DULLNESS_DB:
                print('  next   %-18s %5.1f dB over 6 kHz  (%s dull at %.1f)'
                      % (take['key'], was_bright, record.get('author', '?'), is_bright))
                continue
            chosen = (name, record, path, verdict.mos)
            break
        if not chosen:
            print('  keep   %-18s %-28s (no challenger survives)' % (take['key'], take['author'][:28]))
            continue
        name, record, path, after = chosen
        print('  SWAP   %-18s %-26s -> %-26s  %s -> %s  %s' % (
            take['key'], '%s/%s' % (take['author'][:14], take['license']),
            '%s/%s' % (record.get('author', '?')[:14], record.get('license', '?')),
            shown(take['mos']), shown(after), name))
        swaps += 1
        after_weight[(take['author'], take['license'])] -= 1
        after_weight[(record.get('author'), record.get('license'))] += 1
        if apply:
            stage(take, name, record, path, after, 'consolidate',
                  'voice with %d takes' % weight[(take['author'], take['license'])])
    measures.save()
    print('\n%d swap(s) of %d tail rows — %d credit groups become %d%s' % (
        swaps, len(tail), len(weight), len([k for k, n in after_weight.items() if n]),
        '' if apply else '  (dry run, nothing written)'))
    return swaps, errors


def shown(mos):
    return '  n/a' if mos is None else '%5.2f' % mos
