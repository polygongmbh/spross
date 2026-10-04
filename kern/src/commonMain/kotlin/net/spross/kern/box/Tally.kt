package net.spross.kern.box

import net.spross.kern.model.CardScheduling

/**
 * Which of three kinds one answer is — every answer is exactly one,
 * so the counts of a day or a round add up to its answers.
 * The kinds are the rule; the words, the plurals and the separator between them are the platform's.
 */
enum class TallyPartKind {
    /** A card's first answer — introduction is the first answer, whatever it did to the card. */
    Introduced,

    /** Any other answer that did not carry the card across the settled bar. */
    Reviewed,

    /** The answer that carried the card across the settled bar ([Statistics.hasSettled]). */
    Settled,
}

/** One part of a tally: which count, and how many. */
data class TallyPart(val kind: TallyPartKind, val count: Int)

/**
 * Which kind the answer that turned [before] into [after] was.
 * A first answer stays an introduction even where it crosses the bar,
 * which no graduating rating reaches alone.
 */
internal fun tallyKind(state: BoxState, before: CardScheduling, after: CardScheduling): TallyPartKind = when {
    before.log.isEmpty() -> TallyPartKind.Introduced
    !Statistics.hasSettled(state, before) && Statistics.hasSettled(state, after) -> TallyPartKind.Settled
    else -> TallyPartKind.Reviewed
}

/**
 * A day's or a round's answers spelled out, in the order a surface reads them:
 * words started, words reviewed, words that landed — the rarest part last.
 *
 * Non-zero parts only — a round that started nothing has nothing to say about first meetings,
 * and a zero spelled out reads as a failure to reach a target the box never sets.
 * An empty list means nothing was answered,
 * which is a surface's cue to say so plainly rather than to print three zeros.
 */
fun tallyParts(introduced: Int, reviewed: Int, settled: Int): List<TallyPart> =
    listOf(
        TallyPart(TallyPartKind.Introduced, introduced),
        TallyPart(TallyPartKind.Reviewed, reviewed),
        TallyPart(TallyPartKind.Settled, settled),
    ).filter { it.count > 0 }
