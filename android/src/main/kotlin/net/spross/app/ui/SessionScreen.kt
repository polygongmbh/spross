package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import net.spross.app.AppModel
import net.spross.app.SessionCoach
import net.spross.app.SessionUi
import net.spross.app.areaTitle
import net.spross.app.newTurn
import net.spross.kern.model.PresentationRole
import net.spross.kern.session.question
import net.spross.kern.session.reading

@Composable
fun SessionScreen(model: AppModel) {
    val ui = model.sessionUi ?: return
    BackHandler { model.finishSession() }

    Column(
        modifier = Modifier.fillMaxSize().padding(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
    ) {
        if (ui.card == null) {
            SessionSummary(model, ui)
            return@Column
        }
        RunTopBar(
            model, ui.segments, model::finishSession, ui.remaining,
            closeLabel = model.chrome.commonDone,
            // why: the session reads both prompt and answer aloud, so it owes the
            // learner a way to silence them here.
            showsMuteButton = true,
        )
        TurnCard(model, ui)
    }
}

/**
 * One card, one turn. Every tap and keystroke below becomes a `TurnIntent`, and what
 * comes back is the whole next state plus the only acts this screen takes — kern's
 * `TurnMachine` decides what an answer is worth, this decides what it looks like.
 */
@Composable
private fun TurnCard(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    val hooks = rememberTurnHooks(model)
    // why: keyed on the card AND on how many answers stand behind it — an endless refill
    // can bring the same word back, and a turn carried over would arrive already answered.
    val flow = remember(card.id, ui.segments.size) {
        model.newTurn(ui, onTone = hooks.tone, onReleaseFocus = hooks.releaseFocus)
    } ?: return

    // why: the word in the air belongs to this card alone — leaving it stops it.
    DisposableEffect(flow) { onDispose { model.pronouncer.stop() } }
    // why: keyed on the turn too — a card dealt again straight after itself says both sides afresh.
    val answerSounding = key(flow) { rememberReadAloud(model, flow.state.reading(model.pronouncer.saysMeaning)) }
    BeatEffect(flow.beatToken, flow.armedBeat, flow::advanceElapsed, holding = answerSounding)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        ReportableCard(model, card, flow.answerOut, typed = { flow.answerForReport }) {
            QuestionStage(flow.state.question) { question ->
                QuestionCard(
                    question,
                    model.chrome,
                    surface = QuestionSurface.Review,
                    voice = model.cardVoice,
                    areaTitle = model::areaTitle,
                )
            }
        }
        // Recognition is never typed: a reveal, then an honest self-grade — so no schedule is
        // ever graded against a language it was not learned with (contract §3).
        if (ui.role == PresentationRole.Recognize && model.coachActive) {
            SessionCoach.recognizeLine(model.chrome, ui.role, flow.answerRevealed)?.let { PauseLine(it) }
        }
        ReviewAnswer(model, ui, flow)
        Spacer(Modifier.height(Theme.spacing.sm))
    }
}
