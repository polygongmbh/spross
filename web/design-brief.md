# Design brief — spross.net rebuild

Written from the outside, on the page as it stands at commit `a6f2c68`.
Screenshots referenced: `desk-light-full.png`, `desk-light-fold.png`, `desk-dark-full.png`,
`mob-fold.png`, `mob-full.png`, `drill-picked.png`, `drill-run.png`, `drill-run-after.png`, `drill-primer.png`.

## 1. Verdict

Rebuild the page; keep the drill.
The current page is a competent, tasteful document that fails at the one thing a pre-launch landing page must do —
it never says that Spross is a phone app, never shows it, and buries its single irreplaceable asset
(a real drill from the real engine) at 67 % scroll depth in a 544 px card.
The craft is real and mostly wasted on the wrong things: two ladder rails drawn behind the whole page
end up reading as a table border in `desk-light-full.png`, while the forest — the app's most beautiful,
most specified idea (`docs/design.md` L206–238) — gets one 🌳 emoji and two lines of text.
There is exactly one image in `web/assets/`: the app icon.
Iterating on this shape means keeping a page whose information architecture is upside down;
the copy, the palette, the wordplay and the entire `site.js` drill survive the rebuild intact, so the cost is a day of layout, not a restart.

## 2. What the page is for

**The job:** convert a stranger's curiosity into an email address, using the only proof available before launch — a working piece of the product.

**Audience.** Adults already learning a language the hard way: a tandem partner, a Volkshochschule course,
an exchange semester, a move to Germany.
They are not shopping for a first app; they have one and it is not filling the gap between their conversations.
They arrive from a link in a language-learning forum, a Mastodon or Reddit post, a tandem group chat, a conference talk —
so they arrive **mid-scroll, sceptical, and on a phone**.
Assume no App Store listing to click through to, no brand recognition, and no patience for a manifesto.

**The one action:** the email field. Everything else is instrumentation for it.

**The 10-second contract.** Within ten seconds, above the fold, a visitor must know:

1. Spross is an **app for your phone** (iOS now, Android coming) — today the page never once says this.
   "iOS", "iPhone", "Android", "App Store", "download", "offline" appear zero times in `index.html`; "the app" appears once, in a subordinate clause.
2. It **keeps your vocabulary growing between lessons** — it is not a course and not a competitor to their tandem partner.
3. They can **try it right now, on this page, without installing anything** — and that trial is the real thing.
4. It is **not out yet**, so the ask is an email, not a download.

The current fold (`desk-light-fold.png`) delivers roughly one and a half of these four.

## 3. What is working

Name these as protected; a rebuild that loses them is a worse page.

- **The embedded drill, entire.** `site.js` is the best thing on the site and one of the best things any pre-launch
  language app has ever put on a landing page: real `WebTrainer` grading, typo tolerance with a correction line,
  the app's own chimes, per-language localStorage records, `SpeechSynthesis` read-back, a primer generated from the same
  packs it grades against. **None of this logic should be touched.** Only its frame changes.
- **The showcase.** In `drill-picked.png` the card cycles `🇩🇪 33 dreiunddreißig` and rotates languages every 2.6 s.
  This is the page's best single moment: it demonstrates breadth, the engine, and the invitation in one three-word line, wordlessly.
  It deserves to be ten times its current size and to be the first thing on the page.
- **The palette.** Stone-and-moss paper with a clay accent (`#A23B0B` / `#FF9A6B`) is genuinely distinctive in a category
  of purple-and-mint gradients, and the dark scheme in `desk-dark-full.png` is properly built, not inverted.
  The accessibility discipline behind it (ink-strength accents, `--on-color` never white) carries straight to the web.
- **The copy voice.** "Sleep well — your words are still growing." "Letters at a garden's pace."
  "Start by counting to ten — the novels can wait." This is written, not templated, and it is on-strategy: it names what
  the learner gets and never mentions FSRS. Reuse it nearly verbatim; there is less of it in the rebuild, not different copy.
- **The positioning line.** "Spross doesn't replace your tandem partner or your course" is the sharpest sentence on the page.
  It survives — but not as a lonely centred strip at 385 px (see §4).
- **The two colour-blindness accommodations** in `site.css` (the miss segment is thin as well as brick; ochre never red)
  and the `prefers-reduced-motion` branches. Carry all of them.

## 4. What is not

### Structural

