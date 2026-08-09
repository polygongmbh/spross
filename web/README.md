# spross.net — marketing site

Static landing page advertising Spross and letting visitors try the real numbers drill in the browser.
Positioning: fun, playful, easy, effective breadth-first vocabulary growth —
built to sit beside native-speaker conversation or a structured course, never to replace them.

## Brand carry-over

- Palette: `App/Sources/Design/Theme.swift` is the source of truth (stone-and-moss surfaces, clay accent, ocean/forest secondaries, ochre for near-miss — never red).
  `web/site.css` restates the hex pairs; when Theme.swift moves, the CSS follows.
- Type: SF Rounded in-app → `ui-rounded` system stack on the web.
- Wordplay: **Spross** (sprout) / **Sprosse** (ladder rung) — organic growth first, the rung as the progression wink.
  It is played, never explained: the drill's level IS a "Sprosse" (with a small ladder beside it and one
  hoverable gloss on first mention), so a German reader gets the joke and everyone else still gets a ladder.
  The footer signs off with it — "Sechs Sprossen, ein Spross." — as a colophon, never as a heading.
- Copy names what the learner gets, never how the engine works — no FSRS, no scheduling internals on the page.
- Everything below the positioning line is drawn as one ladder: two tapering rails (`--stem`) down the sides,
  a rung between each feature idea and one above and below the drill, and no card boxes —
  the content stands in the gaps, the way a Sprosse carries you.
  Only the drill card and the signup lift off the ladder, because those are the things you touch;
  every other section is a compartment between the rails, with no stage of its own.
  The page carries two measures only: the hero's full width and 46.5rem for everything below it.
- "Sprössling" is the site's name for a subscriber/learner (coined here, not used in-app).

## Architecture

- `index.html` + `site.css` + `site.js` — hand-authored, no framework, light+dark via `prefers-color-scheme`.
- The drill runs on the real kern: `:kern` has a `js { browser() }` target whose `jsMain` facade
  (`net.spross.kern.web`) exports numbers-drill entry points over `Trainer` + `AnswerNormalizer`
  (target/pin details: `kern/docs/build.md`).
- `scripts/build-web.sh` assembles a deployable `web/dist/` (gitignored):
  runs `:kern:jsBrowserDistribution`, copies the bundle beside the static files.
- Deploy: upload `web/dist/` to any static host, point spross.net at it. No server code.

## Drill scope (the "taste")

- Language chips (de/en/es/sw/uk from `catalog/languages.json` data), an optional primer, then the drill.
- The primer IS the app's numbers page: `Trainer.reference(language)` band by band
  (`ones`/`teens`/`tens`/`twenties`/`compounds`/`hundreds`/`places`/`forms`), the page supplying only
  the English headings. Every reading is generated from the packs the drill grades against,
  so the table cannot drift from it. The mid-run "?" shows the `tens` band, in every language.
- Mechanics mirror iOS (`docs/surfaces.md` § Trainers): numeral → typed word, exact=green/typo=amber+correction/wrong=reveal,
  any accepted answer extends the streak, two clean rights ramp a Sprosse (= a digit) up, one miss steps down; capped at 4 on the web.
- Before a language is picked, the card cycles a numeral and its reading through the five languages — the kern spelling live.
- The app's own chimes play on verdicts (`web/assets/sounds/`, copies of `App/Resources/Sounds/`);
  a per-language best streak lives in localStorage, and only beating it earns the cheer.
- Answer-side audio via the browser's SpeechSynthesis voice when one exists for the language; silent degrade.
- After ~10 answers, a gentle interstitial invites the mailing list; drilling continues freely.

## Mailing list

- Form headline: "Ready to become a Sprössling?" — POSTs `email` to `SIGNUP_ENDPOINT` in `web/site.js`.
- The endpoint is a placeholder until an account exists (Buttondown-shaped:
  `https://buttondown.com/api/emails/embed-subscribe/<slug>`); any provider accepting a form POST works.
- No analytics, no cookies on the page.
