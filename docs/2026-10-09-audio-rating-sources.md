# Audio rating sources
A survey of whether anyone besides us has already rated Commons/Wiktionary/Lingua Libre pronunciation files or their speakers, and whether that judgement is reusable.
Neighbors: the verdicts `audio-verdicts.tsv`, the quality measure survey `2026-09-28-audio-quality-tools.md`.

## Verdict

Nothing is worth wiring in.
No external source rates these files by ear at anything close to our coverage, machine-readably, for free.
Wikimedia Commons has no quality-flagging category or template for pronunciation audio at all — only descriptive/license categories.
Wiktionary's audio-related categories track missing audio, not bad audio, and the `{{audio}}` template carries no quality field.
Lingua Libre's own Wikibase schema has a property built for exactly this (`P33`, "type of issue", described as "used on problematic recordings' elements") but it has zero uses — the mechanism exists and is empty.
The one real signal is Commons deletion requests, where a human sometimes writes "pronunciation is wrong" or "bad quality" in prose, but it covers roughly 0.2% of files and is not structured as a rating.
Keep rating by ear plus DNSMOS; nothing here changes that.

## 1. Wikimedia Commons maintenance categories/templates for audio quality

Queried `https://commons.wikimedia.org/w/api.php` with the agent string given, one request at a time, 1s apart.

- `action=query&list=search&srnamespace=14&srsearch=pronunciation%20quality` → 63 hits, none of them an audio-quality category (matches are things like "Category:Quality of life", "Category:Quality images of Munich").
- `action=query&list=search&srnamespace=14&srsearch=rerecord` → 0 hits (`totalhits: 0`).
- `action=query&list=search&srnamespace=14&srsearch=background%20noise%20pronunciation` → 0 hits.
- `action=query&list=search&srnamespace=14&srsearch=audio%20quality` → 266 hits, all equipment/codec categories (ADAM Audio, FLAC, AAC…), none about recording quality.
- `action=query&list=search&srnamespace=10&srsearch=rerecord` (Template namespace) → 0 hits. No `{{Rerecord}}` or `{{Bad pronunciation}}` template exists.
- `action=query&list=search&srnamespace=10&srsearch=pronunciation` → 50 hits, all descriptive templates (`Template:Pronunciation`, `Template:Lingua Libre record`, `Template:Audio`…), none a quality flag.
- `action=query&titles=Category:Featured%20sounds&prop=categoryinfo` → exists but tiny: 16 pages, 1 file, 4 subcats. It is Commons' general "Featured media" process extended to sound (mostly music/speeches), essentially dormant for single-word pronunciation files.
- `action=query&titles=Category:Quality%20sounds&prop=categoryinfo` → `"missing":""`. Does not exist.
- `action=query&titles=Category:Valued%20sounds&prop=categoryinfo` → `"missing":""`. Does not exist.
- `action=query&titles=File:De-Hund.ogg&prop=categories` → only `CC-BY-SA-3.0`, `German pronunciation of nouns`, `German pronunciation of words relating to dogs`, `Pronunciation of words relating to dogs`, `Self-published work`. No quality category.
- `action=query&titles=File:LL-Q188%20(deu)-Sebastian%20Wallroth-Hund.wav&prop=categories` → only `CC-Zero`, `German pronunciation of nouns`, `Lingua Libre pronunciation-deu`, `Lingua Libre pronunciation by Sebastian Wallroth`. Same pattern: language/speaker/license only, no quality signal, for any Lingua Libre file.

Conclusion: Commons has no quality-grading system that reaches ordinary pronunciation files, for any of our seven languages.
`Commons:WikiProject Pronunciation` (found via web search, `https://commons.wikimedia.org/wiki/Commons:WikiProject_Pronunciation`) explicitly says there are "no best practices" yet for these files — it is a coordination page, not a review pipeline.

## 2. Wiktionary pronunciation maintenance categories (en, de, fr, it, es, uk)

Queried `https://en.wiktionary.org/w/api.php` the same way.

