package net.spross.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.kern.catalog.isVolumeLow

/**
 * The line a screen about to play words shows while the media volume is at or near zero:
 * the words still play, and this asks for them to be heard. iOS's `VolumeHint` is the twin.
 *
 * Read once a second while on screen rather than observed — a volume key posts no public
 * broadcast, and a second is quick enough for a hint that goes once the volume is up.
 * [active] is whether this screen will make a sound on its own right now.
 * It sits under a top bar at no spacing of its own: hidden, it costs the layout nothing.
 */
@Composable
fun VolumeHint(model: AppModel, active: Boolean) {
    val low by produceState(initialValue = false, model) {
        while (true) {
            value = isVolumeLow(model.pronouncer.volumeFraction)
            delay(1_000)
        }
    }
    AnimatedVisibility(visible = active && low, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                SprossIcons.Speaker,
                contentDescription = null,
                tint = Theme.colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                model.chrome.commonVolumeLow,
                style = MaterialTheme.typography.bodySmall,
                color = Theme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
