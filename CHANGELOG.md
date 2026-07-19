# Changelog

## 0.12.3 — 2026-07-19

- **Fuller small & medium widgets**: the 2×2 and 4×2 widgets no longer sit
  half-empty — the small tile gains a streak/retention footer and the medium
  now shows three words under a stats header (streak · fällig · retrievability).

## 0.12.2 — 2026-07-19

- **German clock accepts "um zehn"**: full-hour answers now count whether you
  write "zehn Uhr", "punkt zehn", or the colloquial "um zehn". The reveal hint
  lists every accepted wording ("auch: … oder …") instead of a regional label.

## 0.12.1 — 2026-07-19

- **Swahili clock is less picky about the time of day**: the day-period word
  (asubuhi/mchana/jioni/usiku) is now optional, so the time alone counts as
  correct. "mchana" (afternoon) now starts at noon rather than 10, and the
  fuzzy mchana↔jioni boundary in the late afternoon accepts either word.

## 0.12.0 — 2026-07-19

- **The app speaks your language**: the interface now shows in the language you
  already know, not always German — so if you're *learning* German, the whole UI
  is in English instead of a language you can't yet read. (German and English for
  now; Swahili and Ukrainian interfaces to follow.)
- **A little immersion**: the main "start" and "continue" buttons show the word in
  the language you're learning beneath the familiar one — e.g. "Let's go! / Los
  geht's!" while learning German.

## 0.11.0 — 2026-07-19

- **A wrong article is a slip, not a miss**: typing the wrong (or mistyped) article —
  "das Tisch" for "der Tisch", or a fat-fingered "dee Tisch" — now counts as a typo,
  so you still get credit and see the correct form, instead of failing the card.
- **Nicer finish, keep-going option**: sessions no longer interrupt with a "Kurze
  Pause" countdown. Instead each session ends on a summary (how many new · settled ·
  repeated) with confetti, and a "Weiter üben" button that keeps pulling due and new
  cards for as long as you like.

## 0.10.0 — 2026-07-18

- **New cards flow with your pace, not the calendar**: instead of a fixed "X per
  day", the box keeps a pool of cards you're actively learning topped up — clear
  them and more come in, so you can take on dozens in a day when you feel
  adventurous, or none on a quiet one. Set the pool size (default 8) under
  "Karten gleichzeitig im Lernen".
- **"Pack in die Box" now always works**: cards you explicitly add show up in your
  very next session, even when the learning pool is full.

## 0.9.1 — 2026-07-18

- **Watch practice polish**: the four answer tiles sit in a 2×2 grid (no more
  scrolling), the prompt word is bigger, a wrong pick lingers so the correct
  answer registers, and the app version shows on the watch home screen.

## 0.9.0 — 2026-07-18

- **Practice on the watch ("Üben")**: a tap-based multiple-choice drill over the
  vocab you're learning, right on the wrist — pick the matching translation from
  a few tiles, instant green/amber feedback, endless with a streak. Pure
  practice; it never touches your box.
- **Leaner watch app**: the number/clock/sentence drill generators no longer
  ship to the watch — they moved into a separate module the watch doesn't link.

## 0.8.1 — 2026-07-18

- **Large home-screen widget**: the widget now offers a 4×4 size that fills the
  space with a stats header (streak · fällig · retrievability) above a rotating
  list of five attention-worthy words, instead of a single card on a mostly
  empty tile.
- **Word above the lock-screen clock**: a new inline lock-screen widget shows
  one rotating word next to the time.

## 0.8.0 — 2026-07-18

- **Typos pause for review**: a slightly misspelled answer no longer flashes
  past — your text stays on screen with the correct spelling and a "Weiter"
  tap, so you can see the slip. A clean answer still auto-advances.
- **"Aufdecken" fills the answer field** with the answer instead of leaving an
  empty box beside it; a wrong guess keeps your text with the correction below.
- **Numbers drill goes big**: all three languages now read up to billions
  (milioni/bilioni · Million/Milliarde · мільйон/мільярд), and the drill
  favours rounder numbers (more zeros) so long ones are less tedious to type.
  Swahili answers may drop the "na" ("mia tatu sitini tano").
