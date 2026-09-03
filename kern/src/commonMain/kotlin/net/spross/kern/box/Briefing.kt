package net.spross.kern.box

import net.spross.kern.catalog.Catalog
import net.spross.kern.model.Card
import net.spross.kern.model.articledForm

/** One word as a brief carries it: the form being learned, and what the learner calls it. */
data class BriefWord(val target: String, val source: String)

/** A shelf of words, headed as the catalog names the area. */
data class BriefArea(val title: String, val words: List<String>)

/**
 * What the learner tells an outside conversation partner about themselves, in their voice.
 *
 * An INTERCHANGE format like [Feedback], and in kern for the same reasons: its reader is a
 * machine rather than the learner's device, so one dialect rather than one per platform,
 * and written in English — neither of the learner's two languages, and the one every
 * assistant reads best. Those two are NAMED inside it, never translated around.
 *
 * The app hosts no assistant: this TEXT is its whole contribution, pasted by the learner
 * into whichever one they already have. An in-app chat would put a running cost on a free
 * offline app and tie it to one vendor's terms and one vendor's outage.
 *
 * [GrowthStage.Suspended] and everything unscheduled appear nowhere: naming 800 words to
 * forbid them would be the whole catalog with a "no" on it, so what is listed is where to
 * reach FIRST, never a fence.
 */
data class Briefing(
    val learnerName: String?,
    /** English exonyms — a brief written in English names both sides in it. */
    val sourceName: String,
    val targetName: String,
    val free: List<BriefArea>,
    val inPlay: List<BriefWord>,
    val newWords: List<BriefWord>,
) {
    val freeCount: Int get() = free.sumOf { it.words.size }

    /** Whether there is a conversation to be had: a box with nothing introduced briefs nobody. */
    val hasWords: Boolean get() = freeCount > 0 || inPlay.isNotEmpty()

    /** The whole brief, ready to be pasted into an assistant. */
    val text: String
        get() = buildString {
            appendLine(opening())
            appendLine("Below are the words I have so far, out of Spross, the app I learn with.")
            appendLine()
            appendLine(protocol())
            appendLine()
            appendLine(firstTurn())
            if (freeCount > 0) {
                appendLine()
                appendLine("WORDS I HAVE — $freeCount I have made mine; say anything to me in these")
                for (area in free) appendLine("${area.title}: ${area.words.joinToString(", ")}")
            }
            if (inPlay.isNotEmpty()) {
                appendLine()
                appendLine(
                    "WORDS I AM LEARNING RIGHT NOW — ${inPlay.size} of them. Where the talk goes " +
                        "near one, reach for it; where it does not, leave it.",
                )
                for (word in inPlay) appendLine("${word.target} = ${word.source}")
            }
            if (newWords.isNotEmpty()) {
                appendLine()
                appendLine(
                    "WHAT THE APP TEACHES ME NEXT — when you bring in a word of your own, one of " +
                        "these lands best, but any word the conversation needs is fine.",
                )
                for (word in newWords) appendLine("${word.target} (${word.source})")
            }
            appendLine()
            append(closing())
        }

    /**
     * FIRST PERSON, and load-bearing: the brief is pasted either as a first message or into
     * the standing-instructions field an assistant keeps for what its user is like.
     */
    private fun opening(): String {
        val name = learnerName?.trim()?.takeIf { it.isNotEmpty() }
        val who = if (name == null) "I am" else "I, $name, am"
        return "$who learning $targetName — my own language is $sourceName."
    }

    /** How the partner is asked to behave. Only the ground is a rule; the rest are offers. */
    private fun protocol(): String = """
        Be my conversation partner. Talk to me in $targetName;
        explain in $sourceName, and only where I stall or ask you to.
        We talk about whatever I bring up, and you follow where I take it.
        Build what you say out of the words below — that is the one thing that matters here.
        Bring in a word of your own where the conversation needs one, one or two at a time,
        and gloss it in $sourceName the first time. Never bend the conversation toward a word:
        a word forced into a turn that had no room for it teaches me nothing.
        The app teaches me WORDS, not grammar: assume I have been taught nothing about tense,
        case or agreement, so keep each sentence short and concrete.
        Short sentences, not short turns: two or three of them, not one clipped line.
        Ask me one question per turn and wait for my answer.
        Correct at most one mistake per turn, in $sourceName, after answering what I said.
        Never list vocabulary back at me. Talk to me.
    """.trimIndent()

    /** The opening turn: something to read, before the learner has had to say anything. */
    private fun firstTurn(): String = """
        START HERE, before I say anything: write me a short story to practice on,
        on a topic the words below cover, built out of them.
        Blocks of two sentences — one in $targetName, the same one in $sourceName,
        a line break between them, a blank line between blocks.
        Alternate which language leads. Five or six blocks, so I can start reading quickly.
        Keep the pair as close to word-for-word as the grammar allows, so I can map one
        line onto the other; where that reads unnaturally, give the idiomatic line and the
        literal one in brackets after it.
        Then ask me whether to go deeper, switch topic, or just talk.
    """.trimIndent()

    /** The ask that closes the loop: the words the conversation turned up, fenced for [Harvest]. */
    private fun closing(): String {
        val example = newWords.firstOrNull() ?: inPlay.firstOrNull()
        return """
            When I say I am done, list the words I met that were new to me, one per line
            as `$targetName = $sourceName`, in a block fenced ```spross:

            ```spross
            ${example?.target ?: "…"} = ${example?.source ?: "…"}
            ```
        """.trimIndent()
    }
}