- `action=query&list=search&srnamespace=14&srsearch=audio` → 842 hits, dominated by `Category:Requests for audio pronunciation in <Language> entries` (one per language, including German, French, Italian — these flag words that have **no** audio yet, not bad existing audio) and `Category:<Language> terms with audio pronunciation` (words that do have audio, no quality dimension).
- `categoryinfo` on three of these: `Category:English terms with audio pronunciation` → 93,664 pages; `Category:German terms with audio pronunciation` → 287,513 pages; `Category:Requests for audio pronunciation in German entries` → 633 pages (missing-audio requests, hidden maintenance category).
- Searched `mispronounced OR "bad recording" OR "wrong pronunciation"` in mainspace → 170 hits, all dictionary entries defining words like "mispronounced"/"白字", none of them a maintenance mechanism.
- Pulled `Template:audio/documentation` wikitext in full: parameters are `1` (lang code), `2` (filename), `3`/`q`/`qq`/`a`/`aa` (captions/qualifiers/accent), `ref`, `text`, `IPA`, `t`. No quality or rating parameter exists on the template that renders every audio link on Wiktionary.

Other Wiktionary editions (de/fr/it/es/uk) use the same `{{audio}}`-style templates and the same "Requests for audio pronunciation" pattern for missing audio; nothing suggests any of them track bad existing audio differently — this is a shared MediaWiki/Wiktionary convention, not an English-specific gap.

Conclusion: Wiktionary's machinery is entirely about "is there audio" not "is the audio good." Nothing machine-readable to harvest here.

## 3. Lingua Libre: ratings, flags, deletion requests

Lingua Libre runs on a Wikibase instance with a public SPARQL endpoint at `https://lingualibre.org/sparql` (confirmed via `Help:SPARQL`, `https://lingualibre.org/wiki/Help:SPARQL`; the old `lingualibre.org/api.php` and `lingualibre.org/w/api.php` now serve the SPA shell, not a classic MediaWiki API, so SPARQL is the way in).

Ran, via `curl -G https://lingualibre.org/sparql --data-urlencode query=... -H "Accept: application/sparql-results+json"`:

- Listed every Wikibase property (`SELECT ?p ?pLabel WHERE { ?p a wikibase:Property . ?p rdfs:label ?pLabel . }`) → ~39 properties total (P2–P41). Among them: `P32` "importance level" and `P33` "type of issue".
- `SELECT ?desc WHERE { <https://lingualibre.org/entity/P33> schema:description ?desc . FILTER(LANG(?desc)="en") }` → `"used on problematic recordings' elements"`. This property is *built for* flagging bad recordings.
- `SELECT (COUNT(?item) AS ?count) WHERE { ?item wdt:P33 ?issue . }` → **0**. No item in the entire Lingua Libre Wikibase carries this property.
- Same check on `P32` ("importance level") → **0** uses.
- A direct filter query for any property whose label contains quality/rating/flag/review/delet → 0 results, confirming P33 is the only issue-flagging property and it's unused.

So: the data model anticipated per-recording issue flagging and nobody has ever populated it — a designed-but-dead mechanism, not a missing one.

