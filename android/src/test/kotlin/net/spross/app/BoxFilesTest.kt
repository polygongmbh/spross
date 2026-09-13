package net.spross.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BoxFilesTest {

    private fun tempDir(): File = Files.createTempDirectory("box-files-test").toFile()

    @Test
    fun filenameFollowsConvention() {
        assertEquals("box-sw.json", BoxFiles(tempDir()).fileFor("sw").name)
    }

    @Test
    fun missingDocumentReadsAsNull() {
        assertNull(BoxFiles(tempDir()).read("uk"))
    }

    @Test
    fun writeThenReadRoundTrips() {
        val files = BoxFiles(tempDir())
        files.write("sw", """{"schemaVersion":2}""")
        assertEquals("""{"schemaVersion":2}""", files.read("sw"))
    }

    @Test
    fun overwriteReplacesAndLeavesNoTemp() {
        val dir = tempDir()
        val files = BoxFiles(dir)
        files.write("sw", "first")
        files.write("sw", "second")
        assertEquals("second", files.read("sw"))
        assertFalse(dir.list()!!.any { it.endsWith(".tmp") })
    }

    /** Which languages the device holds — the streak's sibling read, and the export's list. */
    @Test
    fun targetsAreTheDocumentsOnDisk() {
        val dir = tempDir()
        val files = BoxFiles(dir)
        files.write("uk", "{}")
        files.write("sw", "{}")
        files.writeWidgetSnapshot("{}")

        assertEquals(listOf("sw", "uk"), files.targets().sorted())
    }
}
