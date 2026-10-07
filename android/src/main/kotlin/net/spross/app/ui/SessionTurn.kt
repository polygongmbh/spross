package net.spross.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.SessionUi
import net.spross.app.TurnFlow
import net.spross.app.audio.CueSounds
import net.spross.kern.design.PressKind
import net.spross.kern.session.SelfGrading
import net.spross.kern.session.ToneKind

/**
 * The parts of a turn both roles wear: the three verdicts a reveal hands over to, and how a
 * verdict's cue sounds and feels. Everything else under the card is [AnswerArea].
 */

/**
 * The self-grade row: three verdicts, never four — under the question they answer.
 *
 * The learner says whether the word came; the clock behind them decides whether one that
 * came, came instantly (kern's `SelfGrading`). Nobody can pick their way to a long
 * interval — Easy is EARNED by answering fast, which is why it is not on screen.
 *
 * The labels name what the LEARNER knows, never what the scheduler will do, so none of
 * them wears an FSRS rating's name. Ordered best to worst, so the miss ends up under a
 * resting thumb with the middle verdict keeping the two opposites apart. Each carries a
 * mark as well as its color.
 *
 * [caption] is the standing question; the first round's coaching passes its own, so only
 * ever one line stands there.
 */
@Composable
fun VerdictButtons(
    chrome: Chrome,
    onGrade: (SelfGrading.Verdict) -> Unit,
    modifier: Modifier = Modifier,
    caption: String = chrome.sessionRatingQuestion,
) {
    val palette = Theme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            VerdictTile(SprossIcons.Check, chrome.sessionRatingGood, palette.success, Modifier.weight(1f)) {
                onGrade(SelfGrading.Verdict.Knew)
            }
            VerdictTile(SprossIcons.Dot, chrome.sessionRatingHard, palette.amber, Modifier.weight(1f)) {
                onGrade(SelfGrading.Verdict.Tough)
            }
            VerdictTile(SprossIcons.Close, chrome.sessionRatingUnknown, palette.wrong, Modifier.weight(1f)) {
                onGrade(SelfGrading.Verdict.Unknown)
            }
        }
        PauseLine(caption)
    }
}

/**
 * One verdict: its mark over its name, both in the verdict's own color on that color's
 * own wash.
 *
 * A TINTED tile, never a saturated slab. Three solid blocks of forest, ochre and brick is
 * the loudest thing on a screen whose whole job is one quiet word — and a filled button
 * reads as "do this", which is wrong for a row where the learner picks the true one rather
 * than the recommended one. The wash and the edge say the same thing at a tenth the volume
 * (iOS `GradeButton`, same 14 % fill and 35 % edge).
 *
 * The mark sits in a fixed-height slot because the three glyphs are not the same height,
 * and without it the names sit off each other's line.
 */
@Composable
private fun VerdictTile(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .heightIn(min = VERDICT_TILE)
            .background(Theme.colors.wash(color), shape)
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .pressSpring(PressKind.Rating)
            .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Decorative: the name under it is the label, and TalkBack reading "checkmark"
        // before "Wusste ich" says the same thing twice.
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.height(22.dp))
        // why: "Gar nicht" outruns a third of a narrow row — the name steps down to fit
        // rather than losing its tail (the iOS tiles scale the same way).
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 2,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 11.sp,
                maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
            ),
        )
    }
}

/** The iOS tile's own floor — a verdict is a target for a resting thumb, not a link. */
private val VERDICT_TILE = 60.dp

/**
 * What kern's verdict cue becomes on this platform: the chime [CueSounds] holds, and — on
 * a wrong answer alone — a haptic under it.
 *
 * The haptic falls where iOS puts it — a gentle wake-up on a miss, never on a reveal or a hit.
 */
fun View.cueTone(kind: ToneKind, sounds: CueSounds) {
    sounds.play(kind)
    if (kind != ToneKind.Wrong) return
    // why: the expressive constant only exists from API 30 — an older device taps with the
    // long press it has always had rather than letting a miss pass unfelt.
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )
}

/** The target language as the learner's chrome names it — what the write-out asks for. */
fun AppModel.targetName(ui: SessionUi): String {
    val lang = ui.card?.target?.lang ?: return ""
    return languageName(lang)
}

/**
 * The language the ANSWER field asks for, named. Kern's `TurnState.answerLang` decides
 * which side that is — the meaning on a card asked by ear, the target everywhere else —
 * and the placeholder is the one place the learner is told.
 */
fun AppModel.answerName(flow: TurnFlow): String = languageName(flow.state.answerLang)
