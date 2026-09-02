package net.spross.kern.box

import net.spross.kern.model.articledForm
import net.spross.kern.model.nfcNormalized

/**
 * What a conversation sends home: the words it turned up, read back off whatever the
 * learner pasted in.
 *
 * The outbound half ([Briefing]) is only half a loop. A conversation surfaces words no
 * catalog has — the learner's job, their street, their kid's school — and those are
 * exactly what [OwnWord] exists to hold, so the brief asks for them in a fenced block and
 * this reads that block back. Same interchange format both ways, same argument for living
 * in kern: a parser is a rule about a format, and two platforms parsing it separately is
 * two parsers that disagree about the same paste.
 *
 * It parses an ASSISTANT, so it forgives one: fences it did not ask for, bullets, numbering,
 * backticks, an arrow where an `=` was asked for, and prose wrapped around the whole thing.
 * What it will not do is import silently — it hands back a list, and the surface asks.
 */
object Harvest {

    /** The fence the brief asks for. Anything inside one wins over the prose around it. */
    const val FENCE_TAG: String = "spross"

    /**
     * The most words one paste may bring home. An assistant asked for the words that came
     * up in an hour and answering with a dictionary is a misunderstanding, not a windfall:
     * a hundred unreviewed words is a box the learner did not choose to have.
     */
    const val MAX_WORDS: Int = 50

    /** Longer than this on either side and it is a sentence the assistant explained with. */
    private const val MAX_LENGTH: Int = 60

    /** `=` is what the brief asks for; an arrow is what an assistant prettifies it into. */
    private val SEPARATORS = charArrayOf('=', '→')

    /**
     * What a line may OPEN with and mean nothing by. The arrow is in here as well as in
     * [SEPARATORS]: a line cannot begin with the separator between its two halves, so one
     * standing there is a bullet an assistant drew with the character it had in hand.
     */
    private const val BULLETS = "-*•–—>→"

    private val NUMBERING = Regex("""^\d+[.)]\s*""")

    /**
     * The pairs [text] carries that the box does not already hold, in the order they were
     * written, at most [MAX_WORDS] of them.
     *
     * A word the box HAS is dropped rather than offered: the assistant was told to gloss
     * what was new to the learner and cannot know which of those the catalog already
     * teaches, so the overlap is its mistake to absorb, not a decision to hand back.
     */
    fun read(text: String, state: BoxState): List<BriefWord> {
        val known = knownForms(state)
        val seen = mutableSetOf<String>()
        val found = mutableListOf<BriefWord>()
        for (line in fenced(text) ?: text.lines()) {
            val word = parseLine(line) ?: continue
            val key = fold(word.target)
            if (key in known || !seen.add(key)) continue
            found += word
            if (found.size == MAX_WORDS) break
        }
        return found
    }

    /**
     * One harvested pair as a word the box can hold. Which side lands in which language is
     * the join's answer, not the caller's — the brief wrote `target = source` and this is
     * the same fact read back.
     *
     * [BoxEngine.addOwnWord] stamps the age. Add them ONE at a time off the state the last
     * one returned: the id is minted against the ids already taken, and a batch minted
     * against one stale state mints the same id twice.
     */
    fun ownWord(state: BoxState, word: BriefWord): OwnWord = OwnWords.write(
        id = OwnWords.mint(word.target, state.ownWords.mapTo(mutableSetOf()) { it.id }),
        kind = OwnWords.DEFAULT_KIND,
        emoji = null,
        texts = mapOf(
            state.joinStamp.source to word.source,
            state.joinStamp.target to word.target,
        ),
    )

    /**
     * The lines inside ```spross fences, or null when the paste carries none — an assistant
     * that dropped the fence is answering the question anyway, so the whole text is read
     * instead. A fence of another kind (an example, a code block) is not one of ours and
     * its lines never reach the parser.
     */
    private fun fenced(text: String): List<String>? {
        var inside = false
        val lines = mutableListOf<String>()
        var found = false
        for (line in text.lines()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                inside = !inside && trimmed.drop(3).trim().equals(FENCE_TAG, ignoreCase = true)
                if (inside) found = true
                continue
            }
            if (inside) lines += line
        }
        return if (found) lines else null
    }

    private fun parseLine(raw: String): BriefWord? {
        var line = raw.trim().trim('`').trim()
        while (line.isNotEmpty() && line.first() in BULLETS) line = line.drop(1).trim()
        line = line.replaceFirst(NUMBERING, "")
        val cut = line.indexOfFirst { it in SEPARATORS }
        if (cut <= 0) return null
        val target = clean(line.take(cut))
        val source = clean(line.drop(cut + 1))
        if (target.isEmpty() || source.isEmpty()) return null
        if (target.length > MAX_LENGTH || source.length > MAX_LENGTH) return null
        return BriefWord(target = target, source = source)
    }

    private fun clean(part: String): String =
        nfcNormalized(part.trim().trim('`', '*', '"', '\'', '“', '”').trim()).trimEnd('.', ',', ';')

    private fun fold(form: String): String = nfcNormalized(form.trim()).lowercase()

    /**
     * Every written form the box would count as a duplicate: each card's target text, its
     * synonyms and variants, and the ARTICLED form beside the bare one — an assistant
     * writing back "das Amt" for a card the catalog keys as "Amt" has found nothing new.
     */
    private fun knownForms(state: BoxState): Set<String> {
        val forms = mutableSetOf<String>()
        for (card in state.cards.values) {
            forms += fold(card.target.text)
            forms += fold(articledForm(card.target.grammar["gender"], card.target.text))
            card.target.synonyms.forEach { forms += fold(it) }
            card.target.variants.forEach { forms += fold(it) }
        }
        for (word in state.ownWords) word.texts.values.forEach { forms += fold(it) }
        return forms
    }
}
