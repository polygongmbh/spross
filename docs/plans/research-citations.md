# Research citations — what each learning element cites, and where it is thin
Audit (2026-10-19) of the studies behind every learning element a learner meets; sections run summary table → one section per gap → hygiene → candidates for a public "the research behind it" page.
Neighbors: the intake evidence `../growth-evidence.md`, the engine rules `../../kern/README.md`, the website plan `website.md`.

Candidates the first pass was unsure of were checked by web search on 2026-10-19, against abstracts and publisher listings, not full texts; **(verified)** marks those.
**(unverified)** marks what is still open;
unmarked candidates are well-known papers whose existence and gist I am confident of, not ones I re-read for this audit.

## Summary

| Element | Cited? | Where | Gap |
|---|---|---|---|
| FSRS-6 scheduling | Implementation only | `kern/docs/fsrs.md` (ts-fsrs/py-fsrs provenance); Expertium's notes in `growth-evidence.md` § Pulling reviews forward | Why FSRS at all; `desiredRetention` 0.85 rests on arithmetic (commit `b9bec2c55`) |
| Learning-step ladder | Commit only | Commit `77385412c` (unnamed meta-analysis, g = 0.034; errorful-generation boundary); `BoxConfig.stepsSeconds` KDoc points to `growth-evidence.md`, which holds none of it | Dangling citation; leech claim unsourced |
| No in-session retry | No | `kern/README.md` §5 (breadth ruling 2026-07-22) | Successive-relearning evidence, uncited, cuts both ways |
| New-word intake, round size, sitting cap | Yes, well | `docs/growth-evidence.md`, every section | Directional support only for 24; the "tuning trap" has no source |
| Related words arrive together | No | `kern/README.md` §6 | The doc's own Nakata & Suzuki (2019) cuts against it |
| Recognition → typed → by ear → recalled | No | `kern/README.md` §3, `kern/docs/presentation.md`, `docs/design.md` | Every step is a ruling |
| Synonyms on the reveal, form rotation | No | `kern/docs/presentation.md` | Ruling |
| Write-out after a miss, correction box | No | `kern/docs/turns.md` § The vocabulary turn | Well-supported by literature it does not name |
| Self-grading, Easy earned by speed | No | `SelfGrading` KDoc, `kern/README.md` §6 | "Learners are bad at self-reporting Easy" unsourced |
| Grading and typo leniency | No (engineering) | `kern/docs/grading.md` | Product judgment; no study is likely to decide it |
| Watch multiple choice | Commit only | Commit `dd6cdc979` (van den Broek et al. 2023); `docs/surfaces.md` names no study | Pause uncited in docs and much shorter than the study's 4 s; latency grading unsourced |
| Listening playlist | Yes, misapplied | `listen/Listening.kt` KDoc on `RECALL_GAP_HELD_MS` (Rowe 1974; Stahl 1994) | Wait-time research says ≥ 3 s, the gap is 1.2 s |
| Emoji illustrations | No | `kern/docs/presentation.md` § Emoji cue | Both branches have uncited support: reveal-side and fading-cue studies |
| Audio on cards | Engineering only | `docs/2026-09-28-audio-quality-*.md` (DNSMOS survey); loudness research in commit `a7db63e27` and `audio-leveling.md` | "Hearing them paired binds them" unsourced |
| Mini-game ladders (Sprosse, three clean wins) | No | `docs/drills.md` | Criterion-learning evidence fits and is unnamed |
| Letters | No | `docs/drills-words.md` § Buchstaben | Ruling |
| Numbers | Linguistic only | `docs/number-forms.md` (Duden, RAE, PMEG, Treccani, Académie, правопис, Almasi et al.) | No learning research for the drill or timed runs |
| Dates | Linguistic only | `docs/date-readings.md` (Kamusi, Almasi et al.); `docs/clock-registers.md` cites nothing | Same; clock registers rest on native rulings alone |
| Countries | No | `docs/drills-tables.md` § Länder | Ruling |
| SentenceScramble | No | `docs/drills-words.md` § Sentence scramble | Ruling; two searches found no study to cite |
| WordScramble | No | `docs/drills-words.md` § Word scramble | Ruling |
| Opposites | No | `docs/drills-words.md` § Gegenteile | Ruling |
| Which words, in what order | No | `catalog/areas/README.md` (first-day usefulness) | Frequency research unnamed |

