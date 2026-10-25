package net.spross.kern.arch

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import net.spross.kern.repoRoot

/**
 * The kern takes `nowEpochMillis` and `tzId` as parameters and never reads the clock or the
 * device zone itself, so every rule replays the same under a test as on a phone.
 *
 * Read as text out of `kern/src/commonMain`, which is already this task's classpath input,
 * so an edit there re-runs it.
 * Waive a genuine exception with `// layer-ok: <reason>` on the line or the one above it.
 */
class KernClockTest {

    @Test
    fun kernNeverReadsTheClockOrTheDeviceZone() {
        val bad = File(repoRoot, "kern/src/commonMain").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val lines = file.readLines()
                lines.withIndex().filter { (i, line) ->
                    CLOCK.containsMatchIn(line) &&
                        "layer-ok:" !in line && (i == 0 || "layer-ok:" !in lines[i - 1])
                }.map { (i, line) -> "${file.relativeTo(repoRoot)}:${i + 1}: ${line.trim()}" }
            }
            .toList()
        assertTrue(
            bad.isEmpty(),
            "kern reads the clock or the device zone — take `nowEpochMillis` / `tzId` as " +
                "parameters, or waive with `// layer-ok: <reason>`:\n" + bad.joinToString("\n"),
        )
    }
}

private val CLOCK = Regex("""Clock\.System|currentTimeMillis|nanoTime|currentSystemDefault""")
