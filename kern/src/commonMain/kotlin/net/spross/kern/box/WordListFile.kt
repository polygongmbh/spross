package net.spross.kern.box

import net.spross.kern.model.caseFolded

/**
 * A word list from a file read into pairs the box can take in as own words —
 * a Spross chat answer saved to a file, or another app's export:
 * Anki's "Notes in Plain Text" (`#` header lines, HTML in the fields),
 * Quizlet's tab- or semicolon-separated export, a spreadsheet saved as CSV.
 *
 * Same result as [Harvest.read], so one review list serves both;
 * unlike a paste it has no cap, because the learner picked the file.
 * The first two columns are the pair, any further ones (tags, notes) are dropped.
 * Which column is the language being learned is read off the box:
 * the one whose words the box already teaches more of, the first on a tie.
 */
object WordListFile {

    /** Past this many lines a surface asks before reading — the review list grows with the file. */
    const val LARGE_LINES: Int = 1000

    /** Longer than this on either side and it is a note on the card, not a word. */
    private const val MAX_LENGTH: Int = 120

    /** Tried in order; the first that splits at least half the lines is the file's. `=` is the chat's own format. */
    private val DELIMITERS = listOf('\t', '=', ';', ',')

    private val TAG = Regex("<[^>]*>")
    private val BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val ENTITIES = mapOf(
        "&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&amp;" to "&",
    )

    /** The non-blank lines a file holds — what a surface weighs against [LARGE_LINES]. */
    fun lineCount(text: String): Int = text.lineSequence().count { it.isNotBlank() }

    fun read(text: String, state: BoxState): List<HarvestWord> {
        val forms = BoxForms(state)
        val seen = mutableSetOf<String>()
        val found = mutableListOf<HarvestWord>()
        for (word in oriented(pairs(text), forms)) {
            if (word.target.length > MAX_LENGTH || word.source.length > MAX_LENGTH) continue
            if (!seen.add(caseFolded(word.target))) continue
            found += forms.standing(word)
        }
        return found.sortedBy { it.kind.ordinal }
    }

    /** Every pair in the file, column order as written. */
    private fun pairs(text: String): List<BriefWord> {
        Harvest.fenced(text)?.let { fenced -> return fenced.mapNotNull { Harvest.parseLine(it) } }
        val lines = text.removePrefix("﻿").lines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (lines.isEmpty()) return emptyList()
        val delimiter = DELIMITERS.firstOrNull { d -> lines.count { split(it, d).size >= 2 } * 2 >= lines.size }
            ?: return lines.mapNotNull { Harvest.parseLine(it) }
        if (delimiter == '=') return lines.mapNotNull { Harvest.parseLine(it) }
        return lines.mapNotNull { line ->
            val fields = split(line, delimiter).map { Harvest.clean(plain(it)) }
            if (fields.size < 2 || fields[0].isEmpty() || fields[1].isEmpty()) null
            else BriefWord(target = fields[0], source = fields[1])
        }
    }

    /** The pairs turned so the learned language comes first, when the box says the file wrote it second. */
    private fun oriented(pairs: List<BriefWord>, forms: BoxForms): List<BriefWord> {
        val first = pairs.count { forms.holds(it.target) }
        val second = pairs.count { forms.holds(it.source) }
        return if (second > first) pairs.map { BriefWord(target = it.source, source = it.target) } else pairs
    }

    /** One line's fields, a double-quoted field keeping its delimiters and `""` as a quote. */
    private fun split(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                quoted && ch == '"' && line.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                quoted && ch == '"' -> quoted = false
                ch == '"' && field.isBlank() -> { field.clear(); quoted = true }
                !quoted && ch == delimiter -> { fields += field.toString(); field.clear() }
                else -> field.append(ch)
            }
            i++
        }
        fields += field.toString()
        return fields
    }

    /** A field as text: Anki writes HTML when its `#html:true` header is set. */
    private fun plain(field: String): String {
        if ('<' !in field && '&' !in field) return field
        var text = BREAK.replace(field, " ").replace(TAG, "")
        for ((entity, char) in ENTITIES) text = text.replace(entity, char)
        return text
    }
}
