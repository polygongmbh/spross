package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.audio.Pronouncer.AudioPreference
import net.spross.app.audioSources
import net.spross.kern.catalog.AudioCapability
import net.spross.kern.model.Language

/**
 * The mute the session's top bar switches (there reduced to the button), and a standing
 * home for the disclosure that a tap on a word speaks it even while this is off. The
 * three-way preference names the voice source too, so the picker alone decides both:
 * there is no state where a source is chosen but the app is silent.
 *
 * The source belongs to [target], the language being learned; the mute is the device's.
 * Speech is offered only where the device actually has a voice for [target] — a segment
 * that would fall straight back to the recordings promises a sound the phone cannot make.
 * A [target] nothing can say gets no row at all: its caller draws none.
 */
@Composable
internal fun ReadAloudSetting(model: AppModel, target: Language) {
    val chrome: Chrome = model.chrome
    val sources = model.audioSources(target)
    val preference = model.pronouncer.audioPreference(target, sources)
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(
            chrome.settingsAudioTitle,
            style = MaterialTheme.typography.titleMedium,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOfNotNull(
                chrome.settingsAudioOptionOff to AudioPreference.OFF,
                (chrome.settingsAudioOptionRecordings to
                    AudioPreference.RECORDINGS)
                    .takeIf { sources.hasRecordings },
                (chrome.settingsAudioOptionTts to
                    AudioPreference.TTS)
                    .takeIf { sources.hasVoice },
            )
            options.forEachIndexed { index, (label, option) ->
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
        // tap-to-replay gesture is disclosed in the No audio line alone — the only
        // preference where a learner might think the app has gone silent for good.
        SettingHint(
            when (preference) {
                AudioPreference.OFF -> chrome.settingsAudioHintOff
                AudioPreference.RECORDINGS ->
                    chrome.settingsAudioHintRecordings
                AudioPreference.TTS -> chrome.settingsAudioHintTts
            }
        )
        // why: only where there is a choice to be scoped — a language with one source
        // has nothing to remember per language.
        if (sources == AudioCapability.Both) SettingHint(chrome.settingsAudioHintPerLanguage)
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = model.pronouncer.saysMeaning,
                enabled = !model.pronouncer.muted,
                role = Role.Switch,
                onValueChange = { model.pronouncer.saysMeaning = it },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                chrome.settingsAudioSaysMeaning,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = model.pronouncer.saysMeaning,
                onCheckedChange = null,
                enabled = !model.pronouncer.muted,
            )
        }
        SettingHint(chrome.settingsAudioSaysMeaningHint)
    }
}
