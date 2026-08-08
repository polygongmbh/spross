package net.spross.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import net.spross.app.AppModel
import net.spross.app.letterDrillAvailable
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
                Text(chrome.heuteTitle, style = MaterialTheme.typography.headlineLarge)
            }
            // The box itself: every word the profile holds, packed or not. The icon
            // carries the name rather than a label — nothing else on this row does.
            TextButton(
                onClick = { model.openBox() },
                modifier = Modifier.semantics { contentDescription = chrome.boxNav },
            ) { Text("📦", style = MaterialTheme.typography.headlineSmall) }
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
                SessionCard(model, it, stats?.streak ?: 0)
            }

            HeuteCard.Done -> standing?.let {
                DoneCard(model, it, stats?.streak ?: 0)
            }

            HeuteCard.EmptyBox -> StateCard(
                emoji = "📦",
                title = chrome.emptyBoxTitle,
                message = chrome.emptyBoxMessage,
                action = Pair(chrome.emptyBoxAction, { model.openBox() }),
            )
        }

        // The platform's first trainer. It appears by itself once the synthesizer has
        // bound (the predicate is observable — a cold start answers "no voice" for a
        // moment), and stays put while reading aloud is switched off: the drill says so
        // and offers the one tap that undoes it, which hiding the chip would not.
        if (model.letterDrillAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(DlSpace.l),
                    verticalArrangement = Arrangement.spacedBy(DlSpace.m),
                ) {
                    Text(chrome.trainingTitle, style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = { model.startLetterDrill() },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("🔤 ${chrome.lettersTitle}")
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(DlSpace.m)) {
            Text(chrome.progressTitle, style = MaterialTheme.typography.titleLarge)
            // Fortschritt: the same fortnight the streak was counted from, on the very
            // refresh that produced it — the strip reads kern's walk, never one of its own.
            ActivityStrip(model.activityWindow, stats?.streak ?: 0, chrome, locale)
        }
        Spacer(Modifier.height(DlSpace.l))
    }
}

/** "Freitag, 8. August" in the chrome's language — the caption over the day's name. */
private fun todayLine(locale: Locale): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
    return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, locale)).uppercase(locale)
}
