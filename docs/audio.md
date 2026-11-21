# Audio — how a word is spoken
Which sound plays and when, how the mutes and the audio setting interact, what is spoken and how a recording is matched to a form, and how loud it plays.
Neighbors: the recordings' licenses `audio-licensing.md`, the manifest's file format `../catalog/audio/README.md`, listening `listening.md`.

## When a word is spoken

- **Words are read aloud; a recording is only played for the word it actually says.**
  Kern matches recordings by the form on screen, never by concept,
  so a rotated synonym is never answered with the canonical word.
  One exception: a form with no recording of its own plays one of its bare form,
  without its leading citation prefix or particle or with its reflexive ending traded for the bare infinitive's
  (sw `piga simu` under `kupiga simu`, de `irren` under `sich irren`, es `poner` under `ponerse`;
  `LanguageInfo.bareForm`, the `optionalVerbPrefixes`, `detachableParticles` and `reflexiveEndings` of `languages.json`).
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
  Android's Google engine has no such tiers;
  its gap is a language whose voice is not downloaded yet (`Speaker.voiceMissing`),
  which leaves every word without a recording silent,
  so the audio setting links to the system's speech settings and asks again on every foreground;
  a device with no speech engine at all gets a link to Google's speech services in the store instead.
- **A review card and a drill say both their sides, each once, each in the language it is written in.**
  Hearing them paired binds them, and each side is said where the card shows it:
  the prompt as it goes up, the answer once it is out.
  The pairing is the cue, so the learner's own side is said only beside a word this device can sound —
  a recording of that form or a voice (`hearsMeaning`); a word it cannot say leaves its question silent.
  A side in no language (a bare numeral, a clock, the digits a numbers or calendar task owes) is not said.
- **The target language is spoken with its article; the learner's own language is not.**
  The voice says "das Brot"; kern decides whether there is an article (`shownArticle`).
  Where a pack recorded the article too, the recording says it
  (`scripts/audio/coverage.py --credits` lists which packs carry an `articles{}` section).
  Badge, plural line and alternates stay unspoken.
- **Audio may never give the answer away**:
  recognition speaks the word at once, produce says the meaning it asks by and waits for the reveal to say the word.
  A review card and a drill task read aloud through ONE mechanism, kern's `Reading` (cues: `pronunciationCue`, `meaningCue`),
  fired by one reader per app (iOS `Reader`, Android `rememberReadAloud`), so no screen decides what is heard.
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
  The silent switch cannot be read back (no API), so it is followed by deferring to it.
  The top-bar switch turns it into a decision for the current launch:
  ON lifts autoplay past a silenced phone, OFF silences it.
  From following the phone a tap turns it ON, since a tap there is a request to hear;
  after that it toggles between ON and OFF.
  Its icon names what autoplay does now:
  a bell where the silent switch decides (iOS's own sign for it),
  a speaker where it plays past the switch, a slashed speaker where the app is muted.
  The middle state is iOS's: Android's media stream ignores the ringer, so there it is ON.
- **The audio setting's "No autoplay" is stored; the top-bar switch holds for the launch.**
  Every launch starts at following the phone, or muted where "No autoplay" is stored, on both phones.
  One choice for the device (not per language, not in the box);
  the settings show the stored choice alone, never the switch's state for the launch.
- **A card whose only content is a sound is dealt whenever the app itself is not muted.**
  The device's volume holds nothing back;
  iOS's ring/silent switch cannot be read at all, so hear cards carry
  "Can't listen right now?" on screen (`AnswerControls.cantListen`).
- **A screen about to play words at a very low volume says so.**
  Review cards and drills while reading aloud is on, the letter drill and Listening always:
  a line under the top bar asks for the volume to come up, and goes once it has.
  The threshold is kern's (`isVolumeLow`); it never holds a sound or a card back.
  Android's ringer mode is not read (silencing notifications leaves media playing).
- **Audio setting: three-way row -- No autoplay, Recordings, Speech.**
  "No autoplay": nothing is read aloud by itself and no hear card is dealt;
  a tap, the letter drill and Listening still play, with the source last picked.
  "Recordings" (default): bundled recording where available, voice for the rest.
  "Speech": voice preferred, recording only where no voice exists (`soundBranch`).
  The top-bar button is the mute only; it never changes the source.
- **Source remembered per learning language.**
  A source is offered only where it can answer,
  and a stored source that cannot reads as the other one
  (`AudioCapability.preference`, over `Catalog.hasRecordings` and the device voice table).
- **Feedback chimes**: not silenced by the read-aloud switch,
  but play under whatever category it left standing.
  Chimes and words share one volume (one audio session).
  Rendered full scale (`scripts/sounds.py`), each played at its kern `Chime` level.
- VoiceOver: no autoplay talking over it (`PronounceTrigger.held`); headword labeled with its language;
  replay is an action on the word.

## Recordings

- **What is spoken is the headword, and on the TARGET side its article with it** (`spokenTargetForm`, `shownArticle`):
  an article is half of what knowing a noun means, while the source side's grammar is not what is being taught.
  A recording says what was recorded:
  the article reaches the live voice and an `articles{}` recording, never a re-cut file.
- **A recording is looked up by the form it speaks, never by the slug** (`AudioManifest`, `speechKey`),
  so a merge, rename or move of a concept never touches audio
  and a rotated synonym nobody recorded is spoken live rather than answered with the canonical word.
  `speechKey` is the one normalization both sides of the lookup and `scripts/audio/gates.py` apply.
- **The analysis index is measurement data, never an edit** (user ruling 2026-08-01):
  the mp3 bytes stay the untouched Commons transcode (`audio-licensing.md` §3),
  and `gain`, `cap`, `lead`, `end` and `gate` are measurements of them that only the player applies (`AudioIndex`, `Playback`).
  A platform applies them as given and never mints a number of its own;
  what was measured, against which target, is `scripts/audio/index.py`'s `ANALYSIS`.
- **A key may hold several takes, and the engine draws which one plays** (`AudioRecording.take`);
  every take is credited under its own voice.
- **Audio is exempt from the fingerprint**: recordings cannot change the join,
  so a refreshed pack never restamps and recomposes a running session (`CatalogLoading`).
- **Which voice speaks a language** is kern's (`VoiceSelection`); enumerating voices stays app-side.

## Evidence

- Audio alone trains the ear, and the written form beside it carries form and meaning (Sydorenko 2010, *Language Learning & Technology* 14(2)) — why a card says each side where it shows it.
