package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.SessionUi
import net.spross.app.areaEmoji
import net.spross.app.areaTitle
import net.spross.app.continueEndless
import net.spross.app.hasBriefing
import net.spross.kern.box.GrowthClaim
import net.spross.kern.box.GrowthHeadline
import net.spross.kern.box.TallyPartKind
import net.spross.kern.box.completionTallyParts

/**
 * What the round bought, in kern's own order and only where there is something to name —
 * a round that started nothing says so plainly instead of printing three zeros.
 */
@Composable
fun SessionSummary(model: AppModel, ui: SessionUi) {
    val chrome = model.chrome
    // why: the round's own reward, sounded once as the screen arrives — iOS cheers here too.
    LaunchedEffect(Unit) { model.cues.cheer() }
    var briefingOpen by remember { mutableStateOf(false) }
    val parts = completionTallyParts(ui.introduced, ui.strengthened, ui.reviewed)
    val tally = if (parts.isEmpty()) null else {
        parts.joinToString(" · ") {
            when (it.kind) {
                TallyPartKind.Introduced ->
                    if (parts.size == 1) chrome.sessionDoneTallyNewOnly.format(it.count) else chrome.sessionDoneTallyNew.format(it.count)
                TallyPartKind.Consolidated -> chrome.sessionDoneTallyConsolidated.format(it.count)
                TallyPartKind.Reviews -> chrome.sessionDoneTallyReviewed.format(it.count)
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // why: the actions stay on the bottom edge however far the results scroll.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // why: a grown tree's box reaches 45 % of the screen it is given, so the summary
            // fills a tall phone rather than leaving two-thirds of it empty.
            val treeCeiling = maxHeight * 0.45f
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val headline = ui.headline
                val grown = ui.grownArea?.takeIf { headline != null }
                // why: the tree takes the hero slot when the round grew an area — a party popper
                // is the same picture whatever the learner did, and two celebratory graphics on
                // one screen is one too many.
                val area = grown?.after?.area
                if (grown != null && area != null) {
                    GrowingTree(grown, ForestLayout.heroHeight(grown.after, treeCeiling.value).dp)
                    // why: the area is LABELED under its tree rather than named in the claim —
                    // what grew is what the learner can say, never the area itself.
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${model.areaEmoji(area)} ${model.areaTitle(area)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text("🎉", fontSize = 88.sp) // card-parity: the done screen's own glyph, not a card prompt
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (headline != null && area != null) growthLine(chrome, headline) else chrome.sessionDoneTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                // why: the tally counts the whole round, not the area, so it stands apart from the label.
                (tally ?: chrome.sessionDoneTallyAllDone.takeIf { area == null })?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (ui.restSuggested) {
                    // why: a day the box itself is telling the learner to stop makes no growth
                    // claim — a screen that celebrates and is contradicted two lines down
                    // teaches the learner not to believe it.
                    Spacer(Modifier.height(16.dp))
                    Text(chrome.sessionDoneRestHint, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // why: talking asks rather than instructs — the words are warm, the one moment a
        // conversation costs nothing to offer; practicing on stands only while a refill
        // would not come back dry.
        SessionExitButtons(
            chrome,
            onDone = { model.finishSession() },
            onTalk = if (model.hasBriefing) ({ briefingOpen = true }) else null,
            onPractice = if (ui.canPracticeMore) ({ model.continueEndless() }) else null,
        )
    }
    if (briefingOpen) BriefingSheet(model) { briefingOpen = false }
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
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
        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).pressSpring(),
        shape = MaterialTheme.shapes.small,
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

/**
 * Kern's claim ([net.spross.kern.box.growthHeadline]) in this table's words.
 * The first grown line says the words grew, so a round that added none reads only the others.
 */
internal fun growthLine(chrome: Chrome, headline: GrowthHeadline): String {
    fun List<String>.pick() = this[headline.pick % size]
    return when (headline.claim) {
        GrowthClaim.Unclaimed -> chrome.sessionDoneGrowthGrew
        GrowthClaim.Opened -> chrome.sessionDoneGrowthOpened
        GrowthClaim.Matured -> chrome.growthBlooming.pick()
        GrowthClaim.Met -> chrome.growthSown.pick()
        GrowthClaim.Grew -> chrome.growthGrown.pick()
        GrowthClaim.Held -> chrome.growthGrown.drop(1).pick()
    }
}
