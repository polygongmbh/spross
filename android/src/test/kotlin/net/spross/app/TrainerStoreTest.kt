package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.trainer.NumbersExercise
import net.spross.kern.trainer.NumbersMode

/**
 * What the drill store owes: an unrun drill stands at zero, a booking only ever climbs,
 * and what one store wrote the next one reads back — which is the whole of "a Sprosse
 * survives a relaunch" from the store's side, the file itself being the framework's.
 *
 * Most key spellings are kern's ([NumbersMode.progressKey]) and tested there; these read
 * back through the same call that wrote, never through a string of their own. The scrambles
 * are the exception — kern spells no identity for them, so the string itself is the contract
 * with the iOS twin and is asserted whole.
 */
class TrainerStoreTest {

    private val language = "es"

    @Test
    fun anUnrunDrillStandsAtZero() {
        val store = TrainerStore(FakePrefs())
        assertEquals(0, store.record("numbers.es"))
        assertEquals(0, store.best("numbers.es"))
        assertEquals(
            NumbersExercise.entries.associateWith { 0 },
            store.ladder(language),
        )
    }

    @Test
    fun aRecordOnlyEverClimbs() {
        val store = TrainerStore(FakePrefs())
        store.bookRecord("numbers.es", 7)
        assertEquals(7, store.record("numbers.es"))
        store.bookRecord("numbers.es", 4)
        store.bookRecord("numbers.es", 7)
        assertEquals(7, store.record("numbers.es"))
        store.bookRecord("numbers.es", 9)
        assertEquals(9, store.record("numbers.es"))
    }

    @Test
    fun aSprosseOnlyEverClimbs() {
        val store = TrainerStore(FakePrefs())
        store.bookSprosse("numbers.es", 3)
        store.bookSprosse("numbers.es", 2)
        assertEquals(3, store.best("numbers.es"))
        store.bookSprosse("numbers.es", 5)
        assertEquals(5, store.best("numbers.es"))
    }

    @Test
    fun aRecordAndASprosseAreFiledApart() {
        val store = TrainerStore(FakePrefs())
        store.bookRecord("numbers.es", 12)
        assertEquals(0, store.best("numbers.es"))
        store.bookSprosse("numbers.es", 2)
        assertEquals(12, store.record("numbers.es"))
    }

    /** A Sprosse answered out stays answered out: the mask grows, never shrinks or resets. */
    @Test
    fun theAnsweredOutSprossenAccumulateAcrossRuns() {
        val store = TrainerStore(FakePrefs())
        assertEquals(emptySet(), store.cleared("countries.de-sw"))
        store.bookCleared("countries.de-sw", setOf(1, 2))
        store.bookCleared("countries.de-sw", setOf(3))
        store.bookCleared("countries.de-sw", emptySet())
        assertEquals(setOf(1, 2, 3), store.cleared("countries.de-sw"))
    }

    /**
     * One mask per learned language and no direction to split it by — neither scramble asks a
     * different question round the other way, so no key of theirs wears [NumbersMode.REVERSED_SUFFIX].
     */
    @Test
    fun eachScrambleFilesOneMaskPerLearnedLanguage() {
        assertEquals("wordscramble.es", TrainerStore.wordScrambleKey(language))
        assertEquals("sentencescramble.es", TrainerStore.sentenceScrambleKey(language))

        val store = TrainerStore(FakePrefs())
        store.bookCleared(TrainerStore.wordScrambleKey(language), setOf(1, 2))
        assertEquals(setOf(1, 2), store.cleared(TrainerStore.wordScrambleKey(language)))
        assertEquals(emptySet(), store.cleared(TrainerStore.sentenceScrambleKey(language)))
    }

    @Test
    fun theAnswersRecordOnlyEverClimbsAndIsFiledApart() {
        val store = TrainerStore(FakePrefs())
        store.bookAnswers("countries.de-sw", 30)
        store.bookAnswers("countries.de-sw", 12)
        assertEquals(30, store.answers("countries.de-sw"))
        assertEquals(0, store.record("countries.de-sw"))
        assertEquals(0, store.best("countries.de-sw"))
    }

    /** One read hands the page everything, each direction's mask under its own key. */
    @Test
    fun aTypedDrillsStandingReadsBothDirections() {
        val store = TrainerStore(FakePrefs())
        store.bookSprosse("dates.de-en", 4)
        store.bookRecord("dates.de-en", 9)
        store.bookAnswers("dates.de-en", 21)
        store.bookCleared(NumbersMode.clearedKey("dates.de-en", reverse = false), setOf(1, 2))
        store.bookCleared(NumbersMode.clearedKey("dates.de-en", reverse = true), setOf(1))
        val standing = store.typedStanding("dates.de-en")
        assertEquals(4, standing.bestSprosse)
        assertEquals(9, standing.record)
        assertEquals(21, standing.answers)
        assertEquals(setOf(1, 2), standing.cleared(reverse = false))
        assertEquals(setOf(1), standing.cleared(reverse = true))
    }

    @Test
    fun aClosedRunsBookingsAreReadBackByTheLadder() {
        val store = TrainerStore(FakePrefs())
        val numbers = NumbersMode.progressKey(NumbersExercise.Counting, language)
        store.book(mapOf(numbers to 4))
        assertEquals(4, store.ladder(language)[NumbersExercise.Counting])
        assertEquals(4, store.standing(language)[numbers])
        assertEquals(0, store.ladder(language)[NumbersExercise.Clock])
    }

    @Test
    fun aSecondStoreReadsWhatTheFirstBooked() {
        val file = mutableMapOf<String, Any?>()
        val numbers = NumbersMode.progressKey(NumbersExercise.Counting, language)
        TrainerStore(FakePrefs(file)).book(mapOf(numbers to 6))
        val relaunched = TrainerStore(FakePrefs(file))
        assertEquals(6, relaunched.ladder(language)[NumbersExercise.Counting])
    }
}
