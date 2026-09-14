package net.spross.kern.trainer

/**
 * One word of a phrase, with an identity of its own.
 *
 * [id] is the atom's position in the phrase as authored, and it is what an arrangement is
 * measured by: a phrase may print the same word twice ("der Mann und der Hund"), so comparing
 * raw text would call a swapped pair correct.
 */
data class ScrambleAtom(val id: Int, val text: String)

/**
 * How a phrase is cut into the pieces an arrangement moves — a CONTENT rule, so it is kern's
 * and not each platform's.
 *
 * Whitespace is the only seam. Punctuation stays attached to the word it was authored on, so
 * rejoining the atoms with single spaces gives the phrase back and nothing has to be re-glued
 * on the way out; runs of whitespace collapse, which is the only way the round trip can differ
 * from the authored string.
 */
object ScrambleTokenizer {

    private val whitespace = Regex("\\s+")

    /** The words of [text], in authored order. */
    fun tokens(text: String): List<String> =
        text.trim().split(whitespace).filter { it.isNotEmpty() }

    /** The same words, each carrying the position it was authored at. */
    fun atoms(text: String): List<ScrambleAtom> =
        tokens(text).mapIndexed { index, word -> ScrambleAtom(index, word) }

    /** What an arrangement of [atoms] reads as — the inverse of [tokens]. */
    fun joined(atoms: List<ScrambleAtom>): String = atoms.joinToString(" ") { it.text }
}
