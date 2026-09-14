# duolernen — direction & roadmap

Status 2026-07-17 (v0.6.0; see docs/dev-log.md for the narrative): **Phases 0–3 shipped** — native iOS app in `app/`:
DuoKern (FSRS-5, importer, box engine, session composer, slot trainers,
phrase slot templates; 100+ tests) +
SwiftUI app (Heute/Box/Fortschritt, typed-first reviews, Extra-Runde,
number/year/clock/Sätze drills in de/sw/uk, word-of-the-moment widget),
watchOS companion (micro-reviews + complication, WatchConnectivity sync),
and the Photos-face renderer (`app/tools/FaceGen`, `app/docs/facegen.md`).
Content packs (Basics/Amt/Arzt/Arbeit) are verified and MERGED (343 cards/pair).
Remaining: couple mode (post-v1); real-device watch pairing validation; phrase component-link gaps.
Earlier context: working web prototype (`duolernen/`, Lovable/React/Vite, localStorage) + extracted content assets.
Assets in hand: verified trilingual seed data (`data/`),
content-QA methodology and design learnings (`docs/sprachposter-learnings.md`),
a prototype with session/couple/trainer mechanics (`duolernen/prompt.md` is the original brief),
validated delivery trick for the watch (Photos/Portrait face, see below).

## Product thesis

A "growing digital box of cards" as a proper personal mobile app:
curated vocab that expands (topics or single words) only when the current material sits,
scheduled by FSRS,
trained productively (composing phrases, slot drills with times/dates/numbers),
with native non-English pairs (DE–SW, DE–UK) as the wedge nobody else serves.
Note on direction: the poster data teaches **German as target** as much as it serves German speakers —
German headwords carry the articles/plurals and the German grammar sheet;
SW/UK are the learner's anchor language. One card model, both directions.
Passive surface: vocabulary on the Apple Watch face at every wrist raise.
North star (from the prototype brief): every screen answers **"What do I do right now?"** with zero ambiguity —
fully structured sessions, no decision fatigue.
Priority call (2026-07-16): core loop first — SRS + incremental growth + phrases.
Couple/trainer mode is a later enrichment, not v1 forefront.

## What the web prototype already proved (keep these)

- **Couple / trainer mode** — the sleeper differentiator.
  Partner-as-trainer sessions (Korrektur-Runde over saved writing, Rollenspiel, Frage-Spiel,
  free-conversation cards, day-of-week rotation) make a native-speaker partner a first-class feature
  and solve production grading with a human in the loop. No mainstream app has this.
- **Exam-aligned tracks**: topics are the 12 official Goethe A1 curriculum topics —
  a concrete goal (pass A1) instead of Duolingo's aimless tracks. Extend to A2/B1.
