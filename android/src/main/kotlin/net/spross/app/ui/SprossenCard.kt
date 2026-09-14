package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.countriesOffered
import net.spross.app.datesOffered
import net.spross.app.lettersOffered
import net.spross.app.numbersOffered
import net.spross.app.werkstattOffered

/**
 * One entry on the Sprossen card: its face, its name and what it opens.
 *
 * A VALUE per chip rather than a composable apiece, because the card has to COUNT its
 * entries before it can lay them out — an `if` inside a row gives the wrap nothing to count.
 */
data class HubChip(val emoji: String, val title: String, val open: () -> Unit)

/**
 * Sprossen: free practice, with no schedule and no limit.
 *
 * The entries stand on ONE row while there are no more than three of them and break into
 * two lines past that ([chipRows]). Each opens a PAGE rather than a run — the reading and
 * the drill it prepares you for are one surface. Each is its own SKILL, which is the only
 * thing that earns a chip; what each one gates on is `DrillAvailability`. A card with no
 * entry at all is absent rather than empty (`docs/drills.md`).
 */
@Composable
fun SprossenCard(model: AppModel) {
    val chrome = model.chrome
    // why: what the card offers is worked out once per BOX rather than once per frame — a
    // chip's press spring recomposes this card for the whole of its spring.
    val chips = remember(model.box, chrome) { model.hubChips(chrome) }
    if (chips.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().panel(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            Text(chrome.trainerHubTitle, style = MaterialTheme.typography.titleLarge)
            Text(
                chrome.trainerHubSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // why: the chips name an exercise and nothing else — spoken, "Numbers" could
            // be an area of the box. The suffix says it is practice, and in which language.
            val practice =
                chrome.a11ySuffixPractice.format(model.languageName(model.box?.joinStamp?.target.orEmpty()))
            Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                chipRows(chips).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                        row.forEach { EntryChip(it, practice) }
                    }
                }
            }
        }
    }
}

/**
 * The chips cut into lines. Three or fewer stand on one; past that the card breaks into
 * TWO, `ceil(n/2)` above and `floor(n/2)` below — 4 stand 2+2, 5 stand 3+2, 6 stand 3+3 —
 * and each line keeps the equal-width chips one line carries on its own.
 *
 * The break is DRAWN rather than discovered: a [Row] overflows rather than wrapping, and six
 * chips sharing one would be six slivers of a word apiece.
 */
fun chipRows(chips: List<HubChip>): List<List<HubChip>> = when {
    chips.isEmpty() -> emptyList()
    chips.size <= 3 -> listOf(chips)
    else -> {
        // The odd chip goes on TOP, so the card narrows as it is read rather than widening.
        val top = (chips.size + 1) / 2
        listOf(chips.take(top), chips.drop(top))
    }
}

/** Every entry this profile can reach, in the order the card offers them. */
private fun AppModel.hubChips(chrome: Chrome): List<HubChip> {
    if (!werkstattOffered) return emptyList()
    val chips = mutableListOf<HubChip>()
    if (numbersOffered) chips += HubChip("🔢", chrome.trainerSkillNumbers) { openNumbers() }
    if (lettersOffered) chips += HubChip("🔤", chrome.trainerSkillLetters) { openLetters() }
    if (countriesOffered) chips += HubChip("🌍", chrome.trainerSkillCountries) { openCountries() }
    if (datesOffered) chips += HubChip("📅", chrome.trainerSkillDates) { openDates() }
    return chips
}

/**
 * One entry of the Sprossen card: the glyph large on top, the name at full caption size
 * under it — the iOS chip's face, stacked so three names share the row without shrinking
 * to fit beside their glyphs. The label still steps down rather than wrapping, but only
 * where a name alone outgrows a third of the screen.
 *
 * [suffix] finishes the spoken name.
 */
@Composable
private fun RowScope.EntryChip(chip: HubChip, suffix: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            // why: the spring sits OUTSIDE the fill — a press must shrink the tile,
            // not just the label inside it.
            .pressSpring()
            .clip(MaterialTheme.shapes.medium)
            .background(Theme.colors.surfaceTint)
            .clickable(role = Role.Button, onClick = chip.open)
            .semantics(mergeDescendants = true) { contentDescription = chip.title + suffix }
            .heightIn(min = Theme.reserve.tile)
            .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm, Alignment.CenterVertically),
    ) {
        // why: the name is the label — TalkBack reading "Numbers", not "input symbol Numbers".
        Text(chip.emoji, fontSize = 30.sp, modifier = Modifier.clearAndSetSemantics { })
        Text(
            chip.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 9.sp,
                maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
            ),
        )
    }
}
