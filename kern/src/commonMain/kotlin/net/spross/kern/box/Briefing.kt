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
 * [GrowthStage.Suspended] and everything unscheduled appear nowhere: what is listed is
 * where to reach FIRST, never a fence. [OwnWords] are out as well — the box's most
 * personal content, and this is the one text that leaves the device.
 */
data class Briefing(
    val learnerName: String?,
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
            appendLine("The lists below come out of Spross, the app I learn with.")
            appendLine()
            appendLine(protocol())
            if (freeCount > 0) {
                appendLine()
                appendLine("WORDS I HAVE — $freeCount; say anything to me in these")
                for (area in free) appendLine("${area.title}: ${area.words.joinToString(", ")}")
            }
            if (inPlay.isNotEmpty()) {
                appendLine()
                appendLine("WORDS I AM LEARNING RIGHT NOW — ${inPlay.size}")
                for (word in inPlay) appendLine("${word.target} = ${word.source}")
            }
            if (newWords.isNotEmpty()) {
                appendLine()
                appendLine("WHAT THE APP TEACHES ME NEXT — prefer these when you bring in a word")
                for (word in newWords) appendLine("${word.target} (${word.source})")
            }
            appendLine()
            appendLine(firstTurn())
            appendLine()
            append(harvestAsk())
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
        Be my conversation partner.
        Talk to me in $targetName;
        explain in $sourceName only where I stall or ask.
        Build what you say out of the words below.
        Bring in your own words where needed, one or two at a time,
        glossed in $sourceName the first time.
        One to three short sentences per turn.
        Ask me one question per turn.
        Correct mistakes by mirroring inside your answer —
        explain if asked or the mistake repeats.
    """.trimIndent()

    /** The opening turn: something to read, before the learner has had to say anything. */
    private fun firstTurn(): String = """
        START HERE, before I say anything:
        write a short story on a topic suiting the words I am learning.
        Three to five blocks of two sentences — the same sentence in $targetName and
        $sourceName, line break between them, blank line between blocks.
        Put $targetName first in block 1, $sourceName first in block 2, and keep swapping.
        Keep the pair word-for-word where grammar allows; otherwise give the idiomatic
        line with the literal one in brackets.
        Then ask whether to go deeper, switch topic, or just talk.
    """.trimIndent()

    /** The ask that closes the loop: the words the conversation turned up, fenced for [Harvest]. */
    private fun harvestAsk(): String {
        val example = newWords.firstOrNull() ?: inPlay.firstOrNull()
        return """
            Check in at a natural pause, or whenever I ask. Focus on key words that
            came up repeatedly and were not part of the prompt, one per line as
            `$targetName = $sourceName`, fenced ```spross, and remind me to paste it
            back into Spross to add them:

            ```spross
            ${example?.target ?: "…"} = ${example?.source ?: "…"}
            ```
        """.trimIndent()
    }
}

/** Building a [Briefing] out of a box; reading a conversation's answer back is [Harvest]'s. */
object Briefings {

    /** How wide the new-word preference is drawn — a round's worth, give or take. */
    const val NEW_LIMIT: Int = 15

    /** Words in play past which the brief stops naming what is next. */
    const val IN_PLAY_BUSY: Int = 30

    private val IN_PLAY_STAGES = setOf(
        GrowthStage.Learning,
        GrowthStage.Fresh,
        GrowthStage.Relearning,
    )

    fun of(state: BoxState, catalog: Catalog, learnerName: String?): Briefing {
        val stages = state.cards.mapValues { (_, card) ->
            state.scheduling[card.id]?.let { stageOf(state, it) }
        }
        val joined = Inventory.joinedCards(state).filter { it.area != OwnWords.AREA }
        val free = joined
            .filter { stages[it.id] == GrowthStage.Consolidated || stages[it.id] == GrowthStage.Matured }
            .groupBy { it.area }
            .map { (area, cards) ->
                BriefArea(
                    title = areaTitle(catalog, state, area),
                    words = cards.map { targetForm(it) },
                )
            }
        val inPlay = joined
            .filter { stages[it.id] in IN_PLAY_STAGES }
            .map { BriefWord(targetForm(it), it.source.text) }
        val newWords = if (inPlay.size >= IN_PLAY_BUSY) {
            emptyList()
        } else {
            val candidates = Growth.newCandidates(state, NEW_LIMIT, NEW_LIMIT)
            (candidates.newCards + candidates.unlockedPhrases)
                .mapNotNull { state.cards[it] }
                .filter { it.area != OwnWords.AREA }
                .map { BriefWord(targetForm(it), it.source.text) }
        }
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
        catalog.areaTitle(area, Catalog.FALLBACK_SOURCE)
            ?: catalog.areaTitle(area, state.joinStamp.source)
            ?: area

    private fun languageName(catalog: Catalog, code: String): String =
        catalog.languages[code]?.englishName ?: code
}
