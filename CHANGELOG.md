# Changelog

## Unreleased

- **The answer is said once**: a missed word appeared twice — small under the word you were
  asked about, and again in a panel under the input box — and a word you had typed correctly
  was still shown back to you as if you had not. The card now opens only when you did not
  produce the word, and a typo's proper spelling stays where you were already looking:
  right next to what you typed.

- **Watch options all speak one language**: a question could offer meanings and
  target words side by side — the wrong-language tiles were unpickable noise
  and gave the answer away. Every tile now sits on the side the prompt asks for.

- **The app stops buzzing at every answer**: the feedback sounds were iOS system sounds,
  which drag the alert vibration along with them — so a right answer shook the phone
  just like a wrong one, and silencing it meant giving up alert haptics everywhere else.
  The sounds are the app's own now — deeper, softer and shaped to stay in the background,
  since you hear them all session long: a gentle rise for correct, a fall for wrong,
  a quiet tick on reveal. The one tap left is the light one on a wrong answer.

## 1.1.0 — 2026-07-25

- **The progress line counts what has taken root**: Heute, the widget and the Android
  stats row drop the retention percentage — a prediction of the scheduler's own making,
  which sat in the same narrow band whatever you did — for the gefestigt/frisch split.
  A word counts as gefestigt once it sits firmly enough to let its phrases in,
  the same milestone the area chips already mark, and struggling words now count
  in the picture instead of falling out of it.

- **Onboarding speaks your language**: the first-run sheet used to be English on the
  grounds that your language was not known yet — but it is: your device language
  already picks your side of the pair. The sheet now opens in that language and
  follows every tap you make.

- **The reveal's small print can actually be read**: the plural, the "auch: …" alternates,
  the literal gloss and the spelling shown after a typo were set too small and too faint
  to take in — they are now sized and coloured to be read, not squinted at.
  The palette moved with them: paper and ink re-grounded on the growing box,
  ocean and forest alongside the clay, and every colour the app puts on screen
  now carries enough contrast to be legible in both light and dark.

- **A new word asks before it teaches**: the first time a word comes up it now waits
  behind "Aufdecken" like any other card — with its picture as the cue — so a word you
  already know can be recalled instead of just shown to you.

- **Reorganising the content no longer costs you progress**:
  a word that moves to another area keeps everything you have learned about it,
  instead of coming back as if you had never seen it.
  Card ids change once more to make that possible,
  so existing boxes start fresh — as at the 1.0.0 rename (pre-production).

- **Swahili nouns teach their class**: nearly every noun now carries its plural —
  kiti/viti, mlango/milango, kisu/visu, mwalimu/walimu — where a handful used to,
  so the card gives you the form you need to say "two chairs" instead of leaving you
  to guess which class the word belongs to. Words that only exist in the plural
  (maji, maumivu, nguo za kulala) say so.

- **Ukrainian shows the plurals you could not have guessed**: ніж → ножі, день → дні,
  людина → люди, and the і/о alternation running through договір, вечір, папір —
  every form the regular declension does not hand you is now written out,
  including the words that never change and the ones where the adjective has to
  move with the noun (письмовий стіл → письмові столи). Regular plurals stay
  unwritten, the way a bare "+s" does in English.

- **Every plural says that it is one**: plurals now read as the finished word behind
  a "Pl." label — "Pl. watu" under mtu, "Pl. Lehrerinnen" instead of the
  dictionary-style "Lehrerin, -nen" you had to assemble in your head.

- **Half again as much to learn**: every area gained the everyday words it was missing —
  the kitchen finally has food in it (Brot, Milch, Kaffee, Ei, Käse, Reis, Tasse),
  the doctor's area has the body parts you need to say what hurts,
  and the office areas cover Pass, Bescheid, Gebühr, Beruf, Steuer and Kündigung.
- **A new "Das Wichtigste im Alltag" area** opens right after the first words:
  the bedrock vocabulary the greetings pack only implied —
  Geld, Wasser, Zeit, Tag, gehen, kommen, haben, trinken, kaufen, bezahlen,
  gut/schlecht, groß/klein, heute/morgen — plus essen, sprechen, verstehen and warten,
  which used to sit in whichever room happened to mention them.
