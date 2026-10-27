"""Resolve words to Lingua Libre recordings on Wikimedia Commons.

Lingua Libre publishes one Commons category per language and no per-word index —
its own wiki left the picture in May 2026 — so the category is enumerated once and
matched on the filename, `LL-Q<language item> (<code>)-<speaker>-<word>.<ext>`.

License and speaker are per FILE, and one speaker may well have uploaded under two
licenses, so `resolve` fetches metadata BEFORE it picks.
"""
import collections
import os
import re
import sys
import unicodedata

import audio_commons
from audio_gates import apostrophe_folded
from audio_routes import WORKSPACE

CATEGORY = "Category:Lingua Libre pronunciation-%s"
# The space before `(<code>)` and a trailing gloss are both optional: Goethe-Institut
# Cameroon uploaded its Swahili as `LL-Q93189(swa)-Mndetatsin-Yai(l'oeuf).wav`, the word
# followed by its French in parentheses. The gloss is only ever read off the very end, so a
# speaker's own parenthesis (`Patrick Strasser-Mikhail (Past)-…`) stays part of the name.
FILENAME = re.compile(r"^LL-Q\d+ ?\((?P<code>[a-z]{3})\)-(?P<rest>.*?[^ ])(?:\([^()]*\))?"
                      r"\.(?:wav|ogg|flac|mp3)$")

# Lingua Libre-named uploads that never joined their Lingua Libre category, by code: the
# Goethe-Institut's 90 Swahili files are filed only under the language's pronunciation category.
STRAYS = {"swa": "Category:Swahili pronunciation"}


CACHE_DIR = os.path.join(WORKSPACE, "ll-category-cache")


def category_files(category, refresh=False):
    """Every File: title in a Commons category — 500 per request, paged to the end.

    CACHED to `ll-category-cache/`, because the page count is set by the category and not
    by how many words are wanted: French carries 431,957 files, so a run that needs seven
    hundred words still walks 864 sequential requests to find them — 15 minutes before the
    first download. (Spanish is 19,122 and Italian 12,621, which is why the cost went
    unnoticed until French.) Nothing here changes fast enough to re-page per run; delete
    the directory, or pass `refresh`, to re-read the category.
    """
    code = category.rsplit("-", 1)[-1] if category.startswith(CATEGORY % "") else \
        re.sub(r"\W+", "-", category.removeprefix("Category:")).lower()
    cache = os.path.join(CACHE_DIR, f"{code}.txt")
    if not refresh and os.path.isfile(cache):
        with open(cache, encoding="utf-8") as fh:
            titles = fh.read().splitlines()
        print(f"  {category}: {len(titles)} files (cached)", file=sys.stderr)
        return titles

    titles, cont = [], None
    while True:
        page = audio_commons.api(list="categorymembers", cmtitle=category, cmtype="file",
                                 cmlimit="500", **({"cmcontinue": cont} if cont else {}))
        titles += [m["title"].removeprefix("File:") for m in page["query"]["categorymembers"]]
        cont = page.get("continue", {}).get("cmcontinue")
        if not cont:
            print(f"  {category}: {len(titles)} files", file=sys.stderr)
            os.makedirs(CACHE_DIR, exist_ok=True)
            with open(cache, "w", encoding="utf-8") as fh:
                fh.write("\n".join(titles))
            return titles


def files(code):
    """Every Lingua Libre-named file of a language: its category, plus its `STRAYS`."""
    titles = category_files(CATEGORY % code)
    if code in STRAYS:
        titles = titles + [t for t in category_files(STRAYS[code])
                           if t.startswith("LL-") and t not in titles]
    return titles


def speakers(rests, threshold=5):
    """The speaker names a category actually contains, by how often a prefix heads a file.

    Both halves of `<speaker>-<word>` may carry hyphens — "Patrick Strasser-Mikhail (Past)"
    is one contributor, "Ada-Lessing-Straße" is one word — so the boundary cannot be found
    by counting dashes. What separates them is repetition: a speaker heads hundreds of
    files, a word's first syllable heads one. Measured over the 26k-file German category,
    82 speakers account for every filename and none of their names is a prefix of another.
    """
    heads = collections.Counter()
    for rest in rests:
        parts = rest.split("-")
        for i in range(1, len(parts)):
            heads["-".join(parts[:i])] += 1
    return {prefix for prefix, count in heads.items() if count >= threshold}


def candidates(files, code, wanted):
    """word -> [(speaker, filename)] over a category listing.

    The split is decided by the SPEAKER and never by [wanted] — that inversion is the
    whole point. Cutting at "the first tail that happens to be a word we want" forces
    every filename to yield a match: `Mighty Wire-Earl-Grey-Tee` reads as Mighty-Wire-Earl-
    Grey speaking "Tee", `Frank C. Müller-1-Raum-Wohnung` as a recording of "Wohnung", and
    the app then says a compound where the card shows a bare noun. Two such files shipped.

    So: find the speakers first (`speakers`), cut at the LONGEST one that heads the name —
    longest, because "Patrick Strasser" and "Patrick Strasser-Mikhail (Past)" are both real
    prefixes and only the latter is where the word starts — and only THEN ask whether what
    remains is wanted. A tail that is a compound simply fails to match, which is correct.
    """
    rests = []
    for name in files:
        match = FILENAME.match(unicodedata.normalize("NFC", name))
        if match and match.group("code") == code:
            rests.append((match.group("rest"), name))
    known = speakers([rest for rest, _ in rests])
    folded = {apostrophe_folded(w): w for w in wanted}
    found = {}
    for rest, name in rests:
        parts = rest.split("-")
        cuts = [i for i in range(1, len(parts)) if "-".join(parts[:i]) in known]
        # why: no known speaker heads it → assume the plain one-segment name rather than
        # scanning for a cut that flatters the search, which is the bug this replaced.
        cut = max(cuts) if cuts else 1
        word = folded.get(apostrophe_folded("-".join(parts[cut:]).lower()))
        if word:
            found.setdefault(word, []).append(("-".join(parts[:cut]), name))
    return found


def license_rank(license):
    """CC0 and public domain ask nothing, attribution-only asks for a line, share-alike binds the
    file itself; anything else last."""
    if license in ("CC0", "Public domain"):
        return 0
    return 1 if license.startswith("CC BY ") else 2 if license.startswith("CC BY-SA") else 3


def pick(cand, meta):
    """word -> filename. The cheaper license wins outright; inside one rank the speaker
    covering most of what is still uncovered goes first, so the pack rides as few voices
    as coverage allows without ever trading a CC0 recording for a share-alike one."""
    chosen, remaining = {}, set(cand)
    for rank in range(4):
        while True:
            covers = {}
            for word in remaining:
                for speaker, name in cand[word]:
                    if license_rank(meta[name].get("license", "?")) == rank:
                        covers.setdefault(speaker, []).append((word, name))
            if not covers:
                break
            speaker = max(sorted(covers), key=lambda s: len(covers[s]))
            license = meta[covers[speaker][0][1]].get("license", "?")
            print(f"  {license:<12} {speaker}: {len(covers[speaker])} words", file=sys.stderr)
            for word, name in covers[speaker]:
                chosen[word] = name
                remaining.discard(word)
    return chosen


def resolve(wanted, code):
    """word -> filename for every wanted surface form the category can speak."""
    cand = candidates(files(code), code, wanted)
    return pick(cand, audio_commons.meta(sorted({n for rows in cand.values() for _, n in rows})))