- **Numbers drill hints**: reaching a new length shows its place word once
  (tausend / elfu / …); the Swahili drill adds a "?" tens look-up
  (10 kumi … 90 tisini) that also appears after a wrong answer. A typo or a
  looked-up answer counts amber and doesn't advance the level.
- **Years drill removed** — it read identically to plain numbers; years live
  on inside sentence drills.

## 0.7.3 — 2026-07-17

- Swahili verbs count as correct without the ku- prefix ("pika" = "kupika");
  the reveal still shows the full infinitive.

## 0.7.2 — 2026-07-17

- **Tighter cards**: "der Kühlschrank" as one line with the article inline in
  its color; the German plural line appears only when you're learning German;
  the answer no longer reserves empty space — the card grows on reveal.
- Settings version reads "v0.7.2" without the build-number parentheses.

## 0.7.1 — 2026-07-17

- **New words show the learned language first**: the very first exposure of a
  card always displays the unknown word and asks for the known one — you
  can't recall a word you've never seen. Mixing continues from there.
- **Glosses are hints now**: annotations that had leaked into answer strings
  ("kuitwa · wörtl. „gerufen werden“", "(Pl. maombi)", "(nur Pl.)", "(m.!)")
  moved into reveal-notes on 48 entries — they no longer clutter the prompt
  and no longer break typed-answer matching; genuine synonyms
  (мишка, термін, візит …) became accepted answer variants instead.
- Settings footer shows the app version and a "Feedback senden" button
  (mail to lang@polygon.gmbh).

## 0.7.0 — 2026-07-17

- **Answer-colored progress bar**: sessions and drills fill the top bar
  one segment per answer — green right, amber tough (hard rating or typo),
  brick wrong; the rest stays neutral until answered.
- **All content packs integrated** (343 cards per pair, up from 230):
  "Die ersten Wörter" opens the box (survival kit incl. the phrases moved
  from the school area), then the rooms, then Amt & Behörde, Arzt &
  Gesundheit, Arbeit & Beruf — every pack generated, translated, and
  adversarially language-verified before merge. Existing boxes absorb the
  new areas automatically on next launch.
- **Adaptive drills**: number/year/clock drills start easy and ramp —
  numbers begin single-digit, two rights in a row add a digit, a miss
  removes one (the level shows next to the streak: "🔢 3 Stellen · 🔥 5");
  years widen from recent decades to the full historic range, the clock
  from full hours to all five-minute forms.
- **Drill counter reads "richtig/gesamt"** instead of the useless n/n,
  and the pointless "Wusste ich" button is gone from generated drills
  (revealed counts as a miss — the answer was on screen).
- **Box zurücksetzen** in settings: fresh start from the current seed
  (early testers get the new "Die ersten Wörter" ordering), config kept.

## 0.6.0 — 2026-07-17

- **Single-screen app**: the tab bar is gone. Heute is the whole app —
  session card, training, and a condensed Fortschritt section (14-day
  activity, active cards, retention) in one scroll; the Box opens via
  the 📦 button top right (or straight from the empty-box card).
- **Session card stats**: a due-count ring (fills as you review through
  the day) and your 🔥 streak now sit right on the "Los geht's!" card.

## 0.5.0 — 2026-07-17

- **Mixed-direction practice** (on by default): each card keeps ONE memory
  state; the direction you're quizzed in alternates per review — translating
  both ways helps the vocab sit. Settings now say what they mean:
  "Ich lerne: Swahili/Deutsch" + "Beide Richtungen üben" toggle
  (the misleading Erkennen/Tippen labels are gone).
- **Typo tolerance**: ~10% of letters (min word length 5); "Kuhlschrank"
  counts, and the proper spelling is shown when you were close.
- **Review feel**: keyboard up instantly with everything visible above it,
  compact card, flip transition between cards (no more answer spoilers),
  soft sounds + haptics for correct/wrong/reveal, one morphing
  Aufdecken/Prüfen button, emoji hidden while querying cards that already sit,
  and a calmer 1.2 s pause on correct answers.
