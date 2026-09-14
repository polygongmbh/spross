package net.spross.kern.trainer

/**
 * One chip of a phrase, with an identity of its own.
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
 * Whitespace is one seam and PUNCTUATION is the other, because a mark left riding on a word
 * answers the question before it is asked: the chip carrying "?" is the last one, the chip
 * carrying "¿" is the first. A mark therefore stands as a chip of its own, to be placed like
 * any other.
 *
 * The full stop is the exception and is dropped outright: a phrase ends where it ends, and a
 * chip saying so would be a free placement. A period INSIDE a token ("a.m.") is spelling, not
 * a stop, and stays. Everything a mark gives away is restorable for the reveal, which shows the
 * text as authored ([SentenceScrambleTask.display]) rather than a rejoin of the atoms.
 *
 * A rejoined mark sits TIGHT against its word, so a language that sets a space before it
 * (French "Tu me vois ?") reads back without that space; runs of whitespace collapse for the
 * same reason. Both are typography rather than word order, and neither is what the drill grades.
 */
object ScrambleTokenizer {

    /** Marks that close what they stand after, and so rejoin with no space before them. */
    private const val CLOSING_MARKS = "?!,;:…"

    /** Marks that open what they stand before — Spanish leads a question and an exclamation. */
    private const val OPENING_MARKS = "¿¡"

    private const val MARKS = CLOSING_MARKS + OPENING_MARKS

    /** The words of [text], in authored order — whitespace only, marks still attached. */
    fun tokens(text: String): List<String> =
        text.trim().split(whitespace).filter { it.isNotEmpty() }

    /** The chips of [text], each carrying the position it was authored at. */
    fun atoms(text: String): List<ScrambleAtom> {
        val words = tokens(text)
        var id = 0
        return words.flatMapIndexed { index, word -> chips(word, last = index == words.lastIndex) }
            .map { ScrambleAtom(id++, it) }
    }

    /** Whether [text] is a mark standing on its own rather than a word to put in order. */
    fun isMark(text: String): Boolean = text.isNotEmpty() && text.all { it in MARKS }

    /** What an arrangement of [atoms] reads as — a mark hugs the side it was cut from. */
    fun joined(atoms: List<ScrambleAtom>): String {
        val out = StringBuilder()
        var afterOpening = false
        for (atom in atoms) {
            val closing = atom.text.isNotEmpty() && atom.text.all { it in CLOSING_MARKS }
            if (out.isNotEmpty() && !closing && !afterOpening) out.append(' ')
            out.append(atom.text)
            afterOpening = atom.text.isNotEmpty() && atom.text.all { it in OPENING_MARKS }
        }
        return out.toString()
    }

    /**
     * One authored token as the chips it is worth: its leading marks, its word, its trailing
     * ones. [last] drops the phrase's own full stop — but only where the token carries a single
     * period, so "a.m." keeps both of its own.
     */
    private fun chips(word: String, last: Boolean): List<String> {
        val stopped = if (last && word.endsWith('.') && word.count { it == '.' } == 1) {
            word.dropLast(1)
        } else {
            word
        }
        val lead = stopped.takeWhile { it in OPENING_MARKS }
        val rest = stopped.drop(lead.length)
        val trail = rest.takeLastWhile { it in CLOSING_MARKS }
        val body = rest.dropLast(trail.length)
        if (body.isEmpty()) return if (stopped.isEmpty()) emptyList() else listOf(stopped)
        return lead.map(Char::toString) + body + trail.map(Char::toString)
    }

    private val whitespace = Regex("\\s+")
}
