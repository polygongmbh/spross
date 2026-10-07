package net.spross.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import net.spross.kern.design.CardMotion
import net.spross.kern.session.Question

/**
 * Where a question card stands, for the review session and every drill:
 * the switch to the next question turns the outgoing card away and the incoming one in
 * about the vertical axis, timed and angled by kern's [CardMotion] — a reveal within one
 * question (same [key]) changes nothing here.
 * The answer area stands outside, so the field and its focus never turn with the card.
 *
 * [card] draws from the [state] it is handed, never from the screen's current one,
 * so the outgoing card keeps showing the question it asked.
 * The system's animator scale governs the flip like every Compose animation:
 * with animations removed, the next card simply stands there.
 * The iOS twin is `QuestionStage`.
 */
@Composable
fun <S> QuestionStage(
    state: S,
    key: (S) -> Any?,
    modifier: Modifier = Modifier,
    card: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxWidth(),
        contentKey = key,
        contentAlignment = Alignment.TopCenter,
        // why: the turn below is the whole motion — the content transform only keeps the
        // outgoing card composed until it ends, and the size follows without clipping the turn.
        transitionSpec = {
            (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished)
                .using(SizeTransform(clip = false) { _, _ -> tween(CardMotion.FLIP_MS, easing = FastOutSlowInEasing) })
        },
        label = "questionStage",
    ) { shown ->
        // −1 before it enters, 0 standing, +1 once it has left.
        val turn = transition.animateFloat(
            transitionSpec = { tween(CardMotion.FLIP_MS, easing = FastOutSlowInEasing) },
            label = "questionFlip",
        ) { phase ->
            when (phase) {
                EnterExitState.PreEnter -> -1f
                EnterExitState.Visible -> 0f
                EnterExitState.PostExit -> 1f
            }
        }
        Box(
            Modifier.fillMaxWidth().graphicsLayer {
                // why: read in the draw phase, so the flip redraws the layer and recomposes nothing.
                val t = turn.value.toDouble()
                val angle = if (t <= 0) CardMotion.flipAngle(1 + t, incoming = true)
                else CardMotion.flipAngle(t, incoming = false)
                rotationY = angle.toFloat()
                cameraDistance = CAMERA_DISTANCE * density
                alpha = if (CardMotion.flipShows(angle)) 1f else 0f
            },
        ) { card(shown) }
    }
}

/** The common case: a stage over kern's [Question], keyed on its [Question.key]. */
@Composable
fun QuestionStage(question: Question, modifier: Modifier = Modifier, card: @Composable (Question) -> Unit) =
    QuestionStage(question, Question::key, modifier, card)

/** How far the eye stands from a turning card, in card-independent dp — a gentle perspective. */
private const val CAMERA_DISTANCE = 12f
