package net.spross.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.app.sayOnTap
import net.spross.kern.session.Question
import net.spross.kern.session.Saying
import net.spross.kern.design.CardType
import net.spross.kern.design.LetterCase

/**
 * Every card a question is asked on, drawn from kern's [Question]:
 * what stands on it, when it opens and what it closes on are kern's, and this only sets them —
 * so a drill card and a review card cannot drift into two ideas of what a card shows.
 *
 * The answer and the closing lines grow below the prompt only once [Question.opens];
 * before that the card holds the prompt and, at first sight, the hint —
 * or the closing note alone, where an accepted answer [Question.growsNote].
 * The iOS twin is `QuestionCardView`.
 */
@Composable
fun QuestionCard(
    question: Question,
    chrome: Chrome,
    modifier: Modifier = Modifier,
    surface: QuestionSurface = QuestionSurface.Drill,
    voice: CardVoice = CardVoice.Silent,
    /** The title of the area an ambiguous prompt names ([Question.Side.context] is its key). */
    areaTitle: (String) -> String = { it },
    /** What a screen reader hears for the prompt where its text is no word to read — a mixed word, spelled out. */
    promptLabel: String? = null,
    /** TalkBack lands on the replay control as each question goes up, where the prompt is a sound. */
    replayFocus: FocusRequester? = null,
) {
    val card = CardContext(question, chrome, voice, areaTitle, promptLabel, replayFocus)
    when (surface) {
        QuestionSurface.Drill -> card.DrillFace(modifier)
        QuestionSurface.Review -> card.ReviewFace(modifier)
        QuestionSurface.Listening -> card.ListeningFace(modifier)
    }
}

/** What the screen around the card is — the card works its own layout and sizes out from that. */
enum class QuestionSurface {
    /** A drill task above a field, tiles or a pad: the question at the fixed size its form picks. */
    Drill,

    /** A review card above the answer controls: its words at headword size with their grammar, the picture beside them. */
    Review,

    /** The listening card, the screen's only content: the picture above the words, at hero size. */
    Listening,
}

/** What a card's speakers do: the tap that says a side's [Saying]; null drops the speaker. */
fun interface CardVoice {
    fun pronounce(saying: Saying): (() -> Unit)?

    companion object {
        val Silent = CardVoice { null }
    }
}

/** The tap on a card's speaker, which speaks even while reading aloud is off. */
val AppModel.cardVoice: CardVoice get() = CardVoice { sayOnTap(it) }

/** One card's inputs, so the faces and their parts read them without threading each one. */
internal class CardContext(
    val question: Question,
    val chrome: Chrome,
    val voice: CardVoice,
    val areaTitle: (String) -> String,
    val promptLabel: String?,
    val replayFocus: FocusRequester?,
) {
    val opens: Boolean get() = question.opens

    fun pronounce(side: Question.Side): (() -> Unit)? = side.saying?.let(voice::pronounce)
}

/**
 * The picture rides in the card's leading slot, mirrored on the far edge so the words stay
 * centered in the card — unless the picture IS the question, when it stands where the words would.
 */
@Composable
private fun CardContext.DrillFace(modifier: Modifier) {
    val beside = question.emoji?.takeIf { it.isNotEmpty() && !question.emojiIsQuestion }
    val slot = with(LocalDensity.current) { EMOJI_SLOT.toDp() }
    // why: one height for every drill's ordinary question — the field below never jumps.
    CardFace(modifier.heightIn(min = Theme.reserve.drillCard)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            if (beside != null) EmojiSlot(beside, emojiShowing(question.emojiCue, opens), slot, EMOJI_GLYPH)
            Column(
                // why: the growth is animated inside the face, so the edge is never clipped mid-reveal.
                modifier = Modifier.weight(1f).animateContentSize(turnTween()),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                question.ask?.let { Caption(askText(it, chrome)) }
                DrillPrompt()
                val hint = question.hint
                val note = noteText()
                if (opens) {
                    // why: a gap question closes over its blank where it stood, so nothing below it moves.
                    if (question.prompt.form != Question.Form.Gap) {
                        CardReveal(note = note) { DrillAnswer() }
                    }
                    OtherWordLine()
                } else if (question.growsNote && note != null) {
                    // why: an accepted answer to a question whose meaning never stood on screen
                    // still owes that meaning — alone, since the answer stands in the learner's own text.
                    CardReveal(note = note) {}
                } else if (hint != null) {
                    // why: the reveal TAKES this slot rather than stacking under it —
                    // the hint is scaffolding for a prompt still unanswered.
                    DrillHintPill(hintText(hint, chrome))
                }
            }
            if (beside != null) Spacer(Modifier.width(slot))
        }
    }
}

