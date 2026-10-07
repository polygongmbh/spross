package net.spross.kern.catalog

/**
 * Where a request to say a word came from — what decides whether the app's mute and a screen
 * reader may hold it back. Both apps' one pronouncer asks [held]; the platform only reports
 * the two facts.
 */
enum class PronounceTrigger {
    /** Autoplay: a card or a drill reading itself. Both the mute and a screen reader hold it. */
    Auto,

    /**
     * An autoplay that carries the QUESTION itself (the letter drill): opening a screen whose
     * only content is a sound is the request, so the mute never reaches it — only a screen
     * reader, which must not be talked over, still holds it back.
     */
    Essential,

    /** A tap on a word: a request, which passes both. */
    Tap,

    /**
     * A listening run, whose whole content is sound: it passes both like a tap. A screen
     * reader does not hold it — there is no screen to be talked over, and a playlist that fell
     * silent under one would be a mode its users could not have at all.
     */
    Listening,
    ;

    /** Whether the word stays unsaid: [muted] is the app's own read-aloud switch. */
    fun held(muted: Boolean, readsScreenAloud: Boolean): Boolean = when (this) {
        Auto -> muted || readsScreenAloud
        Essential -> readsScreenAloud
        Tap, Listening -> false
    }
}

/** Which branch answers a word that may be said. */
enum class SoundBranch { Speech, Recording, Silent }

/**
 * Which branch says a word: the live voice first where the learner prefers it and the device
 * has one ([prefersSpeech] — every word sounds the same and the article is always spoken),
 * else the recording that matched, else the voice, else nothing.
 *
 * [recordingOnly] skips the preference — the credits screen plays the file it credits, never a
 * voice in its place — but not the fallback. A recording that matched but will not open is
 * asked again with [hasRecording] false.
 */
fun soundBranch(
    prefersSpeech: Boolean,
    recordingOnly: Boolean,
    hasVoice: Boolean,
    hasRecording: Boolean,
): SoundBranch = when {
    !recordingOnly && prefersSpeech && hasVoice -> SoundBranch.Speech
    hasRecording -> SoundBranch.Recording
    hasVoice -> SoundBranch.Speech
    else -> SoundBranch.Silent
}
