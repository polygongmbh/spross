package net.spross.app

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

/** How small a screen shrinks as it is backed out of — the platform's own back preview. */
private const val BACK_SCALE = 0.9f

/**
 * A screen's own corners: drawn over the paper they are cut from, they only show once a back
 * move shrinks the screen away from the edges.
 */
val screenShape = RoundedCornerShape(28.dp)

/**
 * The move between two screens. Going deeper enters from the trailing edge. Two TABS swap
 * sideways rather than in depth, so sliding them would read as a push — they crossfade.
 *
 * Going back — and any back swipe, a tab's included — plays the platform's back preview
 * instead: the screen being left shrinks toward the finger's side and fades late, while the
 * one behind stands still and whole under it, so the two never show through each other.
 */
private fun AnimatedContentTransitionScope<Screen>.screenMotion(swiping: Boolean): ContentTransform {
    val tabs = initialState.asTab() != null && targetState.asTab() != null
    if (swiping || (!tabs && targetState.depth() < initialState.depth())) {
        return ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = scaleOut(tween(SCREEN_MOTION_MS), targetScale = BACK_SCALE) +
                slideOutHorizontally(tween(SCREEN_MOTION_MS)) { it / 10 } +
                fadeOut(tween(SCREEN_MOTION_MS / 2, delayMillis = SCREEN_MOTION_MS / 2)),
            targetContentZIndex = -1f,
        )
    }
    if (tabs) return fadeIn(tween(SCREEN_MOTION_MS)).togetherWith(fadeOut(tween(SCREEN_MOTION_MS)))
    val spec = tween<IntOffset>(SCREEN_MOTION_MS)
    return (slideInHorizontally(spec) { it / 6 } + fadeIn(tween(SCREEN_MOTION_MS)))
        .togetherWith(slideOutHorizontally(spec) { -it / 6 } + fadeOut(tween(SCREEN_MOTION_MS)))
}

/** The transition the screens run on, and the move each change of screen plays. */
class ScreenTransition internal constructor(
    val transition: Transition<Screen>,
    private val swiping: State<Boolean>,
) {
    val motion: AnimatedContentTransitionScope<Screen>.() -> ContentTransform = {
        screenMotion(swiping.value)
    }
}

/**
 * The transition the screens run on: it animates to every screen the model navigates to, and
 * a back swipe drags it toward [Screen.back] with the finger — the screen behind shows under
 * the one being left, which lands on release and returns on cancel.
 *
 * Compose this before any screen, so a screen's own back handler — a run that saves, the box's
 * search — is registered later and stands in front of this one. Where [Screen.back] names
 * nothing, no handler is enabled and a swipe on Home is the system's own way out of the app.
 */
@Composable
fun rememberScreenTransition(model: AppModel): ScreenTransition {
    val state = remember { SeekableTransitionState(model.screen) }
    val swiping = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // why: every navigation, tapped or swiped, plays out here — after a swipe it finishes the
    // move from wherever the finger let go, and only then does the next move stop being a
    // swipe's.
    LaunchedEffect(model.screen) {
        state.animateTo(model.screen)
        swiping.value = false
    }
    val behind = model.screen.back()
    PredictiveBackHandler(enabled = behind != null) { progress ->
        if (behind == null) return@PredictiveBackHandler
        swiping.value = true
        try {
            progress.collect { state.seekTo(it.progress, targetState = behind) }
            model.goBack()
        } catch (e: CancellationException) {
            // why: a canceled swipe cancels this coroutine too, so the way back to the screen
            // that stays runs outside it.
            scope.launch {
                state.animateTo(state.currentState)
                swiping.value = false
            }
            throw e
        }
    }
    val transition = rememberTransition(state, label = "screen")
    return remember(transition) { ScreenTransition(transition, swiping) }
}