**S1 — The page never identifies the product.** The single largest failure.
There is no sentence saying "a free iPhone app", no device shot, no App Store badge or "coming to the App Store" line,
no mention of offline, no accounts, no ads.
A visitor finishing the page still cannot answer "is this a website, an app, or a method?"
Meanwhile `README.md` says there is a watchOS companion, widgets, and an Android app — none of which the page knows about.
The strongest competitive claims Spross owns (offline, no account, no ads, your own words, real recordings)
are the ones a Duolingo-weary reader is scanning for, and four of the five are absent.

**S2 — The drill is buried.** Measured at 1440 × 900: the drill stage starts at y = 1499 and the card at y = 1699
of a 2548 px page — 67 % down. On mobile (`mob-full.png`, 3263 CSS px) it is ~68 % down.
The page asks a stranger to read a hero, a positioning paragraph and six feature blocks before it shows them the one thing
that would convince them. Reverse this: the drill is the argument, the features are the footnotes.

**S3 — The drill's frame steals the drill's space.** In `drill-run.png` a live run is showing the numeral `8` at 56 px
inside a 544 px card, while 200 px directly above it are held by "How far can you count?" and a three-line intro paragraph
that is now stale — the visitor has already picked a language and started. On a 900 px viewport, roughly a quarter of the
screen is copy about an activity the visitor is currently doing.
The card is 544 px of a 1440 px window: 38 %. The most interactive object on the page is its smallest.

**S4 — Six features of exactly equal weight is no hierarchy at all.** In `desk-light-full.png` the six `<li>`s are
typographically identical: same emoji size, same 1.2 rem heading, same two-line body, same rung between each.
"Sown, not crammed" (the thesis) and "Your own words, too" (a feature) are rendered as peers.
The reader has no way to know which two things actually matter, so they skim all six and retain none.

**S5 — The ladder reads as a table.** The `.climb::before` rails at x ≈ 348 and 1092, with a 2 px rung between each `li`,
produce a bordered six-row grid. Nobody who does not already know the intent will see a ladder;
in `mob-full.png`, where the rails sit near the screen edge and the rungs span the full width, it is unambiguously a table.
The `--climb-shift` parallax (15 lines of JS, max 90 px of drift over 1800 px of scroll) is imperceptible and does not rescue it.
Worse, the rails pass *behind* the drill and signup cards, so what the eye actually sees is two vertical lines
that disappear and reappear — visual noise with no explanation.

**S6 — The primer detonates the page.** In `drill-primer.png` opening the primer grows the document from 2548 px to 3883 px:
a ~1300 px inline table that pushes "Start counting" far above the viewport, so the visitor who peeked at the numbers
has to scroll back up to begin. The tables also stretch inside their grid cells — see the "Put together" column,
where 31 / 45 / 99 are spread across the height of the taller "Hundreds" table beside them.

**S7 — The forest is missing.** `docs/design.md` L206–238 specifies a picture worth showing:
one tree per area, a skyline against a shared rolling ground line, canopy = words that landed,
blossom and fruit appearing *on* it, a tree that is one organism its whole life.
The page compresses that into "🌳 A forest, not a chart" + two lines.
This is the app's signature screen and the answer to "why not just use Anki", and it is described rather than shown.

**S8 — The wink is invisible where most people read.** The Sprosse gloss is a `title` attribute
(`index.html` L50, L90) — a hover tooltip. On the phone, where the majority of this traffic lands, it does not exist.
So a non-German reader meets an all-caps "SPROSSEN" heading over six bullets and a mid-run label reading "Sprosse 1"
with no way to learn what either means. The joke is being played to an audience that cannot hear it.

**S9 — The summary sends the visitor away instead of closing.** `#summary` offers three buttons of near-equal weight
("Keep practicing", "Another language", "Become a Sprössling"), the last of which is an anchor that scrolls the visitor
*out* of the moment of maximum warmth and into a separate card lower down. The one instant the page has earned an email
is the instant it hands over navigation.

### Surface

**s1 — The signup card is off-axis by 88 px.** Real bug, visible in `desk-light-full.png`.
`.signup` sets `margin-left/right: auto` at `site.css` L559–560, then the shorthand `margin: 0 0 3rem` at L567 resets both to zero.
Measured: `.signup` sits at x = 260 while `.features` and `.drill-section` sit at x = 348.
The consequence is the odd thing at the bottom of the page — the right ladder rail floats *outside* the signup card's
right edge while the left rail is buried *inside* it.

