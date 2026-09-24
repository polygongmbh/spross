package net.spross.app.ui

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The largest whole-sp size between [min] and [max] at which the text neither overflows
 * nor breaks a line inside a word — a hyphenated break counts as one, a written hyphen does not.
 *
 * A word wider than the line wraps mid-word without overflowing, so overflow alone
 * (`TextAutoSize.StepBased`) leaves "Gute" / "n Tag!" at full size. At [min] the text
 * renders as it lays out.
 */
internal data class WholeWordsAutoSize(val min: TextUnit, val max: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString,
    ): TextUnit {
        var low = min.value.toInt()
        var high = max.value.toInt()
        if (fits(performLayout(constraints, text, high.sp), text.text)) return max
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (fits(performLayout(constraints, text, mid.sp), text.text)) low = mid else high = mid - 1
        }
        return maxOf(low.toFloat(), min.value).sp
    }

    private fun fits(layout: TextLayoutResult, text: String): Boolean =
        !layout.hasVisualOverflow && (0 until layout.lineCount - 1).none { line ->
            val end = layout.getLineEnd(line)
            end in 1 until text.length && text[end - 1] != '-' &&
                !text[end - 1].isWhitespace() && !text[end].isWhitespace()
        }
}
