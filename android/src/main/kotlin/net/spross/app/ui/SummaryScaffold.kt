package net.spross.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.Chrome

/**
 * The screen a round stops on — the session summary and a drill's pause alike, standing in
 * place of the run's whole screen with only a close button above it: a hero, one title,
 * the round's tally, a milestone line where something new was reached, an optional hint, and
 * the exit pair on the bottom edge. Each fills the slots with words; the layout, the type,
 * the ways out and the celebration are this one's, so the two never drift apart (`docs/design.md` § Counts & sessions).
 *
 * [hero] is handed the height it may grow to — a grown tree's ceiling — and the replay count, which a
 * hero with its own rise replays on; [heroLabel] names what it shows; [milestone] is set off in the
 * accent; [hint] says why stopping is the better call. [cheer] non-null celebrates: it sounds as the
 * screen arrives under confetti, and a tap anywhere but the buttons replays both.
 */
@Composable
fun SummaryScaffold(
    title: String,
    chrome: Chrome,
    onDone: () -> Unit,
    onTalk: (() -> Unit)? = null,
    onPractice: (() -> Unit)? = null,
    tally: String? = null,
    milestone: String? = null,
    hint: String? = null,
    heroLabel: String? = null,
    cheer: (() -> Unit)? = null,
    hero: @Composable ColumnScope.(ceiling: Dp, celebration: Int) -> Unit,
) {
    var celebration by remember { mutableIntStateOf(0) }
    if (cheer != null) LaunchedEffect(Unit) { cheer() }
    // why: a button consumes its own tap, so only the rest of the screen replays.
    val replay = if (cheer == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures {
            celebration++
            cheer()
        }
    }
    Box(Modifier.fillMaxSize().then(replay)) {
        SummaryLayout(title, chrome, onDone, onTalk, onPractice, tally, milestone, hint, heroLabel) { hero(it, celebration) }
        if (cheer != null) Confetti(celebration)
    }
}

@Composable
private fun SummaryLayout(
    title: String,
    chrome: Chrome,
    onDone: () -> Unit,
    onTalk: (() -> Unit)?,
    onPractice: (() -> Unit)?,
    tally: String?,
    milestone: String?,
    hint: String?,
    heroLabel: String?,
    hero: @Composable ColumnScope.(ceiling: Dp) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // why: nothing is running any more, so the run's bar and read-aloud switch stay
        // behind and only the way out stands in the corner it held (iOS `sessionCloseCorner`).
        RunCloseButton(onDone, chrome.commonDone, Modifier.align(Alignment.Start))
        // why: the actions stay on the bottom edge however far the results scroll.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // why: a grown tree's box reaches 45 % of the screen it is given, so the summary
            // fills a tall phone rather than leaving two-thirds of it empty.
            val ceiling = maxHeight * 0.45f
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                hero(ceiling)
                if (heroLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        heroLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                if (tally != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tally,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (milestone != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        milestone,
                        style = MaterialTheme.typography.titleMedium,
                        color = Theme.colors.accent,
                        textAlign = TextAlign.Center,
                    )
                }
                if (hint != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SessionExitButtons(chrome, onDone = onDone, onTalk = onTalk, onPractice = onPractice)
    }
}

/** The emoji a summary stands under where no tree takes the hero slot. */
@Composable
fun SummaryGlyph(glyph: String) {
    // why: purely celebratory; the title carries the message.
    Text(glyph, fontSize = 88.sp, modifier = Modifier.clearAndSetSemantics { }) // card-parity: the summary's own glyph, not a card prompt
}

/**
 * The pair every finished round exits through — the session summary and a drill's pause
 * alike, so the two screens never disagree about which way out is the default one.
 * [onTalk] and [onPractice] are left out where there is nothing to offer.
 */
@Composable
fun SessionExitButtons(
    chrome: Chrome,
    onDone: () -> Unit,
    onTalk: (() -> Unit)?,
    onPractice: (() -> Unit)?,
) {
    // why: stopping takes the full-width primary on the bottom edge, and the two ways
    // of going on share one row above it.
    if (onTalk != null || onPractice != null) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            if (onTalk != null) SecondaryAction(chrome.sessionDoneTalk, SprossIcons.Chat, onTalk)
            if (onPractice != null) SecondaryAction(chrome.sessionDoneKeepPracticing, SprossIcons.DoubleArrow, onPractice)
        }
        Spacer(Modifier.height(8.dp))
    }
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        ButtonIcon(SprossIcons.Check)
        Text(chrome.commonDone)
    }
}

@Composable
private fun RowScope.SecondaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        // why: Material's 24dp sides leave half a row too narrow for a two-word label.
        contentPadding = PaddingValues(horizontal = Theme.spacing.md, vertical = 8.dp),
    ) {
        ButtonIcon(icon)
        Text(label, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun ButtonIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
}