Outside learning, two citations exist and need no work: Weber & Penn (1995) for the tree (`design/TreeGrowth.kt`, commit `8247382fa`)
and the audio loudness and quality sources above.

## FSRS-6 scheduling

- `kern/docs/fsrs.md` records provenance (repo, tag, SHA) and golden vectors, which supports "implemented correctly", never "the right choice".
- Expertium's algorithm notes support "a review at high retrievability buys little"; they are a practitioner write-up, not peer-reviewed.
- `desiredRetention` 0.85 is argued from the model's own arithmetic (first intervals, reviews per year), which is sound but cites no study.
- Candidates: Ye, Su & Cao (2022), KDD, "A stochastic shortest path algorithm for optimizing spaced repetition scheduling" — FSRS's own origin.
- Candidates: the open-spaced-repetition `srs-benchmark` repository — FSRS against SM-2 and others on real review logs.
- Candidates: Cepeda et al. (2006), *Psychological Bulletin*, the distributed-practice meta-analysis — why space at all.

## Learning-step ladder

- The ladder's research lives only in commit `77385412c`, unnamed: expanding vs. uniform spacing g = 0.034 n.s., and errorful generation not reaching cue-target pairs.
- `BoxConfig.stepsSeconds` KDoc cites `docs/growth-evidence.md` for both claims, and that file holds neither.
- The leech line ("the evidence supports rewriting the word or letting it go", commit `b9bec2c55`) names no source.
- The g value is Latimier, Peyre & Ramus (2021), *Educational Psychology Review* 33, 959–987: spaced beat massed retrieval at g = 0.74, expanding vs. uniform g = 0.034 n.s., 29 studies **(verified)**.
- Same paper: expanding gained on uniform as the number of tests grew, a moderator the commit leaves out.
- The boundary's likely source: Seabrooke et al. (2019), *JML*, "Learning from failure: errorful generation improves memory for items, not associations" **(verified, abstract)**.
- Older support for it: Grimaldi & Karpicke (2012), *Memory & Cognition*; Huelser & Metcalfe (2012), *Memory & Cognition* — the benefit holds for related pairs only.
- Direct counterexample: Potts & Shanks (2014), *JEP: General* 143(2), 644–667 — generating wrong translations of foreign words, then feedback, beat reading the pairs **(verified)**.
- So "worth about a restudy" is contested rather than settled, and the doc should cite both sides.
- The leech line is SuperMemo folklore (Wozniak's "Twenty rules of formulating knowledge"); say so or drop "the evidence".

## No in-session retry

- The ruling is breadth over depth, and the write-out gives a missed word one restudy.
- Against it: Rawson & Dunlosky (2011), *JEP: General*, "Optimizing schedules of retrieval practice for durable and efficient learning: How much is enough?" — practice to an initial criterion before stopping **(verified title; the criterion of three is secondhand)**.
- For it: the same lab's later review reports one correct recall in each of three spaced sessions beating three in one session, 68% vs. 26% at a week (Rawson et al. 2018) **(secondhand)**.
- Karpicke & Roediger (2008), *Science*, show dropping items once recalled costs nothing while dropping them from testing costs a lot.
- Worth a ruling that names the trade; the evidence prices a same-session retry low next to a next-session one, which is roughly what the ladder's 10-minute step plus the run boundary already does.

## New-word intake and round size

- The strongest-cited part of the app: proactive interference, list length, working memory, part/whole learning, output interference, the floor.
- Nakata & Webb (2016), Kornell (2009) and Welbers et al. (2019) support the rules as built.
- Criss, Malmberg & Shiffrin (2011) measured recognition of word lists over 150 trials; it supports capping the sitting, not the number 24 — the doc should say so.
- Yim, Dennis & Osth (2025), "A systematic reexamination of the list-length effect in recognition memory", exists as cited **(verified)**; the hits-flat/false-alarms finding is not in the abstract and needs the full text.
- Welbers et al. (2019), *E-Learning and Digital Media* 16(2), 92–109: a session limit stopped binging without cutting sessions, N = 101 **(verified)**; the doc's "daily", b = 0.62 and 66 vs. 41 need the full text.
- Underwood & Ekstrand (1966/67), Osth et al. (2018), Sweller (1994) and Kornell (2009) lack full references (title, venue) — needed before anything public.
- "The tuning trap" has no source; Soderstrom & Bjork (2015), *Perspectives on Psychological Science*, "Learning versus performance", is that exact argument.
- The "1.8 points of recall per delay" figure in commit `a4ff8c521` never reached the doc; source it or leave it.

## Related words arrive together

- `kern/README.md` §6 says an area's related words (spoon, fork, knife) arrive in one round "as a set to relate".
- `growth-evidence.md` § Semantic clustering cites Nakata & Suzuki (2019): semantic sets cost more trials (d = 0.73) and cause triple the within-set confusion, with no posttest gain.
- Spoon/fork/knife is the textbook semantic set that literature warns about; thematic sets (a scene) fare better.
- Candidates: Tinkham (1993), *System*; Tinkham (1997), *Second Language Research* — semantic vs. thematic clustering.
- Candidates: Waring (1997), *System*; Erten & Tekin (2008), *System* — both find semantic sets slower.
- Needs a ruling: either seed order interleaves coordinates across rounds, or the README names the cost and why the area order is worth it.

## How a word is asked over time

- Recognition first, typed production second, by ear past the growing bar, recalled in the head once settled — every step is a dated ruling.
- First exposure asks before it teaches; candidates: Richland, Kornell & Kao (2009), *JEP: Applied*; Kornell, Hays & Bjork (2009), *JEP: LMC* — the pretesting effect.
- Typed production as the strongest recall; candidates: Pyc & Rawson (2009), *JML*, retrieval effort; Slamecka & Graf (1978), the generation effect.
- Direction of translation; candidate: Schneider, Healy & Bourne (2002), *JML* — L1→L2 is harder and transfers differently.
- By ear answered with the meaning; candidate: Barcroft & Sommers (2005), *SSLA*, on acoustic variability in L2 word learning.
- Recalled (untyped) production for settled words; candidate: Smith, Roediger & Karpicke (2013), *JEP: LMC* — covert retrieval matches overt.
- That last one is the best-supported uncited rule in the app and a good public story.

## Synonyms on the reveal, form rotation

- The full synonym family on the reveal and rotating forms "at zero scheduling cost" are rulings.
- Candidate: Webb (2007), *Reading in a Foreign Language* 19(2), 120–136 — new words with an already known synonym were learned better on several measures **(verified)**.
- That supports rotating in a synonym once the canonical form is held, more than showing the whole family on a first reveal.
- Risk to check: several L2 forms per meaning at once is the synonym case of the clustering interference above.

## Write-out after a miss, correction box

- A missed word is typed once with the answer in view; a near miss shows the owed form in amber.
- Candidates: Pashler, Cepeda, Wixted & Rohrer (2005), *JEP: LMC* — feedback with the answer is what rescues a missed word.
- Candidates: Metcalfe (2017), *Annual Review of Psychology*, "Learning from errors" — also backs the sounds commit's "the miss is the productive event".

## Self-grading, Easy earned by speed

- `SelfGrading` says learners are bad at self-reporting Easy and spends latency on it instead; no source.
- Candidates: Dunlosky & Rawson (2012), *Learning and Instruction* — overconfident self-scoring undermines retention.
- Candidates: Koriat & Bjork (2005), *JEP: LMC* — illusions of competence.
- Candidates: Mettler, Massey & Kellman (2016), *JEP: General* 145(7), 897–917 — ARTS sets spacing from response time and accuracy and beat fixed schedules, the closest precedent for latency-based ratings **(verified)**.

## Grading and typo leniency

- The budget (a slip per six letters, a slip per four per word, free diacritic drops) is tuned against the catalog's own collisions, not against learners.
- No study is likely to settle a typo budget; state it as a product judgment and lean on the feedback candidates above for the amber near miss.

## Watch multiple choice

- Commit `dd6cdc979` cites van den Broek et al. (2023) for the prompt-alone pause; `docs/surfaces.md` describes the pause and names no study.
- Full reference: van den Broek, Gerritsen, Oomen, Velthoven, van Boxtel, Kester & van Gog (2023), "Optimizing multiple-choice questions for retrieval practice: Delayed display of answer alternatives enhances vocabulary learning", *Journal of Educational Psychology*, doi 10.1037/edu0000810 **(verified)**.
- The study held options back for 4 s; the watch's pause is the Good window over the prompt (1.8 s plus 15 ms a character), well under half that for a single word.
- The effect was strongest for learners who tried to recall during the delay, which is what the pause is for; worth a ruling on its length.
- The same paper found competitive, hard-to-guess options improved retention — direct support for the distractor ranking.
- Also on distractors: Little, Bjork, Bjork & Angello (2012), *Psychological Science* — competitive distractors make MC real retrieval.
- Lure intrusion; candidates: Roediger & Marsh (2005), *JEP: LMC*; Butler & Roediger (2008), *Memory & Cognition* — feedback removes the cost, which the red wash provides.
- Latency curve "compensating" for recognition; candidate: Mettler, Massey & Kellman (2016), as above.

## Listening playlist

- `RECALL_GAP_HELD_MS` (1.2 s) claims to sit "inside the wait-time band" of Rowe (1974) and Stahl (1994).
- Rowe (1974), *Journal of Research in Science Teaching* 11(2), 81–94, found teachers waiting about 1.5 s and better answers past 3 s; Stahl (1994), ERIC Digest ED370885, says the same **(verified)**.
- So 1.2 s is the baseline they argue against, not a point inside their band.
- Their setting is a classroom question answered aloud, not a held word recalled in the head.
- Fix: drop the citation, or lengthen the gap and cite it honestly; either way the "second a held word takes" figure needs its own source.
- Candidates for the turn shape (hear, pause, meaning): Smith, Roediger & Karpicke (2013) on covert retrieval; Pimsleur (1967), *Modern Language Journal*, "A memory schedule".

## Emoji illustrations

- The cue rule (picture upfront only on produce before a word lands, otherwise on the reveal) is argued from leakage, with no study.
- Best fit: van den Broek, van Gog, Jansen, Pleijsant & Kester (2021), "Multimedia effects during retrieval practice: Images that reveal the answer reduce vocabulary learning", *Journal of Educational Psychology* 113(8), 1587–1608 **(verified)**.
- Images that helped retrieve the answer lowered recall days later, against no image and against the image shown after the attempt — the OnReveal rule, measured.
- The **Upfront** branch is deliberate (owner ruling 2026-10-19): before a word lands, the picture is the cue that lets a learner produce a word they could not otherwise reach.
- That is diminishing-cues retrieval practice: Fiechter & Benjamin (2018), *Psychonomic Bulletin & Review* 25, 1868–1876 — a fading cue helped where plain testing did not, under weak initial learning **(verified)**.
- Its forerunner: Finley, Benjamin, Hays, Bjork & Kornell (2011), *JML* — diminishing letter cues beat cues held constant or growing.
- The doc should cite the pair as the reason for Upfront, and van den Broek et al. (2021) as the reason the picture leaves once the word lands.
- Also: Carpenter & Olson (2012), *JEP: LMC*, "Are pictures good for learning new vocabulary in a foreign language? Only if you think they are not" — pictures inflate confidence.
- Also: Lotto & de Groot (1998), *Language Learning* 48(1), 31–69 — adults learned better from L1 words than from pictures **(verified)**; Tonzar, Lotto & Job (2009) found the reverse in children.

## Audio on cards

- "Hearing them paired binds them" (`docs/read-aloud.md`) has no source; the audio research that exists is about recording quality and loudness.
- Candidates: Barcroft & Sommers (2005), *SSLA* — several talkers beat one, which argues for the recording-plus-voice mix.
- Candidates: Sydorenko (2010), *Language Learning & Technology* 14(2), 50–73 — audio alone trained aural recognition, text alongside it trained written form and meaning **(verified)**; supports saying a word while it stands on screen.

## Mini-game ladders

- "A Sprosse costs three clean wins" and "one new thing per Sprosse" are rulings.
- Candidate: Rawson & Dunlosky (2011), *JEP: General* — an initial criterion of three correct recalls, a near-exact match for `WINS_TO_ADVANCE` **(criterion secondhand)**.
- Caveat from the same lab: three correct across sessions beat three in one (above), and a Sprosse's three wins usually fall in one run.
- Candidate: Kulik, Kulik & Bangert-Drowns (1990), *Review of Educational Research* — mastery learning meta-analysis.

## Each mini game

Searched per drill on 2026-10-19; the direct evidence is thin everywhere except numbers and opposites, and none of it tests a drill as built.

- **Letters**: tiles among look- and sound-alikes is identification training; candidates: Logan, Lively & Pisoni (1991), *JASA*; Thomson (2018), *Journal of Second Language Pronunciation* 4(2), 208–231, on high-variability phonetic training **(verified)**.
- **Letters**: one recording or voice per sound is what that literature warns against.
- **Letters**: new-script studies use artificial alphabets; writing letters by hand beat viewing or typing them for recognition (Longcamp et al. 2008, *J. Cognitive Neuroscience*) **(found via a review, unverified)**.
- **Letters**: dictation evidence is small and mixed — some listening gain, shadowing did better on segmentation; no meta-analysis found.
- **Numbers**: Spelke & Tsivkin (2001), *Cognition* 78(1) — bilinguals trained on number facts in one language retrieved them faster in that language: drilled numerals do not transfer for free.
- **Numbers**: Prior, Katz, Mahajna & Rubinsten (2015), *Frontiers in Psychology* 6:266 — L2 number-word structure shapes arithmetic, and learners fall back on L1 for numbers **(verified)**.
- **Numbers**: the timed run and challenge are fluency practice; candidate: DeKeyser (2007), *Practice in a Second Language* (skill acquisition).
- **Dates**: same as numbers; `clock-registers.md` should cite where its registers come from, even if only "native ruling, date".
- **Countries**: not searched beyond the cognate angle; skipping names the two languages share fits de Groot & Keijzer (2000), *Language Learning*, on cognate ease — tangential.
- **SentenceScramble**: two searches found no experiment on sentence unscrambling as practice, only processing studies and teacher claims; this one stays a product judgment.
- **WordScramble**: solving an anagram is a classic generation task, and the generation benefit grows with the retention interval (Bertsch et al. 2007, *Memory & Cognition*, meta-analysis).
- **WordScramble**: harder anagrams buy no extra memory over easy ones in that literature, which questions climbing by masking more of the word.
- **Opposites**: Yang (2021), *Chinese as a Second Language* 56(1), 21–48 — antonyms taught as pairs, retention depends on knowing the L1 translations **(verified)**; a partial replication of Tagashira, Kida & Hoshino (2010) **(unverified venue)**.
- **Opposites**: asking only words that have arrived matches Yang's finding that unfamiliar meanings drag down both words of a pair.
- **Opposites**: antonym generation is one of Slamecka & Graf's (1978) original generation rules.
- **All seven**: Nation's (2013) aspects of word knowledge (form, meaning, use; receptive, productive) is the one frame that explains why each drill exists.

## Which words, in what order

- Areas earn the front by first-day usefulness, not frequency; candidate: Nation (2006), *Canadian Modern Language Review*, on vocabulary size for listening and reading.

## Hygiene

- Research that landed only in a commit: `77385412c` (ladder), `b9bec2c55` (retention, leech), `dd6cdc979` (watch pause), `a4ff8c521` (delay cost), `a7db63e27` (loudness).
- Dangling pointer: `BoxConfig.stepsSeconds` KDoc → `growth-evidence.md`, which has no ladder section.
- No doc owns the review loop's evidence; widen `growth-evidence.md` from intake to "pacing and review" or give it a sibling — the owner's call.
- Each citation wants one full reference line (authors, year, title, venue, link) before a public page can draw from it.

## Candidates for a public "the research behind it" page

- **Fewer new words per round does not help you remember more** — Nakata & Webb (2016), Yim, Dennis & Osth (2025); already cited.
- **Short sittings, not slow intake** — output interference (Criss, Malmberg & Shiffrin 2011) and a session floor because a short round is a massed one (Kornell 2009); already cited.
- **We don't tune for how good a round feels** — Soderstrom & Bjork (2015); to add.
- **A scheduler fitted to real reviews** — FSRS (Ye, Su & Cao 2022) plus the open benchmark; to add.
- **The watch makes you recall before it shows the options** — van den Broek et al. (2023); move from the commit into `surfaces.md`, after a ruling on the pause length.
- **Pictures help while a word is new, then step back to the answer** — Fiechter & Benjamin (2018) for the fading cue, van den Broek et al. (2021) for the reveal; to add.
- **Equal or expanding gaps matter less than spacing at all** — Latimier, Peyre & Ramus (2021); move from the commit into a doc.
- **A miss shows you the answer and has you write it once** — Pashler et al. (2005); to add.
- **Thinking the answer counts once a word is settled** — Smith, Roediger & Karpicke (2013); to add.
- **Three clean wins to climb** — Rawson & Dunlosky (2011); to add.
- **Numbers have to be practiced in the language itself** — Spelke & Tsivkin (2001); to add.
- **What the evidence cannot tell us** — the doc's own gap paragraph (no study manipulates intake in a real SRS) is a credible, rare thing to say out loud.
- Keep off the page until fixed: the listening wait-time citation, related words arriving together, and the typo budget.
