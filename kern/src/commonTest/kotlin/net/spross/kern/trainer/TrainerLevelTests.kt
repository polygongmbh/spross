package net.spross.kern.trainer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainerLevelTests {

    @Test
    fun numberLevelIsDigitCount() {
        val rng = Random(1)
        for (level in 1..10) {
            repeat(50) {
                val task = Trainer.sample(TrainerKind.Numbers, "de", level, rng)
                assertEquals(level, task.prompt.length, "level $level: ${task.prompt}")
            }
        }
    }

    @Test
    fun numberSamplingBiasesZeros() {
        val rng = Random(11)
        var zeros = 0
        var total = 0
        repeat(400) {
            val prompt = Trainer.sample(TrainerKind.Numbers, "de", 5, rng).prompt
            for (d in prompt.drop(1)) {
                total += 1
                if (d == '0') zeros += 1
            }
        }
        // ~40% expected; assert clearly above the 10% a uniform draw would give.
        assertTrue(zeros.toDouble() / total > 0.25)
    }

    @Test
    fun swahiliDrillAcceptsNaLessForm() {
        val rng = Random(7)
        var sawConnector = false
        repeat(300) {
            val task = Trainer.sample(TrainerKind.Numbers, "sw", 3, rng)
            if (task.accepted.size == 2) {
                sawConnector = true
                assertEquals(task.accepted[0].replace(" na ", " "), task.accepted[1])
            }
            assertTrue(task.display in task.accepted)
        }
        assertTrue(sawConnector, "expected some multi-part Swahili numbers with a na-less variant")
    }

    private fun minutesDrawn(level: Int, seed: Int, draws: Int = 200): Set<Int> {
        val rng = Random(seed)
        return (1..draws)
            .map { Trainer.sample(TrainerKind.Clock, "de", level, rng).prompt.takeLast(2).toInt() }
            .toSet()
    }

    @Test
    fun clockRungsOfferExactlyTheirMinutes() {
        val rungs = mapOf(
            1 to setOf(0),
            2 to setOf(0, 15, 30, 45),
            3 to setOf(0, 5, 10, 15, 20, 25, 30, 45),
            4 to (0..55 step 5).toSet(),
        )
        for ((level, expected) in rungs) {
            assertEquals(expected, minutesDrawn(level, seed = 2 + level), "clock level $level")
        }
    }

    @Test
    fun clockRungsAreNested() {
        val seen = (1..Trainer.maxLevel(TrainerKind.Clock)).map { minutesDrawn(it, seed = 20 + it) }
        for ((lower, higher) in seen.zipWithNext()) {
            assertTrue(higher.containsAll(lower), "a minute was withdrawn: $lower ⊄ $higher")
        }
    }

    @Test
    fun topClockRungReadsTheFaceOut() {
        val top = minutesDrawn(Trainer.maxLevel(TrainerKind.Clock), seed = 9, draws = 400)
        assertTrue(top.all { it in 0..59 })
        assertTrue(top.any { it % 5 != 0 }, "expected off-grid minutes at the ceiling")
        assertTrue(top.any { it > 30 && it % 5 != 0 }, "expected off-grid minutes past the half")
    }

    @Test
    fun yearLevelsWidenRange() {
        val rng = Random(3)
        repeat(80) {
            val l1 = Trainer.sample(TrainerKind.Years, "de", 1, rng).prompt.toInt()
            assertTrue(l1 in 1990..2029)
            val l3 = Trainer.sample(TrainerKind.Years, "de", 3, rng).prompt.toInt()
            assertTrue(l3 in 1100..2099)
        }
    }

    @Test
    fun levelClampsToValidBounds() {
        val rng = Random(4)
        val low = Trainer.sample(TrainerKind.Numbers, "de", -3, rng)
        assertEquals(1, low.prompt.length)
        val high = Trainer.sample(TrainerKind.Numbers, "de", 99, rng)
        assertEquals(10, high.prompt.length)
    }
}
