package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.kern.design.PressKind
import net.spross.kern.listen.listeningTimerMinutes
import net.spross.kern.listen.listeningTimerWakeMs

/**
 * The bedtime, as one chip: every tap adds kern's five minutes, and a long press turns it
 * back off — there is no other way the minutes come down.
 *
 * It shows what is LEFT rather than what was picked: a run started an hour ago and a run
 * started a minute ago are the same pick and completely different answers to "is this going
 * to stop before I do". Off it says its own name, dimmed — the run then laps for as long as
 * it is left alone.
 *
 * It counts in MINUTES. A clock ticking down to the second is a clock you watch, which is
 * the opposite of what a sleep timer is for, so the number moves once a minute and nothing
 * on this screen redraws in between.
 */
@Composable
fun SleepTimerChip(model: AppModel) {
    val chrome = model.chrome
    val run = model.listening
    var minutesLeft by remember { mutableStateOf<Int?>(null) }
    // why: the deadline is a moment, not a countdown — this wakes when kern's minute turns,
    // and on the bedtime itself, and not at all while none is set.
    LaunchedEffect(run.deadline) {
        val deadline = run.deadline
        if (deadline == null) {
            minutesLeft = null
            return@LaunchedEffect
        }
        while (true) {
            val left = deadline - System.currentTimeMillis()
            minutesLeft = listeningTimerMinutes(left)
            if (left <= 0) return@LaunchedEffect
            delay(listeningTimerWakeMs(left))
        }
    }
    val left = minutesLeft?.let { chrome.listenMinutesLeft.format(it) }
    val tint = if (left == null) Theme.colors.textSecondary else Theme.colors.accent
    Row(
        modifier = Modifier
            .pressSpring(PressKind.Chip)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // why: the LENGTH is a state of the timer, so the name stays put and the
            // reading moves — a label that flips leaves TalkBack announcing the value
            // where the control's own name belongs.
            .semantics(mergeDescendants = true) {
                contentDescription = chrome.listenTimer
                stateDescription = left ?: chrome.a11yStateOff
            }
            // why: a tap adds five minutes and a hold turns the timer straight back off —
            // the only gesture that ever brings the minutes down.
            .combinedClickable(
                role = Role.Button,
                onClick = { run.cycleTimer() },
                onLongClick = { run.turnOffTimer() },
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = Theme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Icon(SprossIcons.Moon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Text(
            left ?: chrome.listenTimer,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = tint,
        )
    }
}

