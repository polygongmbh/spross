package net.spross.kern.trainer

/**
 * Moves the reading the reveal teaches to the head, where the generators read their
 * display from. A reading that already leads, or is not in the list, leaves the order
 * alone. [textOf] reaches the reading inside whatever the language pairs it with — the
 * two callers pair it with the hour it names, and agree on nothing else.
 */
internal fun <T> List<T>.leadWith(display: String, textOf: (T) -> String): List<T> {
    val at = indexOfFirst { textOf(it) == display }
    return if (at <= 0) this else listOf(this[at]) + filterIndexed { index, _ -> index != at }
}
