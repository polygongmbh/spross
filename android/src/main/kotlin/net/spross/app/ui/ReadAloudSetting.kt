package net.spross.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        Text(
            chrome.settingsAudioTitle,
            style = MaterialTheme.typography.titleMedium,
        )
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
                AudioPreference.Recordings -> chrome.settingsAudioHintRecordings
                AudioPreference.Speech -> chrome.settingsAudioHintTts
            }
        )
        if (model.pronouncer.voiceMissing(target)) VoiceDownloadLink(chrome, targetName)
    }
}

/**
 * The pointer to the speech engine's voice download, for a language it knows but has not
 * downloaded: without it, every word lacking a recording stays silent.
 */
@Composable
internal fun VoiceDownloadLink(chrome: Chrome, targetName: String) {
    val context = LocalContext.current
    TextButton(
        onClick = {
            // why: opens the system's text-to-speech settings, where the voice is downloaded;
            // a device without that screen leaves the tap doing nothing rather than crashing.
            try {
                context.startActivity(Intent(TTS_SETTINGS))
            } catch (_: ActivityNotFoundException) {
            }
        },
        contentPadding = SETTINGS_BUTTON_PADDING,
    ) {
        Text(chrome.settingsAudioVoiceDownload.format(targetName), style = MaterialTheme.typography.bodySmall)
    }
}

private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