/** Building a [Briefing] out of a box; reading a conversation's answer back is [Harvest]'s. */
object Briefings {

    /** Words the learning block may name before it stops being a list anyone reads. */
    const val IN_PLAY_LIMIT: Int = 40

    /** How wide the new-word preference is drawn — a round's worth, give or take. */
    const val NEW_LIMIT: Int = 15

    /** What the learner's own shelf is called; the catalog has no title for it. */
    private const val OWN_TITLE: String = "Words I wrote down myself"

    private val IN_PLAY_STAGES = setOf(
        GrowthStage.Learning,
        GrowthStage.Fresh,
        GrowthStage.Relearning,
    )

    fun of(state: BoxState, catalog: Catalog, learnerName: String?): Briefing {
        val stages = state.cards.mapValues { (_, card) ->
            state.scheduling[card.id]?.let { stageOf(state, it) }
        }
        val joined = Inventory.joinedCards(state)
        val free = joined
            .filter { stages[it.id] == GrowthStage.Consolidated || stages[it.id] == GrowthStage.Matured }
            .groupBy { it.area }
            .map { (area, cards) ->
                BriefArea(
                    title = areaTitle(catalog, state, area),
                    // why: an own word is the one entry an assistant cannot already know,
                    // so it carries its meaning where a catalog word needs none.
                    words = cards.map { card ->
                        if (area == OwnWords.AREA) "${targetForm(card)} (${card.source.text})"
                        else targetForm(card)
                    },
                )
            }
        val inPlay = joined
            .filter { stages[it.id] in IN_PLAY_STAGES }
            // why: the least settled words are the ones a conversation can still save.
            .sortedBy { state.scheduling[it.id]?.memory?.stability ?: 0.0 }
            .take(IN_PLAY_LIMIT)
            .map { BriefWord(targetForm(it), it.source.text) }
        val candidates = Growth.newCandidates(state, NEW_LIMIT, NEW_LIMIT)
        val newWords = (candidates.newCards + candidates.unlockedPhrases)
            .mapNotNull { state.cards[it] }
            .map { BriefWord(targetForm(it), it.source.text) }
        return Briefing(
            learnerName = learnerName,
            sourceName = languageName(catalog, state.joinStamp.source),
            targetName = languageName(catalog, state.joinStamp.target),
            free = free,
            inPlay = inPlay,
            newWords = newWords,
        )
    }

    /** The target form as the brief writes it — with its article, like every spoken one. */
    private fun targetForm(card: Card): String =
        articledForm(card.target.grammar["gender"], card.target.text)

    /** ENGLISH first — the brief's own language — then the learner's, then the bare key. */
    private fun areaTitle(catalog: Catalog, state: BoxState, area: String): String =
        if (area == OwnWords.AREA) OWN_TITLE
        else catalog.areaTitle(area, Catalog.FALLBACK_SOURCE)
            ?: catalog.areaTitle(area, state.joinStamp.source)
            ?: area

    private fun languageName(catalog: Catalog, code: String): String =
        catalog.languages[code]?.englishName ?: code
}
