package net.spross.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BoxFilesTest {

    private fun tempFiles(): BoxFiles =
        BoxFiles(Files.createTempDirectory("box-files-test").toFile())

    @Test
    fun filenameFollowsConvention() {
        assertEquals("box-sw.json", tempFiles().fileFor("sw").name)
    }

    @Test
    fun missingDocumentReadsAsNull() {
        assertNull(tempFiles().read("uk"))
    }

    @Test
    fun writeThenReadRoundTrips() {
        val files = tempFiles()
        files.write("sw", """{"schemaVersion":1}""")
        assertEquals("""{"schemaVersion":1}""", files.read("sw"))
    }

    @Test
    fun overwriteReplacesAndLeavesNoTemp() {
        val files = tempFiles()
        files.write("sw", "first")
        files.write("sw", "second")
        assertEquals("second", files.read("sw"))
        assertFalse(files.fileFor("sw").parentFile.list()!!.any { it.endsWith(".tmp") })
    }
}
