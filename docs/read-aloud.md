# Reading aloud

How the app speaks a word: which sound plays, when autoplay fires, and how the two
mutes interact. The engine's half — whether a form may be heard at all — is
`../kern/docs/audio.md`; whose the recordings are and what their licenses oblige is
`audio-licensing.md`.

- **Words are read aloud, and a recording is only played for the word it actually says.**
  Kern matches recordings by the FORM on screen, never by concept, so a rotated synonym is
  never answered with the canonical word; anything unmatched falls to the device's own
  voice speaking exactly what stands there, and a target with neither — Swahili has no iOS
  voice at all — stays silent rather than be read in the wrong one.
  The drills lean on that fallback for most of what they say: they GENERATE their readings
  ("dreihundertsiebenundvierzig", "son las tres y cuarto"), so no catalog lists them
  and the voice is what says them.
  The two drills whose material is AUTHORED rather than generated are the exceptions: the
  calendar's weekday and month names (`calendar{}`) and the atlas' country and nationality
  names (`countries{}`) are recorded, so those answers play a person while the date
  assembled around a weekday, and every numeral and clock reading, stay synthesized.
- **Which voice answers is the device's business, and its tier is worth naming.** iOS bundles
  the compact voice for a language and nothing else; the enhanced and premium ones are a free
  download under Settings › Accessibility › Spoken Content › Voices that no API announces and
  no system prompt offers. The gap is large enough to hear, and it lands on every generated
  reading — so the app asks what would actually answer (`Speaker.voiceQuality`) and points at
  the download while, and only while, the compact voice is the one speaking: a line in the
  audio setting, and one dismissible notice on Home once the box has cards. Never a link —
  no public URL opens that pane, and the one that exists lands on the app's own settings page,
  which is not where the setting is. The voice table is dropped on every foreground, so a
  download made in Settings is picked up on return and the pointer goes with it.
- **The target language is spoken with its article; the learner's own language is not.**
  An article is not decoration on the side being learned — it is the half of the noun a
  learner most often has wrong, and a word only ever heard bare is a word never heard right.
  So the voice says "das Brot", and kern decides whether there is an article to say
  (`shownArticle`), which keeps it off a rotated synonym that may carry another gender.
  The learner's own side takes none: the meaning is there to identify the word, and its
  grammar is not what is being taught.
  Where a pack recorded the article too, the recording says it as well: German and Italian
  carry an `articles{}` section, found by the very string the voice is handed, so those cards
  sound the same on either branch. It is partial — one speaker reached 221 of the German
  nouns and 54 of the Italian — and a word outside it still plays bare, which is the
  recording falling short of the rule rather than the voice overreaching, and the accepted
  cost of teaching the article at all.
  ♀ badge, plural line and alternates stay unspoken: those are decoration, and gender is
  taught by the article color besides.
- **Audio may never give the answer away**: whether a card's target may be heard is the
  engine's cue, the audio twin of the emoji cue — a recognition prompt carries the target
  from frame one and speaks at once, a produce card owes that very form and waits for the
  reveal. Both apps consume that one cue instead of each deciding for itself.
- **Autoplay fires only where the card holds the learner.** A clean correct answer flips in
  0.45–1.2 s, less than a word lasts, and a word cut off every time teaches worse than one
  not played — the tap and the next recognition of the card both say it in full. Produce
  fires wait a beat so the feedback chime is out of the way first; chimes are never ducked
  for them, and no fire ever delays a flip.
  One fire per card, and one per drill task, whichever path reaches it.

| on screen | speaks? | what is said |
|---|---|---|
| recognition prompt | yes, at once | the prompted form — the rotated synonym, never the canonical word |
| recognition reveal, write-it-out step | no | already said once |
| produce answered correctly, typed or checked | no — the card is already flipping | — |
| near miss accepted — a typo, a form heard instead (waits for a tap) | yes, after the chime | the form the correction box carries |
| produce revealed — Aufdecken, wrong, other word | yes, after the chime | the bare target word |
| trainer drill prompt — a numeral, a clock face, a dated line | no | there is nothing to say yet: the reading IS the answer |
| drill prompt in the language being LEARNED — any reversed atlas or dates run | yes, at once | the form itself, which already stands on the card |
| drill prompt in the learner's OWN language — a forward run, `Mo, 3.3.` among them | no | no autoplay says that side; the reveal is where that run's voice is |
| trainer drill reading revealed or corrected | yes, after the chime | the reading itself — generated, so usually the voice; a weekday, month, country or nationality name is recorded |
| listening mode — the meaning, between the two sayings of the word | yes, unattended | the meaning in the learner's own language: the ONE autoplay that speaks the known side, because a word said into silence teaches nothing and there is no answer being owed |
| a drill answer owed in the learner's OWN language — a reversed atlas run | no | nothing: every autoplay above says a form in the language being LEARNED, and the speaker beside the reveal still says this one on request. The PROMPT carries that run's voice instead — it is the target-language form |

