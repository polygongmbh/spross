package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.DrillRun
import net.spross.app.Screen
import net.spross.app.audio.Pronouncer
import net.spross.app.finishDrill
import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AnswerOutcome
import net.spross.kern.trainer.DrillRunProgress
import net.spross.kern.trainer.DrillTally

/**
 * The shell every asking surface stands in — the review round and all five drills: the
 * constant chrome across the top, and whatever is being asked below it.
 *
 * The runs behind them keep their own machines (a heard glyph and a shuffled phrase share no
 * grammar), and what they ASK differs in every particular; the way they ask does not, so it
 * is written once here (`docs/design.md` § Review UX rules).
 */

/**
 * The constant chrome, in the order the round is worked: the way OUT leading, where the
 * thumb that started the round already is; the progress carrying the whole width; the
 * figures; the read-aloud switch trailing. Nothing up here varies with the card below it, so
 * no card pays a point of layout for it.
 *
 * [remaining] is one on an ENDLESS run, which has no total to count toward — the filled and
 * empty stretches then move together, so the bar fills as the run grows instead of breaking
 * past a fixed end.
 *
 * [showsMuteButton] is opt-in: only a run that reads words aloud owes the learner a way to
 * silence them here (iOS `SessionScaffold.showsMuteButton`) — a run that only offers a tap-to-hear
 * speaker needs no switch, since a tap already outranks the mute.
 */
@Composable
fun RunTopBar(
    model: AppModel,
    outcomes: List<AnswerOutcome>,
    onClose: () -> Unit,
    remaining: Int = 1,
    /** The figures beside the bar; null where the bar alone says where the round stands. */
    counter: String? = null,
    closeLabel: String = model.chrome.commonClose,
    showsMuteButton: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        RunCloseButton(onClose, closeLabel)
        SegmentsBar(outcomes, remaining, model.chrome, Modifier.weight(1f))
        if (counter != null) {
            Text(
                counter,
                style = MaterialTheme.typography.bodySmall,
                color = Theme.colors.textSecondary,
            )
        }
        if (showsMuteButton) ReadAloudSwitch(model)
    }
}

/** The run's own tally as the bar counts it: how many of the judged answers came clean. */
fun DrillTally.counter(): String = "$clean/$judged"

/**
 * The way out of a running round — first in the bar, so it is never hunted for, and in the
 * same corner on the overview that opens the next one.
 */
@Composable
fun RunCloseButton(onClose: () -> Unit, label: String) {
    Box(
        modifier = Modifier
            .chromeDisc()
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SprossIcons.Close, contentDescription = null, tint = Theme.colors.textSecondary)
    }
}

/**
 * The tinted disc both chrome controls sit in: one shape, one 48 dp target, so the pair
 * reads as chrome rather than as two loose glyphs jostling in a corner.
 */
@Composable
fun Modifier.chromeDisc(): Modifier =
    this.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)

/**
 * The read-aloud switch, in constant chrome. It governs the SPOKEN WORDS only — the verdict
 * cues are their own matter, and the media volume is the switch for everything.
 */
@Composable
fun ReadAloudSwitch(model: AppModel) {
    val chrome = model.chrome
    val muted = model.pronouncer.muted
    Box(
        // why: toggleable rather than an IconButton — the control IS a switch, and a
        // button's own Role.Button would win the semantics merge against one set
        // around it. ONE stable label with the state as its VALUE: a label that flips
        // leaves TalkBack announcing the action as though it were the condition.
        modifier = Modifier
            .chromeDisc()
            .toggleable(
                value = !muted,
                role = Role.Switch,
                onValueChange = { model.pronouncer.muted = !it },
            )
            // why: merged, or the glyph inside would be a node of its own and TalkBack
            // would read the picture of a loudspeaker after the switch it belongs to.
            .semantics(mergeDescendants = true) {
                contentDescription = chrome.a11yActionReadAloud
                stateDescription = if (muted) chrome.a11yStateOff else chrome.a11yStateOn
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (muted) SprossIcons.SpeakerOff else SprossIcons.Speaker,
            contentDescription = null,
            tint = Theme.colors.textSecondary,
        )
    }
}

/**
 * The wait a kern-armed beat owes before the run moves on.
 *
 * Nothing is ever armed where a screen reader runs — the flow renders an explicit Weiter
 * instead — so this only waits out beats that may run.
 */
