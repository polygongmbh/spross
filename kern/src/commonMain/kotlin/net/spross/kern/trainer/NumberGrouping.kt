package net.spross.kern.trainer

/**
 * Digit-group separator for long prompts: U+202F NARROW NO-BREAK SPACE.
 *
 * Deliberately neutral. A dot and a comma both mean "thousands" in one of the drill's
 * languages and "decimal point" in another — German writes 1.000 and 3,7 where English
 * writes 1,000 and 3.7 — so either mark would teach one convention as the truth
 * to a learner of the other. A thin space commits to neither, and being no-break it
 * cannot wrap a number across two lines.
 */
internal const val GROUP_SEPARATOR = '\u202F'

/**
 * Groups a digit string in threes from the right, but only from FIVE digits up:
 * 999, 1000 and 9999 stay unbroken, 12345 becomes "12 345" and 4072918300 becomes
 * "4 072 918 300". Four digits read fine as one run and a break there is noise —
 * grouping earns its place only once a glance can no longer count the places.
 */
internal fun groupDigits(digits: String): String {
    if (digits.length < 5) return digits
    val head = digits.length % 3
    return buildString {
        if (head > 0) append(digits, 0, head)
        for (start in head until digits.length step 3) {
            if (isNotEmpty()) append(GROUP_SEPARATOR)
            append(digits, start, start + 3)
        }
    }
}