- **No more unanswerable prompts**: when a word you are learning covers two German
  words at once — Swahili `kuvaa` is both "anziehen" and "sich anziehen" —
  the prompt now shows which area it belongs to, so there is one right answer instead
  of two. Only when you type the answer; a flip-and-check card never gets the hint,
  since it would give the answer away.
- **Sachbearbeiter is now a Sachbearbeiter in Swahili**: `afisa mhusika`
  ("the officer handling your case") instead of the bare `afisa`, which only means
  "official". Two Ukrainian verbs were sharpened where one word was doing two jobs
  (putzen → чистити, ankommen → прибувати).
- **A "Termine & Organisation" area** now opens the Ämter & Beruf group, so the
  vocabulary every office and doctor's visit needs — Termin, Frist, Öffnungszeiten,
  Bestätigung, vereinbaren, verschieben, absagen, pünktlich — arrives before the
  areas that assume it, instead of hiding inside the doctor's area.
- **Phrases now wait for their words**: sentences like "Ich verstehe." and
  "Können Sie das wiederholen?" used to be introduced before you had ever met
  *verstehen* or *wiederholen*, because a phrase can only wait for words in its own
  area. They now sit with those words and unlock from them. The first words area
  keeps the greetings and politeness formulas.
- **"anziehen" is one word again**, not two: a single *sich anziehen* card in the
  hallway, with "die Jacke anziehen" as a phrase that unlocks from it and the jacket.
- **Shorter sentences to type**: no card crams two statements into one answer any more
  ("Das Wasser kocht — es ist sehr heiß!" is now two cards, the warning half unlocking
  from *Vorsicht*), and the longest sentences lost the words that carried no meaning.
  Ukrainian phrases that spelled both gender endings into the answer
  ("Я втомився (втомилася).") now accept either but ask you to type only one.
- **Fewer cards teaching the same thing**: "sich entspannen" and "sich ausruhen"
  were two cards for one idea and one Swahili word — now one. The school exercise
  book is "daftari la mazoezi", no longer sharing "daftari" with the desk notebook.
- **Two cards never ask the same question**: where Swahili had one word for two
  German ones, the narrower card now says which one it means in every language —
  *Medikamente abholen* is `kuchukua dawa`, *losgehen* is `kuondoka nyumbani`,
  *Pause machen* is `kupumzika kazini`. Only "mto" (pillow and river) is still
  one word for two things, which is simply what the word means.
  "abholen" moved to the office area as *den Ausweis abholen*, where collecting a
  document is the counterpart to handing one in — the doctor's area no longer has
  four ways to say "medicine".

## 1.0.0 — 2026-07-25

- **Language pickers speak for themselves**: onboarding is English (it shows before
  your language is known), and every language choice reads "🇩🇪 German" — flag plus
  English name. Nothing is greyed out anymore: picking the language on the other
  side simply swaps "I speak" and "I'm learning" — each direction keeps its own
  box, so nothing is ever lost.
- **Extra rounds bring new words**: the extra round after finishing your day now
  prefers fresh composition — everything due plus NEW vocabulary within your
  learning-pool budget — and only repeats ahead of schedule when nothing new fits.
  "Weiter üben" follows the same rule: due first, then new — it never pulls
  tomorrow's reviews forward.
- **The Box is grouped**: areas sit under their catalog groups (Erste Schritte,
  Zuhause, Alltag & unterwegs, Ämter & Beruf) instead of one long list — far
  less scrolling to find a target.
- **Drills forgive typos and accept real time-telling**: number/clock/sentence
  drills forgive a single-letter slip — digits must be exact, and a proven
  guard keeps any German number from passing for another — and the German
  clock accepts full 24-hour answers like
  "achtzehn Uhr fünfunddreißig" alongside colloquial forms. Sentence drills
  take numbers and times written out OR as digits in both directions —
  "um achtzehn Uhr fünfunddreißig" counts just like "um 18:35 Uhr".