@Composable
fun BeatEffect(beatToken: Int, armedBeat: AdvanceTier?, onElapsed: () -> Unit) {
    LaunchedEffect(beatToken) {
        val tier = armedBeat ?: return@LaunchedEffect
        delay(tier.delayMs)
        onElapsed()
    }
}

/**
 * The run the page opened, or null where this device, box or pair can be asked nothing at
 * all — every entry point gates on the same predicate, so a null run is a closed door rather
 * than a screen, and the page it was opened from takes the learner straight back.
 *
 * Callers take it with `?: return`: a run that cannot open has no screen to draw.
 */
@Composable
fun <F : Any> rememberRun(model: AppModel, back: Screen, key: Any? = Unit, open: () -> F?): F? {
    // why: keyed on nothing the run does — everything a run draws from is resolved ONCE, as
    // it opens, and a foreground that re-sweeps availability must not restart it underneath.
    val flow = remember(key) { open() }
    if (flow == null) LaunchedEffect(Unit) { model.finishDrill(back, null, "") }
    return flow
}

/**
 * Everything a drill run puts around whatever it happens to be asking: the way out in the
 * corner and on the back gesture, the top bar, the score line, and the scrolling body under
 * them — plus the three effects every run owes.
 *
 * [sprosse] is worded by the drill that owns it and is null where a run has one Sprosse only;
 * [announcesRecord] carries a real difference rather than settling it, since the letter
 * drill has always spoken the streak alone. [showsMuteButton] is [RunTopBar]'s own gate,
 * passed through rather than defaulted here — which runs autoplay speech is a call each
 * drill screen makes for itself, matching iOS's per-run `showsMuteButton`.
 */
@Composable
fun DrillRunScaffold(
    model: AppModel,
    run: DrillRun,
    leave: () -> Unit,
    outcomes: List<AnswerOutcome>,
    tally: DrillTally,
    sprosse: String?,
    streak: Int,
    bestStreak: Int,
    announcesRecord: Boolean = false,
    /** False while something stands OVER the run — the number table, which the back gesture closes. */
    backLeaves: Boolean = true,
    showsMuteButton: Boolean = false,
    spacing: Dp = Theme.spacing.md,
    body: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = backLeaves) { leave() }
    DrillRunEffects(run, leave, model.pronouncer)
    Column(
        modifier = Modifier.fillMaxSize().padding(Theme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        RunTopBar(model, outcomes, leave, counter = tally.counter(), showsMuteButton = showsMuteButton)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            DrillStreakLine(sprosse, streak, bestStreak, model.chrome, announcesRecord)
            body()
            Spacer(Modifier.height(Theme.spacing.sm))
        }
    }
}

/**
 * The same shell over a run that already says where it stands: the score line, the bar and
 * the tally are read off kern's [DrillRunProgress] rather than handed over one figure at a
 * time. The typed drills reach the shell through the parameters above, since a country and a
 * date are laddered off a view of their own.
 */
@Composable
fun DrillRunScaffold(
    model: AppModel,
    run: DrillRun,
    leave: () -> Unit,
    progress: DrillRunProgress,
    sprosse: String?,
    announcesRecord: Boolean = false,
    backLeaves: Boolean = true,
    showsMuteButton: Boolean = false,
    spacing: Dp = Theme.spacing.md,
    body: @Composable ColumnScope.() -> Unit,
) = DrillRunScaffold(
    model = model,
    run = run,
    leave = leave,
    outcomes = progress.outcomes,
    tally = progress.tally,
    sprosse = sprosse,
    streak = progress.streak,
    bestStreak = progress.bestStreak,
    announcesRecord = announcesRecord,
    backLeaves = backLeaves,
    showsMuteButton = showsMuteButton,
    spacing = spacing,
    body = body,
)

/**
 * The three effects every endless drill runs the same way: the hand-back when kern runs out,
 * the silence on the way out, and the wait a kern-armed beat owes before it advances.
 */
@Composable
fun DrillRunEffects(run: DrillRun, leave: () -> Unit, pronouncer: Pronouncer) {
    // Nothing left to ask: hand the run back, never repeat a question.
    LaunchedEffect(run.ranOut) { if (run.ranOut) leave() }
    // why: D5 — leaving mid-question must silence, whichever way the screen goes.
    DisposableEffect(Unit) { onDispose { pronouncer.stop() } }
    BeatEffect(run.beatToken, run.armedBeat, run::advanceElapsed)
}
