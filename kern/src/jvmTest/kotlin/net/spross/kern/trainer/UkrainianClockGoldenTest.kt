package net.spross.kern.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Ukrainian clock's reveal — display and gloss — for every minute of the day,
 * against resources/ukrainian-clock-golden.tsv (`HH:MM`, display, gloss; tab-separated,
 * an empty gloss where the reveal names none).
 *
 * The vector was generated from the generator itself, so it pins the output rather than
 * vouching for it: a deliberate change to a reading regenerates the file in the same commit.
 */
class UkrainianClockGoldenTest {

    @Test
    fun everyMinuteRevealsWhatItDidWhenPinned() {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("ukrainian-clock-golden.tsv"))
        val lines = stream.readBytes().decodeToString().lines().filter { it.isNotEmpty() }
        assertEquals(24 * 60, lines.size)
        for (line in lines) {
            val (key, display, gloss) = line.split('\t')
            val (h, m) = key.split(':').map(String::toInt)
            val reading = UkrainianClock.task(h, m)
            assertEquals(display, reading.display, key)
            assertEquals(gloss.ifEmpty { null }, reading.gloss, key)
        }
    }
}
