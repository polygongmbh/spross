package net.spross.kern.catalog

import net.spross.kern.model.Language

/**
 * What can carry a language's sound HERE — [audible]'s per-LANGUAGE sibling.
 *
 * Two sources, and every surface that asks about sound needs to tell them apart: a shipped
 * recording pack is a fact of the catalog and never changes under a running app, while a
 * synthesizer voice is a fact of the device and can arrive from Settings while the app sleeps.
 * Naming the pair once is what keeps the answer from being written five times per platform —
 * the audio setting's options, its hint, the stored preference it neutralizes, the branch
 * playback takes, and the tiles that stand or fall on hearing anything at all.
 *
 * [audible] stays the per-FORM predicate a pool filters on: a pack covers hundreds of forms,
 * never all of them, so "this language has recordings" and "this word has one" are different
 * questions and only the second one can keep a word out of a run.
 */
enum class AudioCapability {
    /** Neither a pack nor a voice: nothing here can be said, and a surface that offers to
     *  say it is promising a sound the device cannot make. */
    None,

    /** A pack, no voice — Swahili on iOS. Only the forms the pack recorded can be heard. */
    RecordingsOnly,

    /** A voice, no pack — English, which ships none and is spoken by every device there is. */
    VoiceOnly,

    Both,
    ;

    /** Whether a pack ships for this language: the catalog's half, fixed for the install. */
    val hasRecordings: Boolean get() = this == Both || this == RecordingsOnly

    /** Whether the device can say this language: the platform's half, live. */
    val hasVoice: Boolean get() = this == Both || this == VoiceOnly

    /** Nothing can say it. */
    val silent: Boolean get() = this == None
}

/**
 * What [language] can be heard through on this device.
 *
 * [hasVoice] is the caller's, for the same reason it is [audible]'s: whether a synthesizer
 * has this language is the single platform fact kern cannot know, and it is never cached
 * here — a voice installed while the app slept has to be picked up on return
 * (`LetterDrillAvailability`, `docs/read-aloud.md`).
 *
 * The catalog half is a map lookup, not a walk: a pack is registered by
 * `audio/<lang>/manifest.json` existing at load, so asking costs nothing and no surface has
 * to sweep the join to find out whether sound is possible at all.
 */
fun audioCapability(catalog: Catalog, language: Language, hasVoice: Boolean): AudioCapability =
    when {
        catalog.hasRecordings(language) && hasVoice -> AudioCapability.Both
        catalog.hasRecordings(language) -> AudioCapability.RecordingsOnly
        hasVoice -> AudioCapability.VoiceOnly
        else -> AudioCapability.None
    }

/**
 * The audio setting's three options, each a combination of the read-aloud mute and the
 * learned language's voice source: there is no state where a source is chosen but the app is
 * silent. Picking either source also turns reading aloud back on.
 */
enum class AudioPreference {
    Off,

    /** Bundled recordings first, the live voice for the rest — the default source. */
    Recordings,

    /** The live voice for everything it can say, so every word sounds the same and the article is always spoken. */
    Speech,
    ;

    /** Whether picking it mutes reading aloud rather than choosing a source. */
    val mutes: Boolean get() = this == Off

    /** The source it chooses, where it chooses one: whether the live voice leads. */
    val prefersSpeech: Boolean get() = this == Speech
}

/**
 * What the audio setting offers here: Off, and one option per source that can actually answer —
 * Speech only where the device has a voice, Recordings only where a pack ships.
 * Either option without its source promises a sound nothing can make.
 */
val AudioCapability.preferenceOptions: List<AudioPreference>
    get() = listOfNotNull(
        AudioPreference.Off,
        AudioPreference.Recordings.takeIf { hasRecordings },
        AudioPreference.Speech.takeIf { hasVoice },
    )

/**
 * The option the setting shows chosen, read from the mute and the stored source
 * ([prefersSpeech]: the live voice leads for this language).
 *
 * A stored source the language cannot answer reads as the other one, in both directions:
 * a voice uninstalled since it was picked and a pack that does not ship are equally empty
 * promises, and the setting must never show an option chosen that plays nothing.
 */
fun AudioCapability.preference(muted: Boolean, prefersSpeech: Boolean): AudioPreference = when {
    muted -> AudioPreference.Off
    prefersSpeech && hasVoice -> AudioPreference.Speech
    hasRecordings -> AudioPreference.Recordings
    else -> AudioPreference.Speech
}
