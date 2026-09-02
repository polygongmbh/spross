# spross.net — marketing site

Static landing page in `web/`, advertising Spross and letting visitors try the real numbers drill in the browser.
Positioning: fun, playful, easy, effective breadth-first vocabulary growth —
built to sit beside native-speaker conversation or a structured course, never to replace them.

## Brand carry-over

- Palette: `App/Sources/Design/Theme.swift` is the source of truth (stone-and-moss surfaces, clay accent, ocean/forest secondaries, ochre for near-miss — never red).
  `web/site.css` restates the hex pairs; when Theme.swift moves, the CSS follows.
- Type: SF Rounded in-app → `ui-rounded` system stack on the web.
- Wordplay: **Spross** (sprout) / **Sprosse** (a ladder's step) — organic growth first, the climb as the progression wink
  (phrases unlock from component words; drill levels climb a digit at a time).
  The pun is performed, never written out: the drill's level IS a Sprosse with a small ladder beside it,
  where a card spelling the joke out had stopped being one.
- Copy names what the visitor gains, never how the engine works.
  "Following a conversation takes many words, not a few perfect ones" is a reason to want the app;
  "no account, no ads, FSRS-6, and here are the scheduling rules" is an implementation fact,
  and a landing page earns its attention with the first.
  Before shipping a copy block, ask what changes for the reader — if the answer is
  "the engine works this way", cut it or reframe it as the outcome it buys.
- "Sprössling" is the site's name for a subscriber/learner (coined here, not used in-app).

## Architecture

- `web/index.html` + `web/site.css` + `web/site.js` — hand-authored, no framework, light+dark via `prefers-color-scheme`.
- The drill runs on the real kern: `:kern` has a `js { browser() }` target whose `jsMain` facade
  (`net.spross.kern.web`) exports numbers-drill entry points over `Trainer` + `AnswerNormalizer`
  (target/pin details: `kern/docs/build.md`).
- `scripts/build-web.sh` assembles a deployable `web/dist/` (gitignored):
  runs `:kern:jsBrowserDistribution`, copies the bundle beside the static files.
  Every `web/*.html` ships, so a new page needs no edit there.
- Deploy: upload `web/dist/` to any static host, point spross.net at it. No server code.

## Legal pages

- `web/privacy.html` and `web/impressum.html` (English, what a visitor lands on)
  with `privacy.de.html` / `impressum.de.html` beside them —
  one click apart, `hreflang`-paired, cross-linked to each other and reachable from every footer.
  Separate pages rather than one bilingual scroll: each language keeps a quotable URL,
  and a reader never sits in the wrong version.
- The company facts restate `App/Sources/Screens/CreditsView+Legal.swift`, which is their source of truth
  (the in-app Impressum and this page have to name the same GmbH).
- The app links `https://spross.net/privacy` extensionless,
  so the host has to serve `/privacy` from `privacy.html` — a host that does not needs a redirect rule.

## Drill scope (the "taste")

- Language chips (de/en/es/sw/uk from `catalog/languages.json` data), optional generated primer
  (0–12, tens, 100/1000 with place-value words — spelled by the kern, so never wrong), then the drill.
- Mechanics mirror iOS (`docs/surfaces.md` § Trainers): numeral → typed word, exact=green/typo=amber+correction/wrong=reveal,
  any accepted answer extends the streak, two clean rights ramp a digit up, one miss steps down; level capped at 4 digits on the web.
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
