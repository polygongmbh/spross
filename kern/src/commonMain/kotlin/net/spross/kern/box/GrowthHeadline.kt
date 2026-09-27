package net.spross.kern.box

/** What a finished round may honestly say it did to the area it worked hardest. */
enum class GrowthClaim {
    /** The day is strained ([TodayReport.recallStrained]): no claim beyond a vague one. */
    Unclaimed,

    /** The area had nothing in it before this round. */
    Opened,

    /** More words stand matured or past than before. */
    Matured,

    /** Words were met, and none newly landed. */
    Met,

    /** Words were added or landed. */
    Grew,

    /** Nothing was added: only what was there took a firmer hold. */
    Held,
}

/**
 * The line over the round's tree: which [claim], and a stable [pick] among the lines a
 * platform holds for it (`lines[pick % lines.size]`).
 */
data class GrowthHeadline(val claim: GrowthClaim, val pick: Int)

/**
 * What the round's summary says about [transition] ([grownArea]) — read off what THIS
 * area gained, never off the round's session-wide tallies, which can name a gain made in
 * another area. Null where there is no tree to speak of.
 *
 * The pick is seeded by the round's tallies and the streak: stable within one summary,
 * and a learner answering the same shape of round every morning still reads a new line.
 */
fun growthHeadline(
    transition: TreeTransition?,
    restSuggested: Boolean,
    introduced: Int,
    consolidated: Int,
    reviews: Int,
    streakDays: Int,
): GrowthHeadline? {
    val move = transition ?: return null
    val before = move.before
    val after = move.after
    if (after.isBare) return null
    val claim = when {
        restSuggested -> GrowthClaim.Unclaimed
        before.isBare -> GrowthClaim.Opened
        after.matured + after.longHeld > before.matured + before.longHeld -> GrowthClaim.Matured
        after.arriving > before.arriving && after.growing <= before.growing -> GrowthClaim.Met
        after.met == before.met -> GrowthClaim.Held
        else -> GrowthClaim.Grew
    }
    return GrowthHeadline(claim, stablePick("$introduced:$consolidated:$reviews:$streakDays"))
}

/** A non-negative stable int for [key]: an FNV-1a fold finished by SplitMix64. */
private fun stablePick(key: String): Int {
    var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
    for (byte in key.encodeToByteArray()) {
        hash = (hash xor (byte.toLong() and 0xff)) * 0x100000001b3L
    }
    var x = hash + -0x61c8864680b583ebL
    x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
    x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
    x = x xor (x ushr 31)
    return (x ushr 33).toInt()
}
