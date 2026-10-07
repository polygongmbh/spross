package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.spross.kern.box.BoxEngine
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.JoinStamp

/** Which of a drill's filed Sprossen the suggestion weighs. */
class DrillLaddersTest {

    private val box = BoxEngine.bootstrap(emptyList(), BoxConfig(), JoinStamp("de", "uk", "test"))

    private class Masks(val masks: Map<String, Set<Int>>) : DrillLadders.Store {
        override fun reached(key: String) = 0
        override fun cleared(key: String) = masks[key].orEmpty()
    }

    private fun atlas(reverse: Boolean) = Masks(
        mapOf(NumbersMode.clearedKey(CountryDrill.storageKey("de", "uk"), reverse) to (1..CountryDrill.MAX_SPROSSE).toSet()),
    )

    private fun ladder(store: DrillLadders.Store) =
        DrillLadders.ladder(Drill.Countries, "de", "uk", box, emptyList(), null, store)!!

    /** A ladder is read the way its run opens: a reversed ladder cleared to the top masters nothing. */
    @Test
    fun onlyTheForwardLadderCountsAsMastered() {
        assertTrue(ladder(atlas(reverse = false)).mastered)
        assertFalse(ladder(atlas(reverse = true)).mastered)
    }
}
