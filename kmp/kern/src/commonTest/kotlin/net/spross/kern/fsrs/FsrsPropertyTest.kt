package net.spross.kern.fsrs

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.spross.kern.model.MemoryState
import net.spross.kern.model.Rating

/**
 * FSRS-6 re-expression of the v1 property suite: structural guarantees of the
 * formula set, seeded-random sampled (no reference numerics asserted here).
 */
class FsrsPropertyTest {

    private val fsrs = Fsrs()
    private val grades = listOf(Rating.Again, Rating.Hard, Rating.Good, Rating.Easy)

    private fun Random.stability() = nextDouble(Fsrs.S_MIN, 1000.0)
    private fun Random.difficulty() = nextDouble(1.0, 10.0)

    @Test
    fun retrievabilityStaysInUnitIntervalAndDecreases() {
        val random = Random(1)
        repeat(200) {
            val s = random.stability()
            var previous = fsrs.retrievability(0.0, s)
            assertEquals(1.0, previous, 1e-12)
            for (t in listOf(0.5, 1.0, 3.0, 10.0, 100.0, 1000.0)) {
                val r = fsrs.retrievability(t, s)
                assertTrue(r > 0.0 && r <= 1.0, "R($t, $s) = $r out of (0, 1]")
                assertTrue(r < previous, "R must decrease in t")
                previous = r
            }
        }
    }

    @Test
    fun retrievabilityAtDueIsNinetyPercent() {
        val random = Random(2)
        repeat(100) {
            val s = random.stability()
            assertEquals(0.9, fsrs.retrievability(s, s), 1e-9)
        }
    }

    @Test
    fun goodChainMonotonicallyRaisesStability() {
        var memory = fsrs.nextMemory(null, 0.0, Rating.Good)
        repeat(20) {
            val elapsed = fsrs.intervalDays(memory.stability).toDouble()
            val next = fsrs.nextMemory(memory, elapsed, Rating.Good)
            assertTrue(
                next.stability > memory.stability || next.stability == Fsrs.S_MAX,
                "Good at due must raise stability (${memory.stability} -> ${next.stability})",
            )
            memory = next
        }
    }

    @Test
    fun againAtDueLowersStability() {
        val random = Random(3)
        repeat(100) {
            val memory = MemoryState(random.stability() + 1.0, random.difficulty())
            val elapsed = fsrs.intervalDays(memory.stability).toDouble()
            val next = fsrs.nextMemory(memory, elapsed, Rating.Again)
            assertTrue(next.stability < memory.stability, "Again must lower stability")
        }
    }

    @Test
    fun difficultyStaysBoundedUnderRandomChains() {
        val random = Random(4)
        repeat(50) {
            var memory: MemoryState? = null
            repeat(40) {
                val rating = grades[random.nextInt(4)]
                val elapsed = random.nextDouble(0.0, 50.0)
                val next = fsrs.nextMemory(memory, elapsed, rating)
                memory = next
                assertTrue(next.difficulty in 1.0..10.0, "difficulty out of [1, 10]")
                assertTrue(next.stability in Fsrs.S_MIN..Fsrs.S_MAX, "stability out of clamp")
            }
        }
    }

    @Test
    fun intervalIsMonotoneNonIncreasingInRetention() {
        val random = Random(5)
        repeat(100) {
            val s = random.stability()
            var previous = Int.MAX_VALUE
            for (tenths in 1..10) {
                val interval = fsrs.intervalDays(s, tenths / 10.0)
                assertTrue(interval <= previous, "higher retention must not lengthen interval")
                assertTrue(interval >= 1)
                previous = interval
            }
        }
    }

    @Test
    fun hardPenaltyAndEasyBonusOrderStability() {
        val random = Random(6)
        repeat(100) {
            val d = random.difficulty()
            val s = random.stability()
            val r = random.nextDouble(0.3, 0.999)
            val hard = fsrs.recallStability(d, s, r, Rating.Hard)
            val good = fsrs.recallStability(d, s, r, Rating.Good)
            val easy = fsrs.recallStability(d, s, r, Rating.Easy)
            assertTrue(hard < good, "hard penalty must undercut Good")
            assertTrue(good < easy, "easy bonus must exceed Good")
        }
    }

    @Test
    fun sameDaySuccessNeverShrinksStability() {
        val random = Random(7)
        repeat(100) {
            val s = random.stability()
            for (rating in listOf(Rating.Hard, Rating.Good, Rating.Easy)) {
                assertTrue(
                    fsrs.shortTermStability(s, rating) >= s,
                    "sinc >= 1 mask must hold for $rating",
                )
            }
            assertTrue(fsrs.shortTermStability(s, Rating.Again) < s, "same-day Again shrinks")
        }
    }

    @Test
    fun higherRatingNeverLowersNextDifficultyOrShortTermStability() {
        val random = Random(8)
        repeat(100) {
            val d = random.difficulty()
            val s = random.stability()
            for (i in 0 until grades.size - 1) {
                assertTrue(
                    fsrs.nextDifficulty(d, grades[i]) >= fsrs.nextDifficulty(d, grades[i + 1]),
                    "difficulty must be non-increasing in rating",
                )
                assertTrue(
                    fsrs.shortTermStability(s, grades[i]) <=
                        fsrs.shortTermStability(s, grades[i + 1]),
                    "short-term stability must be non-decreasing in rating",
                )
            }
        }
    }
}
