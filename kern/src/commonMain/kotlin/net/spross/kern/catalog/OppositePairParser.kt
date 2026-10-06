package net.spross.kern.catalog

/**
 * Two concepts the opposites drill asks for each other, by slug — language-neutral like a
 * concept, so one line lights up every language that realizes both sides.
 */
data class OppositePair(val first: String, val second: String)

/**
 * `catalog/opposites/pairs.json` → [OppositePair]s, in authored order: an array of
 * two-slug arrays. Rejected at parse time is what no language could repair — a slug the
 * catalog never defines, a pair of one concept, the same pair twice in either order.
 */
internal object OppositePairParser {

    fun parse(path: String, text: String, conceptSlugs: Set<String>): List<OppositePair> {
        val rows = parseJson(path, text).arr(path, "root")
        val pairs = rows.mapIndexed { i, el ->
            val where = "pairs[$i]"
            val slugs = el.arr(path, where).map { it.str(path, where) }
            if (slugs.size != 2) parseError(path, "$where: a pair names exactly two slugs")
            val (first, second) = slugs
            for (slug in slugs) {
                if (slug !in conceptSlugs) parseError(path, "$where: unknown slug \"$slug\"")
            }
            if (first == second) parseError(path, "$where: \"$first\" is its own opposite")
            OppositePair(first, second)
        }
        val seen = mutableSetOf<Set<String>>()
        for (pair in pairs) {
            if (!seen.add(setOf(pair.first, pair.second))) {
                parseError(path, "duplicate pair ${pair.first}/${pair.second}")
            }
        }
        return pairs
    }
}
