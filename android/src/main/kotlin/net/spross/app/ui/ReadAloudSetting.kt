package net.spross.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.audioSources
import net.spross.kern.catalog.AudioPreference
import net.spross.kern.catalog.preferenceOptions
import net.spross.kern.model.Language

/**
 * The stored mute, never the top bar's mute for the launch, and a standing
 * home for the disclosure that a tap on a word speaks it even while this is off. The
 * three-way preference names the voice source too, so the picker alone decides both:
 * there is no state where a source is chosen but the app is silent.
 *
 * The source belongs to [target], the language being learned; the mute is the device's.
 * Speech is offered only where the device actually has a voice for [target] — a segment
 * that would fall straight back to the recordings promises a sound the phone cannot make.
 * A [target] nothing can say gets no row: its caller draws at most the voice download link.
 */
@Composable
internal fun ReadAloudSetting(model: AppModel, target: Language, targetName: String) {
    val chrome: Chrome = model.chrome
    val sources = model.audioSources(target)
    val preference = model.pronouncer.audioPreference(target, sources)
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        SettingTitle(SprossIcons.Speaker, chrome.settingsAudioTitle)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = sources.preferenceOptions
            options.forEachIndexed { index, option ->
                val label = when (option) {
                    AudioPreference.Off -> chrome.settingsAudioOptionOff
                    AudioPreference.Recordings -> chrome.settingsAudioOptionRecordings
                    AudioPreference.Speech -> chrome.settingsAudioOptionTts
                }
                SegmentedButton(
                    selected = option == preference,
                    onClick = { model.pronouncer.setAudioPreference(target, option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        // why: the hint names the CHOSEN behavior, not the picker as a whole. The
        // tap-to-replay gesture is disclosed in the No autoplay line alone — the only
        // preference where a learner might think the app has gone silent for good.
        SettingHint(
            when (preference) {
                AudioPreference.Off -> chrome.settingsAudioHintOff
                AudioPreference.Recordings ->
                    if (sources.hasVoice) chrome.settingsAudioHintRecordings else chrome.settingsAudioHintRecordingsOnly
                AudioPreference.Speech -> chrome.settingsAudioHintTts
            }
        )
        VoiceGapLink(model, target, targetName)
    }
}

/**
 * The pointer to what a voice for [target] is missing, if anything: Google's speech services
 * where no engine is installed, its voice download where the engine knows the language but
 * has not downloaded it. Without either, every word lacking a recording stays silent.
 */
@Composable
internal fun VoiceGapLink(model: AppModel, target: Language, targetName: String) {
    val chrome = model.chrome
    val context = LocalContext.current
    val (label, intents) = when {
        model.pronouncer.engineMissing -> chrome.settingsAudioEngineInstall to listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE_TTS")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$GOOGLE_TTS")),
        )
        model.pronouncer.voiceMissing(target) ->
            chrome.settingsAudioVoiceDownload.format(targetName) to listOf(Intent(TTS_SETTINGS))
        else -> return
    }
    // why: plain clickable text rather than a TextButton, whose zero-inset clip cuts the
    // first letter off a link that wraps onto a second line.
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = Theme.colors.accent,
        modifier = Modifier
            .clickable(role = Role.Button) {
                // why: opens the store or the system's text-to-speech settings, first that
                // answers; a device with neither leaves the tap doing nothing rather than crashing.
                intents.firstOrNull { intent -> runCatching { context.startActivity(intent) }.isSuccess }
            }
            .padding(vertical = Theme.spacing.sm),
    )
}

/** Whether [VoiceGapLink] has anything to say for [target]. */
internal fun AppModel.hasVoiceGap(target: Language): Boolean =
    pronouncer.engineMissing || pronouncer.voiceMissing(target)

private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
private const val GOOGLE_TTS = "com.google.android.tts"
