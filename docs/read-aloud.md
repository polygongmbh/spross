# Reading aloud
How the app speaks a word: which sound plays, when autoplay fires, how the two mutes interact.
Neighbors: engine `../kern/docs/audio.md`, licensing `audio-licensing.md`.

- **Words are read aloud; a recording is only played for the word it actually says.**
  Kern matches recordings by the form on screen, never by concept,
  so a rotated synonym is never answered with the canonical word.
  One exception: a verb with no recording of its citation form plays one of its bare stem
  (sw `piga simu` under `kupiga simu`, `LanguageInfo.optionalVerbPrefixes`).
  Unmatched forms fall to the device voice;
  a target with neither stays silent.
  The drills' generated readings ("dreihundertsiebenundvierzig") use the voice.
  The calendar's weekday/month names (`calendar{}`) and
  the atlas' country/nationality names (`countries{}`) are recorded.
- **Voice quality matters.**
  iOS bundles only the compact voice;
  enhanced and premium are a free download under
  Settings > Accessibility > Spoken Content > Voices.
  The app checks the answering quality (`Speaker.voiceQuality`)
  and points at the download while the compact voice is active:
  a line in the audio setting and one dismissible notice on Home.
  The voice table is dropped on every foreground.
- **A review card says both its sides, each once: the word and its meaning.**
  Hearing them paired binds them, and each side is said where the card shows it.
  The meaning is the learner's own language, on until the audio setting's "Say the meaning too" turns it off.
- **The target language is spoken with its article; the learner's own language is not.**
  The voice says "das Brot"; kern decides whether there is an article (`shownArticle`).
  Where a pack recorded the article too, the recording says it
  (`scripts/audio-coverage.py --credits` lists which packs carry an `articles{}` section).
  Badge, plural line and alternates stay unspoken.
- **Audio may never give the answer away**:
  recognition speaks the word at once, produce says the meaning it asks by and waits for the reveal to say the word.
  A review card and a drill task read aloud through ONE mechanism:
  kern hands each question a `Reading` (its prompt saying and its answer saying),
  and one reader per app fires it (iOS `Reader`, Android `rememberReadAloud`).
  Every drill state must rule on both sayings (`DrillRunProgress`), so no screen decides what is heard.
- **Autoplay fires only where the card holds the learner.**
  A clean correct answer says the word too, and its flip waits for the saying to end, up to a ceiling (`READING_CEILING_MS`);
  a word cut off teaches nothing.
  A drill says every graded answer, right or wrong, and its beat waits for the reading to end;
  a timed run leaves its clean answers unsaid, since the clock is running,
  a reversed numbers task says its reading as the prompt and nothing after,
  and the letter drill says nothing more, since its question already was the sound.
  Produce fires wait for the feedback chime (`READING_CHIME_CLEARANCE_MS`);
  chimes are never ducked.
  One prompt fire and one answer fire per card, and one per drill task.

| on screen | speaks? | what is said |
|---|---|---|
| recognition prompt | yes, at once | the prompted form (rotated synonym, not canonical) |
| recognition reveal | yes, after chime | the meaning |
| write-it-out | no | already said once |
| produce prompt, asked by meaning | yes, at once | the meaning |
| produce prompt, asked by ear | yes, at once | the target word |
| produce correct | yes, after chime; the flip waits for it | the target word, or the meaning on a card asked by ear |
| near miss (typo, other form) | yes, after chime | the correction box form |
| produce revealed (Aufdecken/wrong/other word) | yes, after chime | the bare target word, or the meaning on a card asked by ear |
| trainer drill prompt (numeral, clock, date) | no | the reading IS the answer |
| reversed numbers prompt (the reading) | yes, at once; not again after the verdict | the reading |
| drill prompt in learning language (reversed run, opposites) | yes, at once | the form on the card |
| drill prompt in known language (forward run) | no | the reveal carries the voice |
| trainer drill graded answer (right, slip, miss, reveal) | yes, after chime | the answer in the learned language: the reading, the name, the word, the authored phrase (usually voice; weekday/month/country/nationality recorded) |
| listening mode (between two sayings) | yes, unattended | the meaning in the known language |
| drill answer in known language (reversed atlas) | no | prompt carries the voice instead |

- **Listening mode**: target word, meaning, target word again,
  every beat kern's (`../kern/docs/turns.md`).
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
  Credits: a row plays the recording it credits (never a voice in its place),
  and each row carries a small trailing link to its Commons page, the one per-row icon on any list.
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
  The middle state is iOS's: Android's media stream ignores the ringer, so there it is ON.
  The silent switch cannot be read back (no API), so it is followed by deferring to it.
  A choice made with the switch holds for the current launch;
  every launch starts back at following the phone, on both phones.
- **A card whose only content is a sound is dealt whenever the app itself is not muted.**
  The device's volume holds nothing back;
  iOS's ring/silent switch cannot be read at all, so sound-prompted cards carry
  "Can't listen right now?" on screen (`design.md`).
- **A screen about to play words at a very low volume says so.**
  Review cards and drills while reading aloud is on, the letter drill and Listening always:
  a line under the top bar asks for the volume to come up, and goes once it has.
  The threshold is kern's (`isVolumeLow`, a tenth of the range);
  iOS reads `outputVolume`, Android the media stream's volume and mute, once a second.
  Android's ringer mode is not read (silencing notifications leaves media playing).
- **Audio setting: three-way row -- No audio, Recordings, Speech.**
  "Recordings" (default): bundled recording where available, voice for the rest.
  "Speech": voice preferred, recording only where no voice exists (`soundBranch`).
  The top-bar button is the mute only; it never changes the source.
  Choosing a voice lifts autoplay past a silenced phone.
- **Source remembered per learning language; the mute is remembered nowhere.**
  A source is offered only where it can answer,
  and a stored source that cannot reads as the other one
  (`AudioCapability.preference`, over `Catalog.hasRecordings` and the device voice table).
- **Feedback chimes**: not silenced by the read-aloud switch,
  but play under whatever category it left standing.
  Chimes and words share one volume (one audio session).
  Rendered full scale (`scripts/sounds.py`), each played at its kern `Chime` level.
- VoiceOver: no autoplay talking over it (`PronounceTrigger.held`); headword labeled with its language;
  replay is an action on the word.
