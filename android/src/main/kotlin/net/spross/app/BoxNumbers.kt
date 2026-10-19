package net.spross.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spross.kern.box.ACTIVITY_WINDOW_DAYS
import net.spross.kern.box.ActivityDay
import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.box.BoxStatistics
import net.spross.kern.box.ShelfCounts
import net.spross.kern.session.HomeStanding

/**
 * Everything the box surfaces read off kern, taken when the box last moved ([refresh]) —
 * never re-derived per composition.
 */
class BoxNumbers {
    var stats by mutableStateOf<BoxStatistics?>(null)
        private set

    /**
     * The trailing fortnight the activity strip draws, oldest day first.
     * Kern walks it beside the streak number, so a strip and a flame cannot disagree.
     */
    var activityWindow by mutableStateOf<List<ActivityDay>>(emptyList())
        private set

    /** Everything Home asks kern for; null before a box exists. */
    var homeStanding by mutableStateOf<HomeStanding?>(null)
        private set

    /**
     * What each shelf's two queue controls would do, every area at once
     * (`BoxBrowser.shelfCounts`).
     *
     * The browser draws both numbers on every shelf it lists, and asked one shelf at a
     * time each answer scans and sorts the whole box — so a screenful cost sixty walks
     * per frame.
     */
    var shelfCounts by mutableStateOf<Map<String, ShelfCounts>>(emptyMap())
        private set

    /** [otherDays] are the answers from every other target-language box (`AppModel.otherLanguagesAnswerDays`). */
    internal fun refresh(state: BoxState, now: Long, tz: String, otherDays: Map<String, Int>) {
        stats = BoxEngine.statistics(state, now, tz, otherDays)
        // why: the strip reads the same merged days the streak does — a day worked in
        // another language is still a day worked, on the picture as well as the count.
        activityWindow = BoxEngine.activityWindow(state, ACTIVITY_WINDOW_DAYS, now, tz, otherDays)
        homeStanding = HomeStanding.of(state, now, tz, otherDays)
        shelfCounts = BoxBrowser.shelfCounts(state)
    }
}
