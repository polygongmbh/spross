package net.spross.kern.trainer

/**
 * The move a clock generator makes that carries no language rule.
 *
 * Which readings a time has, which hour a part of the day belongs to, and what the
 * reveal teaches are each language's own rules and stay in its own file;
 * `docs/clock-registers.md` owns the policy they implement.
 */

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
