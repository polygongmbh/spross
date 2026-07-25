package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hand-picked Ukrainian assertions (no golden fixture exists for uk).
 * Expected strings are documented here for external verification;
 * canonical = masculine counting form, feminine (одна/дві) accepted.
 */
class TrainerUkrainianTests {

    private fun number(n: Long) = Trainer.number(n, "uk")
    private fun clock(h: Int, m: Int) = Trainer.clock(h, m, "uk")

    @Test
    fun basicNumbers() {
        val expected = mapOf(
            0L to "нуль", 1L to "один", 2L to "два", 3L to "три", 4L to "чотири",
            5L to "п'ять", 6L to "шість", 7L to "сім", 8L to "вісім", 9L to "дев'ять",
            10L to "десять", 11L to "одинадцять", 12L to "дванадцять", 13L to "тринадцять",
            14L to "чотирнадцять", 15L to "п'ятнадцять", 16L to "шістнадцять",
            17L to "сімнадцять", 18L to "вісімнадцять", 19L to "дев'ятнадцять", 20L to "двадцять",
        )
        for ((n, word) in expected) {
            assertEquals(word, number(n).display, "n=$n")
        }
    }

    @Test
    fun feminineVariantsForOneAndTwo() {
        assertEquals(listOf("один", "одна"), number(1).accepted)
        assertEquals(listOf("два", "дві"), number(2).accepted)
        assertEquals(listOf("двадцять один", "двадцять одна"), number(21).accepted)
        assertEquals(listOf("тридцять два", "тридцять дві"), number(32).accepted)
        // teens never take the feminine split
        assertEquals(listOf("одинадцять"), number(11).accepted)
        assertEquals(listOf("дванадцять"), number(12).accepted)
    }

    @Test
    fun tensAndHundreds() {
        val expected = mapOf(
            21L to "двадцять один", 32L to "тридцять два", 40L to "сорок",
            45L to "сорок п'ять", 67L to "шістдесят сім", 89L to "вісімдесят дев'ять",
            90L to "дев'яносто", 99L to "дев'яносто дев'ять",
            100L to "сто", 101L to "сто один", 111L to "сто одинадцять",
            200L to "двісті", 300L to "триста", 400L to "чотириста", 500L to "п'ятсот",
            345L to "триста сорок п'ять", 999L to "дев'ятсот дев'яносто дев'ять",
        )
        for ((n, word) in expected) {
            assertEquals(word, number(n).display, "n=$n")
        }
    }

    @Test
    fun thousandAgreement() {
        // 1 тисяча / 2–4 тисячі / 5+ тисяч, always with feminine multiplier
        val expected = mapOf(
            1_000L to "одна тисяча", 2_000L to "дві тисячі", 3_000L to "три тисячі",
            4_000L to "чотири тисячі", 5_000L to "п'ять тисяч", 11_000L to "одинадцять тисяч",
            12_000L to "дванадцять тисяч", 21_000L to "двадцять одна тисяча",
            22_000L to "двадцять дві тисячі", 25_000L to "двадцять п'ять тисяч",
            100_000L to "сто тисяч", 111_000L to "сто одинадцять тисяч",
            1_001L to "одна тисяча один", 2_345L to "дві тисячі триста сорок п'ять",
            15_690L to "п'ятнадцять тисяч шістсот дев'яносто",
            999_999L to "дев'ятсот дев'яносто дев'ять тисяч дев'ятсот дев'яносто дев'ять",
        )
        for ((n, word) in expected) {
            assertEquals(word, number(n).display, "n=$n")
        }
        // "тисяча" without "одна" is an accepted colloquial reading for 1xxx
        assertTrue("тисяча" in number(1_000).accepted)
        assertTrue("тисяча дев'ятсот сімдесят вісім" in number(1_978).accepted)
    }

    @Test
    fun yearsUsePlainCardinalReading() {
        val task = Trainer.year(1978, "uk")
        assertEquals("одна тисяча дев'ятсот сімдесят вісім", task.display)
        assertTrue("тисяча дев'ятсот сімдесят вісім" in task.accepted)
        assertEquals("дві тисячі двадцять шість", Trainer.year(2026, "uk").display)
    }

    @Test
    fun clockPatterns() {
        // exact hour: ordinal + година, bare ordinal accepted
        val two = clock(14, 0)
        assertEquals("друга година", two.display)
        assertTrue("друга" in two.accepted)
        assertEquals("дванадцята година", clock(0, 0).display)
        assertEquals("перша година", clock(13, 0).display)

        // half past: пів на + accusative of next hour
        assertEquals("пів на третю", clock(2, 30).display)
        assertEquals("пів на дванадцяту", clock(11, 30).display)
        assertEquals("пів на першу", clock(12, 30).display)

        // quarter past: чверть на + accusative; variant "п'ятнадцять по <locative>"
        val quarter = clock(2, 15)
        assertEquals("чверть на третю", quarter.display)
        assertTrue("п'ятнадцять по другій" in quarter.accepted)
        assertEquals("чверть на першу", clock(12, 15).display)

        // quarter to: за чверть + nominative of next hour
        assertEquals("за чверть третя", clock(2, 45).display)
        assertEquals("за чверть дванадцята", clock(23, 45).display)
        assertTrue("за п'ятнадцять третя" in clock(2, 45).accepted)

        // generic minutes: digital reading, plus по/за variants
        val d35 = clock(14, 35)
        assertEquals("друга тридцять п'ять", d35.display)
        assertTrue("за двадцять п'ять третя" in d35.accepted)
        val d10 = clock(14, 10)
        assertEquals("друга десять", d10.display)
        assertTrue("десять по другій" in d10.accepted)
        assertTrue("за п'ять десята" in clock(9, 55).accepted)
    }
}
