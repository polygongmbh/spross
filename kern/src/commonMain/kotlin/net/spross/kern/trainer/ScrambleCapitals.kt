package net.spross.kern.trainer

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind

/**
 * Whether a chip's capital belongs to the WORD or only to the place it stands in.
 *
 * A sentence scramble that keeps every capital where the catalog authored it names its own
 * first chip: the one word wearing a capital is the one the sentence opens on. Lowercasing all
 * of them is no answer either — German capitalizes its nouns wherever they stand, and a chip
 * reading "kühlschrank" teaches a spelling that is simply wrong.
 *
 * So only the POSITIONAL capital comes off, and the catalog itself says which those are. A word
 * that is capitalized for its own sake shows up capitalized where position cannot explain it:
 * somewhere other than first inside an authored text ([inherent]), or as a word card's own
 * citation form. A German noun clears that bar over and over; an article, a pronoun, a verb
 * form and a question word never do, because no sentence prints them capitalized mid-way.
 *
 * The rule therefore errs toward KEEPING a capital — a capital left standing is a small hint,
 * one taken off a word that owns it is bad orthography — and its residue is the words the
 * catalog only ever prints sentence-initially: a vocative that names nobody ("Mama, …") comes
 * back lowercase. Nothing reachable from the join tells that apart from an interjection.
 */
internal object ScrambleCapitals {

    /**
     * The tokens this join shows capitalized where position cannot explain it — one set per
     * target language, built once beside the pool that reads it.
     *
     * A phrase or an idiom contributes its non-initial tokens only; a word card contributes
     * every token of every form it authors, its first included, since a citation form stands
     * first by convention rather than by sentence position.
     */
    fun inherent(cards: List<Card>): Set<String> {
        val out = mutableSetOf<String>()
        for (card in cards) {
            val positional = card.kind == CardKind.Phrase || card.kind == CardKind.Idiom
            val forms = listOf(card.target.text) + card.target.teaches + card.target.accepts
            for (form in forms) {
                val tokens = ScrambleTokenizer.atoms(form).map { it.text }
                for ((index, token) in tokens.withIndex()) {
                    if (index == 0 && positional) continue
                    if (token.firstOrNull()?.isUpperCase() == true) out += token
                }
            }
        }
        return out
    }

    /** [atoms] with the leading word's positional capital taken off, where it has one to lose. */
    fun neutralized(atoms: List<ScrambleAtom>, inherent: Set<String>): List<ScrambleAtom> {
        val at = atoms.indexOfFirst { !ScrambleTokenizer.isMark(it.text) }
        if (at < 0) return atoms
        val leading = atoms[at]
        if (leading.text in inherent) return atoms
        val lowered = positionalCapitalOff(leading.text) ?: return atoms
        return atoms.mapIndexed { index, atom -> if (index == at) atom.copy(text = lowered) else atom }
    }

    /**
     * [text] lowercased where its first letter is one position put there, or null where there
     * is nothing positional to take off.
     *
     * A word whose capital is its only letter keeps it: en "I", "I'd" and "I'll" are spelled
     * that way wherever they stand, and the article "A" left capitalized is a hint rather than
     * a misspelling — which is the trade this whole rule makes.
     */
    private fun positionalCapitalOff(text: String): String? {
        val first = text.firstOrNull() ?: return null
        if (!first.isUpperCase()) return null
        if (text.getOrNull(1)?.isLetter() != true) return null
        return first.lowercaseChar() + text.substring(1)
    }
}
