package net.spross.kern

import java.io.File
import kotlin.test.fail

/** The repo root, walked up to from the test's working directory. */
internal val repoRoot: File by lazy {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        if (File(dir, "kern/build.gradle.kts").isFile) return@lazy dir
        dir = dir.parentFile
    }
    error("kern/build.gradle.kts not found above ${System.getProperty("user.dir")}")
}

/**
 * A repo file read as text, for the lints that hold a hand-written copy to the kern
 * table it restates — a missing file is a reshaped surface, never a passing check.
 */
internal fun repoText(path: String): String =
    File(repoRoot, path).takeIf { it.isFile }?.readText()
        ?: fail("$path: missing — a copy cannot be checked against nothing")
