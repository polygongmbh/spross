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
  The drills lean on that fallback entirely: they GENERATE their readings
  ("dreihundertsiebenundvierzig", "son las tres y cuarto"), so no catalog lists them
  and the voice is what says them.
- **Which voice answers is the device's business, and its tier is worth naming.** iOS bundles
  the compact voice for a language and nothing else; the enhanced and premium ones are a free
  download under Settings › Accessibility › Spoken Content › Voices that no API announces and
  no system prompt offers. The gap is large enough to hear, and it lands on every generated
  reading — so the app asks what would actually answer (`Speaker.voiceQuality`) and points at
  the download while, and only while, the compact voice is the one speaking: a line in the
  audio setting, and one dismissible notice on Heute once the box has cards. Never a link —
  no public URL opens that pane, and the one that exists lands on the app's own settings page,
  which is not where the setting is. The voice table is dropped on every foreground, so a
  download made in Settings is picked up on return and the pointer goes with it.
- **Only the headword is ever spoken.** Article, ♀ badge, plural line and alternates are
  grammar decoration: gender is taught by the article color, and the recordings say bare
  words — speaking the article in the synthesized branch alone would make a word's
  pronunciation depend on which branch happened to answer.
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
| trainer drill prompt — a numeral, a clock face | no | there is nothing to say yet: the reading IS the answer |
| trainer drill reading revealed or corrected | yes, after the chime | the reading itself, generated, so usually the voice |
| a drill answer owed in the learner's OWN language — a reversed atlas run | no | nothing: every autoplay above says a form in the language being LEARNED, and the speaker beside the reveal still says this one on request |

- **Tapping a word says it again — past both mutes.** A tap is a request, so it outranks
  the app's own switch (mute has to stay usable as the accessibility affordance) and the
  phone's silent switch alike: that one sound plays under `.playback`, which the hardware
  switch cannot reach. Everything the app fires by ITSELF stays `.ambient`, where the phone
  keeps its authority — the whole rule is `AudioSession`, one category chosen per sound.
  The audio setting's hint line names the gesture once for the app as a whole.
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
