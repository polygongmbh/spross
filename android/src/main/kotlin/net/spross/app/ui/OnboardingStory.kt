package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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

/**
 * The frame every onboarding page stands in: one scrolling column, one rhythm.
 *
 * The two pages built on it here ([PrinciplesPage], [FirstRoundPage]) take chrome and callbacks
 * and never the model, so [OnboardingScreen] stays the one place the flow is decided.
 * The scroll is the reachability floor for a large font scale, never a step of the flow.
 */
@Composable
fun OnboardingStoryPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = DlSpace.xl, vertical = DlSpace.l),
        verticalArrangement = Arrangement.spacedBy(DlSpace.xl),
        content = content,
    )
}

/**
 * A page's opening: the mark, the title,
 * and — where the page asks something — the line under it.
 *
 * Centered, because these pages are read rather than operated;
 * the mark says nothing a screen reader could pass on, so it is skipped rather than read out.
 */
@Composable
fun OnboardingHero(mark: String, title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DlSpace.l),
    ) {
        Text(
            mark,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
        modifier = Modifier.fillMaxWidth().pressSpring(),
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
 * One thing Spross believes, in a title and the sentences that make it concrete.
 *
 * Both lines carry the page's own ink — the body is a principle, not a caption,
 * so it is not muted the way a hint under a control would be.
 * The pair reads as one item to a screen reader.
 */
@Composable
fun PrincipleBlock(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(DlSpace.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * What Spross is for, said once before it asks anything of the learner.
 *
 * Three principles, in the order they matter: breadth over perfection,
 * a companion rather than a course, and grammar left to the speaking.
 * Expectations are cheaper to set here than to correct after a week of rounds.
 */
@Composable
fun PrinciplesPage(chrome: Chrome, onNext: () -> Unit, onBack: () -> Unit) {
    OnboardingStoryPage {
        OnboardingHero("🌱", chrome.whyTitle)
        PrincipleBlock(chrome.whyBreadthTitle, chrome.whyBreadthBody)
        PrincipleBlock(chrome.whyCompanionTitle, chrome.whyCompanionBody)
        PrincipleBlock(chrome.whyGrammarTitle, chrome.whyGrammarBody)
        OnboardingPrimary(chrome.next, onClick = onNext)
        OnboardingBack(chrome.back, onBack)
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
fun FirstRoundPage(chrome: Chrome, busy: Boolean, onStart: () -> Unit, onBack: () -> Unit) {
    OnboardingStoryPage {
        OnboardingHero("🌿", chrome.firstRoundTitle)
        Column(verticalArrangement = Arrangement.spacedBy(DlSpace.m)) {
            Text(chrome.firstRoundRecognize, style = MaterialTheme.typography.bodyLarge)
            Text(chrome.firstRoundGrade, style = MaterialTheme.typography.bodyLarge)
            Text(chrome.firstRoundWrite, style = MaterialTheme.typography.bodyLarge)
        }
        OnboardingPrimary(chrome.letsGo, busy = busy, onClick = onStart)
        OnboardingBack(chrome.back, onBack)
    }
}
