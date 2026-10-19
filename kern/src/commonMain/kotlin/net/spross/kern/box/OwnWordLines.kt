package net.spross.kern.box

import net.spross.kern.model.JoinStamp
import net.spross.kern.model.PAIR_JOINER

/**
 * The half a suggestion does carry, whichever language it is in ([OwnWord.isSuggestion]).
 * A suggestion joins nothing, so no card holds it to be read off, and its one text is the one to show.
 */
val OwnWord.suggestionText: String get() = languages.firstOrNull()?.let { texts[it] }.orEmpty()

/**
 * Every half the learner wrote, in [OwnWord.languages] order —
 * how a pair the open profile cannot study reads, neither half being the known or the learning side here.
 */
val OwnWord.writtenLine: String get() = languages.mapNotNull { texts[it] }.joinToString(PAIR_JOINER)

/** The halves [stamp] pairs, the learning side first as a card reads ([net.spross.kern.model.pairLine]). */
fun OwnWord.profileLine(stamp: JoinStamp): String =
    listOfNotNull(texts[stamp.target], texts[stamp.source]).joinToString(PAIR_JOINER)