**s2 — Misses render as a stray red rule.** In `drill-run-after.png`, four misses paint as two 2 px brick hairlines
across the top of the card. The thin-segment colour-blindness accommodation is right in principle,
but at 6 px total bar height, four adjacent misses read as a horizontal rule someone forgot to delete, not as progress.

**s3 — The empty progress bar leaves a hole.** `run-head` is a `44px 1fr 44px` grid; before the first answer the `1fr`
centre is blank (`drill-run.png`), so the run opens with a ✕ and a 🔊 marooned at opposite corners of an empty row.

**s4 — Emoji as the entire icon set.** 🌱🌿⏰🗣️✍️🌳 in the features, 🔊/🔇 for mute, 🏆🎉💪🌱 in the summary, ✕ for close.
This is the visual language of a README, not of a product whose app ships a hand-tuned design system with three
corner radii and an accessibility-audited palette. Emoji also render differently per OS,
so the page's iconography is the one element the designer does not control.

**s5 — The declared typeface is not what most visitors see.** `ui-rounded` resolves to SF Pro Rounded on Apple only;
the fallbacks `"Nunito", "Quicksand"` are not installed anywhere by default, so Windows and Android visitors land on
Segoe UI / Roboto. The site's stated brand carry-over ("SF Rounded in-app → `ui-rounded` on the web", `web/README.md`)
silently does not happen for roughly half of desktop traffic.

**s6 — Token drift against the stated source of truth.** `web/README.md` says `site.css` restates Theme.swift's pairs,
but `--leaf-a`, `--leaf-b` and `--stem` exist only in CSS, and Theme.swift's most distinctive tokens
(`dlDer` / `dlDie` / `dlDas`, the article colours) never reach the web at all.

**s7 — Vast dead margins, one measure.** `.wrap` is 60 rem (960 px) inside 1440, and the real content column is 46.5 rem
(744 px) — 52 % of the window, 39 % at 1920. Every band below the hero is exactly that same width,
so the page has one rhythm from top to bottom and reads as a document, not a site.

**s8 — The hero image is App Store furniture.** A 256 px app icon in a rounded square, floated right (`desk-light-fold.png`),
stacked on top on mobile where it takes the first 400 px of the phone screen (`mob-fold.png`).
It is a nice icon and it communicates nothing a visitor cannot get from the 34 px mark in the masthead.

## 5. Art direction for the rebuild

> **Governing idea: the page is a windowsill, and the drill is the thing growing on it.**
> One warm, quiet, paper-coloured room with a single living object in it — everything else is the sill it stands on.

### The duality: split it, don't blend it

Right now growth and ladder are drawn on top of each other everywhere, and the result reads as neither.
Separate them by domain:

- **Growth is atmosphere.** Paper colour, the sprout mark, the verbs in the copy ("sown", "tending", "ripens"),
  the forest picture. It is never drawn as a diagram or a decorative frame.
- **The ladder is the drill's alone.** A rung is a thing you *earn*, so it may only appear where one is being earned.
  Delete `.climb::before` and the `--climb-shift` parallax entirely.
  In their place, give the running drill card a real ladder: a vertical rail down the left inside edge of the card,
  four rungs, each ~24 px wide and 4 px thick, the earned ones in `--accent` — at a size where it is legibly a ladder,
  not the 14 px glyph beside "Sprosse 1" in `drill-run.png` that reads as a hamburger menu.
  When a rung is won, that rung fills. That is the whole pun, paid once, where it means something.

### Palette

Keep, unchanged, from `Theme.swift`: `--bg`, `--surface`, `--surface-tint`, `--separator`, `--border-strong`,
`--text`, `--text-2`, `--on-color`, `--accent`, `--ocean`, `--forest`, `--ochre`, `--brick`.
That set is well-built and hard-won; do not re-pick it.

Add four web-only tokens, and say in `web/README.md` that they are web-only:

