package net.spross.app

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.spross.kern.model.Language
import net.spross.kern.trainer.DrillRunSummary
import net.spross.kern.trainer.DrillVariant
import net.spross.kern.trainer.LetterDrillAvailability
import net.spross.kern.trainer.TrainerMode

/**
 * Where a drill's standing record and its climbed Sprossen are filed.
 *
 * SharedPreferences rather than the box document, for the reason iOS files them in
 * UserDefaults: a drill run touches no card and no schedule, so it is not box state —
 * losing a Sprosse costs a climb, where anything in the box costs learning history.
 *
 * Every key is KERN's ([TrainerMode.RECORD_PREFIX] / [TrainerMode.PROGRESS_PREFIX] plus the
 * identity kern spells for the run), so the two platforms file the same feat under the same
 * name and neither can invent a scheme of its own.
 */
class TrainerStore(private val prefs: SharedPreferences) {

    /** The longest streak this run selection ever reached, 0 where it was never run. */
    fun record(key: String): Int = prefs.getInt(TrainerMode.RECORD_PREFIX + key, 0)

    /**
     * Books a streak as the new record. Strictly greater, so re-closing a resumed run
     * never re-claims one that was already standing.
     */
    fun bookRecord(key: String, streak: Int) {
        if (streak <= record(key)) return
        prefs.edit().putInt(TrainerMode.RECORD_PREFIX + key, streak).apply()
    }

    /**
     * Every variant's highest Sprosse in [language], as the unlock table wants to read it.
     * Read whole rather than per row, because a requirement names a variant other than
     * the row it gates (Phrases is bought with Clock).
     */
    fun ladder(language: Language): Map<DrillVariant, Int> =
        DrillVariant.entries.associateWith { sprosse(TrainerMode.progressKey(it, language)) }

    /** The same numbers keyed the way [net.spross.kern.trainer.TrainerRun.close] books them. */
    fun standing(language: Language): Map<String, Int> =
        DrillVariant.entries.associate {
            val key = TrainerMode.progressKey(it, language)
            key to sprosse(key)
        }

    /** What a closed run left behind — already filtered by kern to what beats the standing. */
    fun book(bookings: Map<String, Int>) {
        if (bookings.isEmpty()) return
        val edit = prefs.edit()
        for ((key, level) in bookings) edit.putInt(TrainerMode.PROGRESS_PREFIX + key, level)
        edit.apply()
    }

    /** The highest Sprosse ever reached under [key], 0 where it was never run. */
    fun best(key: String): Int = sprosse(key)

    /**
     * Books [level] as the furthest Sprosse reached. Strictly greater, so a run re-closed
     * over its own figures never re-claims a Sprosse that was already standing.
     */
    fun bookSprosse(key: String, level: Int) {
        if (level <= sprosse(key)) return
        prefs.edit().putInt(TrainerMode.PROGRESS_PREFIX + key, level).apply()
    }

    private fun sprosse(key: String): Int = prefs.getInt(TrainerMode.PROGRESS_PREFIX + key, 0)

    /** The most answers one run under [key] ever took, right or wrong; 0 where none has closed. */
    fun answers(key: String): Int = prefs.getInt(TrainerMode.ANSWERS_PREFIX + key, 0)

    /** Books [answers] where it beats the standing figure — strictly greater, like the streak. */
    fun bookAnswers(key: String, answers: Int) {
        if (answers <= answers(key)) return
        prefs.edit().putInt(TrainerMode.ANSWERS_PREFIX + key, answers).apply()
    }

    /** The Sprossen every run under [key] has answered out — kern reads the mask. */
    fun cleared(key: String): Set<Int> =
        TrainerMode.clearedSprossen(prefs.getInt(TrainerMode.CLEARED_PREFIX + key, 0))

    /** ORs a closed run's answered-out Sprossen into the standing mask; never filtered. */
    fun bookCleared(key: String, sprossen: Set<Int>) {
        if (sprossen.isEmpty()) return
        val standing = prefs.getInt(TrainerMode.CLEARED_PREFIX + key, 0)
        val mask = standing or TrainerMode.clearedMask(sprossen)
        prefs.edit().putInt(TrainerMode.CLEARED_PREFIX + key, mask).apply()
    }

