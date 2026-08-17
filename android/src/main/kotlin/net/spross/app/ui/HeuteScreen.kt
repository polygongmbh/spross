package net.spross.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import net.spross.app.AppModel
import net.spross.app.countriesOffered
import net.spross.app.lettersOffered
import net.spross.app.numbersOffered
import net.spross.app.werkstattOffered
import net.spross.kern.box.DayPart
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.dayPart
import net.spross.kern.box.partVariant
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.session.SessionOfferKind

/**
 * The north star screen: one glance = what to do right now.
 *
 * Top to bottom: the date and the day's name, ONE state card, the trainers, and the
 * fortnight behind it. Which state card is a strict precedence over the box's own answers
 * ([heuteCard]) — an offer outranks a done state, and a box nothing has been packed into
 * is not "caught up", it has never been opened.
 */
@Composable
fun HeuteScreen(model: AppModel) {
    val chrome = model.chrome
    val stats = model.stats
    // The run's grade travels with its count: the card's flame and the strip's read one
    // answer, so they can never show two different states of the same day.
    val health = stats?.streakHealth ?: StreakHealth.None
    val box = model.box
    val source = box?.joinStamp?.source ?: "en"
    val locale = remember(source) {
        Locale.forLanguageTag(LanguageChoices.chromeLanguage(source))
    }
    // why: each of these is a walk over the box, and they are read together so the card,
    // its tally and its fine print describe one moment rather than three a frame apart.
    val standing = remember(box) {
        box?.let { HeuteStanding.of(it, System.currentTimeMillis(), TimeZone.getDefault().id) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DlSpace.xl),
        verticalArrangement = Arrangement.spacedBy(DlSpace.xl),
    ) {
        Spacer(Modifier.height(DlSpace.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    todayLine(locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A greeting is a phrase, not a headline word: it shrinks a step rather
                // than pushing the day's card down a third line.
                Text(
                    greeting(model),
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 20.sp,
                        maxFontSize = MaterialTheme.typography.headlineLarge.fontSize,
                    ),
                )
            }
            // The way out of Heute: the box holds every word the profile has, packed
            // or not. Named rather than a bare glyph — an unlabelled emoji does not
            // read as a control — and tonal on the clay wash, one step under the
            // day's own call to action inside the card.
            FilledTonalButton(
                onClick = { model.openBox() },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = chrome.boxNav }
                    .pressSpring(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = DlSpace.l, vertical = DlSpace.s),
            ) {
                Text("📦 ${chrome.boxNav}")
            }
        }

        // Android surfaces no load failure of its own yet (the model has no such state),
        // so the failure branch is unreachable here — the chrome for it stands ready.
        val card = heuteCard(
            failed = false,
            offerKind = standing?.offer?.kind ?: SessionOfferKind.Nothing,
            activeCards = stats?.activeCount ?: 0,
        )
        when (card) {
            HeuteCard.Failure -> StateCard(
                emoji = "🫤",
                title = chrome.errorTitle,
                message = chrome.errorCatalogMissing,
            )

            HeuteCard.Session -> standing?.let {
                SessionCard(model, it, stats?.streak ?: 0, health)
            }

            HeuteCard.Done -> standing?.let {
                DoneCard(model, it, stats?.streak ?: 0, health)
            }

            HeuteCard.EmptyBox -> StateCard(
                emoji = "📦",
                title = chrome.emptyBoxTitle,
                message = chrome.emptyBoxMessage,
                action = Pair(chrome.emptyBoxAction, { model.openBox() }),
            )
        }

        WerkstattCard(model)

        // The same fortnight the streak was counted from, on the very refresh that
        // produced it — the strip reads kern's walk, never one of its own. It names
        // itself, so nothing announces it a second time above.
        ActivityStrip(model.activityWindow, stats?.streak ?: 0, health, chrome, locale)
        Spacer(Modifier.height(DlSpace.l))
    }
}

/**
 * Sprossen: free practice, with no schedule and no limit.
 *
 * THREE entries on ONE row, and each opens a PAGE rather than a run — the reading and the
 * drill it prepares you for are one surface. Each is its own SKILL, which is the only thing
 * that earns a chip. Zahlen stands where the pair has counting content; Buchstaben on the
 * alphabet FILE alone, because the table ships even where this device can sound nothing;
 * Länder on the joined atlas. A card with none of the three is absent rather than empty.
 */
@Composable
private fun WerkstattCard(model: AppModel) {
    val chrome = model.chrome
    if (!model.werkstattOffered) return
    Column(
        modifier = Modifier.fillMaxWidth().panel(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(DlSpace.l),
            verticalArrangement = Arrangement.spacedBy(DlSpace.m),
        ) {
            Text(chrome.trainingTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                chrome.trainingSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DlSpace.m)) {
                if (model.numbersOffered) {
                    EntryChip("🔢", chrome.numbersTitle) { model.openNumbers() }
                }
                if (model.lettersOffered) {
                    EntryChip("🔤", chrome.lettersTitle) { model.openLetters() }
                }
                if (model.countriesOffered) {
                    EntryChip("🌍", chrome.countriesTitle) { model.openCountries() }
                }
            }
        }
    }
}

/**
 * One Werkstatt entry: the glyph large on top, the name at full caption size under it —
 * the iOS chip's face, stacked so three names share the row without shrinking to fit
 * beside their glyphs. The label still steps down rather than wrapping, but only where
 * a name alone outgrows a third of the screen.
 */
@Composable
private fun RowScope.EntryChip(emoji: String, title: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            // why: the spring sits OUTSIDE the fill — a press must shrink the tile,
            // not just the label inside it.
            .pressSpring()
            .clip(MaterialTheme.shapes.medium)
            .background(Dl.colors.surfaceTint)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = DlSpace.xs, vertical = DlSpace.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DlSpace.s, Alignment.CenterVertically),
    ) {
        // why: the name is the label — TalkBack reading "Zahlen", not "Eingabesymbol Zahlen".
        Text(emoji, fontSize = 30.sp, modifier = Modifier.clearAndSetSemantics { })
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 9.sp,
                maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
            ),
        )
    }
}

/**
 * "Nächtliche Suaheli-Einheit" — the language the box is growing, in words that fit the
 * hour. Which stretch of the day it is and which of its phrasings this one takes are kern's
 * ([dayPart], [partVariant]); the words are ours. The screen's own name stands in only
 * while no profile names a language.
 */
private fun greeting(model: AppModel): String {
    val target = model.box?.joinStamp?.target ?: return model.chrome.heuteTitle
    val now = System.currentTimeMillis()
    val tz = TimeZone.getDefault().id
    val lines = when (dayPart(now, tz)) {
        DayPart.Morning -> model.chrome.greetMorning
        DayPart.Day -> model.chrome.greetDay
        DayPart.Evening -> model.chrome.greetEvening
        DayPart.Night -> model.chrome.greetNight
    }
    return lines[partVariant(now, tz, lines.size)].format(model.languageName(target))
}

/** "Freitag, 8. August" in the chrome's language — the caption over the day's name. */
private fun todayLine(locale: Locale): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
    return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, locale)).uppercase(locale)
}