- **Sentence drills start gentle**: slot values ramp from short-and-easy upward
  with the same two-wins-up mechanics as the other drills, instead of opening
  with mouthfuls.
- **Watch: one multiple-choice practice that counts.** The separate "Start"
  (flip + self-grade) and "Üben" (throwaway quiz) modes are now a single tap-the-
  answer loop that feeds your schedule: it drains what's due, then keeps going by
  reviewing ahead. Faster correct answers score higher (a snappy tap counts as
  Easy, a hesitant one as Hard), and the wrong-answer options are kept similar in
  length so you can't guess by shape.
- **DuoLernen is now Spross** (`net.spross.app`): new name, new app/widget/watch
  identities, feedback address `feedback@spross.net`. Card ids and stored boxes
  start fresh — existing test installs begin at an empty box (pre-production).
- **Learn any language pair**: pick the language you *know* and the language you
  *learn* from the catalog (Deutsch · English · Kiswahili · Українська) — German
  is no longer hardwired as one side. The target picker shows how many terms
  each choice offers, and switching your known language keeps all learning
  progress.
- **Alternating practice, self-graded recognition**: each word keeps one memory;
  reviews alternate between typing the word in the language you learn and
  reading it + self-grading your comprehension (Again/Hard/Good/Easy) — typed
  recognition is gone. The very first encounter always *shows* the new word
  with its emoji as a teaching moment, and the second review always asks you to
  produce it. Synonyms take turns as recognition prompts ("auch: …" on reveal),
  and phrases now alternate too.
- **FSRS-6 scheduling**: the scheduler moved a generation forward (forgetting-curve
  decay is now a real parameter), verified against the reference
  implementations' own test vectors. A failed review card returns after
  10 minutes — usually next session — instead of looping inside the current one.
- **Feminine forms are real cards** (where authored — Ukrainian professions
  today): the feminine sibling prompts with a ♀ badge for non-German sources
  (answering the base word is a typo, not a miss), and plural forms read
  dictionary-style ("Lehrerin, -nen").
- **"ss" counts for "ß"**: typing Strasse for Straße is accepted — helpful on
  keyboards without the sharp s.
- **Catalog v2.1 unifications**: near-duplicate phrase twins merged into one card
  each, Sie-form phrases accept the du-form silently, and Ukrainian
  near-synonyms are accepted silently instead of shown as alternates. Since
  then the catalog kept moving: single words split out of phrases, filler
  adverbs and embedded politeness trimmed, and a new ADJECTIVE kind for words
  that are neither noun nor verb (draußen, immer …) — 358 concepts,
  352–356 cards per German-source pair at release.
- **Adding a group no longer floods a session**: packing a whole area into the
  box now enrolls it — its words drip in at the normal learning-pool rate (a
  handful per session, ahead of automatic growth) instead of dumping dozens of
  new cards into one sitting. The rest wait their turn until you've absorbed
  what's already in flight.
- **Spross runs on Android** (first cut): the growing box arrives as a native
  Jetpack Compose app on the same SprossKern engine and in-repo catalog —
  language pickers with concept counts, Heute, alternating sessions (typed
  production with the same typo tolerance, reveal + self-grade recognition),
  extra round and endless practice. Box browsing, trainers, and a widget
  remain iOS-only for now.

## 0.12.4 — 2026-07-19

- **Widgets surface new words too**: the rotation now leads with just-lapsed
  cards, then previews words you've queued (or are about to meet) before their
  first study, then your weakest ones — so the widget primes upcoming vocab
  instead of only recycling words you already know.
- **Emoji only while a word is landing**: vocab cards show the picture emoji as
  light support on new and learning cards, then drop it once the word sticks (so
  a depictive emoji can't leak the answer). Verbs and phrases with no emoji no
  longer get a generic figure — the card just centers on the word.
- **Widgets report the real app version** instead of a hardcoded 1.0.

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