- **Listening mode is a run made entirely of sound, and it is the only one that plays
  unattended.** A turn says the target word, the meaning, then the target again: the second
  saying is where the word and its meaning meet, and it is what makes the mode worth an hour
  with the phone in a pocket. The gap before the meaning is the only beat that varies — a
  word the learner already holds gets 1.2 s to reach for it before the meaning arrives, an
  unseen word has nothing to recall and its meaning follows in 0.6 s, and the echo and the
  breath between turns are those same two lengths. Every beat is kern's number
  (`../kern/docs/turns.md`), so the two phones cannot drift on the pacing.
  It carries no mute button, for the letter drill's reason: entering a surface whose only
  content is a sound is itself the request to hear one, so neither mute reaches it and the
  run plays under `.playback`.
  It also takes the audio OVER rather than mixing into it — no `.mixWithOthers`, spoken-audio
  mode, and the session is released on stop so whatever was playing resumes. A mode meant to
  be listened to that lands on top of a podcast is a mode nobody hears; that is the one place
  the app interrupts, and it interrupts as a player, not as a notification.
  A word only enters a run if BOTH its sides can be said on this device — a word spoken into
  silence where the meaning should be teaches nothing.
- **Tapping a word says it again — past both mutes.** A tap is a request, so it outranks
  the app's own switch (mute has to stay usable as the accessibility affordance) and the
  phone's silent switch alike: that one sound plays under `.playback`, which the hardware
  switch cannot reach. Everything the app fires by ITSELF stays `.ambient`, where the phone
  keeps its authority — the whole rule is `AudioSession`, one category chosen per sound.
  The audio setting's hint line names the gesture once for the app as a whole —
  in the No audio line, the only preference where a learner might think the app
  has gone silent for good.
- **What answers the tap, and what discloses it, follows the surface.**
  On a card or in a drill there is nothing else to hit,
  so the speaker beside the word IS the control;
  a form that can be heard neither way drops the icon,
  and the card holds its height regardless,
  so a synonym rotation landing on an unrecorded form never resizes it.
  A reference page is reading matter, read by running DOWN it,
  so there the CONTENT is the control:
  a numbers row and an atlas country hold one reading each,
  say it wherever they are tapped, and draw no speaker at all —
  the page discloses the gesture once instead, in a hint line under its heading,
  shown only where this device can actually say the language.
  The alphabet sheet keeps its glyphs, because its rows disagree about what they hold:
  the letter's name and its example word are two readings, a prose rule row has neither,
  and only a glyph per line can say which line answers a tap —
  those two lines are tap targets themselves besides,
  so a tapped word speaks on every reference page.
  A smaller glyph per row is not the middle way it looks like:
  a speaker keeps its 44 pt hit region however small the frame around it is drawn,
  so on rows four points apart it overhangs its neighbor
  and a tap in a row's bottom sliver speaks the row below —
  two targets for one action, one of them wrong.
  A drill PROMPT draws one wherever it stands in the language being learned,
  which is the same rule a card obeys — a target-language form gets a speaker
  whenever the device can say it, drill or no drill.
  That is a REVERSED run, whose answer is the learner's own side and stays silent,
  so without the prompt the whole task would be unhearable.
  Forward runs need no exception written for them: their prompt is the learner's own
  language, which nothing outside listening mode says, and their target-language form
  is the ANSWER, which already speaks on the reveal.
  The dates drill needs none either — every reversed prompt there IS a target-language
  reading, the names and the dates alike, and what a reversed date owes back is digits,
  which nothing says out loud.
  What the speaker follows is the revealed FORM, not the surface it stands on:
  the letter drill hands back a bare glyph on every Sprosse but the dictation,
  and a glyph is not a form anything may be asked to say —
  the form-keyed lookup never reaches the letter-name recording
  the card's own replay button just played,
  and a voice reads it as anything from a spelling alphabet to a pause
  (`../kern/docs/audio.md`).
  So a revealed letter and the correction box beside it draw no speaker,
  the replay above stays the one way to hear the question,
  and only the dictated word — a word, in the language being learned — carries one.
