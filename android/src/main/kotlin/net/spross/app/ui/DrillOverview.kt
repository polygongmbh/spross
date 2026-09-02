package net.spross.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.Chrome

/**
 * The shape both Werkstatt pages wear.
 *
 * Run first, reading after: the picks and the button on top, the table below them. Reading
 * matter and the run it prepares you for are ONE surface — a look-up five taps inside a
 * running drill is a look-up nobody makes, and a reference page you cannot start from is a
 * page nobody returns to — but the run is what the page is opened for, so nobody scrolls
 * past twenty screens of table to reach it.
 *
 * The corners are the app's: the ✕ out on the left, the run in on the right. The right one
 * REPEATS the `Los` button on purpose — it is the one still in reach from inside the
 * reading.
 */
@Composable
fun OverviewScaffold(
    title: String,
    chrome: Chrome,
    scroll: ScrollState,
    startEnabled: Boolean,
    onClose: () -> Unit,
    onStart: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DlSpace.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrillCloseButton(chrome, onClose)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = DlSpace.s),
            )
            TextButton(onClick = onStart, enabled = startEnabled) {
                Text(chrome.trainerOverviewStart)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(DlSpace.xl),
            verticalArrangement = Arrangement.spacedBy(DlSpace.xl),
            content = content,
        )
    }
}

/** The `Los` under the picks — the same run the corner opens, where the thumb already is. */
@Composable
fun OverviewStartButton(chrome: Chrome, enabled: Boolean, onStart: () -> Unit) {
    Button(
        onClick = onStart,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(chrome.trainerOverviewStart, style = MaterialTheme.typography.titleMedium)
    }
}

/** The recessed tile a group of rows sits in — the settings pattern, on a reading page. */
@Composable
fun OverviewPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .panel()
            .padding(DlSpace.l),
        verticalArrangement = Arrangement.spacedBy(DlSpace.l),
        content = content,
    )
}

/**
 * How a row of picks answers a tap.
 *
 * [One] is a radio — while any offered variant is still locked a run asks ONE thing at a
 * time, and a learner who has just met the clock is asked to climb it rather than dilute
 * it. [Many] is the checkbox a fully open ladder earns. [Locked] is a padlock that states
 * its price: a ladder you can see is a reason to climb, and an absence is not.
 */
enum class RowMark { One, Many, Locked }

/**
 * One pickable row: its mark, its name, and the line under it — what it asks, or what it
 * costs. One TalkBack stop, because the three describe a single thing.
 */
@Composable
fun SelectionRow(
    title: String,
    caption: String?,
    mark: RowMark,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val open = mark != RowMark.Locked
    val tap = when (mark) {
        RowMark.One -> Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick)
        RowMark.Many -> Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() })
        RowMark.Locked -> Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(tap)
            .padding(vertical = DlSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        // why: the control carries no click of its own — the row owns the tap and the
        // role, so TalkBack reads one target instead of two that do the same thing.
        when (mark) {
            RowMark.One -> RadioButton(selected, onClick = null)
            RowMark.Many -> Checkbox(selected, onCheckedChange = null)
            RowMark.Locked -> Text(LOCK, modifier = Modifier.clearAndSetSemantics { })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (open) Dl.colors.textPrimary else Dl.colors.textSecondary,
            )
            caption?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Dl.colors.textSecondary)
            }
        }
    }
}

/**
 * How a run is PLAYED. A switch with a line under it saying what it does — the settings
 * pattern, because a modifier changes the whole run rather than adding to what it asks.
 *
 * A LOCKED one keeps its switch, off and untappable, and swaps the line for its price: a
 * ladder you can see is a reason to climb it, and an absence is not. What that price says
 * is the caller's, since each ladder prices its own Sprosse.
 */
@Composable
fun ModifierSwitchRow(
    title: String,
    caption: String,
    open: Boolean,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            // why: the ROW is the switch — a control beside a label it does not own leaves
            // TalkBack two stops for one thing, and the smaller of them is the tappable one.
            .toggleable(value = on, enabled = open, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (open) title else "$LOCK $title",
                style = MaterialTheme.typography.titleMedium,
                color = if (open) Dl.colors.textPrimary else Dl.colors.textSecondary,
            )
            Text(caption, style = MaterialTheme.typography.bodySmall, color = Dl.colors.textSecondary)
        }
        Switch(
            checked = on,
            onCheckedChange = null,
            enabled = open,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** A prose line under a group of rows — the caption that says why they behave as they do. */
@Composable
fun OverviewNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Dl.colors.textSecondary,
        modifier = modifier.fillMaxWidth(),
    )
}
