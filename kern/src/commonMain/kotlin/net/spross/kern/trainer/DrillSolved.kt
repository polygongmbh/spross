package net.spross.kern.trainer

/**
 * What a run has already answered RIGHT, and the two rules that follow from it — shared by
 * all three drills, because all three drew from the same pool every question and asked the
 * same handful of prompts round an evening.
 *
 * **A prompt is asked once.** Every draw skips what the run already holds, so it is spent on
 * the questions the learner still owes rather than on the ones they have just answered. Only
 * a CLEAN answer solves a prompt: a slip, a look-up and a reveal all leave it in the pool,
 * which is the ramp's own reading of an almost ([DrillRamp.step] moves nothing on one).
 *
 * **A rung with nothing left to ask is climbed past**, never repeated, and the rung it
 * climbs to is booked like any other — answering a rung out is standing on it. Where the
 * whole ladder is answered out the run ENDS on its summary, which is where the letter
 * drill's "nothing left to ask" already went.
 *
 * Nothing here is persisted: the set lives and dies with the run. What outlives it is the
 * rung the ladder books — a prompt answered on Tuesday is worth asking again on Friday, and
 * keeping that kind of score is the growing box's job, never a drill's.
 */
internal object DrillSolved {

    /**
     * How many draws in a row must land on an already-solved prompt before a GENERATED rung
     * counts as spent. The slot drill draws values rather than picking them out of a list —
     * ten single digits at its first rung, a billion at its last — so its rungs cannot be
     * enumerated, and a run of nothing but repeats is what "spent" can honestly mean there.
     * Twenty is far enough that a rung with a fifth of itself left almost never trips it,
     * and cheap enough to spend on every question.
     */
    const val SPENT_ATTEMPTS: Int = 20

    /**
     * A slot prompt is what stands on the card: the variant that asked plus the prompt
     * itself, which is the digits forward and the reading reversed — two questions about one
     * value, and the learner owes both.
     */
    fun key(variant: DrillVariant, task: TrainerTask): String = "$variant:${task.prompt}"

    /**
     * The letter drill asks one letter several ways up its ladder, so the STAGE carries the
     * identity: picking `m` out of four tiles and writing it down are different questions.
     * A gap row is one question per WORD rather than one per grapheme, which is why the
     * prompt text is in the key and not the answer alone.
     */
    fun key(task: LetterDrillTask): String = letterKey(task.stage, task.answerRef, task.promptText)

    /** The same key from the parts, for a draw that is choosing what to build. */
    fun letterKey(stage: LetterStage, ref: String, promptText: String): String =
        "$stage:$ref:$promptText"

    /** The atlas asks each row several ways too, so its KIND carries the identity. */
    fun key(task: CountryDrillTask): String = "${task.kind}:${task.id}"
}
