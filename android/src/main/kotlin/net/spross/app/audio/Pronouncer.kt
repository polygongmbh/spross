package net.spross.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetFileDescriptor
import android.media.AudioManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import net.spross.kern.catalog.AudioCapability
import net.spross.kern.catalog.AudioPreference
import net.spross.kern.catalog.PronounceTrigger
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.catalog.SoundBranch
import net.spross.kern.catalog.preference
import net.spross.kern.catalog.soundBranch
import net.spross.kern.catalog.spokenTargetForm
import net.spross.kern.model.Language

/**
 * The one way anything in the app says a target word out loud: review cards, drills and
 * the letter drill all knock here, so kern's mute and TalkBack gate ([PronounceTrigger.held])
 * and its branch ([soundBranch]) are asked in a single place.
 *
 * Kern decides WHAT to say ([Pronunciation]: the form, the utterance, and the
 * catalog-relative path of a recording that speaks that very form); this decides
 * WHETHER and WITH WHAT. The iOS `Pronouncer` is the same four steps in the same order.
 */
class Pronouncer(context: Context, private val prefs: SharedPreferences) {

    /**
     * Which voice answers a target word: the bundled recording when one matched, or the
     * synthesizer. The box settings' audio row carries it as the two "on" options; the
     * read-aloud switch above only mutes, it never changes this.
     *
     * Stored PER TARGET LANGUAGE, unlike [muted]: how good the system voice is against
     * the pack is a fact about one language, and a language with no voice at all is
     * never offered the choice.
     */
    enum class VoiceSource(val storedValue: String) {
        /** Bundled recordings first, the live voice for the rest — the default. */
        RECORDINGS("recordings"),
        /** The live voice for everything it can say, so every word sounds the same and the
         * article is always spoken; recordings answer only where no voice exists. */
        TTS("tts"),
    }

    private val assets = context.applicationContext.assets
    private val accessibility = context.applicationContext
        .getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val player = PronunciationPlayer()
    private val speaker = Speaker(context)

    /** Path of the clip the player still holds — asking for it again needs no load. */
    private var loaded: String? = null

    private var mutedState by mutableStateOf(false)

    /** The word sounding right now, for a speaker that pulses while it does; null in silence. */
    var sounding: Pronunciation? by mutableStateOf(null)
        private set

    /** Bumped by every fire and stop, so only the latest word's end clears [sounding]. */
    private var fires = 0

    /**
     * What THIS launch has picked, per language — everything else answers from the
     * preferences. Compose state, because the box picker renders it.
     */
    private var pickedSources by mutableStateOf(emptyMap<Language, VoiceSource>())

    /**
     * The source in force for [lang]. The absent default keeps bundled recordings first
     * for every language until it is told otherwise.
     */
    fun voiceSource(lang: Language): VoiceSource = pickedSources[lang] ?: stored(lang)

    fun setVoiceSource(lang: Language, value: VoiceSource) {
        pickedSources = pickedSources + (lang to value)
        prefs.edit().putString(keyFor(lang), value.storedValue).apply()
    }

    /** The audio setting's option for [lang]: kern reads the mute and the stored source over [sources]. */
    fun audioPreference(lang: Language, sources: AudioCapability): AudioPreference =
        sources.preference(muted, voiceSource(lang) == VoiceSource.TTS)

    /** Picking a source also turns reading aloud back on, so the picker never leaves the app silent behind it. */
    fun setAudioPreference(lang: Language, preference: AudioPreference) {
        if (preference.mutes) {
            muted = true
            return
        }
        setVoiceSource(lang, if (preference.prefersSpeech) VoiceSource.TTS else VoiceSource.RECORDINGS)
        muted = false
    }

    private var saysMeaningState by mutableStateOf(prefs.getBoolean(SAYS_MEANING, true))

    /**
     * Whether a review card also says the learner's own side — the meaning it asks by or
     * reveals. One device-wide choice, on until turned off; [muted] silences it with
     * everything else.
     */
    var saysMeaning: Boolean
        get() = saysMeaningState
        set(value) {
            saysMeaningState = value
            prefs.edit().putBoolean(SAYS_MEANING, value).apply()
        }

    private fun stored(lang: Language): VoiceSource =
        VoiceSource.entries.firstOrNull { it.storedValue == prefs.getString(keyFor(lang), null) }
            ?: VoiceSource.RECORDINGS

