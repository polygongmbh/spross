# Drills — the ladders, the overviews and the letter drill

What the four Sprossen entries ask, how a ladder is climbed, and what the page in
front of each holds. The surfaces around them — listening, the wrist, the Android
companion — are `surfaces.md`'s; the review loop these share their card and their
answering rules with is `design.md`'s.

## The hub, and what a Sprosse is

- **Trainers**: the Sprossen card offers FOUR entries — Zahlen, Buchstaben, Länder and Datum —
  on ONE row, which all four sit on comfortably. A step of the ladder is a **Sprosse**
  in EVERY interface language, plural Sprossen: it is the brand word (`website.md`),
  so English chrome says "Sprosse 5" where the code and these docs still say Sprosse.
  Each is its own SKILL,
  which is the only thing that earns a chip. Clock,
  sentences and number forms are not siblings of the numbers drill but ways of being
  asked, so they are variants a run selects rather than chips, and the modifiers
  (reverse, fast, mix) are how it is played. A chip apiece was the alternative and it
  was rejected twice over: chips are peers, so a row of them says the negatives and the
  clock are alternatives to counting rather than what counting earns you, and a hub that
  grows a chip per exercise has no way to say that one is not open yet — the ladder is
  the reward, and only a list that can hold a locked row can show it. What each language
  reads for those forms, with its sources, is `number-forms.md`. Which of them a learner may pick is derived
  from one stored number per variant — the highest Sprosse ever reached — through kern's
  unlock table; everything else is registry-driven from kern and the catalog, so the hub
  offers only languages with authored content. A numbers run starts at Sprosse 1 however far
  the learner has climbed: persisted progress buys access, never a head start. The atlas and
  the calendar open where their last run left off instead (§ A run, and what it leaves behind).
  **A Sprosse has to ask something no other Sprosse already asks.** One that composes
  answers the ladders teach separately is a free rung, however sensible its name: the
  calendar dropped its bare day-of-month because that reading IS the numbers drill's
  cardinal or the Forms drill's ordinal, and dropped its centuries because a span is that
  ordinal plus a word the box already holds. Where the composition genuinely adds a rule —
  a concord, an agreement, another numeral family — it is that rule the Sprosse is for, and
  a note or a frame is usually the cheaper home for one.
  Drills grade word by word and ramp with the learner instead of sitting at one level.
  A drill card is a review card — same face, same reveal, and the revealed reading is
  spoken and replayable like any other answer (`read-aloud.md`) — and carries nothing but
  the prompt: the run's header line already names what is drilled and how far the ramp has
  come, and the field's placeholder names what is owed — the language to answer in, or
  digits where the task was reversed — so a badge or a "Zahl · auf Spanisch" caption would
  be the third telling of what one tap said.
  The one thing it does carry is the FIRST-SIGHT hint, and it is always a word in the
  language being LEARNED: the place word the first time a length appears, the word a form
  adds the first time a mark does ("Neu: menos", "Neu: Komma"), and on the calendar the word
  a date's pattern adds the first time that kind is asked ("Neu: tarehe", then
  "Neu: mwaka wa"). One slot, the form winning
  where both could fire. Naming the category in the reader's language instead ("Neu:
  Kommazahl") taught nothing — a learner cannot say it, and the card is where saying it is
  owed. The word is DERIVED, never authored: `formMarker` takes the reference band's
  worked example and removes the cardinal's own words, including a cardinal welded to the
  front of one ("dreimal" → "mal"); where nothing can be removed the whole reading stands,
  which is the honest answer for an ordinal ("erste") or a half ("nusu").
  `DateDrill.patternWord` is the same derivation on a calendar — the pattern with its slots
  taken out, minus whatever the kind below it already added, so Swahili's dated line repeats
  `tarehe` silently and owes only `mwaka wa`, and a language whose pattern is its slots alone
  (en's `{month} {day}`, every uk one) hands over nothing rather than inventing something.
  A slot BETWEEN two of those words comes back as an ellipsis ("Neu: el … de"): two words a
  pattern holds apart are not a phrase, and welding them would teach one nobody says.
  A reversed task gets no hint at all: the prompt is then the reading,
  which says it in words already.

## The four overview pages

- **Zahlen overview**: the numbers entry opens a page, not a run — options and start
  first, reference under them. Reading matter and the run it prepares you for are ONE
  surface on purpose: a look-up that lives five taps inside a running drill is a look-up
  nobody makes, and a reference page you cannot start from is a page nobody returns to.
  The run comes first because it is what the page is opened for: twenty screens of table
  above the button would make starting the thing a scroll. Both overviews wear the app's
  corners — the ✕ out on the left, the run in on the right — and the right one repeats the
  `Los` button on purpose: it is the one still in reach from inside the reading.
  Picking SEVERAL variants for one run is itself earned — while any offered variant is
  still locked the list is a radio and a run asks one thing at a time; a fully open ladder
  turns it into checkboxes, and the row of picks says so while it is closed.
  The picks themselves are never stored — they last as long as the screen does, so a page
  reopened offers the defaults rather than last night's run — and the stored Sprosse the rows
  read is written by a closing RUN, never by the page.
  Being one surface also closes the drift — the table is drawn from the packs the run
  grades against, and a Sprosse the run just earned unlocks its row on the way back out.
  The reference table is GENERATED by `Trainer.reference`
  from the packs the drill grades against, so the page cannot claim one reading and mark
  another; kern names the bands and the app words their headings. It is one component,
  and the "?" on a numbers task raises the very same table, in every language — a look-up while the
  answer is still owed books the task amber, and after the answer it is free.
  Every row of it is READ by tapping the row itself,
  the reading spoken in the language being learned (`read-aloud.md`),
  and a band of short readings stands in TWO columns,
  which is what puts the counting words and the tens under them on one screen.
  Its last band is the FORMS one: a worked example per form the language reads, so the marks a Forms run asks
  about are written down somewhere other than a failed task, and a form the language cannot
  read has no row there either. Beside it sit two to
  four prose notes on what trips a learner up in that language. A variant the ladder has
  not opened keeps its row and states its price out of the same unlock table, because a
  ladder you can see is a reason to climb and an absence is not; a variant the pair cannot
  offer at all (no forms reading, no realized frames) has no row, since a padlock that can
  never open is a lie.
- **Buchstaben overview**: the letters entry opens the same shape as the numbers one —
  the drill's stages and start first, the alphabet table under them. The Sprossen card shows
  when slots OR an alphabet exist for the target (the second predicate is catalog file
  presence), and the letters chip on the second alone: the table renders every row (glyph,
  name, IPA, context, hint, example with meaning where the reader's language knows the
  word) and ships even where the drill cannot — audio is the drill's precondition, not the
  table's, so where this device can sound nothing the stages are out of reach and the page
  is the alphabet alone. What the drill can ask is recomputed on foreground — a voice
  installed in Settings turns the start button on without a relaunch. The stage rows carry
  no earned ladder (the drill books no review and keeps no record): they say which stage a
  run OPENS on, derived from the learner's consolidated words, and dictation states its
  price until enough of them can be played back. Their mark is the shared Sprosse circle,
  filled on the stage the run opens on and nowhere else — this ladder has no record to
  wear — and the rows are not tapped: the run walks the ladder by itself from that stage.
  Each row is ONE line, the stage named by what it asks, with a caption only where
  dictation states its price.
- **Länder overview**: the atlas entry opens the run-first shape the other two use — the
  Sprossen and the start above, the table under them. Its Sprossen are POOLS rather than stages:
  Sprosse 1 is the two languages the profile already has and the countries they are at home
  in, and every Sprosse after keeps everything below it, so climbing widens the world instead
  of replacing it. NINE Sprossen, each bringing exactly ONE new thing — either a question or a
  tier, never both: country names, then language names, then the people; then tier 2; then
  which language is spoken there; then tier 3; then the country behind a flag alone; then
  tier 4; and at the top the reverse of the spoken-in question, where a language is spoken.
  The row is named for that one thing and nothing else — "Dazu: …" where a question is
  added, "Mehr Länder: …" where a tier is — so the ladder reads as the list of what each
  Sprosse brings.
  Bundling the two (the old Sprosse 4 opened a tier AND the flag question at once) left a
  learner who slipped unable to say what had got harder. A tier nobody has
  authored yet costs the learner nothing: the pool is the join intersected with the Sprosse's
  ceiling, so an empty tier just repeats the one below.
  A country the two languages call the SAME is not asked by name — the prompt would be the
  answer — and that is exactly the country the flag question brings back, since a card with
  no name written on it gives nothing away. Which names count as the same is kern's
  (`CountryDrill`), compared over every accepted form and blind to case and accents.
  A REVERSED run shows no flag anywhere, and has no flag question at all: the answer is
  then owed in the learner's OWN language, so a flag beside the prompt gives it away and a
  flag alone asks them to recognize their own and write a name they have said all their
  life. That leaves the flag Sprosse adding nothing in that direction, where it stands on the
  pool below exactly as an unauthored tier does — and its row says so
  (`CountryDrill.repeatsBelow`) rather than promising a question the run never asks.
  The RUNGS are not earned. The drill books no review and keeps no schedule, exactly
  as the letter drill does not, so the Sprosse rows say what a Sprosse ASKS and never carry a
  padlock or a price. The reverse
  modifier is offered from the first run for the same reason — there is no ladder for it to
  sit behind — and it flips which side asks: forward the learner answers in the language
  being learned, reversed in their own. Which side that is the placeholder says, not the
  card: its caption carries the ask — a bare "Deutschland" cannot say whether the country,
  its people or its language is owed — and, like the letter drill's, no language.
  FAST is the one thing here with a price, and it is a way of PLAYING rather than something
  to be asked: a Sprosse falls on one clean win instead of the three it costs by default, and
  it is offered only once the top Sprosse has EVER been stood on. So the stored best Sprosse is
  no longer only read — it is what buys fast — and until it is paid the row keeps its
  switch, dimmed behind a padlock, with the price where its line would be, out of kern's
  own ceiling rather than a Sprosse number authored beside it.
  The table under it is GENERATED from the joined atlas the run grades against, so the page
  cannot show one exonym and mark another, and a country only one side names has neither a
  row nor a task. It is the one surface here written in TWO languages at once: a country's
  name is a pair, not a property of the language being learned.
  Tapping a country says it — the row is the control here as in the numbers table (`read-aloud.md`) —
  and what is said is the name in the language being learned,
  the half of the pair a run would ask for.
- **Datum overview**: the calendar entry, on the run-first shape again — the Sprossen and the
  start above, the reference table of both calendars under them. Its Sprossen NEST like the
  atlas': each adds what it introduces to everything below it — the weekday names, the
  month names, day and month assembled, the whole dated line — so the names keep coming
  once the dates are being assembled.
  The day of the month has no Sprosse of its own: its reading (`date-readings.md`) is met
  inside the assembled ones, which is where `le premier` and `tarehe mosi` ever stand,
  while a bare numeral on a card is the numbers drill's question and not the calendar's.
  The ladder OPENS on the one Sprosse that is TAPPED: all nineteen names offered four at a
  time, the answer among three others of its own half, before any of them is written out.
  Recognition before production is the box's own rule for a word nobody has produced yet
  and the letters ladder's opening; a blank field is not where a name is met.
  That Sprosse is a landing rather than a step — every Sprosse above it leaves it behind,
  because four tiles among written dates are a free point and the Sprosse above would climb
  on a tap. It is on the reversed ladder too, where the tiles are the learner's own names. Half of a Sprosse's draws lead with the kind it introduced, which is what keeps
  three weekday wins from carrying a learner past a question they never met. A Sprosse the
  answer language cannot read is absent, not locked: Ukrainian speaks no year inside a
  date, so its ladder simply tops out a Sprosse short.
  Under the two tables sit two to four authored prose notes on what trips a learner up in
  that calendar, the Zahlen page's own band: the table is generated from the drill's rows
  and so can say nothing about the ASSEMBLY around them, which is the half a date is.
  The prompt is the source language's name on the bare Sprossen and the date in the
  source's own digits above them — `Mo, 3.3.` wears the source's weekday abbreviation,
  authored display-only, never graded. REVERSED the numeric Sprossen have a direction of
  their own: the card carries the reading and the run wants the date written down. That is
  PARSING where forward is production — the easier half of the skill and the half a learner
  actually spends — and its answer is DIGITS, so a reversed run is a comprehension check the
  whole way up rather than a test of the learner's spelling in a language they already have.
  A written date forgives the zero-padding and the trailing ordinal dot; a digit is never
  forgiven, so `3.7.` for `3.6.` is another date and not a slip. NO WEEKDAY is in play either
  side: `Sa,` is the source's own abbreviation and no number pad types it, so the answer is
  the date alone — and a card that named a weekday the answer then threw away would be asking
  for something it discards, so the reversed dated card reads its date without one. That
  reading is composed, not authored: a year joins a date the same way with or without a
  weekday in front, so the join is what `dateWithYear` adds to `date`, and what it joins to is
  `dayMonth`. Cutting the weekday off `dateWithYear` would not do — Spanish, French and
  Italian give up their article once a weekday stands there. It is also why the whole date is
  the one Sprosse with no way round: with no weekday to account for it is day and month over
  again, so a reversed ladder is exactly one Sprosse shorter. The Sprossen are not earned (no review, no
  schedule — the letter drill's rule), reverse is offered from the first run, and FAST has
  the atlas' price: one clean win a Sprosse, offered once the top Sprosse has ever been stood on.
  A bare name typed for its neighbor is refused by name — *Juli* for *Juni* is the other
  month, not a slip (`kern/docs/grading.md`) — while the same slip inside an assembled date
  stays the typo it is: strictness is graded by how much of the answer the word was.

## A run, and what it leaves behind

- **The atlas and the calendar wear their record on the Sprosse circles, and open where it
  stands.** A circle is an outline where no run has stood on the Sprosse, filled ocean where
  one has reached it, filled forest where one run answered EVERY question of it clean —
  which only a Sprosse that enumerates can earn, so the assembled date Sprossen never turn
  forest. `Los` opens the run on the lowest Sprosse no run has answered out
  (`TrainerMode.entrySprosse`), and a row the learner has been on — the entry or below, or
  a Sprosse some run reached — is a control that opens a run on its own Sprosse
  (`TrainerMode.openable`); the one line under the ladder says so, and a Sprosse nobody has
  reached yet reads dimmed and answers no tap. What a run answered out is filed per
  DIRECTION, because a row means another question turned round
  (`kern/docs/turns.md` § storage contract), and the ladder the reverse switch shows reads
  its own direction's mask. The record line under it COUNTS rather than places — the
  longest clean streak and the most answers one run took, right or wrong — since the
  circles already say
  where the ladder stands. Every Sprosse row is ONE line: the atlas names the question or
  tier it adds, the calendar the kind, and no row explains what its title already says.
- **The numbers page prints the Sprosse each exercise has been climbed to, as it stands,**
  under the exercise's own name, because there four ladders are climbed separately, their
  Sprossen draw values rather than listing them (so none can be answered out), and Zahlen
  counts its Sprosse in digits. The number is never trimmed to the rows on the page: a
  Sprosse goes on counting past the last named one (`DrillRamp.step`), which is what a
  climbed-out ladder leaves to beat.
- **An endless run offers its exit where it is wanted, not on a schedule.** "Fertig"
  appears under the button that goes on, and only on the SECOND miss in a row: one
  miss is what a drill is made of, two is where carrying on stops feeling like a choice.
  A clean answer takes the offer away again. The one thing that ends a run unasked is
  running OUT of questions: a run asks each prompt once (`kern/docs/turns.md`), so a ladder
  answered out hands its figures over rather than coming round to a question twice.
  Short of that the corner ✕ still works, and the offer is the same close, worded as
  finishing rather than abandoning.
- **A closed run has no screen of its own.** The endless drills hand their figures — answered,
  best streak, whether the record fell — to the page that started them and leave; the page
  wears them as one tile above the picks and scrolls up to meet it, and a record still
  rains its confetti there. Three numbers do not earn a page, and a page they do not earn
  is one more ✕ between a learner and their next run — which the `Los` button already is.

## The letter drill

- **Letter drill**: it shares the slot drill's chrome — the endless scaffold, the streak
  line, the result tile (each platform's `DrillChrome`) — and keeps its own state machine, which
  is the whole of what the two have in common: its Sprossen are stages that change what a
  question IS rather than how big the number is, and its verdict ladder carries a third
  outcome (a synonym of the dictated word) that no slot task can produce.
  Its card is the one that keeps a caption, because a sound cannot say whether it wants a
  letter, a missing grapheme or the whole word — but the caption names the ask alone, since
  no drill card names a language while the placeholder below it is already saying one.
  The drill asks everything by ear: letter
  NAME or gap word — and a gap word is drawn from the whole catalog wherever the glyph
  says its own sound, words the learner already holds first, so a Sprosse stops meaning one
  memorized blank (`catalog/alphabet/README.md` owns which rows may draw). Tiles first
  among strangers, then among look- and sound-alikes, then
  typed, and — once enough words are consolidated — dictation of the learner's own
  consolidated words, which never touches their schedule and leans toward the ones worth
  spelling twice: words carrying the language's hard graphemes, and words this learner has
  forgotten before. Correctness is never color
  alone (checkmark/X over the tint); a miss never auto-advances. Neither mute reaches the
  drill and it carries no mute button: entering a screen whose only content is a sound is
  itself the request to hear one (`read-aloud.md`), so no run of it can open on a card with
  nothing to answer.
