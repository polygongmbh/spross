package net.spross.kern.trainer

import net.spross.kern.box.BoxState
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.OppositePair
import net.spross.kern.model.Language

/**
 * What each drill has filed of its ladder, in the shape [DrillSuggestion] weighs it.
 * Which key each drill files under and how tall each ladder runs are decided here;
 * the platform hands in its store and what the box offers.
 *
 * The numbers ladders file the highest Sprosse reached per exercise ([NumbersMode.progressKey]);
 * every other ladder files the Sprossen it cleared, read the way its run opens: forward.
 * The letter drill files none.
 */
object DrillLadders {

    /** The platform's trainer store, read by key. */
    interface Store {
        /** The highest Sprosse reached under a numbers progress key ([NumbersMode.progressKey]). */
        fun reached(key: String): Int

        /** The Sprossen cleared under a mask key ([NumbersMode.clearedKey]). */
        fun cleared(key: String): Set<Int>
    }

    /**
     * [drill]'s ladder for the pair [source] → [target];
     * null where it files none, and for the calendar where the pair has no [dates] content.
     */
    fun ladder(
        drill: Drill,
        source: Language,
        target: Language,
        box: BoxState,
        oppositePairs: List<OppositePair>,
        dates: DateDrillContent?,
        store: Store,
    ): DrillSuggestion.Ladder? {
        fun cleared(key: String, top: Int) =
            DrillSuggestion.Ladder.cleared(store.cleared(NumbersMode.clearedKey(key, reverse = false)), top)
        return when (drill) {
            Drill.Numbers -> DrillSuggestion.Ladder.numbers(
                NumbersExercise.entries.associateWith { store.reached(NumbersMode.progressKey(it, target)) },
                target,
            )
            Drill.Letters -> null
            Drill.Countries -> cleared(CountryDrill.storageKey(source, target), CountryDrill.MAX_SPROSSE)
            Drill.Dates -> dates?.let {
                cleared(DateDrill.storageKey(source, target), DateDrill.maxSprosse(it, reverse = false))
            }
            Drill.WordScramble ->
                cleared(WordScrambleRunState.storageKey(target), WordScrambleAvailability.report(box).maxSprosse)
            Drill.SentenceScramble ->
                cleared(SentenceScrambleRunState.storageKey(target), SentenceScrambleAvailability.report(box).maxSprosse)
            Drill.Opposites ->
                cleared(OppositesRunState.storageKey(target), OppositesAvailability.report(box, oppositePairs).maxSprosse)
        }
    }
}
