package net.spross.kern.box

import net.spross.kern.catalog.Catalog
import net.spross.kern.model.Card
import net.spross.kern.model.articledForm

/** One word as a brief carries it: the form being learned, and what the learner calls it. */
data class BriefWord(val target: String, val source: String)

/** A shelf of words the learner may be spoken to in, headed as the catalog names it. */
data class BriefArea(val title: String, val words: List<String>)

/**
 * What the learner tells an outside conversation partner about themselves, in their voice.
 *
 * The app ships no AI: it hands the learner this text, and they paste it into whichever
 * assistant they already have — as a first message, or into the standing-instructions
 * field one keeps for what its user is like, which is why it is written first person
 * (§ [opening]). So the brief is an INTERCHANGE format like [Feedback] and
 * lives here for the same reasons — its reader is a machine rather than the learner's
 * device, one dialect rather than one per platform — and it is written in English, which
 * is neither of the learner's two languages but is the one every assistant reads best.
 * The two languages are NAMED inside it; the instructions around them are not translated.
 *
 * Three blocks, and the Sprossen decide which word lands in each ([GrowthStage]).
 * Only the FIRST of them is a rule: consolidated and matured words are the ground the
 * conversation is built out of, and staying on it is the whole of what the box asks.
 * The ones still coming in are an OFFER — reached for where the talk passes them, left
 * alone where it does not — and the [Growth.newCandidates] list is a preference among
 * words the partner was going to bring in anyway. Both are worded that way on purpose:
 * a quota turns a conversation into an exercise (§ [protocol]).
 *
 * The new words are still what makes this part of the loop rather than a parallel course,
 * where the partner takes the hint: a word met in talk on Tuesday is a word with a hook in
 * it when the box deals it on Thursday.
 *
 * [GrowthStage.Suspended] appears nowhere: a word taken out of rotation is one the learner
 * has said they do not want to meet. Neither does anything unscheduled — naming 800 words
 * to forbid them would be the whole catalog with a "no" on it, so what is listed is where
 * to reach FIRST, never a fence.
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

    /**
     * Whether there is a conversation to be had at all. A box with nothing introduced
     * briefs nobody — the surface offering this hides instead of handing over a page of
     * instructions with three empty lists under them.
     */
    val hasWords: Boolean get() = freeCount > 0 || inPlay.isNotEmpty()

    /** The whole brief, ready to be pasted into an assistant. */
    val text: String
        get() = buildString {
            appendLine(opening())
            appendLine("Below are the words I have so far, out of Spross, the app I learn with.")
            appendLine()
            appendLine(protocol())
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
     * The learner's own opening line, because the brief is theirs to hand over.
     *
     * FIRST PERSON throughout, and not a flourish: this text is pasted into a chat as the
     * learner's first message, or into the standing-instructions field an assistant keeps
     * for what its user is like — both of which are written in the user's voice, and the
     * second of which reads a third-person "the learner has 60 words" as a document about
     * somebody who is not in the room.
     */
    private fun opening(): String {
        val name = learnerName?.trim()?.takeIf { it.isNotEmpty() }
        val who = if (name == null) "I am" else "I, $name, am"
        return "$who learning $targetName — my own language is $sourceName."
    }

    /**
     * How the partner is asked to behave.
     *
     * The ONE hard rule is the ground: build out of the words the learner has. Everything
     * else is a preference, deliberately — a partner told to place eight words per session
     * steers a conversation about dinner into somebody's relatives because that is the shelf
     * the box happens to be on, and a learner who cannot say what they wanted to say has
     * been handed an exercise rather than a conversation. So the words being learned are
     * offered where the talk passes them, new words are the partner's own call, and what
     * the learner wants to talk about outranks both.
     *
     * The grammar line is load-bearing for the same reason: the box teaches WORDS and
     * conjugates nothing, so a partner left to assume otherwise writes perfect subordinate
     * clauses at someone who has met nouns.
     *
     * That line governs the SENTENCE, and says so twice because the half of it a partner
     * acts on otherwise is the wrong half: told only to keep it short, it answers in one
     * clipped line, and a learner handed four words has nothing to answer. Two or three
     * short sentences carry a turn — the conversation is meant to die of difficulty, if it
     * dies at all, never of thrift.
     */
    private fun protocol(): String = listOf(
        "Be my conversation partner. Talk to me in $targetName;",
        "explain in $sourceName, and only where I stall or ask you to.",
        "We talk about whatever I bring up, and you follow where I take it.",
        "Build what you say out of the words below — that is the one thing that matters here.",
        "Bring in a word of your own where the conversation needs one, one or two at a time,",
        "and gloss it in $sourceName the first time. Never bend the conversation toward a word:",
        "a word forced into a turn that had no room for it teaches me nothing.",
        "The app teaches me WORDS, not grammar: assume I have been taught nothing about tense,",
        "case or agreement, so keep each sentence short and concrete.",
        "Short sentences, though, not short turns — say two or three of them, so there is",
        "something for me to answer; a single clipped line leaves me nowhere to go.",
        "Ask me one question per turn and wait for my answer.",
        "Correct at most one mistake per turn, in $sourceName, after answering what I said.",
        "Never list vocabulary back at me. Talk to me.",
    ).joinToString("\n")

    /**
     * The ask that closes the loop: the words the conversation turned up, fenced so the Box
     * can read them back ([Harvest]). Asked for in prose rather than behind a stop
     * word, because a stop word would have to be written in one of the learner's two
     * languages and this text is in neither.
     */
    private fun closing(): String {
        val example = newWords.firstOrNull() ?: inPlay.firstOrNull()
        return listOf(
            "When I say I am done, list the words I met that were new to me, one per line",
            "as `$targetName = $sourceName`, in a block fenced ```spross:",
            "",
            "```spross",
            "${example?.target ?: "…"} = ${example?.source ?: "…"}",
            "```",
        ).joinToString("\n")
    }
}

/** Building a [Briefing] out of a box; reading a conversation's answer back is [Harvest]'s. */
object Briefings {

    /** Words the "worth reaching for" block may name before it stops being a list anyone reads. */
    const val IN_PLAY_LIMIT: Int = 40

    /** How wide the new-word preference is drawn — a round's worth, give or take. */
    const val NEW_LIMIT: Int = 15

    /** What the learner's own shelf is called in a text written in their voice. */
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

    /**
     * Headings read in ENGLISH first — the brief's own language — then in the learner's,
     * then as the area key: a shelf named by its key is a visible content gap rather than
     * a blank line.
     */
    private fun areaTitle(catalog: Catalog, state: BoxState, area: String): String =
        if (area == OwnWords.AREA) OWN_TITLE
        else catalog.areaTitle(area, Catalog.FALLBACK_SOURCE)
            ?: catalog.areaTitle(area, state.joinStamp.source)
            ?: area

    private fun languageName(catalog: Catalog, code: String): String =
        catalog.languages[code]?.englishName ?: code
}
