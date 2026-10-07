package net.spross.kern.trainer

import net.spross.kern.session.AnswerControls.Slot
import net.spross.kern.session.AnswerControls
import net.spross.kern.model.ClosingNote
import net.spross.kern.model.EmojiCue
import net.spross.kern.session.Question
import net.spross.kern.session.QuestionAsk
import net.spross.kern.session.Saying
import net.spross.kern.model.Card
import net.spross.kern.model.Language
import net.spross.kern.session.CatalogAnswerGrader
import net.spross.kern.session.TurnFeedback

/** What the learner does to a letter run. */
sealed class LetterDrillIntent {
    /** A choice tile. One attempt per question: a second tap would be a retry, which has no verdict. */
    data class Choose(val glyph: String) : LetterDrillIntent()

    /** A keystroke on a typed format: an exact answer approves itself, with no Check tap. */
    data class InputChanged(val text: String) : LetterDrillIntent()

    data class Submit(val text: String) : LetterDrillIntent()

    /** "Aufdecken" on an empty field — the card carries the answer and the question books a miss. */
    data object Reveal : LetterDrillIntent()

    data object ConfirmPending : LetterDrillIntent()

    data object AdvanceElapsed : LetterDrillIntent()

    /** Keep practicing from a pause ([DrillPacing]): the same run goes on, and a fresh stretch starts. */
    data object KeepPracticing : LetterDrillIntent()
}

/** The closed result of one intent. */
data class LetterDrillReduction(val state: LetterDrillRunState, val effects: List<DrillEffect>)

/** What a closed letter run leaves behind — its figures and the Sprossen it answered out. */
data class LetterDrillClose(
    val state: LetterDrillRunState,
    /** null ⇒ nothing was answered: dismiss, report nothing. */
    val summary: DrillRunSummary?,
    /**
     * The tile and typed Sprossen this run climbed off or answered out before its first slip
     * ([DrillSprossen], the scrambles' ledger), for the store to OR into the mask under
     * [LetterDrillRunState.storageKey]. Dictation draws from the box, which grows, so it is
     * never cleared.
     */
    val clearedSprossen: Set<Int>,
    val effects: List<DrillEffect>,
)

/**
 * Everything one letter run is fixed to: what this device can ask, the learner's own cards, and
 * the grader dictation is judged by. Resolved when the run opens, never per question.
 */
class LetterDrillRunConfig(
    val report: LetterDrillAvailability.Report,
    /** The learner's real cards by id — dictation grades against the card's own identity. */
    val cards: Map<String, Card>,
    /**
     * The STRICT drill grader with the whole join in view: a per-word slip budget alone would
     * accept `kufungua` for `kufunga`, and only the catalog-wide grader withdraws that credit.
     * Null falls the dictation Sprosse back to glyph grading — defensive, never asserted.
     */
    val dictationGrader: CatalogAnswerGrader?,
    /**
     * The Sprossen earlier runs answered out, as the PLATFORM's store holds them
     * ([NumbersMode.clearedSprossen] over the mask under [LetterDrillRunState.storageKey]).
     */
    val cleared: Set<Int>,
)

/**
 * One letter run, whole and immutable.
 *
 * No FSRS anywhere (D12 — transcription is not recall): the box is READ, for the pacing figures
 * and the dictation pool, and never written. The run keeps no streak record; what its close
 * stores is the Sprossen it answered out.
 */
data class LetterDrillRunState(
    val config: LetterDrillRunConfig,
    /** The question on screen; null only once nothing can be asked any more. */
    val task: LetterDrillTask?,
    override val index: Int,
    val sprosse: Int,
    val winsAtSprosse: Int,
    /** The Sprossen climbed off before the run's first slip — what the close hands the store. */
    val clearedSprossen: Set<Int>,
    /** The counters every drill run keeps, booked as one ([DrillRunCore.book]). */
    override val core: DrillRunCore,
    /** The tile the learner picked, so the grid can mark both it and the answer. */
    val chosen: String?,
    override val feedback: TurnFeedback,
    override val finished: Boolean,
) : DrillRunProgress {
    val format: LetterFormat? get() = task?.format

    /** The formats that carry an input field. */
    val typing: Boolean get() = format == LetterFormat.Typed || format == LetterFormat.Dictation

    /** Nothing through the reader: the question's sound IS the question, and plays past the mute on its own. */
    override val promptSaying: Saying? get() = null

    /** Nothing: the question already was the sound, and the answer is the glyph or the word it said. */
    override val answerSaying: Saying? get() = null

    /**
     * The question is a sound, over the word with its grapheme blanked where it asks one;
     * its replay says [LetterDrillTask.promptText], through the recording the drill's own player resolves.
     * A gap question opens onto the whole word it blanked, which is its gloss, so it carries no note.
     * Only a dictated word carries a speaker: every other answer is a bare glyph, which nothing may be asked to say.
     * An accepted answer grows the meaning alone, which the sound never put on screen.
     */
    override val question: Question?
        get() = task?.let { t ->
            val gap = t.gapText != null
            val dictation = t.format == LetterFormat.Dictation
            val word = if (gap) t.gloss ?: t.display else t.display
            Question(
                key = index.toString(),
                ask = when {
                    dictation -> QuestionAsk.LetterDictation
                    gap -> QuestionAsk.LetterSpell
                    else -> QuestionAsk.LetterHear
                },
                prompt = Question.Side(
                    t.gapText, t.language, if (gap) Question.Form.Gap else Question.Form.Sound,
                    saying = Saying(t.promptText, t.language),
                ),
                answer = Question.Side(
                    word, t.language, if (gap || dictation) Question.Form.Word else Question.Form.Glyph,
                    saying = Saying(word, t.language).takeIf { dictation },
                ),
                emoji = null,
                emojiCue = EmojiCue.Upfront,
                opens = showsAnswer,
                growsNote = answerAccepted,
                closing = Question.Closing(note = t.gloss?.takeUnless { gap }?.let { ClosingNote.Own(it) }),
            )
        }

    /** The tile Sprossen pick a glyph off kern's four; the typed and dictated ones write it. */
    override val controls: AnswerControls?
        get() = task?.let { t ->
            answerControls(if (typing) typedSlot(t.language) else Slot.Choices(t.choices.orEmpty(), t.display))
        }

    /**
     * The Sprossen the store may keep of those climbed off so far.
     * Dictation draws from the box, which grows —
     * a Sprosse of it climbed today says nothing about the words it will hold tomorrow.
     */
    internal val keptSprossen: Set<Int>
        get() = clearedSprossen.filter { LetterDrill.formatFor(it) != LetterFormat.Dictation }.toSet()

    /** The kept Sprossen the store did not hold — what a pause for improving names. */
    internal val newSprossen: Int get() = (keptSprossen - config.cleared).size

    companion object {
        /** Where the answered-out mask is filed: one per learned language, with no direction. */
        fun storageKey(language: Language): String = "letters.$language"
    }
}
