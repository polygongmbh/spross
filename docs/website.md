# spross.net — marketing site

Static landing page in `web/`, advertising Spross and letting visitors try the real numbers drill in the browser.
Positioning: fun, playful, easy, effective breadth-first vocabulary growth —
built to sit beside native-speaker conversation or a structured course, never to replace them.

## Brand carry-over

- Palette: `App/Sources/Design/Theme.swift` is the source of truth (stone-and-moss surfaces, clay accent, ocean/forest secondaries, ochre for near-miss — never red).
  `web/site.css` restates the hex pairs; when Theme.swift moves, the CSS follows.
- Type: SF Rounded in-app → `ui-rounded` system stack on the web.
- Wordplay: **Spross** (sprout) / **Sprosse** (ladder rung) — organic growth first, the rung as the progression wink
  (phrases unlock from component words; drill levels climb a digit at a time).
- "Sprössling" is the site's name for a subscriber/learner (coined here, not used in-app).

## Architecture

- `web/index.html` + `web/site.css` + `web/site.js` — hand-authored, no framework, light+dark via `prefers-color-scheme`.
- The drill runs on the real kern: `:kern` has a `js { browser() }` target whose `jsMain` facade
  (`net.spross.kern.web`) exports numbers-drill entry points over `Trainer` + `AnswerNormalizer`
  (target/pin details: `kern/docs/build.md`).
- `scripts/build-web.sh` assembles a deployable `web/dist/` (gitignored):
  runs `:kern:jsBrowserDistribution`, copies the bundle beside the static files.
- Deploy: upload `web/dist/` to any static host, point spross.net at it. No server code.

## Drill scope (the "taste")

- Language chips (de/en/es/sw/uk from `catalog/languages.json` data), optional generated primer
  (0–12, tens, 100/1000 with place-value words — spelled by the kern, so never wrong), then the drill.
- Mechanics mirror iOS (`docs/surfaces.md` § Trainers): numeral → typed word, exact=green/typo=amber+correction/wrong=reveal,
  streak line, two clean rights ramp a digit up, one miss steps down; level capped at 4 digits on the web.
- Answer-side audio via the browser's SpeechSynthesis voice when one exists for the language; silent degrade.
- After ~10 answers, a gentle interstitial invites the mailing list; drilling continues freely.

## Mailing list

- Form headline: "Ready to become a Sprössling?" — POSTs `email` to `SIGNUP_ENDPOINT` in `web/site.js`.
- The endpoint is a placeholder until an account exists (Buttondown-shaped:
  `https://buttondown.com/api/emails/embed-subscribe/<slug>`); any provider accepting a form POST works.
- No analytics, no cookies on the page.
