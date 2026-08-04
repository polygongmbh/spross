# Reading aloud

How the app speaks a word: which sound plays, when autoplay fires, and how the two
mutes interact. The engine's half — whether a form may be heard at all — is
`../kern/docs/audio.md`; whose the recordings are and what their licences oblige is
`audio-licensing.md`.

- **Words are read aloud, and a recording is only played for the word it actually says.**
  Kern matches recordings by the FORM on screen, never by concept, so a rotated synonym is
  never answered with the canonical word; anything unmatched falls to the device's own
  voice speaking exactly what stands there, and a target with neither — Swahili has no iOS
  voice at all — stays silent rather than be read in the wrong one.
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
  for them, and no fire ever delays a flip. One fire per card, whichever path reaches it.

| on screen | speaks? | what is said |
|---|---|---|
| recognition prompt | yes, at once | the prompted form — the rotated synonym, never the canonical word |
| recognition reveal, write-it-out step | no | already said once |
| produce answered correctly, typed or checked | no — the card is already flipping | — |
| produce typo accepted (waits for a tap) | yes, after the chime | the correction line's proper spelling |
| produce revealed — Aufdecken, wrong, other word | yes, after the chime | the bare target word |

- **Tapping a word says it again — past both mutes.** A tap is a request, so it outranks
  the app's own switch (mute has to stay usable as the accessibility affordance) and the
  phone's silent switch alike: that one sound plays under `.playback`, which the hardware
  switch cannot reach. Everything the app fires by ITSELF stays `.ambient`, where the phone
  keeps its authority — the whole rule is `AudioSession`, one category chosen per sound.
  The gesture is disclosed by the settings row's hint line, never by a mark on the card —
  the hit area sits on every headword whether or not it can be heard, so no card changes
  size between reviews because a synonym rotation landed on an unrecorded form.
- **Reading aloud is on by default, and the silent switch is free to silence it.** That is
  the untouched state; the switch — at the session's top bar, constant chrome so the card
  below never moves for it, and in the Box settings — turns it into a decision. Switched
  OFF it silences autoplay whatever the phone says. Switched ON it is itself a request to
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
- VoiceOver never gets autoplay talking over it. The headword is labeled with the language
  it is written in instead, so the screen reader says it in the right voice, and the replay
  is an action ON the word rather than a button around it.
- Whose the voices are, what their licences ask of the app, and why a credits screen has to
  stand before a word may be heard: `audio-licensing.md`.
