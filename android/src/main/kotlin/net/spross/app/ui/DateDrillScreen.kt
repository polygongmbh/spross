package net.spross.app.ui

import androidx.compose.runtime.Composable
import net.spross.app.AppModel
import net.spross.app.Screen
import net.spross.app.TrainerStore
import net.spross.app.newDateDrill

/**
 * The dates drill: the weekday names alone, the month names alone, the day-of-month
 * numeral alone — and then the whole spoken date assembled out of them. Typed answers
 * only, in whichever direction the page was started with.
 *
 * Every rule is kern's `DateDrill`, reached through `DateDrillFlow`; every pixel is
 * [TypedDrillScreen], the screen this shares with the atlas drill. What is left here is
 * the three things that tell the two apart.
 */
@Composable
fun DateDrillScreen(model: AppModel, reverse: Boolean, fast: Boolean) {
    val stamp = model.box?.joinStamp
    TypedDrillScreen(
        model = model,
        reverse = reverse,
        fast = fast,
        page = TypedDrillPage(
            back = Screen.Dates,
            skill = model.chrome.trainerSkillDates,
            // One key per PAIR, the same one the page reads its best Sprosse back from.
            key = stamp?.let { TrainerStore.datesKey(it.source, it.target) },
            // why: false though the weekday and month Sprossen DO prompt with a name — the
            // day and date Sprossen prompt with a rendering whose reading is the answer.
            promptIsAName = false,
            open = { onTone, onReleaseFocus ->
                model.newDateDrill(reverse, fast, onTone, onReleaseFocus)
            },
        ),
    )
}
