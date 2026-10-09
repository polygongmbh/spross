package net.spross.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import net.spross.app.AppModel
import net.spross.kern.box.StreakHealth
import net.spross.kern.box.chromePart
import net.spross.kern.box.dayPart
import net.spross.kern.catalog.LanguageChoices
import net.spross.kern.trainer.DayLead

/**
 * The north star screen: one glance = what to do right now.
 *
 * Top to bottom: the date and the day's name, ONE state card, the listening card, the
 * trainers, the companion card, the fortnight behind it, and the trees. Which state card is kern's
 * [DayLead] under a load failure ([homeCard]); the drill Home names stands only inside one.
 */
@Composable
fun HomeScreen(model: AppModel) {
    val chrome = model.chrome
    val stats = model.stats
    // The run's grade travels with its count: the card's flame and the strip's read one
    // answer, so they can never show two different states of the same day.
    val health = stats?.streakHealth ?: StreakHealth.NoRun
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
    val standing = model.homeStanding

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Theme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xl),
        ) {
            PageTitle(hello, eyebrow = today)

            val pick = rememberSuggestedDrill(model, standing)
            val lead = standing?.lead(pick) ?: DayLead.Done
            val card = homeCard(failed = model.loadFailure != null, lead = lead)
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

                HomeCard.Drill -> if (standing != null && pick != null) {
                    DrillLeadCard(model, standing, pick, stats?.streak ?: 0, health)
                }

                HomeCard.Done -> standing?.let {
                    DoneCard(model, it, stats?.streak ?: 0, health)
                }
            }

            ListenCard(model)

            TrainerHubCard(model)

            TalkCard(model) { briefingOpen = true }

            // The same fortnight the streak was counted from, on the very refresh that
            // produced it — the strip reads kern's walk, never one of its own. It names
            // itself, so nothing announces it a second time above.
            ActivityStrip(model.activityWindow, stats?.streak ?: 0, health, chrome, locale)

            HomeTrees(model)
            Spacer(Modifier.height(Theme.spacing.lg))
        }
        // why: a celebrated scramble close rains over the whole screen — the hub card wearing
        // its tile cannot hold it.
        ClosedRunConfetti(model.trainer)
    }
    if (briefingOpen) BriefingSheet(model) { briefingOpen = false }
}

/** "Freitag, 8. August" in the chrome's language — the caption over the day's name. */
private fun todayLine(locale: Locale): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
    return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, locale)).uppercase(locale)
}
