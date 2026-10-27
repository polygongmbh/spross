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
import net.spross.kern.catalog.OppositePair
import net.spross.kern.session.HomeStanding
import net.spross.kern.trainer.OppositesAvailability
import net.spross.kern.trainer.SentenceScrambleAvailability
import net.spross.kern.trainer.WordScrambleAvailability

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

    /**
     * Which box-drawn drills the box can ask at all — the hub's chips and Home's suggestion
     * read them, and each answer is a sweep of the whole join.
     */
    var boxDrills by mutableStateOf(BoxDrills())
        private set

    /**
     * [otherDays] are the answers from every other target-language box (`AppModel.otherLanguagesAnswerDays`);
     * [oppositePairs] the catalog's, for the opposites drill.
     */
    internal fun refresh(
        state: BoxState,
        now: Long,
        tz: String,
        otherDays: Map<String, Int>,
        oppositePairs: List<OppositePair>,
    ) {
        stats = BoxEngine.statistics(state, now, tz, otherDays)
        // why: the strip reads the same merged days the streak does — a day worked in
        // another language is still a day worked, on the picture as well as the count.
        activityWindow = BoxEngine.activityWindow(state, ACTIVITY_WINDOW_DAYS, now, tz, otherDays)
        homeStanding = HomeStanding.of(state, now, tz, otherDays)
        shelfCounts = BoxBrowser.shelfCounts(state)
        boxDrills = BoxDrills(
            words = WordScrambleAvailability.drillExists(state),
            phrases = SentenceScrambleAvailability.drillExists(state),
            opposites = OppositesAvailability.drillExists(state, oppositePairs),
        )
    }
}

/** Whether each box-drawn drill can ask this box anything, taken again wherever the box moves. */
data class BoxDrills(val words: Boolean = false, val phrases: Boolean = false, val opposites: Boolean = false)
