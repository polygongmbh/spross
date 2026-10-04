package net.spross.kern.box

/**
 * Days of stability at which a card has ARRIVED ([Statistics.hasArrived]) — gate (a), read by
 * every rule that asks whether a word has landed enough to lean on: phrase
 * unlock (see [Growth.isComponentStable]), the drill
 * pools, [net.spross.kern.model.producePrompt] (which WITHDRAWS the meaning),
 * and [net.spross.kern.model.emojiCue] (which ADDS support). The stats
 * display, the settled badge, and the day tallies read a stricter, later bar
 * instead ([SETTLED_STABILITY]).
 *
 * Set between S0(Good) = 2.3065 and S0(Easy) = 8.2956, so a merely-Good first
 * answer does not read as landed while a genuinely known-on-sight Easy one does.
 * That gap is the whole point: a first answer of Good is as easily an emoji
 * recognized as a word recalled, and the word keeps its support until a second
 * answer says otherwise — where Easy, which only a fast learner-reported Knew
 * can earn ([net.spross.kern.session.SelfGrading]), clears the bar on the spot.
 *
 * A separate, faster `settledStability` of 2.0 used to gate presentation support
 * on its own. It sat BELOW S0(Good), so a single Good — the emoji-lucky case
 * included — withdrew the emoji from the very next review, which is the first
 * TYPED one and the first that can actually catch the guess.
 */
const val GROWING_STABILITY: Double = 6.0

/**
 * Days of stability at which a card counts as settled ([Statistics.hasSettled]).
 * Gates no support or unlock: it backs the counts, the badge, the area-complete mark,
 * the word scramble's pool and what [Briefing] hands over as known.
 *
 * Above two plain Goods (S ≈ 17), at or below Good then Easy (S ≈ 29.8):
 * reinforcement alone never settles a word, one fast answer has to.
 */
const val SETTLED_STABILITY: Double = 25.0

/**
 * Days of stability at which a settled card counts as matured — fruit rather than a blossom.
 * Kern's so both platforms draw the same tree.
 */
const val MATURED_STABILITY: Double = 120.0
