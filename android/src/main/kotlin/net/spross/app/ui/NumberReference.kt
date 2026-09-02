package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.speakFormOnTap
import net.spross.kern.model.Language
import net.spross.kern.trainer.ReferenceEntry
import net.spross.kern.trainer.Trainer

/**
 * How a language counts, as a table: every band kern names, each row a written value beside
 * the reading the drill grades against.
 *
 * GENERATED, never authored — the readings come out of the very packs the run grades
 * against, so the page cannot claim one reading and mark another. Kern names the bands; the
 * heading each one gets is chrome and is resolved here.
 *
 * One component, two doors: the numbers page stacks it under its own heading, and the
 * in-run "?" raises the very same table, so a look-up mid-drill lands on exactly the page
 * the overview shows.
 *
 * [speak] is how a row is heard, and it is required:
 * a surface that left it off would drop the hint and every row tap without a word.
 * It hands back null for a reading the device cannot say —
 * the readings are generated and no catalog lists them,
 * so what answers is almost always the live voice — and nothing at all where the language has none.
 */
@Composable
fun NumberReferenceTable(
    language: Language,
    chrome: Chrome,
    speak: (String) -> (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // why: empty for a language kern has no pack for — `reference` requires one, and the
    // absence is checked here rather than trusted of every caller.
    val sections = remember(language) {
        if (Trainer.supports(language)) Trainer.reference(language) else emptyList()
    }
    val fontScale = LocalDensity.current.fontScale
    val heard = sections.any { section ->
        section.entries.any { speak(it.reading) != null }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // why: a pair is only drawn where the panel is wide enough to hold it,
        // and only the page itself knows how wide that is.
        val pageWidth = maxWidth
        Column(
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            if (heard) TapToHearHint(chrome)
            for (section in sections) {
                Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
                    // A band this build has no wording for still gets its rows: a new band
                    // must be able to land in kern first.
                    chrome.numberSections[section.key]?.let {
                        Text(
                            it.uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Theme.colors.textSecondary,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    ReferenceBand(
                        entries = section.entries,
                        columns = columnCount(section.entries, fontScale, pageWidth - Theme.spacing.lg * 2),
                        language = language,
                        chrome = chrome,
                        speak = speak,
                    )
                }
            }
        }
    }
}

/**
 * How many columns a band stands in: two for a band of short readings, one for everything else.
 * The counting words are read at a glance,
 * and a page that spends a whole line on "vier" is a page of scrolling.
 *
 * Two tests, because either alone gets it wrong.
 * The characters are counted rather than measured:
 * a pair picked off a measurement is picked narrower than it renders,
 * and "dreizehn" then wraps inside its column.
 * But a count knows nothing of how wide the page is,
 * so a page narrower than [PAIRED_MIN_WIDTH] keeps the single column whatever its readings weigh.
 * Anything past either bound — the accessibility font scales included —
 * stays single-column, where a reading has the whole width to grow into.
 */
internal fun columnCount(entries: List<ReferenceEntry>, fontScale: Float, width: Dp): Int {
    if (fontScale > 1f || width < PAIRED_MIN_WIDTH || entries.size < 6) return 1
    val widest = entries.maxOf { it.value.length + it.reading.length }
    return if (widest <= PAIRED_ROW_CHARS) 2 else 1
}

/** A band's rows, filled column by column, so each column still counts upward. */
@Composable
private fun ReferenceBand(
    entries: List<ReferenceEntry>,
    columns: Int,
    language: Language,
    chrome: Chrome,
    speak: (String) -> (() -> Unit)?,
) {
    val perColumn = (entries.size + columns - 1) / columns
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .panel()
            .padding(Theme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xl),
    ) {
        for (column in 0 until columns) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                for (entry in entries.drop(column * perColumn).take(perColumn)) {
                    ReferenceRow(entry, language, chrome, speak)
                }
            }
        }
    }
}

/**
 * Value and reading on one line. One TalkBack stop — "1 000 → eintausend", not two stops
 * that have to be paired by ear — and the reading wraps rather than truncating, because
 * this is the page a learner reads the language off.
 *
 * The WHOLE row says it, and no glyph stands at the end of one:
 * a table is read by running down the readings,
 * and a speaker that has to be aimed at is a detour per row.
 */
@Composable
private fun ReferenceRow(
    entry: ReferenceEntry,
    language: Language,
    chrome: Chrome,
    speak: (String) -> (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .pronounceOnTap(speak(entry.reading), chrome, minHeight = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Text(
            entry.value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = Theme.colors.textSecondary,
            // why: the numeral is set smaller than the reading, so it sits on the reading's
            // baseline rather than at the row's top, where it reads as a superscript.
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            localizedTarget(entry.reading, language),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.alignByBaseline().weight(1f),
        )
    }
}

/**
 * The whole numbers page, one tap away mid-run — the overview's own table, not a second,
 * smaller truth beside it. It covers the run rather than replacing it, so closing it lands
 * back on the very question that raised it.
 */
@Composable
fun NumberReferenceOverlay(
    model: AppModel,
    language: String,
    chrome: Chrome,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // why: the panel SWALLOWS taps — it covers the run rather than replacing it,
            // and an unconsumed tap would reach the button standing underneath.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { },
            )
            .padding(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        BackHandler { onDismiss() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                chrome.numbersTitle.format(model.languageName(language)),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onDismiss) { Text(chrome.commonDone) }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            NumberReferenceTable(
                language,
                chrome,
                speak = { model.speakFormOnTap(it, language) },
            )
        }
    }
}

/**
 * The widest row a phone fits twice, in characters.
 * Ten rather than twelve because the column is only ~128 dp on the 360 dp class,
 * where a twelve-character row wraps mid-word.
 * `App/Sources/Design/NumberReferenceTable.swift` names the same bound; the two move together.
 */
private const val PAIRED_ROW_CHARS = 10

/**
 * The narrowest PANEL a pair is drawn in, measured inside its padding.
 * A 360 dp phone leaves 296 dp there and a 320 dp one only 256 dp,
 * where a half-width column is under 110 dp and even a ten-character row wraps mid-word.
 * `App/Sources/Design/NumberReferenceTable.swift` holds the same bound; the two move together.
 */
private val PAIRED_MIN_WIDTH = 288.dp
