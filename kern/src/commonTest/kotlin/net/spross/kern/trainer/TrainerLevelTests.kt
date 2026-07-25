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

    @Test
    fun clockLevelsRestrictMinutes() {
        val rng = Random(2)
        repeat(80) {
            val l1 = Trainer.sample(TrainerKind.Clock, "de", 1, rng)
            assertTrue(l1.prompt.endsWith(":00"))
            val l3 = Trainer.sample(TrainerKind.Clock, "sw", 3, rng)
            val minute = l3.prompt.takeLast(2).toInt()
            assertTrue(minute <= 30)
        }
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
