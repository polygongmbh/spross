package net.spross.app

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * How far under Home a screen sits — the only thing a push or a pop needs to tell them apart.
 *
 * Not a route stack: the model holds ONE screen and the app has no back stack to read a
 * direction off, so depth is what says whether the learner went in or came back out. Home is
 * the floor, everything reached from it is one down, and About is one further because the only
 * way in is through the settings' own footer.
 */
private fun Screen.depth(): Int = when (this) {
    Screen.Loading, Screen.Onboarding, Screen.Home -> 0
    Screen.About -> 2
    else -> 1
}

/** Long enough to read as a move, short enough that a tap still feels answered. */
private const val SCREEN_MOTION_MS = 220

/**
 * The move between two screens. Going deeper enters from the trailing edge and going back
 * reverses it, so the motion says which way the learner moved. Two TABS swap sideways rather
 * than in depth, so sliding them would read as a push either way — they crossfade instead.
 */
val screenMotion: AnimatedContentTransitionScope<Screen>.() -> ContentTransform = {
    if (initialState.asTab() != null && targetState.asTab() != null) {
        fadeIn(tween(SCREEN_MOTION_MS)).togetherWith(fadeOut(tween(SCREEN_MOTION_MS)))
    } else {
        val forward = targetState.depth() >= initialState.depth()
        val enterFrom = if (forward) 1 else -1
        val spec = tween<IntOffset>(SCREEN_MOTION_MS)
        (slideInHorizontally(spec) { it / 6 * enterFrom } + fadeIn(tween(SCREEN_MOTION_MS)))
            .togetherWith(
                slideOutHorizontally(spec) { it / 6 * -enterFrom } + fadeOut(tween(SCREEN_MOTION_MS)),
            )
    }
}

/**
 * The transition the screens run on: it animates to every screen the model navigates to, and
 * a back swipe drags it toward [Screen.back] with the finger — the screen behind shows through
 * the same move a tap would play, lands on release and slides back on cancel.
 *
 * Compose this before any screen, so a screen's own back handler — a run that saves, the box's
 * search — is registered later and stands in front of this one. Where [Screen.back] names
 * nothing, no handler is enabled and a swipe on Home is the system's own way out of the app.
 */
@Composable
fun rememberScreenTransition(model: AppModel): Transition<Screen> {
    val state = remember { SeekableTransitionState(model.screen) }
    val scope = rememberCoroutineScope()
    // why: every navigation, tapped or swiped, plays out here — after a swipe it finishes the
    // move from wherever the finger let go.
    LaunchedEffect(model.screen) { state.animateTo(model.screen) }
    val behind = model.screen.back()
    PredictiveBackHandler(enabled = behind != null) { progress ->
        if (behind == null) return@PredictiveBackHandler
        try {
            progress.collect { state.seekTo(it.progress, targetState = behind) }
            model.goBack()
        } catch (e: CancellationException) {
            // why: a cancelled swipe cancels this coroutine too, so the way back to the screen
            // that stays runs outside it.
            scope.launch { state.animateTo(state.currentState) }
            throw e
        }
    }
    return rememberTransition(state, label = "screen")
}
