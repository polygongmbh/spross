package net.spross.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.countLine
import net.spross.app.SessionUi
import net.spross.app.areaEmoji
import net.spross.app.areaTitle
import net.spross.app.continueEndless
import net.spross.app.garden
import net.spross.app.hasBriefing
import net.spross.kern.box.GrowthClaim
import net.spross.kern.box.GrowthHeadline
import net.spross.kern.box.TallyPartKind
import net.spross.kern.design.AreaTree

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
    val summary = ui.summary ?: return
    val parts = summary.parts
    val tally = if (parts.isEmpty()) null else {
        parts.joinToString(" · ") {
            when (it.kind) {
                TallyPartKind.Introduced ->
                    if (parts.size == 1) chrome.sessionDoneTallyNewOnly.format(it.count) else chrome.sessionDoneTallyNew.format(it.count)
                TallyPartKind.Settled -> countLine(chrome.tallySettledOne, chrome.tallySettled, it.count)
                TallyPartKind.Reviewed ->
                    if (parts.size == 1) chrome.sessionDoneTallyReviewedOnly.format(it.count) else chrome.sessionDoneTallyReviewed.format(it.count)
            }
        }
    }
    val headline = summary.headline
    val grown = summary.grownArea?.takeIf { headline != null }
    val area = grown?.after?.area
    SummaryScaffold(
        // why: one title — the growth claim where a tree stands over it, the plain
        // "All done!" where the popper does.
        title = if (headline != null && area != null) growthLine(chrome, headline) else chrome.sessionDoneTitle,
        chrome = chrome,
        onDone = { model.finishSession() },
        // why: talking asks rather than instructs — the words are warm, the one moment a
        // conversation costs nothing to offer; practicing on stands only while a refill
        // would not come back dry.
        onTalk = if (model.hasBriefing) ({ briefingOpen = true }) else null,
        onPractice = if (ui.canPracticeMore) ({ model.continueEndless() }) else null,
        // why: the tally counts the whole round, not the area, so it stands apart from the label.
        tally = tally ?: chrome.sessionDoneTallyAllDone.takeIf { area == null },
        // why: a day the box itself is telling the learner to stop makes no growth claim —
        // a screen that celebrates and is contradicted two lines down teaches the learner
        // not to believe it.
        hint = chrome.sessionDoneRestHint.takeIf { summary.restSuggested },
    ) { treeCeiling ->
        // why: the tree takes the hero slot when the round grew an area — a party popper
        // is the same picture whatever the learner did, and two celebratory graphics on
        // one screen is one too many.
        if (grown != null && area != null) {
            GrowingTree(grown, model.garden, AreaTree.heroHeight(grown.after, treeCeiling.value.toDouble()).dp)
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
            SummaryGlyph("🎉")
        }
    }
    if (briefingOpen) BriefingSheet(model) { briefingOpen = false }
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
        GrowthClaim.Settled -> chrome.growthBlooming.pick()
        GrowthClaim.Met -> chrome.growthSown.pick()
        GrowthClaim.Grew -> chrome.growthGrown.pick()
        GrowthClaim.Held -> chrome.growthGrown.drop(1).pick()
    }
}