- **Procedural slot trainers work**: NumbersTrainer and ClockTrainer generate unlimited drills
  from number→word functions for German *and* Swahili (incl. the saa system's +6-hour reckoning);
  GenderTrainer, date trainer, FalseFriends, PronunciationGuide round it out.
  These are Phase-3 mechanics already running in TypeScript — port, don't reinvent.
- **PersonalDictionary** = the seed of the growing box (user-added words).
- **Recall UX refinements** (hard-won through many iterations, see `duolernen/` git log):
  typed answers beat flip-and-self-grade; normalize before comparison (strip "the"/"to");
  wrong answer reveals **inline** — the card must stay visually stable, no flip;
  missed cards cycle back at session end; Enter advances; correct answers auto-advance after ~800 ms;
  bias random sampling toward useful ranges (years in the numbers trainer).
- **Streak forgiveness**: one missed day doesn't break the streak — warm, never punishing.

Known prototype gaps: no real SRS (only known/again, no scheduling — FSRS is the upgrade),
localStorage single-device, seed data has errors (e.g. `word: "living"` for *wohnen* in topics.ts)
and never went through the sprachposter-style verification sweep.

## Phases

### Phase 0 — watch-face pipeline prototype (days, no App Store)

Prove the passive-exposure loop before writing any Swift:

- Script renders 24 card images (emoji illustration + article-colored German + translation,
  reusing the poster design system and fonts in `sprachposter/fonts/`)
  from `data/vocab-de-*.json`.
- Sync into a photo album → Apple **Photos watch face** (24-photo cap, shuffles per wrist raise).
  This is the WordFace mechanism; our edge is real card selection behind it.
- Re-render daily from a due-card selection (manual or scripted FSRS-lite)
  instead of a static 24.

### Phase 1 — content schema & pipeline hardening

- Extend the card schema with first-class fields learned from the poster session:
  literal gloss, usage note, plural convention, area/topic, register.
- Codify the QA loop as a repeatable pipeline:
  generate → per-language adversarial review sweep
  (explicit anti-calque / anti-Russism lens, review-only, corrections-with-reasons)
  → human spot-check queue.
- New topic packs for the Integrationskurs audience: Amt/Behörde, Arzt/Gesundheit,
  Arbeit, Einkaufen/Geld, Zahlen/Uhrzeit/Datum (the slot-drill substrate).

### Phase 2 — iOS app v1 (the centerpiece)

The core loop, nothing else:

- SwiftUI + local store (SwiftData or GRDB), fully offline.
- FSRS scheduler (port the reference implementation; ~20 parameters, small surface).
- **Box mechanic**: seed deck per pair; new cards trickle in only while retention holds;
  user chooses what enters next (topic pack or single word via personal dictionary).
- **Phrases as first-class cards**: the 64 verified phrases per pair enter the box
  once their component words sit (composition = the phrase card's unlock condition).
- Review UX straight from the prototype's refinements:
  typed answers, normalization, inline reveal (card visually stable),
  retry cycle at session end, Enter to advance, ~800 ms auto-advance.
- Structured sessions, not a menu: the app composes today's session (North star).
- Watch companion: 20-second review sessions; Smart Stack widget/complication
  with pre-scheduled WidgetKit timeline (new word every 5–15 min);
  automated Photos-face rendering as the passive layer.

Explicitly NOT in v1: couple mode, accounts/sync, writing session steps.
(Listening was on this line until v1 shipped; the passive layer Phase 0 prototyped is
now a surface of its own — `app/docs/surfaces.md` § Listening.)
(Sync is planned now that v1 has shipped, as the box side of an account rather than a
social one — `app/docs/sync.md`.)

(The line that also ruled out "streak gamification beyond a simple counter" is gone: it was
written before v1 shipped and was read afterwards as a bar on showing progress at all. What
the app actually holds to is narrower and lives in `app/docs/design.md` — a celebration is
earned by a record rather than emitted per event, and nothing on screen may overstate what
the box has done.)

### Phase 2.5 — enrichments (post-v1, order by appetite)

- Couple/trainer mode (Korrektur-Runde, Frage-Spiel) — nice, not forefront.
- Slot trainers folded into sessions (see Phase 3).
- Listening/writing steps from the prototype's session structure.

### Phase 3 — composition & slot training

- Port the procedural trainers (numbers, clock incl. Swahili saa time, dates, gender) from the prototype.
- Phrase templates with typed slots: „Der Zug fährt um {Uhrzeit} ab."
- Pre-generate + verify variants at content-build time (no live LLM on device);
  grading is local pattern matching → offline-capable.

## Decisions made

- 2026-07-16: v1 = personal core-loop app (FSRS + growing box + phrases);
  couple mode deferred to post-v1. Card model is direction-agnostic from day one
  (the poster data serves German-as-target and German-as-base alike).

## Open decisions

- v1 pair to dogfood first: DE–SW (in-house native QA, both directions used in the household)
  vs DE–UK (larger audience later). Data is ready for both.
- Posters as marketing wedge: same design system renders posters, watch cards, app cards —
  the PDF poster could be the free artifact that funnels into the app.
- Prototype's fate: keep `duolernen/` as the interaction lab (fast to iterate via Lovable);
  the iOS app is the product. Sync content from `data/` into both rather than hand-editing topics.ts.
