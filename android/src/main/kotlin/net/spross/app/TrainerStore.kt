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
 * Where a drill's standing record and its climbed rungs are filed.
 *
 * SharedPreferences rather than the box document, for the reason iOS files them in
 * UserDefaults: a drill run touches no card and no schedule, so it is not box state —
 * losing a rung costs a climb, where anything in the box costs learning history.
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
     * Every variant's highest rung in [language], as the unlock table wants to read it.
     * Read whole rather than per row, because a requirement names a variant other than
     * the row it gates (Phrases is bought with Clock).
     */
    fun ladder(language: Language): Map<DrillVariant, Int> =
        DrillVariant.entries.associateWith { rung(TrainerMode.progressKey(it, language)) }

    /** The same numbers keyed the way [net.spross.kern.trainer.TrainerRun.close] books them. */
    fun standing(language: Language): Map<String, Int> =
        DrillVariant.entries.associate {
            val key = TrainerMode.progressKey(it, language)
            key to rung(key)
        }

    /** What a closed run left behind — already filtered by kern to what beats the standing. */
    fun book(bookings: Map<String, Int>) {
        if (bookings.isEmpty()) return
        val edit = prefs.edit()
        for ((key, level) in bookings) edit.putInt(TrainerMode.PROGRESS_PREFIX + key, level)
        edit.apply()
    }

    /** The highest rung ever reached under [key], 0 where it was never run. */
    fun best(key: String): Int = rung(key)

    /**
     * Books [level] as the furthest rung reached. Strictly greater, so a run re-closed
     * over its own figures never re-claims a rung that was already standing.
     */
    fun bookRung(key: String, level: Int) {
        if (level <= rung(key)) return
        prefs.edit().putInt(TrainerMode.PROGRESS_PREFIX + key, level).apply()
    }

    private fun rung(key: String): Int = prefs.getInt(TrainerMode.PROGRESS_PREFIX + key, 0)

    companion object {
        /**
         * Where the atlas ladder and its record are filed — one key per PAIR, because the
         * atlas is a pair's material and not a language's. Kern spells every other drill's
         * identity ([TrainerMode.progressKey]); this one it does not, so the two platforms
         * agree on it by both writing the string the iOS twin authored
         * (`CountriesOverview.storageKey`).
         */
        fun countriesKey(source: Language, target: Language): String = "countries.$source-$target"
    }
}

/**
 * The free-practice standing, as the three overview pages read it: how far the ladder has
 * been climbed, what the letter drill can ask on THIS device, how far up the atlas any run
 * has come, and what the run that just closed came to.
 *
 * Held apart from the run itself because all of it outlives one: the ladder is what a
 * closing run books INTO, the availability is recomputed on every foreground, and the
 * result is shown by the page the run came back to rather than by a screen of its own.
 */
class Werkstatt(val store: TrainerStore) {

    /** The highest rung each variant ever reached in the language being learnt. */
    var ladder by mutableStateOf<Map<DrillVariant, Int>>(emptyMap())
        private set

    /**
     * What the letter drill can ask here. Never cached across a foreground: a voice
     * installed in Settings while the app slept must turn the start button on without a
     * relaunch.
     */
    var letters by mutableStateOf<LetterDrillAvailability.Report?>(null)
        private set

    /**
     * The furthest rung any atlas run ever reached for this PAIR; 0 where none has.
     * The atlas page only reads it — nothing on that page is earned — except that Fast is
     * priced against it ([net.spross.kern.trainer.CountryDrill.fastUnlocked]).
     */
    var countriesBest by mutableStateOf(0)
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
        countriesBest = store.best(TrainerStore.countriesKey(source, target))
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
