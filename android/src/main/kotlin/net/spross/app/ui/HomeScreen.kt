package net.spross.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import net.spross.app.AppModel
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.chromePart
import net.spross.kern.box.dayPart
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.session.SessionOfferKind

/**
 * The north star screen: one glance = what to do right now.
 *
 * Top to bottom: the date and the day's name, ONE state card, the listening card, the
 * trainers, the companion card, and the fortnight behind it. Which state card is a strict
 * precedence over the box's own answers ([homeCard]) — an offer outranks a done state.
 */
@Composable
fun HomeScreen(model: AppModel) {
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
    // why: an ICU pattern built and a formatter compiled, for a date that moves once
    // a day — it stood in the body, so a button's press animation rebuilt it once a
    // frame for the whole spring.
    val today = remember(locale, LocalDate.now()) { todayLine(locale) }
    // why: the greeting's words follow the stretch of the day, and which of the
    // candidates it takes is keyed on exactly that (kern's `partVariant`) — so it is
    // rebuilt when the stretch turns, and never between two frames of the same one.
    val greetingTarget = box?.joinStamp?.target
    val greetingNow = System.currentTimeMillis()
    val greetingZone = TimeZone.getDefault().id
    val hello = remember(
        model.chrome, greetingTarget,
        chromePart(greetingNow, greetingZone),
        dayPart(greetingNow, greetingZone, greetingTarget),
    ) { greetingTarget?.let { greeting(model, it) } }
    var briefingOpen by remember { mutableStateOf(false) }
    val standing = remember(box, model.canPracticeExtra) {
        box?.let {
            HomeStanding.of(it, System.currentTimeMillis(), TimeZone.getDefault().id,
                             model.canPracticeExtra)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Theme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xl),
    ) {
        Spacer(Modifier.height(Theme.spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    today,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A greeting is a phrase, not a headline word: it shrinks a step rather
                // than pushing the day's card down a third line.
                if (hello != null) Text(
                    hello,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 20.sp,
                        maxFontSize = MaterialTheme.typography.headlineLarge.fontSize,
                    ),
                )
            }
            // The way out of Home: the box holds every word the profile has, packed
            // or not. Named rather than a bare glyph — an unlabelled emoji does not
            // read as a control — and tonal on the clay wash, one step under the
            // day's own call to action inside the card.
            FilledTonalButton(
                onClick = { model.openBox() },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = chrome.boxDoor }
                    .pressSpring(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = Theme.spacing.lg, vertical = Theme.spacing.sm),
            ) {
                Text("📦 ${chrome.boxDoor}")
            }
        }

        val card = homeCard(
            failed = model.loadFailure != null,
            offerKind = standing?.offer?.kind ?: SessionOfferKind.Nothing,
        )
        when (card) {
            HomeCard.Failure -> StateCard(
                emoji = "🫤",
                title = chrome.errorTitle,
                // The catalog is present — the box is what could not be read, so the card
                // names the reason the decode gave rather than a missing content pack.
                message = model.loadFailure
                    ?.let { chrome.errorContentUnavailable.format(it) }
                    ?: chrome.errorCatalogMissing,
            )

            HomeCard.Session -> standing?.let {
                SessionCard(model, it, stats?.streak ?: 0, health)
            }

            HomeCard.Done -> standing?.let {
                DoneCard(model, it, stats?.streak ?: 0, health)
            }
        }

        ListenCard(model)

        MeadowCard(model)

        TalkCard(model) { briefingOpen = true }

        // The same fortnight the streak was counted from, on the very refresh that
        // produced it — the strip reads kern's walk, never one of its own. It names
        // itself, so nothing announces it a second time above.
        ActivityStrip(model.activityWindow, stats?.streak ?: 0, health, chrome, locale)
        Spacer(Modifier.height(Theme.spacing.lg))
    }
    if (briefingOpen) BriefingSheet(model) { briefingOpen = false }
}

/** "Freitag, 8. August" in the chrome's language — the caption over the day's name. */
private fun todayLine(locale: Locale): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
    return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, locale)).uppercase(locale)
}
