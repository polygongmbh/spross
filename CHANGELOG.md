# Changelog

## 0.6.0 — 2026-07-17

- **Single-screen app**: the tab bar is gone. Heute is the whole app —
  session card, training, and a condensed Fortschritt section (14-day
  activity, active cards, retention) in one scroll; the Box opens via
  the 📦 button top right (or straight from the empty-box card).
- **Session card stats**: a due-count ring (fills as you review through
  the day) and your 🔥 streak now sit right on the "Los geht's!" card.
- **Answer-colored progress bar**: sessions and drills fill the top bar
  one segment per answer — green right, amber tough (hard rating or typo),
  brick wrong; the rest stays neutral until answered.
- **All content packs integrated** (343 cards per pair, up from 230):
  "Die ersten Wörter" opens the box (survival kit incl. the phrases moved
  from the school area), then the rooms, then Amt & Behörde, Arzt &
  Gesundheit, Arbeit & Beruf — every pack generated, translated, and
  adversarially language-verified before merge. Existing boxes absorb the
  new areas automatically on next launch.

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