Checked whether problems surface elsewhere instead:
- `Commons_talk:Lingua_Libre` (the project's own chat room, fetched and summarized) shows no systematic quality-review process, no rating system, no deletion protocol specific to quality — only scattered bug reports about the recording interface itself.
- Commons deletion requests do pick up individual bad Lingua Libre recordings. Searching Commons (ns=4, `Commons:` namespace) for deletion-request pages:
  - `srsearch="LL-Q188" "Deletion requests"` → 58 hits (discussions mentioning a German Lingua Libre file), e.g. `Commons:Deletion requests/File:LL-Q188 (deu)-Poslovitch-Brücke.wav` ("The pronunciation is wrong...").
  - Total German Lingua Libre files (`intitle:"LL-Q188 (deu)"` in File namespace) → 26,333.
  - 58 / 26,333 ≈ **0.22%** of just the German LL corpus has ever had a deletion discussion, and not all of those discussions are about quality (some are copyright/scope/duplicate, e.g. `.../File:LL-Q188 (deu)-Michael Schoenitzer (MichaelSchoenitzer)-Mecklenburg-Vorpommern.wav`).
  - Similar spot-checks found deletion requests citing quality for Wiktionary-convention files too: `Commons:Deletion requests/File:French pronunciation.ogg` ("Own work, bad quality"), `Commons:Deletion requests/File:Fr-Afrique.wav` ("the pronunciation... sounds weird"), `Commons:Deletion requests/File:Nl-Prince-article.ogg` ("of such poor quality..."), `Commons:Deletion requests/Files uploaded by 31NOVA` ("too bad quality, robot pronunciation").
  - These pages are findable by filename via `action=query&list=search&srnamespace=4&srsearch="<exact filename>" "Deletion requests"`, but each is free-text prose, not a score, and a file that survived a "keep" close stays in our catalog carrying a disputed-but-unresolved human complaint that only shows up if you go looking for it per file.

Conclusion: Lingua Libre itself has no ratings. Commons deletion requests are the only real per-file human judgement that exists, but at ~0.2% coverage, mixed with non-quality reasons, and unstructured, they're not a feed worth building — at most a one-off sanity check ("has this exact filename ever had a quality complaint?") before shipping a specific speaker/word.

## 4. Other open datasets of human ratings

- **Forvo**: does have a real per-clip upvote/downvote system (confirmed via web search and `forvo.com/license/`). Irrelevant to us for two reasons: (a) Forvo's recordings are a separate corpus from Wikimedia Commons/Lingua Libre — the files don't overlap, so its votes don't rate *our* files or speakers; (b) its content license is CC BY-NC-SA 3.0, non-commercial only, and its API is a paid/keyed service — not usable for bulk reuse regardless.
- **Speech Wikimedia** (arXiv, "Speech Wikimedia: A 77-Language Multilingual Speech Dataset", `https://arxiv.org/pdf/2308.15710`): extracts ~1,780 hours of audio+transcripts from Wikimedia Commons, including single-word pronunciation-style clips, and reports DNSMOS scores per dataset slice. These are **automated** DNSMOS numbers computed by the researchers, not human ratings — the same measure we already run ourselves — so it adds no new judgement, only a possible cross-check if its per-file outputs were published (the paper reports aggregate stats, not a searchable file-level table as far as this search surfaced).
- No academic paper or open dataset turned up that publishes human MOS/quality ratings keyed to individual Commons/Wiktionary/Lingua Libre filenames or speaker IDs.

## 5. Fetch recipes, coverage, file-vs-speaker (summary table)

| Source | Query | Covers | Rates file or speaker | Usable? |
|---|---|---|---|---|
| Commons categories/templates | `commons.wikimedia.org/w/api.php?action=query&list=search&srnamespace=14\|10&srsearch=...` | 0 files | — | No — doesn't exist |
| Commons `prop=categories` on a file | `...?action=query&titles=File:X&prop=categories` | all files (but only language/license/speaker cats) | neither (no quality dim) | No |
| Wiktionary audio categories | `en.wiktionary.org/w/api.php?action=query&list=search&srnamespace=14&srsearch=audio` | missing-audio requests only | — | No |
| Lingua Libre SPARQL `P33`/`P32` | `https://lingualibre.org/sparql` with `wdt:P33`/`wdt:P32` | 0 records | file (by design, unused) | No — empty |
| Commons deletion requests | `commons.wikimedia.org/w/api.php?action=query&list=search&srnamespace=4&srsearch="<filename>" "Deletion requests"` | ~0.2% of files, mixed reasons | file | Marginal — per-file spot-check only, not a feed |
| Forvo votes | Forvo API (keyed, NC license) | 0 overlap with our files | file (and implicitly speaker) | No — different corpus, wrong license |
| Speech Wikimedia (arXiv) | dataset download / paper tables | automated DNSMOS, not human | file | No new signal — duplicates our own metric |