    /** Everything one typed drill's page reads for [key], both directions' masks included. */
    fun typedStanding(key: String): TypedDrillStanding = TypedDrillStanding(
        bestSprosse = best(key),
        record = record(key),
        answers = answers(key),
        cleared = mapOf(
            false to cleared(TrainerMode.clearedKey(key, false)),
            true to cleared(TrainerMode.clearedKey(key, true)),
        ),
    )

    companion object {
        /**
         * Where the atlas ladder and its record are filed — one key per PAIR, because the
         * atlas is a pair's material and not a language's. Kern spells every other drill's
         * identity ([TrainerMode.progressKey]); this one it does not, so the two platforms
         * agree on it by both writing the string the iOS twin authored
         * (`CountriesOverview.storageKey`).
         */
        fun countriesKey(source: Language, target: Language): String = "countries.$source-$target"

        /** The dates ladder's twin of [countriesKey], authored by `DatesOverview.storageKey`. */
        fun datesKey(source: Language, target: Language): String = "dates.$source-$target"
    }
}

/**
 * What a typed drill's page reads: every figure its runs have filed for this pair. The page
 * only reads them — nothing on it is earned — except that Fast is priced against
 * [bestSprosse] and a run opens above [cleared].
 */
data class TypedDrillStanding(
    /** The furthest Sprosse any run reached; 0 where none has. */
    val bestSprosse: Int,
    /** The longest clean streak any run held. */
    val record: Int,
    /** The most answers one run took, right or wrong. */
    val answers: Int,
    private val cleared: Map<Boolean, Set<Int>>,
) {
    /** The Sprossen answered out in ONE direction — a row means another question turned round. */
    fun cleared(reverse: Boolean): Set<Int> = cleared[reverse].orEmpty()

    companion object {
        val NONE = TypedDrillStanding(0, 0, 0, emptyMap())
    }
}

/**
 * The free-practice standing, as the three overview pages read it: how far the ladder has
 * been climbed, what the letter drill can ask on THIS device, what the atlas and calendar
 * runs have filed, and what the run that just closed came to.
 *
 * Held apart from the run itself because all of it outlives one: the ladder is what a
 * closing run books INTO, the availability is recomputed on every foreground, and the
 * result is shown by the page the run came back to rather than by a screen of its own.
 */
class TrainerStanding(val store: TrainerStore) {

    /** The highest Sprosse each variant ever reached in the language being learnt. */
    var ladder by mutableStateOf<Map<DrillVariant, Int>>(emptyMap())
        private set

    /**
     * What the letter drill can ask here. Never cached across a foreground: a voice
     * installed in Settings while the app slept must turn the start button on without a
     * relaunch.
     */
    var letters by mutableStateOf<LetterDrillAvailability.Report?>(null)
        private set

    /** What every atlas run has filed for this PAIR. */
    var countries by mutableStateOf(TypedDrillStanding.NONE)
        private set

    /** The dates ladder's twin of [countries]. */
    var dates by mutableStateOf(TypedDrillStanding.NONE)
        private set

    /** The figures the last closed run handed back; null while no run has closed. */
    var result by mutableStateOf<DrillRunSummary?>(null)
        private set

    /** What the result tile says was drilled — a page can host several. */
    var resultTitle by mutableStateOf("")
        private set

    fun readLadder(language: Language) {
        ladder = store.ladder(language)
    }

    fun seeLetters(report: LetterDrillAvailability.Report?) {
        letters = report
    }

    fun readCountries(source: Language, target: Language) {
        countries = store.typedStanding(TrainerStore.countriesKey(source, target))
    }

    fun readDates(source: Language, target: Language) {
        dates = store.typedStanding(TrainerStore.datesKey(source, target))
    }

    fun show(summary: DrillRunSummary?, title: String) {
        result = summary
        resultTitle = title
    }

    /** Opening a page from Home is a fresh visit — last night's figures are not news. */
    fun clearResult() {
        result = null
    }
}
