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
import net.spross.kern.catalog.Pronunciation
import net.spross.kern.catalog.spokenTargetForm
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

    /**
     * Where a fire came from. Autoplay may be silenced; the other two are requests.
     *
     * [LISTENING] is a run whose only content is sound, which is itself the request to hear
     * one — so it passes both mutes exactly as a [TAP] does. It is named apart all the same:
     * a tap is one word answered on the spot, and this is an hour of them playing unattended.
     */
    enum class Trigger { AUTO, TAP, LISTENING }

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

    /** The box row's three options, one per [setAudioPreference] call. */
    enum class AudioPreference { OFF, RECORDINGS, TTS }

    private val assets = context.applicationContext.assets
    private val accessibility = context.applicationContext
        .getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val player = PronunciationPlayer()
    private val speaker = Speaker(context)

    /** Path of the clip the player still holds — asking for it again needs no load. */
    private var loaded: String? = null

    private var mutedState by mutableStateOf(prefs.getBoolean(KEY_MUTED, false))

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

    /**
     * The box row's three-way preference for [lang], derived from [muted] and
     * [voiceSource]: there is no state where a source is chosen but the app is silent.
     * Setting [AudioPreference.OFF] silences the review loop; picking either source also
     * turns reading aloud back on, so the picker can never leave the app silent behind a
     * chosen voice. A stored [VoiceSource.TTS] the device can no longer answer reads as
     * [AudioPreference.RECORDINGS], which is what would sound anyway.
     */
    fun audioPreference(lang: Language): AudioPreference = when {
        muted -> AudioPreference.OFF
        voiceSource(lang) == VoiceSource.TTS && canSpeak(lang) -> AudioPreference.TTS
        else -> AudioPreference.RECORDINGS
    }

    fun setAudioPreference(lang: Language, preference: AudioPreference) {
        when (preference) {
            AudioPreference.OFF -> muted = true
            AudioPreference.RECORDINGS -> {
                setVoiceSource(lang, VoiceSource.RECORDINGS)
                muted = false
            }
            AudioPreference.TTS -> {
                setVoiceSource(lang, VoiceSource.TTS)
                muted = false
            }
        }
    }

    private fun stored(lang: Language): VoiceSource =
        VoiceSource.entries.firstOrNull { it.storedValue == prefs.getString(keyFor(lang), null) }
            ?: VoiceSource.RECORDINGS

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
     * Whether the DEVICE itself would swallow a word right now — the media stream muted
     * outright, or turned all the way down. A card whose only content is a sound must not
     * be dealt onto a phone that has been told to be quiet, so this is the third mute the
     * sound prompt asks about, beside [muted] and the screen reader.
     *
     * The RINGER mode is deliberately not read: silencing the ringer leaves media playing
     * on Android, and a learner who silenced their notifications did not ask for a silent
     * lesson. Read per question rather than watched — a volume key is pressed between
     * cards, and the card already asked has its own way out.
     */
    val deviceSilenced: Boolean
        get() {
            val manager = audioManager ?: return false
            return manager.isStreamMute(AudioManager.STREAM_MUSIC) ||
                manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }

    /**
     * Silences autoplay for THIS launch, storing nothing — the mirror of iOS's
     * `-readAloud off` launch argument, which lands in the argument domain and leaves the
     * stored preference where it was. A script-driven run (`scripts/run-emu.sh --mute`)
     * starts quiet so nothing speaks at an unattended machine, and because nothing was
     * written, [muted]'s setter remains the only thing that changes what the app
     * remembers: the top-bar toggle turns sound back on with no special case, and a
     * hand-launched app is unaffected.
     */
    fun muteThisLaunch() {
        mutedState = true
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
     * [onFinish] fires ONCE on the main thread when the word has been said — including the
     * cases where nothing sounds at all, so a run armed off it can never wedge on a silent
     * word. It does not fire for a word this call itself cut off.
     */
    fun pronounce(
        pronunciation: Pronunciation,
        trigger: Trigger,
        article: String? = null,
        fadeDb: Double = 0.0,
        onFinish: (() -> Unit)? = null,
    ) {
        // why: TalkBack reads the card itself, target word included — autoplay on top
        // of it is two voices over one word. A tap is never gated: it is a request.
        if (trigger == Trigger.AUTO && (muted || readsScreenAloud)) {
            onFinish?.invoke()
            return
        }
        speaker.stop()
        val lang = pronunciation.lang
        // "Speech" preference: the voice reads everything, for one consistent sound and
        // the article always said aloud — the recording only answers where the language
        // has no voice at all.
        if (voiceSource(lang) == VoiceSource.TTS && canSpeak(lang)) {
            // why: a recording from a previous fire may still be sounding — the
            // synthesized branch takes the word over completely.
            player.stop()
            loaded = null
            val spoken = spokenTargetForm(article, pronunciation.form, pronunciation.form)
            if (!speaker.speak(spoken, lang, fadeVolume(fadeDb), onFinish)) {
                onFinish?.invoke()
            }
            return
        }
        val path = pronunciation.recordingPath
        // why: the player still holds the last clip prepared, so a second ask for the
        // same word answers without a second decode — the reason it keeps it.
        val (indexDb, capDb) = index(pronunciation)
        if (path != null && path == loaded &&
            player.replay(playbackVolume(indexDb, capDb, fadeDb), onFinish)
        ) {
            return
        }
        // why: one word at a time — a new fire replaces whatever is sounding.
        player.stop()
        loaded = null
        val recording = path?.let(::openRecording)
        if (recording != null) {
            // why: the loudness and the dead air are the catalog's MEASUREMENTS of bytes
            // that stay the untouched transcode — playback is the one place they are ever
            // applied, and never the file.
            player.play(recording, indexDb, capDb, pronunciation.leadMs, fadeDb, onFinish)
            loaded = path
            return
        }
        // The synthesized branch, and the only one the article reaches.
        val spoken = spokenTargetForm(article, pronunciation.form, pronunciation.form)
        if (!speaker.speak(spoken, pronunciation.lang, fadeVolume(fadeDb), onFinish)) {
            // No voice for the language: the word is silent, and it is over at once.
            onFinish?.invoke()
        }
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

    /**
     * The recording's gain for the current output route — `gainPhone` on the built-in
     * speaker, the full-range `gain` elsewhere, and `gain` wherever no phone plane was
     * measured (letters, texts). Reads the route once per fire, so a headphone change
     * between words is picked up by the next one.
     */
    private fun gain(pronunciation: Pronunciation): Double = index(pronunciation).first

    /** The picked plane's gain and the cap ITS ceiling held back — the two always travel together. */
    private fun index(pronunciation: Pronunciation): Pair<Double, Double> {
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.map { it.type }?.toSet() ?: emptySet()
        val phone = pronunciation.gainPhone
        return if (playbackPlane(devices) == PlaybackPlane.PHONE && phone != null) {
            phone to (pronunciation.capPhone ?: 0.0)
        } else {
            pronunciation.gain to pronunciation.cap
        }
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

        fun keyFor(lang: Language) = "pronunciationSource.$lang"
    }
}
