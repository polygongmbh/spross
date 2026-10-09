package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.Chrome
import net.spross.kern.catalog.OnboardingTourStop

/**
 * The frame every onboarding page stands in: one scrolling column, one rhythm.
 *
 * The two pages built on it here ([TourPage], [FirstRoundPage]) take chrome and callbacks
 * and never the model, so [OnboardingScreen] stays the one place the flow is decided.
 * The scroll is the reachability floor for a large font scale, never a step of the flow.
 */
@Composable
fun OnboardingStoryPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Theme.spacing.xl, vertical = Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xl),
        content = content,
    )
}

/**
 * A page's opening: the mark, and the title under it.
 *
 * Centered, because these pages are read rather than operated;
 * the mark says nothing a screen reader could pass on, so it is skipped rather than read out.
 */
@Composable
fun OnboardingHero(mark: String, title: String, trailing: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
    ) {
        // why: [trailing] gets the space beside the mark and wraps inside it,
        // so a long label never covers the centered mark.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(
                mark,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailing() }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * The one button a page ends on.
 *
 * [busy] belongs to the page that commits: the label becomes a spinner and the button stops
 * taking taps, so the wait for the box is answered where the tap landed.
 */
@Composable
fun OnboardingPrimary(
    label: String,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(label)
        }
    }
}

/** The way back to the page before, quieter than the way on and centered under it. */
@Composable
private fun ColumnScope.OnboardingBack(label: String, onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(label)
    }
}

/**
 * One stop of the tour: its glyph, the name its own screen carries, and what the learner does there.
 * The pair reads as one item to a screen reader.
 */
@Composable
fun TourStopRow(emoji: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Where things are, before the first round:
 * the day's round on Home, the box where the learner picks what comes next, and the ways to practice beside it.
 * Each stop is named by its own screen's chrome, so the learner recognizes it on arrival.
 */
@Composable
fun TourPage(chrome: Chrome, emoji: String, onNext: () -> Unit, onBack: () -> Unit) {
    OnboardingStoryPage {
        OnboardingHero(emoji, chrome.onboardingTourTitle)
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
            OnboardingTourStop.entries.forEach { stop ->
                val (title, body) = when (stop) {
                    OnboardingTourStop.Home -> chrome.homeName to chrome.onboardingTourHome
                    OnboardingTourStop.Box -> chrome.boxName to chrome.onboardingTourBox
                    OnboardingTourStop.Drills -> chrome.trainerHubTitle to chrome.onboardingTourDrills
                    OnboardingTourStop.Listening -> chrome.listenTitle to chrome.onboardingTourListening
                }
                TourStopRow(stop.emoji, title, body)
            }
        }
        OnboardingPrimary(chrome.commonNext, onClick = onNext)
        OnboardingBack(chrome.commonBack, onBack)
    }
}

/**
 * What a round asks of you, before the first one runs.
 *
 * Three lines, in the order the learner will meet them: recognize, grade, write.
 * Nothing about scheduling — the one job here is that a blank card is not a test you can fail.
 * The session then coaches the same three at the moment each applies (`SessionCoach`),
 * which is why these stay short enough to be read once and left.
 */
@Composable
fun FirstRoundPage(chrome: Chrome, emoji: String, busy: Boolean, onStart: () -> Unit, onBack: (() -> Unit)?) {
    OnboardingStoryPage {
        OnboardingHero(emoji, chrome.onboardingFirstRoundTitle)
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
            Text(chrome.onboardingFirstRoundRecognize, style = MaterialTheme.typography.bodyLarge)
            Text(chrome.onboardingFirstRoundGrade, style = MaterialTheme.typography.bodyLarge)
            Text(chrome.onboardingFirstRoundWrite, style = MaterialTheme.typography.bodyLarge)
        }
        OnboardingPrimary(chrome.onboardingStart, busy = busy, onClick = onStart)
        // why: no way back while the box is being joined — the join leaves this screen itself.
        onBack?.let { OnboardingBack(chrome.commonBack, it) }
    }
}
