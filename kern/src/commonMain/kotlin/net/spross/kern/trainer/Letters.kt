package net.spross.kern.trainer

/**
 * The letters a reader sees in this text, each as the characters that write it:
 * a combining mark rides on the letter before it, and so does a surrogate's second half.
 * Counted here once, where each platform's own text API would split graphemes its own way.
 */
internal fun String.letters(): List<String> {
    val out = mutableListOf<String>()
    for (c in this) {
        val rides = c.isLowSurrogate() || c in '̀'..'ͯ'
        if (rides && out.isNotEmpty()) out[out.lastIndex] = out.last() + c else out += c.toString()
    }
    return out
}
