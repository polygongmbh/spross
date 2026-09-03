package net.spross.kern.box

import kotlin.math.abs
import net.spross.kern.model.articledForm
import net.spross.kern.model.nfcNormalized
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

    /** The box teaches something spelled almost like it, inside it, or glossed the same. */
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
 * "Something like it" is deliberately WIDE, because the two mistakes are not the same size:
 * a near word shown beside the form it leans on costs one tap to keep, while a missed one
 * is a second card teaching a word the box already has. It catches three shapes:
 * a spelling one or two slips off, a taught word sitting INSIDE a longer one — agglutinating
 * languages hand back whole phrases as single words, sw `ninapenda` around `penda` — and a
 * gloss whose telling words are the other's, which is how a pair the target side hides
 * (`ninapenda` = "I love") is still caught on the language the learner reads.
 */
internal class BoxForms(state: BoxState) {

    /** Every written target form, folded for comparison, against the form as the box writes it. */
    private val targets = LinkedHashMap<String, String>()

    /** Each card's gloss reduced to its telling words, against that card's target form. */
    private val glosses = mutableListOf<Pair<Set<String>, String>>()

    init {
        for (card in state.cards.values) {
            val shown = articledForm(card.target.grammar["gender"], card.target.text)
            put(card.target.text, shown)
            put(shown, shown)
            card.target.synonyms.forEach { put(it, shown) }
            card.target.variants.forEach { put(it, shown) }
            glosses += stems(card.source.text) to shown
        }
        for (word in state.ownWords) {
            val shown = word.texts[state.joinStamp.target] ?: continue
            put(shown, shown)
            word.texts[state.joinStamp.source]?.let { glosses += stems(it) to shown }
        }
    }

    /** Which of the three [HarvestKind]s [word] is, and the form that decided it. */
    fun standing(word: BriefWord): HarvestWord {
        val target = fold(word.target)
        targets[target]?.let { return HarvestWord(word, HarvestKind.Held, it) }
        val near = nearForm(target) ?: nearGloss(stems(word.source))
        return if (near == null) HarvestWord(word, HarvestKind.New, null)
        else HarvestWord(word, HarvestKind.Near, near)
    }

    private fun put(form: String, shown: String) {
        targets.getOrPut(fold(form)) { shown }
    }

    /** A spelling a slip or two off, or one word standing inside the other. */
    private fun nearForm(target: String): String? {
        if (target.length < MIN_STEM) return null
        for ((folded, shown) in targets) {
            if (folded.length < MIN_STEM) continue
            if (target.contains(folded) || folded.contains(target)) return shown
            val slips = if (minOf(folded.length, target.length) >= TWO_SLIP_LENGTH) 2 else 1
            if (abs(folded.length - target.length) > slips) continue
            if (AnswerNormalizer.damerauLevenshtein(target, folded) <= slips) return shown
        }
        return null
    }

    /** One gloss carrying every telling word of the other — the same meaning, longer or shorter. */
    private fun nearGloss(stems: Set<String>): String? {
        if (stems.isEmpty()) return null
        for ((known, shown) in glosses) {
            if (known.isEmpty()) continue
            if (stems.containsAll(known) || known.containsAll(stems)) return shown
        }
        return null
    }

    private companion object {
        /** Under this many letters a shared spelling is a coincidence rather than a stem. */
        const val MIN_STEM = 4

        /** From this length on, a word survives two slips and is still the same word. */
        const val TWO_SLIP_LENGTH = 8

        fun fold(form: String): String = nfcNormalized(form.trim()).lowercase()

        /** A gloss as its telling words: articles and pronouns are too short to count. */
        fun stems(text: String): Set<String> {
            val found = mutableSetOf<String>()
            val part = StringBuilder()
            for (ch in fold(text)) {
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
