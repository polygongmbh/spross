package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import net.spross.kern.trainer.DrillVariant
import net.spross.kern.trainer.TrainerMode

/**
 * What the drill store owes: an unrun drill stands at zero, a booking only ever climbs,
 * and what one store wrote the next one reads back — which is the whole of "a Sprosse
 * survives a relaunch" from the store's side, the file itself being the framework's.
 *
 * The key spellings are kern's ([TrainerMode.progressKey]) and tested there; these read
 * back through the same call that wrote, never through a string of their own.
 */
class TrainerStoreTest {

    private val language = "es"

    @Test
    fun anUnrunDrillStandsAtZero() {
        val store = TrainerStore(FakePrefs())
        assertEquals(0, store.record("numbers.es"))
        assertEquals(0, store.best("numbers.es"))
        assertEquals(
            DrillVariant.entries.associateWith { 0 },
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

    @Test
    fun aClosedRunsBookingsAreReadBackByTheLadder() {
        val store = TrainerStore(FakePrefs())
        val numbers = TrainerMode.progressKey(DrillVariant.Numbers, language)
        store.book(mapOf(numbers to 4))
        assertEquals(4, store.ladder(language)[DrillVariant.Numbers])
        assertEquals(4, store.standing(language)[numbers])
        assertEquals(0, store.ladder(language)[DrillVariant.Clock])
    }

    @Test
    fun aSecondStoreReadsWhatTheFirstBooked() {
        val file = mutableMapOf<String, Any?>()
        val numbers = TrainerMode.progressKey(DrillVariant.Numbers, language)
        TrainerStore(FakePrefs(file)).book(mapOf(numbers to 6))
        val relaunched = TrainerStore(FakePrefs(file))
        assertEquals(6, relaunched.ladder(language)[DrillVariant.Numbers])
    }
}