@Composable
private fun CardContext.DrillPrompt() {
    val side = question.prompt
    val emoji = question.emoji
    when {
        side.form == Question.Form.Sound -> ReplayGlyph(pronounce(side), chrome, replayFocus)
        side.form == Question.Form.Gap -> {
            ReplayGlyph(pronounce(side), chrome, replayFocus)
            GapLine()
        }
        // why: the flag IS the question here, so it takes the place and size the name would have had.
        question.emojiIsQuestion && emoji != null ->
            Text(emoji, fontSize = Theme.prompt.glyph, textAlign = TextAlign.Center)
        else -> side.text?.let { text ->
            SpokenWord(pronounce(side), chrome) {
                val labeled = Modifier.weight(1f, fill = false).then(
                    if (promptLabel == null) Modifier else Modifier.semantics { contentDescription = promptLabel },
                )
                if (side.form == Question.Form.Name) {
                    Headword(promptText(side, text), modifier = labeled)
                } else {
                    Text(
                        promptText(side, text),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = promptSize(side.form),
                            fontWeight = FontWeight.Bold,
                            fontFamily = if (side.form == Question.Form.Numeral) FontFamily.Monospace else FontFamily.Default,
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = promptLines(side.form),
                        modifier = labeled,
                    )
                }
            }
        }
    }
}

/** The blanked word while the question stands, the whole word in the answer's accent once it opens. */
@Composable
private fun CardContext.GapLine() {
    val side = if (opens) question.answer else question.prompt
    val word = side.text ?: return
    Text(
        tagged(word, side.lang),
        fontSize = Theme.prompt.word,
        fontWeight = FontWeight.Bold,
        color = if (opens) Theme.colors.accent else Theme.colors.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun CardContext.DrillAnswer() {
    val side = question.answer
    val text = side.text ?: return
    SpokenWord(pronounce(side), chrome) {
        Text(
            tagged(text, side.lang),
            style = answerStyle(),
            color = Theme.colors.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * What a refused answer actually named, beside the opened answer. The line says what the
 * learner DID write; the word a tap on it plays is the one they owed, the answer above it.
 */
@Composable
internal fun CardContext.OtherWordLine() {
    val other = question.otherWord ?: return
    if (!opens) return
    PauseLine(
        chrome.sessionOtherWord.format(other.word, other.meanings.joinToString(", ")),
        modifier = Modifier.pronounceOnTap(pronounce(question.answer), chrome),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        if (CardType.askCase == LetterCase.Upper) text.uppercase() else text,
        style = MaterialTheme.typography.bodySmall,
        color = Theme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

/** Text tagged with the language it is written in, so TalkBack reads it in that voice. */
internal fun tagged(text: AnnotatedString, lang: String?): AnnotatedString =
    if (lang == null) text else localizedTarget(text, lang)

internal fun tagged(text: String, lang: String?): AnnotatedString = tagged(AnnotatedString(text), lang)

/**
 * The replay control a sound prompt stands as: big, but never circled or filled —
 * it names what the card does rather than looking like a button. Dimmed and inert where
 * nothing can be heard, and still focusable, so TalkBack's hand-off to it lands somewhere.
 *
 * why: it keeps a generous tap target but reserves only the glyph's height in layout.
 */
@Composable
internal fun ReplayGlyph(replay: (() -> Unit)?, chrome: Chrome, focus: FocusRequester? = null) {
    Box(
        modifier = Modifier
            .size(width = 88.dp, height = 52.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .then(
                if (replay != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 44.dp),
                        onClick = replay,
                    )
                } else {
                    Modifier.focusable()
                },
            )
            // why: merged, or the loudspeaker would be a node of its own after the button it belongs to.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = chrome.a11yActionReplayPrompt
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            SprossIcons.Speaker,
            contentDescription = null,
            tint = Theme.colors.accent,
            modifier = Modifier.size(40.dp).alpha(if (replay != null) 1f else 0.35f),
        )
    }
}
