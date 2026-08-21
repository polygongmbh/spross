package net.spross.app.listen

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * The audio, taken OVER rather than mixed into.
 *
 * Every other sound the app makes shares the device with whatever else is playing, because a
 * chime or a single word has no business interrupting a podcast. A listening run is the one
 * exception (`docs/read-aloud.md`): a mode meant to be listened to that lands on top of
 * something else is a mode nobody hears. So it asks for `AUDIOFOCUS_GAIN` — the platform's
 * way of saying "this is the playback now" — and gives it back when the run stops, at which
 * point whatever was interrupted resumes by itself.
 *
 * Focus runs both ways. A call, a navigation prompt or another player asking for its own
 * turn pauses the run rather than talking underneath it; where the loss was temporary the
 * run picks up again on the word it was cut off in the middle of.
 *
 * `setWillPauseWhenDucked` is the same rule at lower stakes: a duck leaves speech audible but
 * unintelligible, so a run that would be ducked pauses instead.
 */
class ListeningAudioFocus(
    context: Context,
    private val onLoss: () -> Unit,
    private val onGain: () -> Unit,
) {

    private val manager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setWillPauseWhenDucked(true)
        // The listener has no Handler of its own, so it lands on the main looper — the very
        // thread the run's state is written and read from.
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> onGain()
                else -> onLoss()
            }
        }
        .build()

    fun take(): Boolean =
        manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    fun release() {
        manager.abandonAudioFocusRequest(request)
    }
}
