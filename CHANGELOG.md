# Changelog

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