    /**
     * One device-wide flag (never per target language, never in the box): silences
     * AUTOPLAY only, and for this launch only — every launch starts read aloud, as iOS's
     * `ReadAloud` starts at following the phone. Compose state, because a plain field would
     * never recompose the toggle that shows it.
     */
    var muted: Boolean
        get() = mutedState
        set(value) {
            mutedState = value
            // why: muting is expected to take effect on the word in the air, not only
            // on the next card.
            if (value) stop()
        }

    /**
     * The media stream's volume over its whole range, 0 where the stream is muted outright —
     * what a screen about to play words reads for its low-volume hint (kern `isVolumeLow`).
     *
     * The RINGER mode is deliberately not read: silencing the ringer leaves media playing
     * on Android, and a learner who silenced their notifications did not ask for a silent
     * lesson. Read on demand rather than watched; the hint polls it.
     */
    val volumeFraction: Double
        get() {
            val manager = audioManager ?: return 1.0
            if (manager.isStreamMute(AudioManager.STREAM_MUSIC)) return 0.0
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return 1.0
            return manager.getStreamVolume(AudioManager.STREAM_MUSIC).toDouble() / max
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

    /**
     * Says the form: the recording when one matched, else the live voice.
     *
     * [article] is the target-side article to say in front of a SYNTHESIZED form, already
     * decided by kern's `shownArticle` rule so a rotated synonym carrying another gender
     * gets none. It never reaches a recording: a recording says what was recorded, and the
     * two branches sounding different is the accepted cost of teaching the article at all
     * (`docs/read-aloud.md`).
     *
     * [fadeDb] is kern's listening ramp (`listeningGainDb`), 0 everywhere else. It rides the
     * volume on top of a recording's own index, never instead of it — kern adds the two and
     * holds the sum at its floor (`fadedGainDb`).
     *
     * [recordingOnly] skips the Speech preference: the credits screen plays the file it
     * credits, never a voice in its place.
     *
     * [onFinish] fires ONCE on the main thread when the word has been said — including the
     * cases where nothing sounds at all, so a run armed off it can never wedge on a silent
     * word. It does not fire for a word this call itself cut off.
     */
    fun pronounce(
        pronunciation: Pronunciation,
        trigger: PronounceTrigger,
        article: String? = null,
        fadeDb: Double = 0.0,
        recordingOnly: Boolean = false,
        onFinish: (() -> Unit)? = null,
    ) {
        // why: TalkBack reads the card itself, target word included — autoplay on top
        // of it is two voices over one word.
        if (trigger.held(muted, readsScreenAloud)) {
            onFinish?.invoke()
            return
        }
        speaker.stop()
        val fire = ++fires
        sounding = pronunciation
        val ended: () -> Unit = {
            if (fires == fire) sounding = null
            onFinish?.invoke()
        }
        val lang = pronunciation.lang
        val path = pronunciation.recordingPath
        val prefersSpeech = voiceSource(lang) == VoiceSource.TTS
        var branch = soundBranch(prefersSpeech, recordingOnly, canSpeak(lang), path != null)
        if (branch == SoundBranch.Recording && path != null) {
            // why: the player still holds the last clip prepared, so a second ask for the
            // same word answers without a second decode — the reason it keeps it.
            val (indexDb, capDb) = pronunciation.gain to pronunciation.cap
            if (path == loaded && player.replay(playbackVolume(indexDb, capDb, fadeDb), ended)) return
            // why: one word at a time — a new fire replaces whatever is sounding.
            player.stop()
            loaded = null
            val recording = openRecording(path)
            if (recording != null) {
                // why: the loudness and the dead air are the catalog's MEASUREMENTS of bytes
                // that stay the untouched transcode — playback is the one place they are ever
                // applied, and never the file.
                player.play(recording, indexDb, capDb, pronunciation.leadMs, fadeDb, pronunciation.gate, ended)
                loaded = path
                return
            }
            branch = soundBranch(prefersSpeech, recordingOnly, canSpeak(lang), hasRecording = false)
        }
        // why: a recording from a previous fire may still be sounding — the synthesized
        // branch takes the word over completely.
        player.stop()
        loaded = null
        // The synthesized branch, and the only one the article reaches.
        val spoken = spokenTargetForm(article, pronunciation.form, pronunciation.form)
        if (branch != SoundBranch.Speech || !speaker.speak(spoken, lang, fadeVolume(fadeDb), ended)) {
            // No voice for the language: the word is silent, and it is over at once.
            ended()
        }
    }

    fun stop() {
        fires++
        sounding = null
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
        const val SAYS_MEANING = "audio.saysMeaning"

        fun keyFor(lang: Language) = "pronunciationSource.$lang"
    }
}
