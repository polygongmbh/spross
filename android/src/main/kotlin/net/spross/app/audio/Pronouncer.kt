package net.spross.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetFileDescriptor
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.model.Language

/**
 * The one way anything in the app says a target word out loud: review cards and
 * (later) the letter drill both knock here, so the mute flag, the TalkBack gate and
 * "recordings first" are decided in a single place.
 *
 * Kern decides WHAT to say ([Pronunciation]: the form, the utterance, and the
 * catalog-relative path of a recording that speaks that very form); this decides
 * WHETHER and WITH WHAT. The iOS `Pronouncer` is the same four steps in the same order.
 */
class Pronouncer(context: Context, private val prefs: SharedPreferences) {

    /** Where a fire came from. Autoplay may be silenced; a tap is a request. */
    enum class Trigger { AUTO, TAP }

    private val assets = context.applicationContext.assets
    private val accessibility = context.applicationContext
        .getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    private val player = PronunciationPlayer()
    private val speaker = Speaker(context)

    /** Path of the clip the player still holds — asking for it again needs no load. */
    private var loaded: String? = null

    private var mutedState by mutableStateOf(prefs.getBoolean(KEY_MUTED, false))

    /**
     * One device-scoped flag (never per target language, never in the box): silences
     * AUTOPLAY only. Absent default = false, so words are read aloud on fresh installs
     * and upgrades alike; the top-bar toggle is the off switch. Compose state, because
     * a plain field would never recompose the toggle that shows it.
     */
    var muted: Boolean
        get() = mutedState
        set(value) {
            mutedState = value
            prefs.edit().putBoolean(KEY_MUTED, value).apply()
            // why: muting is expected to take effect on the word in the air, not only
            // on the next card.
            if (value) stop()
        }

    /**
     * Whether the device has a voice for [lang] at all — Swahili has none without
     * Google TTS installed, and those words stay silent unless a recording matched.
     *
     * Reads the synthesizer's readiness, which is Compose state: a surface that gates on
     * this recomposes by itself the moment the engine finishes binding.
     */
    fun canSpeak(lang: Language): Boolean = speaker.canSpeak(lang)

    /**
     * Whether a screen reader is reading the screen aloud — the same fact that gates
     * autoplay, exposed because a drill built ENTIRELY out of audio has to compensate
     * for the suppression (focus goes to the replay button, no timed screen change).
     * One definition, so the gate and its compensation can never disagree.
     */
    val readsScreenAloud: Boolean get() = accessibility?.isTouchExplorationEnabled == true

    /**
     * Whether this form can be heard at all — gates the tap-to-replay affordance so a
     * word with neither a recording nor a voice grows no gesture that does nothing.
     */
    fun canPronounce(pronunciation: Pronunciation): Boolean =
        pronunciation.recordingPath != null || canSpeak(pronunciation.lang)

    /** Says the form: the recording when one matched, else the live voice. */
    fun pronounce(pronunciation: Pronunciation, trigger: Trigger) {
        // why: TalkBack reads the card itself, target word included — autoplay on top
        // of it is two voices over one word. A tap is never gated: it is a request.
        if (trigger == Trigger.AUTO && (muted || readsScreenAloud)) return
        speaker.stop()
        val path = pronunciation.recordingPath
        // why: the player still holds the last clip prepared, so a second ask for the
        // same word answers without a second decode — the reason it keeps it.
        if (path != null && path == loaded && player.replay()) return
        // why: one word at a time — a new fire replaces whatever is sounding.
        player.stop()
        loaded = null
        val recording = path?.let(::openRecording)
        if (recording != null) {
            // why: the loudness and the dead air are the catalog's MEASUREMENTS of bytes
            // that stay the untouched transcode — playback is the one place they are ever
            // applied, and never the file.
            player.play(recording, pronunciation.gain, pronunciation.leadMs)
            loaded = path
            return
        }
        // Silent no-op when no voice exists for the language.
        speaker.speak(pronunciation.utterance, pronunciation.lang)
    }

    fun stop() {
        player.stop()
        speaker.stop()
        loaded = null
    }

    /** From `AppModel.onCleared()`: the decoded clip and the engine binding both go. */
    fun release() {
        player.release()
        speaker.shutdown()
    }

    // why: the "catalog/" prefix mirrors AssetCatalogSource — kern hands out
    // catalog-relative paths and never opens a file. openFd answers only for a STORED
    // asset, which the noCompress pin in build.gradle.kts guarantees for mp3.
    private fun openRecording(path: String): AssetFileDescriptor? =
        try {
            assets.openFd("catalog/$path")
        } catch (_: IOException) {
            null // no file behind the path: fall through to the live voice
        }

    private companion object {
        const val KEY_MUTED = "pronunciationMuted"
    }
}
