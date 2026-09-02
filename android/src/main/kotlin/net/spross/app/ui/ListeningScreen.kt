package net.spross.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
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
 *
 * The set of controls, their order, their copy and their states are the OTHER phone's too;
 * only the drawing is this one's (`docs/surfaces.md` § Android companion).
 */
@Composable
fun ListeningScreen(model: AppModel) {
    val chrome = model.chrome
    val run = model.listening
    BackHandler { model.closeListening() }
    AskForTheShade()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            // The bar's own inset, not the body's: a navigation icon sits 4 dp in, which
            // puts its glyph at the 16 dp every other screen's title starts from.
            modifier = Modifier.fillMaxWidth().padding(start = Theme.spacing.xs, end = Theme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            BackFromRun(chrome) { model.closeListening() }
            Text(
                chrome.listenTitle,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            SleepTimerChip(model)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Theme.spacing.xl, vertical = Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            run.turn?.let { ListeningCard(model, it, run.beat) }
            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    Theme.spacing.xl,
                    Alignment.CenterHorizontally,
                ),
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
}

/**
 * The way out. A back ARROW rather than the ✕ the drills wear: those two sit in a bar beside
 * the read-aloud switch, and the tinted disc is what makes the pair read as chrome instead of
 * two loose glyphs — alone on a screen with neither, the disc is a grey blob. Back and ✕ do
 * the same thing here (`docs/surfaces.md`), which was never a promise to wear the same glyph.
 */
@Composable
private fun BackFromRun(chrome: Chrome, onClose: () -> Unit) {
    IconButton(
        onClick = onClose,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = chrome.commonClose },
    ) {
        Icon(SprossIcons.ArrowLeft, contentDescription = null, tint = Theme.colors.textSecondary)
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
    // why: the meaning's LINE is held for the whole turn and only its ink fades in, the
    // same bargain the picture's slot makes — a card that grows and shrinks every few
    // seconds pumps in height with nothing being revealed.
    //
    // Keyed on the turn, exactly as the picture is keyed on the picture: a new word
    // re-seeds the fade at nothing. Animating across the swap instead showed the INCOMING
    // word's meaning at full ink and then faded it away — the answer handed over before
    // the word had been said once.
    val meaning = remember(turn.cardId) { Animatable(0f) }
    LaunchedEffect(turn.cardId, meaningOut) { meaning.animateTo(if (meaningOut) 1f else 0f) }
    // why: the picture is a cue withheld while an answer is OWED, and listening owes
    // none — held back on the meaning it vanished and returned on every word, which
    // reads as a flicker rather than as a reveal.
    // why: this card OWNS the screen — nothing to type, nothing to press, no keyboard — so
    // the picture stands above the words and they take the card's whole width, which is
    // the width a long target word needs to stay one unbroken line.
    VocabCard(
        card?.emoji,
        cue = LISTENING_EMOJI_CUE,
        revealed = false,
        arrangement = CardArrangement.Above,
    ) {
        Headword(
            localizedTarget(
                target?.let { Theme.colors.articleColoredText(it) } ?: AnnotatedString(turn.targetForm),
                lang,
            ),
        )
        Headword(
            turn.sourceForm,
            color = Theme.colors.accent,
            // why: alpha does not measure, so the line is there all along — but it is
            // not YET part of the card, and a screen reader that read it out would be
            // saying the meaning ahead of the voice that owes it.
            modifier = Modifier.alpha(meaning.value)
                .then(if (meaningOut) Modifier else Modifier.clearAndSetSemantics { }),
        )
    }
}

/**
 * One transport control. The pause is the big one — it is the button a hand reaches for
 * without looking, which is the whole posture this mode is used in.
 *
 * A real icon button rather than a tinted box: the ripple, the role and the 48 dp floor come
 * with it, where a box clears that floor only as long as nobody changes the number.
 * The label names what the tap DOES, so it flips with the state — these are buttons, not the
 * switch one screen over, whose name has to stay put while its value moves.
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    big: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .pressSpring()
            .size(if (big) 72.dp else 56.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        colors = if (big) {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = Theme.colors.textSecondary,
            )
        },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(if (big) 32.dp else 24.dp))
    }
}
