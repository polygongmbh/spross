package net.spross.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import net.spross.app.Chrome

/**
 * How an overview row that just unlocked is marked, once (kern's `DrillUnlockMark` decides
 * which): the padlock it wore fades where it stood while a wash over the row fades with it.
 * Short and quiet on purpose — it confirms what the learner earned rather than celebrating it.
 */
private object UnlockTiming {
    /** How long the padlock stands before it goes — past a screen sliding in. */
    const val HOLD_MS = 600L
    const val FADE_MS = 900
}

/** Whether a newly unlocked row's padlock is still standing: true for [UnlockTiming.HOLD_MS], then false. */
@Composable
private fun padlockStanding(newlyUnlocked: Boolean): Boolean {
    var standing by remember(newlyUnlocked) { mutableStateOf(newlyUnlocked) }
    LaunchedEffect(newlyUnlocked) {
        if (!newlyUnlocked) return@LaunchedEffect
        delay(UnlockTiming.HOLD_MS)
        standing = false
    }
    return standing
}

/** A row's mark that was a padlock the last time the page showed it: the padlock crossfades into [mark]. */
@Composable
fun UnlockingMark(newlyUnlocked: Boolean, mark: @Composable () -> Unit) {
    AnimatedContent(
        targetState = padlockStanding(newlyUnlocked),
        transitionSpec = {
            fadeIn(tween(UnlockTiming.FADE_MS)) togetherWith
                (fadeOut(tween(UnlockTiming.FADE_MS)) + scaleOut(tween(UnlockTiming.FADE_MS), targetScale = 0.6f))
        },
        contentAlignment = Alignment.Center,
        label = "unlock",
    ) { locked ->
        if (locked) Padlock(MaterialTheme.typography.titleMedium) else mark()
    }
}

/** The padlock in front of a switch's title, where the unlocked switch wears nothing. */
@Composable
fun FadingPadlock(newlyUnlocked: Boolean) {
    AnimatedVisibility(
        visible = padlockStanding(newlyUnlocked),
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(UnlockTiming.FADE_MS)) + shrinkHorizontally(tween(UnlockTiming.FADE_MS)),
    ) {
        Padlock(MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Padlock(style: androidx.compose.ui.text.TextStyle) {
    Text(LOCK, style = style, color = Theme.colors.textSecondary, modifier = Modifier.clearAndSetSemantics { })
}

/** The brief wash over a row that just unlocked, gone with its padlock. */
fun Modifier.unlockWash(newlyUnlocked: Boolean): Modifier = composed {
    val alpha = remember(newlyUnlocked) { Animatable(if (newlyUnlocked) 1f else 0f) }
    LaunchedEffect(newlyUnlocked) {
        if (!newlyUnlocked) return@LaunchedEffect
        delay(UnlockTiming.HOLD_MS)
        alpha.animateTo(0f, tween(UnlockTiming.FADE_MS, easing = FastOutSlowInEasing))
    }
    val wash = Theme.colors.accent.copy(alpha = 0.12f)
    drawBehind { drawRect(wash.copy(alpha = wash.alpha * alpha.value)) }
}

/**
 * Says the unlocked rows once, a beat after the page settles, so the screen change TalkBack
 * is announcing is not talked over.
 */
@Composable
fun AnnounceUnlocks(titles: List<String>, chrome: Chrome) {
    val view = LocalView.current
    LaunchedEffect(titles) {
        if (titles.isEmpty()) return@LaunchedEffect
        delay(700)
        @Suppress("DEPRECATION") // why: the one call that speaks without moving focus
        view.announceForAccessibility(chrome.a11yTrainerUnlocked.format(titles.joinToString(", ")))
    }
}
