package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.spross.app.AppModel
import net.spross.app.QuestionDriver
import net.spross.kern.session.AdvanceBeat
import net.spross.kern.session.READING_CEILING_MS

/**
 * What every question screen runs the same way, review and drill alike: the question's
 * reading said, and the wait a kern-armed beat owes before it moves on — holding while the
 * answer still sounds, so a clean answer is never cut off by its own advance.
 */
@Composable
fun QuestionEffects(flow: QuestionDriver, model: AppModel) {
    val answerSounding = rememberReadAloud(model, flow.reading)
    BeatEffect(flow.beatToken, flow.armedBeat, flow::advanceElapsed, holding = answerSounding)
}

/**
 * The wait a kern-armed beat owes before the run moves on — and past it, whatever [holding]
 * still says is sounding, up to a ceiling for an end that never arrives.
 *
 * Nothing is ever armed where a screen reader runs — the flow renders an explicit Weiter
 * instead — so this only waits out beats that may run.
 */
@Composable
fun BeatEffect(
    beatToken: Int,
    armedBeat: AdvanceBeat?,
    onElapsed: () -> Unit,
    holding: () -> Boolean = { false },
) {
    LaunchedEffect(beatToken) {
        val beat = armedBeat ?: return@LaunchedEffect
        delay(beat.delayMs)
        withTimeoutOrNull(READING_CEILING_MS) { snapshotFlow(holding).first { !it } }
        onElapsed()
    }
}
