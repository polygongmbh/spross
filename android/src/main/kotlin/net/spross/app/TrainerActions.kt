package net.spross.app

import net.spross.kern.box.BoxEngine
import net.spross.kern.trainer.Drill
import net.spross.kern.trainer.DrillRunSummary
import net.spross.kern.trainer.NumbersChallenge
import net.spross.kern.trainer.NumbersMode

/**
 * The hub's three entries. Each opens a PAGE, never a run: reading matter and the
 * drill it prepares you for are one surface, and the run is what the page is opened
 * for, so the picks and the button sit above the reading.
 *
 * The ladder is re-read on the way in — a run closed earlier may have opened a Sprosse —
 * and last night's figures are not news, so the result tile starts clear.
 */
fun AppModel.openNumbers() {
    trainer.clearResult()
    refreshTrainer()
    navigate(Screen.Numbers)
}

fun AppModel.openLetters() {
    trainer.clearResult()
    refreshTrainer()
    refreshLetters()
    navigate(Screen.Letters)
}

fun AppModel.openCountries() {
    trainer.clearResult()
    refreshTrainer()
    navigate(Screen.Countries)
}

fun AppModel.openDates() {
    trainer.clearResult()
    refreshTrainer()
    navigate(Screen.Dates)
}

/** The ✕ on any of them: one plain step back ([Screen.back]). */
fun AppModel.closeOverview() {
    goBack()
}

fun AppModel.startTrainerRun(mode: NumbersMode) {
    navigate(Screen.NumbersRun(mode))
}

/** A challenge's timed run, on the questions its code spells. */
fun AppModel.startChallenge(challenge: NumbersChallenge) {
    navigate(Screen.NumbersRun(challenge.mode, challenge))
}

fun AppModel.startLetterDrill() {
    navigate(Screen.LetterDrill)
}

/** The word scramble, straight from its chip — there is no page to open first. */
fun AppModel.startWordScramble() {
    navigate(Screen.WordScramble)
}

fun AppModel.startSentenceScramble() {
    navigate(Screen.SentenceScramble)
}

fun AppModel.startOpposites() {
    navigate(Screen.Opposites)
}

/**
 * An atlas run. Both switches and the Sprosse it opens on are the page's to settle —
 * Fast has a price and the page has already checked it — so the run only obeys them.
 */
fun AppModel.startCountryDrill(reverse: Boolean, fast: Boolean, sprosse: Int) {
    navigate(Screen.CountryDrill(reverse, fast, sprosse))
}

/** A dates run — the atlas rule: the switches and the Sprosse are the page's, the run only obeys. */
fun AppModel.startDateDrill(reverse: Boolean, fast: Boolean, sprosse: Int) {
    navigate(Screen.DateDrill(reverse, fast, sprosse))
}

/**
 * A closed run has no screen of its own: its figures travel back to the page that
 * started it, which wears them as one tile above the picks.
 *
 * [summary] null ⇒ nothing was answered; the run simply closes. The ladder is re-read
 * because a closing run books the Sprossen it stood on, and the rows behind it are stale
 * the moment it leaves.
 */
fun AppModel.finishDrill(back: Screen, summary: DrillRunSummary?, title: String) {
    pronouncer.stop()
    bookDrillAnswers(summary)
    // why: a close kern celebrates ([DrillRunSummary.celebrated]) is cheered as it closes —
    // the tile the learner lands on carries the words, but not until they look.
    if (summary?.celebrated == true) cues.cheer()
    trainer.show(summary, title)
    refreshTrainer()
    // why: a closing letter run lands back on the page that reads the report, and the
    // box it walks has moved — every other drill's page reads prefs alone.
    if (back == Screen.Letters) refreshLetters()
    navigate(back)
}

/** A closed run's answers, booked to today so the streak and the activity strip count them. */
private fun AppModel.bookDrillAnswers(summary: DrillRunSummary?) {
    val answers = summary?.done ?: return
    updateBox { BoxEngine.bookDrillAnswers(it, answers, now(), tz()) }
}

/**
 * The way out of either scramble. Neither has a page to land on, so the run closes onto Home
 * and hands its figures to the hub card ([TrainerStanding.showOnHub]); neither keeps a streak record or a high-water Sprosse beside its mask,
 * because nothing reads one back. The mask under [clearedKey] is what the NEXT run reads: it
 * passes each Sprosse held there on one clean answer.
 */
fun AppModel.closeScramble(drill: Drill, title: String, clearedKey: String, cleared: Set<Int>, summary: DrillRunSummary?) {
    trainer.store.bookCleared(clearedKey, cleared)
    bookDrillAnswers(summary)
    stampRun(drill, summary)
    trainer.showOnHub(summary, title)
    if (summary?.celebrated == true) cues.cheer()
    finishDrill(Screen.Home, null, "")
}

/**
 * What the overview pages read: the climbed ladder and the two drills' best Sprossen.
 * Four preference reads, recomputed rather than cached — a Sprosse opens as a run closes.
 *
 * Never on the way to Home: the hub card gates on file presence alone. What the
 * LETTER drill can ask is not here: that one is a catalog walk, so it belongs to the
 * page that reads it ([refreshLetters]).
 */
fun AppModel.refreshTrainer() {
    val stamp = box?.joinStamp ?: return
    trainer.readLadder(stamp.target)
    trainer.readCountries(stamp.source, stamp.target)
    trainer.readDates(stamp.source, stamp.target)
}

/**
 * What the letter drill can ask on THIS device — the one trainer question that is a
 * walk: the growing cards for dictation, and every alphabet row's example words
 * mined out of the catalog.
 *
 * So it is asked by the page that reads it and nowhere else, which is where iOS has
 * always asked it (`LettersOverview`). Recomputed rather than cached: the box moves as
 * runs close, and a voice may be installed in Settings while the app sleeps.
 */
fun AppModel.refreshLetters() {
    trainer.seeLetters(letterReport())
}
