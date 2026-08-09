package net.spross.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.formPronunciation
import net.spross.app.letterName
import net.spross.app.speakOnTap
import net.spross.kern.catalog.AlphabetEntry
import net.spross.kern.catalog.AlphabetKind
import net.spross.kern.model.Language

/**
 * The alphabet sheet, one card per row of `catalog/alphabet/<lang>.json`: the glyph with
 * its capital, the letter's own name, its IPA, when it takes that value, what it sounds
 * like, and an example word — the name and the word each with a speaker beside them.
 *
 * Reading matter, not a drill: nothing here is graded, and nothing plays unasked. Every tap
 * is a request, so it sounds even while reading aloud is switched off — nobody opens a
 * reference sheet by accident.
 *
 * Rows are whatever the file holds, in authored order, and where the file declares sections
 * they head their runs of rows. Teaching aids follow the READER, with one fallback rule for
 * both maps: the source language, else English.
 */
@Composable
fun AlphabetSection(model: AppModel, language: Language, chrome: Chrome) {
    val alphabet = model.catalog?.alphabet(language) ?: return
    val reader = model.box?.joinStamp?.source ?: FALLBACK_READER
    OverviewHeading(chrome.alphabetTitle)
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.m)) {
        if (alphabet.sections.isEmpty()) {
            for (entry in alphabet.entries) AlphabetRow(model, entry, language, reader, chrome)
            return@Column
        }
        for (section in alphabet.sections) {
            section.titles.reader(reader)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Dl.colors.textSecondary,
                    modifier = Modifier.padding(top = DlSpace.s).semantics { heading() },
                )
            }
            for (entry in alphabet.entries(section.id)) {
                AlphabetRow(model, entry, language, reader, chrome)
            }
        }
    }
}

/**
 * One row, one TalkBack stop, read in the order it stands — glyph, name, context, hint,
 * example. Thirty-five separate elements to swipe through is not a reference sheet, so the
 * two speakers inside it are row ACTIONS rather than targets to hunt for inside the label.
 */
@Composable
private fun AlphabetRow(
    model: AppModel,
    entry: AlphabetEntry,
    language: Language,
    reader: Language,
    chrome: Chrome,
) {
    val speakName = model.speakOnTap(
        entry.name?.let { model.letterName(it, entry.glyph.lowercase(), language) },
    )
    val example = model.catalog?.alphabetExample(entry, language)
    val exampleText = example?.text ?: entry.exampleText
    val speakExample = exampleText?.let { model.speakOnTap(model.formPronunciation(it, language)) }
    val actions = listOfNotNull(
        speakName?.let { CustomAccessibilityAction(chrome.alphabetSpeakName) { it(); true } },
        speakExample?.let { CustomAccessibilityAction(chrome.alphabetSpeakExample) { it(); true } },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .panel()
            .padding(DlSpace.l)
            .semantics(mergeDescendants = true) {
                if (actions.isNotEmpty()) customActions = actions
            },
        verticalArrangement = Arrangement.spacedBy(DlSpace.s),
    ) {
        AlphabetHeader(entry, language, speakName)
        entry.context.reader(reader)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Dl.colors.textSecondary)
        }
        entry.hints.reader(reader)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        if (exampleText != null) {
            AlphabetExample(
                emoji = example?.emoji,
                text = exampleText,
                meaning = example?.let { model.catalog?.exampleMeaning(it.slug, reader) },
                language = language,
                speak = speakExample,
            )
        }
    }
}

/** Glyph and capital large, the name beside it, the IPA trailing. */
@Composable
private fun AlphabetHeader(entry: AlphabetEntry, language: Language, speak: (() -> Unit)?) {
    // A rule row's "glyph" is a list of them and prose besides — it takes the heading size,
    // not the display size a single grapheme is set in.
    val glyphs = entry.upper?.let { "$it ${entry.glyph}" } ?: entry.glyph
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Text(
            localizedTarget(glyphs, language),
            fontSize = if (entry.kind == AlphabetKind.Rule) 20.sp else 30.sp,
            fontWeight = FontWeight.Bold,
        )
        displayName(entry)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Dl.colors.textSecondary)
        }
        if (speak != null) SpeakerButton(speak)
        Spacer(Modifier.weight(1f))
        entry.ipa?.let {
            // why: phonetic symbols read out by a chrome-language voice are noise — the
            // hint under the row says the same thing in words.
            Text(
                "[$it]",
                style = MaterialTheme.typography.bodySmall,
                color = Dl.colors.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * The example word, in the fallback chain the schema promises: the alphabet language's OWN
 * realization of the slug — with the concept's emoji, and its meaning where the reader's
 * language realizes it too — else the verbatim `exampleText`.
 */
@Composable
private fun AlphabetExample(
    emoji: String?,
    text: String,
    meaning: String?,
    language: Language,
    speak: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DlSpace.s),
    ) {
        emoji?.let { Text(it, modifier = Modifier.clearAndSetSemantics { }) }
        Text(localizedTarget(text, language), style = MaterialTheme.typography.titleMedium)
        meaning?.let {
            Text("· $it", style = MaterialTheme.typography.bodyMedium, color = Dl.colors.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        if (speak != null) SpeakerButton(speak)
    }
}

/**
 * Hidden from TalkBack on purpose: the row is one element, and hearing it is offered as a
 * row action instead. The glyph is what makes the tap findable for everyone else.
 */
@Composable
private fun SpeakerButton(speak: () -> Unit) {
    Icon(
        SprossIcons.Speaker,
        contentDescription = null,
        tint = Dl.colors.teal,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = speak)
            .padding(DlSpace.m)
            .clearAndSetSemantics { },
    )
}

/**
 * The name, unless the glyph column already says it: German Ü is NAMED "Ü" and Ukrainian а
 * is named «а», so printing both makes the row stutter. Hiding it costs nothing — a
 * letter's name is there to be HEARD, and the speaker stays either way.
 */
private fun displayName(entry: AlphabetEntry): String? {
    val name = entry.name ?: return null
    val shown = listOf(entry.glyph.lowercase(), entry.upper.orEmpty().lowercase())
    return if (name.lowercase() in shown) null else name
}

/** A teaching aid in the reader's language, else English — one rule for every such map. */
private fun Map<Language, String>.reader(source: Language): String? = this[source] ?: this[FALLBACK_READER]

private const val FALLBACK_READER = "en"
