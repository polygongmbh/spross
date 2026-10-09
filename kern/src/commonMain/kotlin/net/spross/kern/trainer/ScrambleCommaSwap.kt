package net.spross.kern.trainer

/**
 * The one word order every phrase with a single comma gets without authoring it: its two
 * halves swapped ("Mom, help me!" → "Help me, Mom!").
 *
 * The sentence's own opening mark and closing mark stay at its edges; a mark inside a half
 * ("Papá, ¿dónde estás?") travels with that half.
 */
internal object ScrambleCommaSwap {

    private const val COMMA = ","

    /** [atoms] with the halves around their only comma exchanged, or null when there is no such comma. */
    fun of(atoms: List<ScrambleAtom>): List<ScrambleAtom>? {
        val at = atoms.indexOfFirst { it.text == COMMA }
        if (at < 0 || atoms.indexOfLast { it.text == COMMA } != at) return null
        val before = atoms.subList(0, at)
        val after = atoms.subList(at + 1, atoms.size)
        if (before.none { !ScrambleTokenizer.isMark(it.text) } || after.none { !ScrambleTokenizer.isMark(it.text) }) return null
        val opening = before.takeWhile { ScrambleTokenizer.isMark(it.text) }
        val closing = after.takeLastWhile { ScrambleTokenizer.isMark(it.text) }
        val first = before.drop(opening.size)
        val second = after.dropLast(closing.size)
        return opening + second + atoms[at] + first + closing
    }
}
