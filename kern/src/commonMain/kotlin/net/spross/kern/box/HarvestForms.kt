package net.spross.kern.box

import net.spross.kern.model.articledForm
import net.spross.kern.model.caseFolded
import net.spross.kern.session.AnswerNormalizer

/**
 * Where a pasted pair stands against the box it came home to.
 *
 * The three are a KEEPING order, not a quality order: [New] is what the learner asked the
 * conversation for, and the other two are what an assistant cannot know it is handing back
 * twice — it was told to gloss what was new to the LEARNER, and has never seen the catalog.
 */
enum class HarvestKind {
    /** Nothing in the box looks like it. The only kind that arrives ticked. */
    New,

    /** The box teaches something spelled almost like it that also means almost the same. */
    Near,

    /** The box already teaches this exact form. */
    Held,
}

/** One pasted pair with what the box already has of it. */
data class HarvestWord(
    val word: BriefWord,
    val kind: HarvestKind,
    /** The box's own form this one leans on, as the box writes it; null when [HarvestKind.New]. */
    val match: String?,
)

/**
 * The box as a paste has to ask about it: is this word here already, is something like it
 * here, or is it genuinely new.
 *
 * Built once per paste — a walk of every card — and asked once per pasted line.
 *
 * "Something like it" wants BOTH sides of the pair to lean the same way: a spelling that
 * relates AND a gloss that does not contradict it. Either side alone is noise in a language
 * that builds long words out of short ones — sw `kupotea` ("get lost") is one letter off
 * `kupokea` ("receive") and `anga` ("sky") sits inside `kuchanganya` ("confuse"), and neither
 * is the word the box already teaches. The spelling side catches a slip or two, and a taught
 * word sitting INSIDE a longer one where the extra letters do not outweigh the shared ones —
 * agglutinating languages hand back whole phrases as single words, sw `ninapenda` around
 * `penda`, while `hapa` inside `tunamaliza hapa` is a different word standing next to it.
 * The gloss side only VETOES: two glosses whose telling words are strangers are two words,
 * and a gloss too short to have telling words says nothing either way.
 */
internal class BoxForms(state: BoxState) {

    /** One written form of the box's, folded for comparison, with the gloss it was taught by. */
    private class Known(val folded: String, val shown: String, val gloss: Set<String>)

    /** Every written target form against the form as the box writes it — the exact lookup. */
    private val targets = LinkedHashMap<String, String>()

    private val known = mutableListOf<Known>()

    init {
        for (card in state.cards.values) {
            val shown = articledForm(card.target.grammar["gender"], card.target.text)
            val gloss = stems(card.source.text)
            put(card.target.text, shown, gloss)
            put(shown, shown, gloss)
            card.target.synonyms.forEach { put(it, shown, gloss) }
            card.target.variants.forEach { put(it, shown, gloss) }
        }
        for (word in state.ownWords) {
            val shown = word.texts[state.joinStamp.target] ?: continue
            put(shown, shown, stems(word.texts[state.joinStamp.source].orEmpty()))
        }
    }

    /** Which of the three [HarvestKind]s [word] is, and the form that decided it. */
    fun standing(word: BriefWord): HarvestWord {
        val target = caseFolded(word.target)
        targets[target]?.let { return HarvestWord(word, HarvestKind.Held, it) }
        val near = nearForm(target, stems(word.source))
        return if (near == null) HarvestWord(word, HarvestKind.New, null)
        else HarvestWord(word, HarvestKind.Near, near)
    }

    private fun put(form: String, shown: String, gloss: Set<String>) {
        val folded = caseFolded(form)
        if (targets.containsKey(folded)) return
        targets[folded] = shown
        known += Known(folded, shown, gloss)
    }

    /** The box's form this one leans on: spelled close enough, and not glossed against it. */
    private fun nearForm(target: String, gloss: Set<String>): String? {
        if (target.length < MIN_STEM) return null
        for (form in known) {
            if (form.folded.length < MIN_STEM) continue
            if (!spellingLeans(target, form.folded)) continue
            if (!glossesAgree(gloss, form.gloss)) continue
            return form.shown
        }
        return null
    }

    /**
     * One word standing inside the other with the shared letters outweighing the extra ones,
     * or a spelling a slip or two off.
     */
    private fun spellingLeans(one: String, other: String): Boolean {
        val longest = maxOf(one.length, other.length)
        val shortest = minOf(one.length, other.length)
        if (one.contains(other) || other.contains(one)) return shortest * 2 >= longest
        val slips = if (shortest >= TWO_SLIP_LENGTH) 2 else 1
        if (longest - shortest > slips) return false
        return AnswerNormalizer.damerauLevenshtein(one, other) <= slips
    }

    /**
     * Whether two glosses could be saying the same thing: one telling word shared, counting a
     * word the other's is built on. A gloss with no telling word at all agrees with anything —
     * it is silence, not disagreement.
     */
    private fun glossesAgree(one: Set<String>, other: Set<String>): Boolean {
        if (one.isEmpty() || other.isEmpty()) return true
        return one.any { mine -> other.any { theirs -> sharedRoot(mine, theirs) } }
    }

    private fun sharedRoot(one: String, other: String): Boolean =
        one.commonPrefixWith(other).length >= MIN_STEM

    private companion object {
        /** Under this many letters a shared spelling is a coincidence rather than a stem. */
        const val MIN_STEM = 4

        /** From this length on, a word survives two slips and is still the same word. */
        const val TWO_SLIP_LENGTH = 8


        /** A gloss as its telling words: articles and pronouns are too short to count. */
        fun stems(text: String): Set<String> {
            val found = mutableSetOf<String>()
            val part = StringBuilder()
            for (ch in caseFolded(text)) {
                if (ch.isLetter()) {
                    part.append(ch)
                } else {
                    if (part.length >= MIN_STEM) found += part.toString()
                    part.clear()
                }
            }
            if (part.length >= MIN_STEM) found += part.toString()
            return found
        }
    }
}
