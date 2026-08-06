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
        // exact hour: ordinal + година + the part of the day; both are droppable
        val two = clock(14, 0)
        assertEquals("друга година дня", two.display)
        assertTrue("друга година" in two.accepted)
        assertTrue("друга дня" in two.accepted)
        assertTrue("друга" in two.accepted)
        assertTrue("рівно друга" in two.accepted)
        assertEquals("перша година ночі", clock(1, 0).display)

        // half past: пів на + accusative of next hour
        assertEquals("пів на третю ночі", clock(2, 30).display)
        // 11:30 names the twelfth hour, which is noon — "дванадцята ранку" is not a thing.
        assertEquals("пів на дванадцяту дня", clock(11, 30).display)
        assertEquals("пів на першу дня", clock(12, 30).display)
        assertTrue("пів третьої" in clock(2, 30).accepted)

        // quarter past: чверть на + accusative; variant "п'ятнадцять по <locative>"
        val quarter = clock(2, 15)
        assertEquals("чверть на третю ночі", quarter.display)
        assertTrue("п'ятнадцять по другій" in quarter.accepted)
        assertTrue("п'ятнадцять хвилин на третю" in quarter.accepted)
        assertEquals("чверть на першу дня", clock(12, 15).display)

        // quarter to: за чверть + nominative of next hour
        assertEquals("за чверть третя ночі", clock(2, 45).display)
        assertEquals("за чверть дванадцята ночі", clock(23, 45).display)
        assertTrue("за п'ятнадцять третя" in clock(2, 45).accepted)
        assertTrue("чверть до третьої" in clock(2, 45).accepted)

        // round steps take the construction; a minute off the grid is read out
        val d35 = clock(14, 35)
        assertEquals("за двадцять п'ять третя дня", d35.display)
        assertTrue("двадцять п'ять хвилин до третьої" in d35.accepted)
        assertEquals("двадцять хвилин на третю дня", clock(14, 20).display)
        assertEquals("друга сімнадцять дня", clock(14, 17).display)
        assertTrue("десять по другій" in clock(14, 10).accepted)
        assertTrue("за п'ять десята" in clock(9, 55).accepted)
    }

    /** Minutes count хвилина, which is feminine — "дві", never "два". */
    @Test
    fun minutesAreCountedAsTheFeminineNounTheyAre() {
        assertTrue("дві хвилини на третю" in clock(14, 2).accepted)
        assertTrue("двадцять одна хвилина на третю" in clock(14, 21).accepted)
        assertTrue("за дві хвилини третя" in clock(14, 58).accepted)
        assertTrue("за одну хвилину третя" in clock(14, 59).accepted)
        // At a count of one the noun stands alone; the bare numeral never does.
        assertTrue("хвилина на третю" in clock(14, 1).accepted)
        assertTrue("одна на третю" !in clock(14, 1).accepted)
        for (task in listOf(clock(14, 2), clock(14, 21), clock(14, 58))) {
            val masculine = task.accepted.filter { "два" in it.split(' ') || "один" in it.split(' ') }
            assertTrue(masculine.isEmpty(), masculine.toString())
        }
    }

    /**
     * The part of the day belongs to the hour the reading NAMES: 11:45 names noon,
     * so it is дня, not ранку — and 17:45 names six in the evening, not the afternoon.
     */
    @Test
    fun theDayPartFollowsTheHourTheReadingNames() {
        assertEquals("за чверть дванадцята дня", clock(11, 45).display)
        assertEquals("за чверть шоста вечора", clock(17, 45).display)
        assertEquals("дев'ята година ранку", clock(9, 0).display)
        assertEquals("дев'ята година вечора", clock(21, 0).display)
        // Overlapping boundaries accept both readings.
        assertTrue("третя година ночі" in clock(3, 0).accepted)
        assertTrue("третя година ранку" in clock(3, 0).accepted)
    }

    @Test
    fun midnightAndNoonAreNamedAndTheOfficialClockIsAccepted() {
        val midnight = clock(0, 0)
        assertEquals("північ", midnight.display)
        assertTrue("опівночі" in midnight.accepted)
        assertTrue("нульова година" in midnight.accepted)
        val noon = clock(12, 0)
        assertEquals("дванадцята година дня", noon.display)
        assertTrue("полудень" in noon.accepted)
        assertTrue("опівдні" in noon.accepted)
        // Timetables and news run 0–23, and never name a part of the day.
        assertTrue("шістнадцята година сорок п'ять хвилин" in clock(16, 45).accepted)
        assertTrue("шістнадцята сорок п'ять" in clock(16, 45).accepted)
        assertTrue("двадцять перша година" in clock(21, 0).accepted)
    }
}