- **The letters drill is the one autoplay no mute reaches, and it carries no switch of its
  own.** Entering a screen whose only content is a sound is itself the request to hear one,
  so its question plays under `.playback` like a tap does — the exception to "app-fired
  sounds stay `.ambient`", and the only one. Deferring there bought no silence anyway: the
  replay glyph goes past the hardware switch on the first tap, so a silenced phone only ever
  cost a tap per question and a moment of a screen that looked broken. VoiceOver still holds
  it back, and gets the replay glyph handed to it on every task instead.
- **Reading aloud is on by default, and the silent switch is free to silence it.** That is
  the untouched state; the switch — at the top bar of every run it governs, review and
  number drill alike, constant chrome so the card below never moves for it, and in the Box
  settings — turns it into a decision. Switched OFF it silences autoplay whatever the
  phone says. Switched ON it is itself a request to
  hear something and lifts autoplay past a silenced phone, because a switch that says on
  and says nothing is worse than no switch. Three states, one setting for the device: not
  per target language, and not in the box, where the product calibration would reset it.
  The silent switch's position cannot be read back (no API), so it is followed by deferring
  to it, never by mirroring it.
- **A card whose only content is a sound is not dealt onto a phone that cannot play it.**
  The review card asked by ear (`../kern/docs/presentation.md`) asks the app whether the
  word can be heard RIGHT NOW, and a device silenced by its own volume answers no — iOS
  reads `outputVolume`, Android the media stream's volume and mute. Android's RINGER mode
  is deliberately not read: silencing notifications there leaves media playing and never
  asked for a silent lesson. What neither can ask about is iOS's ring/silent switch, so a
  card dealt through it carries the way out on screen instead ("can't listen right now?",
  `design.md`) — which is also the answer for headphones that were unplugged between one
  card and the next, since the question is asked once as the card goes up.
- **The audio setting in the Box names the voice too, in one three-way row: No audio,
  Recordings, Speech.** "No audio" is the switch off, and it silences autoplay whatever
  the phone says. "Recordings" — the default — plays the bundled recording where the form
  has one and falls to the system voice for the rest — including the article recording
  where the pack has one for that word. "Speech" prefers the system voice wherever one
  exists, for one consistent sound and the article said aloud on EVERY word rather than
  on the ones a speaker got to (`shownArticle`); the recording answers only where the
  language has no voice at all, Swahili on iOS among them.
  The session's top-bar button is that same setting reduced to the mute: it turns the
  picked voice on or off, and never changes which one it is. Choosing a voice by hand also
  lifts autoplay past a silenced phone, exactly as the switch does — the picker and the
  button write the same two facts, mute and source, never one without the other.
- **The source is remembered PER LANGUAGE BEING LEARNED, the mute per device.** Which of
  the two sounds better is a fact about one language — a pack that beats the system voice
  in Ukrainian says nothing about Spanish, where a downloaded premium voice may beat the
  pack — so each target carries its own pick, defaulting to Recordings.
  **A source is offered only where it can answer**, both ways round: "Speech" where the
  device HAS a voice for that target, "Recordings" where a pack ships for it, and the row
  not at all where neither does. A segment that would fall straight through to the other
  source promises a sound nothing can make — English ships no pack, Swahili has no iOS
  voice — and a stored source the language cannot answer reads as the one that sounds.
  Kern names the pair once (`AudioCapability`, over `Catalog.hasRecordings` and the
  device's own voice table) so the setting, its hint and the listening card cannot come to
  different answers about the same language.
- **The feedback chimes are their own matter** — the read-aloud switch does not silence
  them — but they play under whatever category it left standing, so the phone reaches them
  exactly as it reaches autoplay. Nobody ever asked for a chime, so no chime is ever louder
  than the phone.
- **Chimes and words share one volume.** Both play on the app's own audio session, so the
  levels they were authored at are the levels heard against each other. A chime routed to
  the system-sound server instead would answer to the ringer while every word answered to
  media — two sliders, and the chime gone whenever the ringer sat low.
  Android holds both rules by the same reasoning: `CueSounds` plays the very same files
  under `USAGE_MEDIA`, beside the spoken words and past the read-aloud switch.
  One volume also means one measure: the chimes are levelled against the loudness target
  every recording is boosted to, not against their own peaks, and `scripts/sounds.py` holds
  the numbers and why a struck note has to sit above a word's to be heard beside it.
- VoiceOver never gets autoplay talking over it. The headword is labeled with the language
  it is written in instead, so the screen reader says it in the right voice, and the replay
  is an action ON the word rather than a button around it.
- Whose the voices are, what their licenses ask of the app, and why a credits screen has to
  stand before a word may be heard: `audio-licensing.md`.