- **Endless drills**: trainers run as long as you want with 🔥 streak +
  best-of-run instead of fixed ten; drills always run in the language you're
  learning (toggle removed); the Sätze drill reverses for German learners
  (target sentence shown, German typed).
- **Content**: new verified packs under review in `data/packs/` — Basics
  ("Die ersten Wörter" survival kit, now home of Langsamer!/Wiederholen/
  Verstehen, moved out of the school area), Amt, Arzt, Arbeit.

## 0.4.0 — 2026-07-17

- **Apple Watch companion**: micro-review sessions on the wrist (reveal +
  four-button grading; the phone reschedules with real answer timestamps via
  WatchConnectivity) and a "Wort des Moments" complication
  (rectangular/circular/corner, 15-minute rotation).
- **Photos-watch-face renderer** (`tools/FaceGen`): renders up to 24
  attention-ranked card images with the top zone kept clear for the watch
  clock — drop into an album, set as Photos face, new word every wrist raise
  (see `docs/facegen.md`).
- **Sätze drill**: sentence rounds composing verified phrase templates with
  generated numbers/years/times ("Der Zug fährt um 20:00 Uhr ab." →
  *treni inaondoka saa mbili usiku*). All templates passed a dedicated
  language review; Ukrainian counting templates deliberately reject feminine
  numeral variants before masculine nouns — that agreement is the lesson.

## 0.3.0 — 2026-07-17

- **Training drills** on the Heute screen: Zahlen, Jahreszahlen, and Uhrzeit as
  quick 10-task typed rounds in German, Swahili (incl. the saa system with a
  German gloss explaining the −6-hour shift), and Ukrainian — language toggle
  defaults to what you're learning. Drills are stateless: they never touch the
  box or scheduling. Ported from the web prototype's refined generators
  (golden-verified against the original code; fixed its "einsundzwanzig" bug);
  Ukrainian is new and passed a dedicated language review (no Russisms,
  common typed variants like «чверть по другій» accepted).
- **"Wort des Moments" widget** (home + lock screen): a word from your box
  every 15 minutes, biased toward cards that need attention, with due-count
  badge and article colors; refreshes after every session.

## 0.2.0 — 2026-07-17

- **Type before revealing** in both directions: recognition mode now offers an
  answer field first (checked against the translation; "/"-separated
  alternatives all count); "Aufdecken" remains as the self-grading fallback.
- **Extra-Runde**: an on-demand practice round from the done screen — anything
  due, everything you explicitly packed into the box (bypassing the daily
  budget), and soonest-due cards reviewed ahead. Never empty while the box has cards.
- Hyphen/apostrophe-insensitive answer matching ("E-Mail" = "Email").
- Interrupted sessions no longer lose reviews from the streak/statistics
  when iOS evicts the app.

## 0.1.0 — 2026-07-17

First working version, built end-to-end:

- **Growing box**: seed decks for German–Swahili and German–Ukrainian
  (233 verified cards per pair from the sprachposter dataset, curated order preserved);
  new cards enter on a daily budget (default 5) behind a health gate,
  phrases unlock once all their component words sit (stability ≥ 3 days).
- **FSRS-5 scheduling**: faithful port, golden-vector-tested against ts-fsrs v4.7.1;
  every answer is a real review, retries included; leeches auto-suspend after 8 lapses.
- **Composed sessions**: due reviews with reserved slots for growth, drain loop for
  10-minute learning steps (with a short in-app pause when steps come due),
  recognition mode (4-button self-grade) and typed production mode with inline reveal.
- **Three screens**: Heute (one-glance session CTA / done state),
  Box (areas, sitting/learning split, pack-into-box, leech revive, settings),
  Fortschritt (streak with one-day forgiveness, stats, 14-day activity).
- **Offline, file-backed**: one JSON document per language pair, atomic debounced saves.
- Warm poster-derived design language, full dark-mode support, never-punishing feedback.