- `--soil: #1E2620` — light mode's `--text` promoted to a **band fill**. The app never inverts a section,
  so Theme.swift has no reason for this token; the page does. One full-bleed dark band (the drill's stage)
  in an otherwise pale page is what stops 2500 px of `#F2F1EA` reading as a Word document.
  In dark mode `--soil` becomes `#0B0F0C`, one step below `--bg`, so the relationship survives the inversion.
- `--paper-grain` — a ~2 % opacity inline SVG feTurbulence over `--bg`. Stone-and-moss paper should have tooth.
  At 2 % it is felt, not seen; it costs about 300 bytes inline.
- `--rung: #A0733F` — the ladder needs its own token, distinct from `--stem` (which belongs to the sprout mark),
  because the two now live in different places and will be tuned separately.
- Promote `dlDer` / `dlDie` / `dlDas` to `--der` / `--die` / `--das`. Blue/berry/green article colouring is the
  app's most recognisable palette signature and the page currently shows none of it.
  Use it in exactly one place: the forest/feature band, on three real German words with their articles.
  It is proof the product has thought about gender, which is the thing German learners suffer over.

Retire `--leaf-a` / `--leaf-b` / `--stem` from the global scope into the inline `<svg>` mark, where they are actually used.

### Typography

**Recommendation: inline one self-hosted variable face.** The no-CDN constraint does not mean no webfont —
it means the file ships beside the CSS. Subset **Nunito Variable** to Latin + Latin-Ext + **Cyrillic**
(non-negotiable: the page renders Українська chips and Ukrainian readings) and inline it as a `woff2` `@font-face`
with `font-display: swap`. Budget ~45 kB, one request, no third party, works offline.
Nunito is the closest OFL face to SF Pro Rounded's warmth and carries `tnum` for the numerals.
Do **not** use Quicksand — no Cyrillic coverage, and the page will silently fall back mid-sentence.
Keep `ui-rounded, "SF Pro Rounded"` ahead of it in the stack so Apple devices still get the real thing.

If the owner rules out any font file at all, then the honest fallback is to stop claiming roundness and design for
`system-ui` with a wider tracking on headlines — but the brand carry-over is then a fiction and `web/README.md` should say so.

Four roles, and no more:

| Role | Treatment |
|---|---|
| Hero line | `clamp(2.4rem, 5.5vw, 4rem)` / 800 / `-0.025em` / `line-height: 1.05` — in `--text`, **not** `--accent` |
| Section lead | `clamp(1.6rem, 3vw, 2.2rem)` / 700, one per band |
| Body | `1.0625rem` / 400 / `line-height: 1.6` / max 62ch |
| **The numeral** | `clamp(4.5rem, 12vw, 8rem)` / 800 / `tnum` — its own role, the largest type on the site |

The headline moves from clay to ink. In `desk-light-fold.png` the clay `h1` and the clay primary button are the two
loudest things on the fold and they compete; the accent should belong to the button and the numeral, so the eye
knows what is clickable. Type carries the headline; colour carries the action.

### Layout system and rhythm

Replace the single 46.5 rem measure with **three** and alternate them, so the page has a pulse:

- **Full-bleed band** — for the drill stage and the forest. Edge to edge, `--soil` or `--surface-tint` fill.
- **Stage measure**, 64 rem — the drill card, the device shots.
- **Prose measure**, 34 rem — every paragraph on the site, left-aligned, never centred over more than three lines.
  Centred body copy at 42 rem (`.positioning`) and 34 rem (`.drill-section > p.intro`) is why the current page
  feels like a poster rather than a page.

Band padding `clamp(3.5rem, 8vw, 6.5rem)`. Alternate fill: paper → **soil (drill)** → paper → tint (forest) → paper.
Five bands, four transitions, one dark. Corner radii stay on Theme.swift's family (28 / 20 / 14) — do not invent a fourth.

Fix the centring bug (s1) by deleting the `margin` shorthand, and make every band centre on the same axis.

### Motion

The app's own rule for the forest is *"Nothing in it moves"* (`docs/design.md` L237–238), and it is the right rule for
this page too. **Motion may only report state.** Permitted:

- The showcase's 400 ms `fade-slide` on each numeral swap — keep exactly as is; it is the page's one ambient motion and it earns it by teaching.
- The verdict on the input border (green glow / ochre / brick) — keep, it is feedback.
- A rung filling: 220 ms, a scale-and-settle, once per level. This is the only *celebratory* motion on the site.
- The summary emoji's sway — keep, it is 2.2 s and it marks an ending.

Forbidden: scroll parallax (delete `--climb-shift`), scroll-triggered fade-ins on the feature band, any hero animation.
All of it stays inside `prefers-reduced-motion`, as the current CSS already does correctly.

### The aesthetic risk, named

**Open on the numeral, not on a headline.**
The first screen is the drill: a `#1E2620` band, the five language chips, and a single enormous numeral cycling its
reading through Deutsch → English → Español → Kiswahili → Українська, at 8 rem, in clay on near-black.
The wordmark sits small in the corner. The product headline goes *below* the fold-line, as the caption to what just happened.

This breaks the rule that a landing page must state its value proposition in the first 400 px, and it asks a stranger
to do a small piece of work before being told why. The payoff is that Spross's proposition is *unstateable* and
*demonstrable*: "vocabulary that grows while you rest" is a sentence anyone could write, while a Swahili numeral
you can suddenly read is a thing only this product can hand over. If it fails, it fails by looking like an art piece
instead of a product page — which is why the identifying line ("A free iPhone app…") must sit immediately under it,
and why this is the one decision worth A/B-ing if a way to measure ever exists.

## 6. Content model

Five bands. Roughly 320 words total, against the current ~470.

1. **Drill band** (full-bleed, `--soil`) — *the fold.*
   Wordmark + one nav link. The cycling numeral. Five chips. "Start counting."
   Copy: one imperative line only, ~8 words: *"Pick a language. See how far you can count."*
   Everything currently in `.drill-section > h2` and `> p.intro` (44 words) is cut — the object explains itself.

2. **Identity band** (paper, prose measure) — *~55 words.*
   The h1: *"A growing box for your new language."*
   Then the sentence the page does not currently contain, in plain type at body size:
   *"A free iPhone app — Android next. Works offline, no account, no ads. Not on the App Store yet."*
   Then the positioning line, kept nearly verbatim and moved here from its lonely strip at y = 385:
   *"Following a conversation takes many words, not a few perfect ones. Spross doesn't replace your tandem partner or your course — it keeps your vocabulary growing between them."*
   This band is where the 10-second contract is actually satisfied; the drill above it only earns the attention to read it.

3. **Three claims** (paper, stage measure) — *~90 words.*
   Six equal features become **three weighted ones**, each with a real visual (see below), plus one catch-all line:
   - **Sown, not crammed** — the growing box. A few new words a day, each returning just before it would be lost.
   - **Sentences grow from what you've sown** — phrases unlock once their words are steady. This is the mechanic no competitor has, and it currently gets the same 40 px as "Real voices".
   - **A forest, not a chart** — one tree per topic, canopy of the words that landed, blossom and fruit as they ripen.
     **Shown, not described** (see §7 note below).
   - Then one line, no heading, ~25 words: *"Also: clock times, years, the alphabet, dictation, real recordings from native speakers in four languages, and any word your tandem partner just taught you."*
     "It doesn't stop at numbers", "Real voices", and "Your own words" fold into this. They are true and they are not why anyone signs up.

4. **Proof band** (tint, stage measure) — *~20 words of caption.*
   **Three device shots**: Heute, a drill mid-run, the forest. This asset does not exist and must be produced —
   `scripts/run-sim.sh --shot` already takes them. Nothing else on the page will do as much work per pixel.
   The forest shot is the reason this band exists.

5. **Signup** (paper, prose measure) — *~35 words.*
   "Ready to become a Sprössling?" stays; it is charming and it is the site's own coinage.
   Keep the garden's-pace promise. Add the honest status: *"Not shipped yet. The list is how you'll hear — and nothing else."*
   Footer: "Sleep well — your words are still growing." + the mailto. Keep both, unchanged.

**Cut list:** the hero app-icon image; the "SPROSSEN" all-caps eyebrow; the standalone `.positioning` strip
(the sentence survives, the strip does not); the "How far can you count?" heading and its intro paragraph;
three of the six feature blocks (folded into one line); the masthead "Try counting" link (redundant once the drill *is* the fold);
the `.climb` rails and the parallax; the six feature emoji.

**Add:** the platform/price/status sentence; three device shots; a **visible** one-time gloss of *Sprosse*
in running text, replacing the `title=` tooltip — e.g. *"two clean answers take you up a Sprosse — a rung"* —
after which the word may be used bare.

## 7. The drill's place

**Where.** The fold. It is the hero. Nothing precedes it but the wordmark.
At ≥ 900 px it is a single centred card on a full-bleed `--soil` band, ~64 rem wide;
below 900 px the band is the whole screen and the card is edge-to-edge minus 1 rem.
This replaces its current position at 67 % scroll depth and its current 544 px width.

**How much space.** The card owns a minimum of 70 vh at the fold.
Numeral at `clamp(4.5rem, 12vw, 8rem)` — against 3.5 rem today.
**Reserve the height**, exactly as the app does (`DL.Reserve.drillCard = 144`): the prompt zone, the input and the button
must not move as the hint pill, the correction line and the reveal come and go.
A card that jumps under a typing visitor is the fastest way to lose one.

**Before a run.** The showcase, enlarged, is the entire pre-state: flag + numeral + reading, cycling.
Below it, five chips and one primary button. No heading, no intro paragraph.
Picking a language should immediately swap the showcase to that language and hold it —
which `pickLanguage()` already does; it just needs the room to be seen.

**The primer.** It must stop being a page-length expansion (S6).
At desktop, open it as a **second column** beside the card inside the same band, scrolling in its own `max-height: 70vh` box —
the numbers and the drill visible together is a better experience than either alone, and it is what the primer is *for*.
Below 900 px, a bottom sheet over the card, `max-height: 75vh`, with the start button pinned to its bottom edge.
Either way `Start counting` never leaves the screen. Fix the stretched table rows by giving each band table
`align-self: start` in the grid.

**During a run.** The card takes the band over: the pre-state copy collapses out, the ladder appears as the card's
left-hand spine, the numeral is the largest object on the screen.
Fix the empty-progress hole (s3) by rendering the bar's full track from the start, greyed, so the head row reads as a
bar between two buttons rather than two marooned glyphs. Fix the miss segment (s2): keep the second channel but express
it as a **notch** — full 6 px height in `--brick` with a 2 px inset lighter core — so four misses read as four marks, not one rule.
Move the mute control out of the corner and give it a label until it is used once.

**After a run — the handoff.** This is the rebuild's most valuable change.
The summary must **become the signup**, in place, in the same card, with no navigation:

> 🎉 **14 numbers in Deutsch.** Best streak: 6 — a new personal best.
> That was the numbers drill. The app does this for your whole vocabulary.
> `[ you@example.org ]` `[ Tell me when it ships ]`
> *Keep practicing · Another language*

The email field is *in* the summary card, pre-framed by the visitor's own result;
"Keep practicing" and "Another language" drop to quiet text links beneath it.
Today those three options are near-equal buttons and the signup one is an anchor that scrolls the visitor away —
the page throws away its warmest moment.

The band-5 signup form stays as the fallback for visitors who never ran the drill, and the two forms post identically.

**The 10-answer nudge.** Interrupting a run that is going well is the wrong instinct.
Either delete it and let the summary do the work, or trigger it on the *rung-up* moment instead of a fixed count —
a visitor who just climbed a Sprosse is receptive; a visitor mid-streak is annoyed.

## 8. Open questions for the owner

1. **Ship order.** Does spross.net go live before or after the App Store listing?
   If after, the whole page's CTA changes from an email field to a download badge, and band 5 shrinks to a footer line.
   Everything in §6–7 assumes before.
2. **Screenshots.** Will you produce the three device shots?
   The repo contains no app imagery of any kind — `web/assets/` holds one icon and four wav files.
   Without at least the forest shot, band 4 cannot exist and §3's strongest recommendation is unavailable.
3. **The font file.** Is an inlined ~45 kB self-hosted woff2 acceptable, or is "three hand-authored files, no binaries"
   a hard constraint? This changes the typographic recommendation, not just its execution.
4. **Which audience is primary** — German learners, or the any-pair story?
   The page currently hedges: the hero is language-neutral, the drill showcases all five, and the wordplay,
   the tandem framing and "Sprössling" all pay double for a German-reading visitor.
   Picking one would sharpen the hero line considerably.
5. **A German-language version of the site?** Related to (4), and cheap for a page this size.
   The pun is currently being explained to English readers rather than enjoyed by German ones.
6. **Are "free, offline, no accounts, no ads" claims you are willing to commit to in writing?**
   They are the page's strongest differentiators and they appear nowhere. If any of them is uncertain
   (a paid tier later, say), say so now — the identity band in §6 is written assuming all four are safe.
7. **Mailing-list provider.** `SIGNUP_ENDPOINT` is still `""`, so every submission today returns
   "write to feedback@spross.net and we'll plant you in by hand" — which is charming exactly once and is
   a conversion loss on a page whose only job is conversion. This should be resolved before, not after, a redesign.
8. **Android.** `README.md` describes a Compose app on the same engine; the page never mentions a platform.
   Is Android a "coming soon" you want stated, or is iOS-only the launch story?
9. **Does the drill stay numbers-only?** A second drill (clock times reads beautifully on a landing page) would
   double the fold's demonstrative power, but doubles the JS surface and the "taste" scope in `web/README.md`.
