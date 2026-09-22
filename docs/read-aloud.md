# Reading aloud

How the app speaks a word: which sound plays, when autoplay fires, and how the two
mutes interact.
Engine half: `../kern/docs/audio.md`;
licensing: `audio-licensing.md`.

- **Words are read aloud; a recording is only played for the word it actually says.**
  Kern matches recordings by the form on screen, never by concept,
  so a rotated synonym is never answered with the canonical word.
  Unmatched forms fall to the device voice;
  a target with neither stays silent.
  The drills' generated readings ("dreihundertsiebenundvierzig") use the voice.
  The calendar's weekday/month names (`calendar{}`) and
  the atlas' country/nationality names (`countries{}`) are recorded.
- **Voice tier matters.**
  iOS bundles only the compact voice;
  enhanced and premium are a free download under
  Settings > Accessibility > Spoken Content > Voices.
  The app checks the answering quality (`Speaker.voiceQuality`)
  and points at the download while the compact voice is active:
  a line in the audio setting and one dismissible notice on Home.
  The voice table is dropped on every foreground.
- **The target language is spoken with its article; the learner's own language is not.**
  The voice says "das Brot"; kern decides whether there is an article (`shownArticle`).
  Where a pack recorded the article too, the recording says it:
  German and Italian carry an `articles{}` section.
  Badge, plural line and alternates stay unspoken.
- **Audio may never give the answer away**:
  recognition speaks at once, produce waits for the reveal.
  Both apps consume one cue (`PronunciationCue`).
- **Autoplay fires only where the card holds the learner.**
  A clean correct flips in 0.45-1.2 s; a word cut off teaches nothing.
  Produce fires wait for the feedback chime;
  chimes are never ducked, and no fire delays a flip.
  One fire per card and one per drill task.

| on screen | speaks? | what is said |
|---|---|---|
| recognition prompt | yes, at once | the prompted form (rotated synonym, not canonical) |
| recognition reveal, write-it-out | no | already said once |
| produce correct | no -- card is flipping | -- |
| near miss (typo, other form) | yes, after chime | the correction box form |
| produce revealed (Aufdecken/wrong/other word) | yes, after chime | the bare target word |
| trainer drill prompt (numeral, clock, date) | no | the reading IS the answer |
| drill prompt in learning language (reversed run) | yes, at once | the form on the card |
| drill prompt in known language (forward run) | no | the reveal carries the voice |
| trainer drill reveal/correction | yes, after chime | the reading (usually voice; weekday/month/country/nationality recorded) |
| listening mode (between two sayings) | yes, unattended | the meaning in the known language |
| drill answer in known language (reversed atlas) | no | prompt carries the voice instead |

- **Listening mode**: target word, meaning, target word again.
  Gap before meaning: 1.2 s for a held word, 0.6 s for a new one;
  echo and breath between turns are the same two lengths.
  Every beat is kern's (`../kern/docs/turns.md`).
  No mute button; plays under `.playback`.
  Takes audio over (no `.mixWithOthers`, spoken-audio mode),
  session released on stop.
  A word enters only if both sides can be said on this device.
- **Tapping a word says it again, past both mutes.**
  A tap is a request, so it plays under `.playback`.
  App-fired sounds stay `.ambient` (`AudioSession`).
  The audio setting's hint line names the gesture once.
- **Speaker icon follows the surface.**
  Card/drill: the speaker beside the word is the control;
  a form with no audio drops the icon; the card holds its height regardless.
  Reference pages: content is the control (tap a row to hear it),
  one hint line under the heading, shown only where the device can say the language.
  Alphabet sheet keeps glyphs (rows disagree about what they hold).
  Drill prompts in the learning language draw a speaker (same rule as a card).
  Revealed letter: no speaker (the glyph is not a form);
  dictated word: speaker (it is a word in the learning language).
- **The letter drill is the one autoplay no mute reaches.**
  Entering a screen whose only content is a sound is the request;
  plays under `.playback`. VoiceOver holds it back.
- **Reading aloud is on by default, and the silent switch silences it.**
  The top-bar switch turns it into a decision:
  OFF silences autoplay; ON lifts autoplay past a silenced phone.
  Three states, one setting for the device (not per language, not in the box).
  The silent switch cannot be read back (no API), so it is followed by deferring to it.
- **A card whose only content is a sound is not dealt onto a silent phone.**
  iOS reads `outputVolume`, Android the media stream's volume and mute.
  Android's ringer mode is not read (silencing notifications leaves media playing).
  iOS's ring/silent switch cannot be read, so sound-prompted cards carry
  "Can't listen right now?" on screen (`design.md`).
- **Audio setting: three-way row -- No audio, Recordings, Speech.**
  "Recordings" (default): bundled recording where available, voice for the rest.
  "Speech": voice preferred, recording only where no voice exists.
  The top-bar button is the mute only; it never changes the source.
  Choosing a voice lifts autoplay past a silenced phone.
- **Source remembered per learning language, mute per device.**
  A source is offered only where it can answer
  (`AudioCapability`, over `Catalog.hasRecordings` and the device voice table).
- **Feedback chimes**: not silenced by the read-aloud switch,
  but play under whatever category it left standing.
  Chimes and words share one volume (one audio session).
  Leveled against the loudness target (`scripts/sounds.py`).
- VoiceOver: no autoplay talking over it; headword labeled with its language;
  replay is an action on the word.
- Licensing: `audio-licensing.md`.
