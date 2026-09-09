package net.spross.kern.trainer

import net.spross.kern.session.AdvanceTier
import net.spross.kern.session.AlmostReason
import net.spross.kern.session.Match
import net.spross.kern.session.ToneKind
import net.spross.kern.session.TurnFeedback

/** The feedback a turn lands on, and what the surface does about it. */
internal data class TypedVerdict(
    val feedback: TurnFeedback,
    /** The entry the answer really names, where the refusal carries one. */
    val otherWord: Match.OtherWord?,
    val effects: List<DrillEffect>,
)

/** What an answer is worth to the ramp: the two flags [DrillRamp.step] and [DrillRunCore] read. */
internal data class DrillBooking(val correct: Boolean, val clean: Boolean)

/**
 * The verdict ladder every TYPED drill climbs — what a keystroke, a submit, a look-up, the
 * armed beat and a close make of the answer on screen.
 *
 * The atlas and the dates both grade a written name against the forms their task accepts, so
 * a learner meets one ladder in both: the live approve that books on the last letter, the
 * almost hold that shows a spelling, the reveal that counts as a miss. Written once, because
 * two copies is how the same slip comes to be worth two different things.
 */
internal object TypedDrillVerdicts {

    /**
     * A keystroke: "finishing the answer IS the answer", the live approve. Null while a pause
     * is showing — an almost or a reveal is the learner's to confirm, and typing on may not
     * overwrite it, so [exact] is asked only where the approve is listening.
     *
     * EXACT only, where an explicit check still forgives a slip: the typo budget would fire a
     * letter early and grade the answer before it was finished, and a real slip has to pause
     * on its correction anyway. Backing out of a finished answer withdraws the approval, so
     * typing PAST it never books it.
     */
    fun typed(feedback: TurnFeedback, exact: () -> Boolean): TypedVerdict? {
        if (feedback is TurnFeedback.Almost || feedback == TurnFeedback.Revealed) return null
        if (!exact()) {
            val withdrawn = if (feedback == TurnFeedback.Correct) TurnFeedback.Neutral else feedback
            return TypedVerdict(withdrawn, null, listOf(DrillEffect.CancelAdvance))
        }
        // why: the cue sounds once per approval — a keystroke inside an already-approved
        // answer must not re-chime on every letter.
        val tone: List<DrillEffect> =
            if (feedback == TurnFeedback.Correct) emptyList() else listOf(DrillEffect.Tone(ToneKind.Correct))
        return TypedVerdict(
            TurnFeedback.Correct,
            null,
            tone + DrillEffect.ArmAdvance(AdvanceTier.Live),
        )
    }

    /** An answer handed in: taken, held for its spelling, or refused with what it really named. */
    fun submit(match: Match): TypedVerdict = when (match) {
        Match.Exact -> TypedVerdict(
            TurnFeedback.Correct,
            null,
            listOf(
                DrillEffect.Silence,
                DrillEffect.Tone(ToneKind.Correct),
                DrillEffect.ArmAdvance(AdvanceTier.Explicit),
            ),
        )
        // why: no beat on a slip — the pause shows the proper spelling and waits for the tap
        // that books it almost, so the keyboard has to give the button back.
        is Match.Typo -> TypedVerdict(
            TurnFeedback.Almost(match.corrected, AlmostReason.Typo),
            null,
            listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Correct), DrillEffect.ReleaseFocus),
        )
        else -> TypedVerdict(
            TurnFeedback.Revealed,
            match as? Match.OtherWord,
            listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Wrong)),
        )
    }

    /**
     * The look-up. The field stays EMPTY — the card is where the answer stands, and typing it
     * in for the learner would put the same words on screen twice.
     */
    fun reveal(): TypedVerdict = TypedVerdict(
        TurnFeedback.Revealed,
        null,
        listOf(DrillEffect.Silence, DrillEffect.Tone(ToneKind.Reveal)),
    )

    /**
     * What an accepted answer still owes the ramp — null where nothing is pending. Closing
     * books exactly this, so leaving may neither lose an answer nor upgrade it.
     *
     * The almost hold: accepted, but the pause showed a spelling, so the Sprosse stays.
     */
    fun pending(feedback: TurnFeedback): DrillBooking? = when (feedback) {
        TurnFeedback.Correct -> DrillBooking(correct = true, clean = true)
        is TurnFeedback.Almost -> DrillBooking(correct = true, clean = false)
        else -> null
    }

    /**
     * The confirming tap, null on an untouched turn.
     *
     * why: no "I knew it" in a drill — the questions are generated, so self-reporting after
     * seeing the answer proves nothing; revealed simply counts as a miss.
     */
    fun confirmed(feedback: TurnFeedback): DrillBooking? =
        if (feedback == TurnFeedback.Revealed) DrillBooking(correct = false, clean = true)
        else pending(feedback)

    /** The beat only ever arms on a clean answer, so nothing else may ride it. */
    fun elapsed(feedback: TurnFeedback): DrillBooking? =
        if (feedback == TurnFeedback.Correct) DrillBooking(correct = true, clean = true) else null
}
