package net.spross.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.listen.ListeningBeat
import net.spross.kern.listen.LISTENING_EMOJI_CUE
import net.spross.kern.listen.ListeningTurn

/**
 * The listening run: a playlist over the learner's own words, made entirely of sound.
 *
 * The target stands from the first frame — nothing is being asked, so there is nothing to
 * hold back — and the meaning arrives with its reading, which is the one moment of the turn
 * where the word and what it means meet. The picture comes with it for the same reason.
 *
 * FOUR controls and no fifth. There is no mute button: entering a surface whose only content
 * is a sound is itself the request to hear one (`docs/read-aloud.md`), so neither mute
 * reaches this screen and a switch would only offer to break it. There is no end screen and
 * no progress either — a run the learner ends when they like has nothing to celebrate, and
 * nothing here is being graded.
 */
@Composable
fun ListeningScreen(model: AppModel) {
    val chrome = model.chrome
    val run = model.listening
    BackHandler { model.closeListening() }
    AskForTheShade()

    Column(
        modifier = Modifier.fillMaxSize().padding(DlSpace.xl),
        verticalArrangement = Arrangement.spacedBy(DlSpace.l),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DlSpace.m),
        ) {
            DrillCloseButton(chrome) { model.closeListening() }
            Text(
                chrome.listenTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            SleepTimerChip(model)
        }

        Spacer(Modifier.weight(1f))
        run.turn?.let { ListeningCard(model, it, run.beat) }
        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DlSpace.l, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(SprossIcons.Again, chrome.listenRepeat, big = false) { run.repeat() }
            TransportButton(
                if (run.paused) SprossIcons.Play else SprossIcons.Pause,
                if (run.paused) chrome.listenResume else chrome.listenPause,
                big = true,
            ) { run.togglePause() }
            TransportButton(SprossIcons.SkipNext, chrome.listenSkip, big = false) { run.skip() }
        }
    }
}

/**
 * The run's controls on the lock screen and in the shade are a NOTIFICATION, and since API 33
 * that is a permission. It is asked for here rather than at launch because here is where it
 * first buys the learner something — a run they can steer with the phone in a pocket — and a
 * prompt at launch would be a prompt for a mode most people have not opened yet.
 *
 * A denial costs the buttons and nothing else: the run still plays, and the platform stops
 * asking of its own accord after the second dismissal.
 */
@Composable
private fun AskForTheShade() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * The word on air. The card face is the review loop's, so a word looks the same wherever it
 * is met — with its article in the gender's color, since that is the half of the noun this
 * mode exists to teach by ear.
 */
@Composable
private fun ListeningCard(model: AppModel, turn: ListeningTurn, beat: ListeningBeat?) {
    val card = model.box?.cards?.get(turn.cardId)
    val target = card?.target
    val lang = target?.lang ?: model.box?.joinStamp?.target ?: return
    // The meaning is out from the moment its reading starts and stays through the echo —
    // that second saying of the target is where the two meet, and a meaning gone by then
    // would leave it meeting nothing.
    val meaningOut = beat == ListeningBeat.Meaning || beat == ListeningBeat.Echo
    // why: the picture is a cue withheld while an answer is OWED, and listening owes
    // none — held back on the meaning it vanished and returned on every word, which
    // reads as a flicker rather than as a reveal.
    VocabCard(card?.emoji, cue = LISTENING_EMOJI_CUE, revealed = false) {
        Headword(
            localizedTarget(
                target?.let { Dl.colors.articleColoredText(it) } ?: AnnotatedString(turn.targetForm),
                lang,
            ),
        )
        AnimatedVisibility(meaningOut, enter = fadeIn(), exit = fadeOut()) {
            Headword(turn.sourceForm, color = Dl.colors.accent)
        }
    }
}

/**
 * One transport control. The pause is the big one — it is the button a hand reaches for
 * without looking, which is the whole posture this mode is used in.
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    big: Boolean,
    onClick: () -> Unit,
) {
    val size = if (big) 72.dp else 56.dp
    Box(
        modifier = Modifier
            .pressSpring()
            .size(size)
            .clip(CircleShape)
            .background(
                if (big) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(if (big) 32.dp else 24.dp),
            tint = if (big) MaterialTheme.colorScheme.onPrimaryContainer else Dl.colors.textSecondary,
        )
    }
}

/**
 * The bedtime, as one chip that walks kern's list of them.
 *
 * It shows what is LEFT rather than what was picked: a run started an hour ago and a run
 * started a minute ago are the same pick and completely different answers to "is this going
 * to stop before I do". Off is the moon alone, dimmed — the run then laps for as long as it
 * is left alone.
 */
@Composable
private fun SleepTimerChip(model: AppModel) {
    val run = model.listening
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // why: the deadline is a moment, not a countdown — the label is derived from the clock
    // once a second while a bedtime stands, and nothing ticks at all while none does.
    LaunchedEffect(run.deadline) {
        while (run.deadline != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remaining = run.deadline?.let { it - now }
    val label = remaining?.let { "🌙 ${sleepTimerClock(it)}" } ?: "🌙"
    Box(
        modifier = Modifier
            .pressSpring()
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Button) { run.cycleTimer() }
            .heightIn(min = 48.dp)
            .padding(horizontal = DlSpace.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (remaining == null) Dl.colors.textSecondary else Color.Unspecified,
        )
    }
}

/**
 * "12:04" — minutes and seconds, rounded UP: a chip reading 0:00 while the run is still
 * talking says the timer is broken, and the last second is the one anyone falling asleep is
 * most likely to be looking at. Minutes are never capped at an hour, because the longest
 * bedtime kern offers is exactly one.
 */
internal fun sleepTimerClock(ms: Long): String {
    val seconds = ((ms.coerceAtLeast(0) + 999) / 1_000)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
