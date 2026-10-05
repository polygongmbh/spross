package net.spross.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.spross.kern.box.BoxEngine
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.JoinStamp
import net.spross.kern.store.SaveScope
import net.spross.kern.store.StoredBox
import net.spross.kern.store.StoredBoxes

/** What a save and a restore leave on disk. */
class BoxStoreTest {

    private val files = BoxFiles(Files.createTempDirectory("box-store-test").toFile())
    private val store = BoxStore(files) {}
    private val state = BoxEngine.bootstrap(emptyList(), BoxConfig.product(), JoinStamp("de", "sw", "fp"))

    @Test
    fun aBoxSaveWritesTheBoxAlone() {
        store.saveNow(state, SaveScope.BOX)
        assertNotNull(files.read("sw"))
        assertNull(files.readWidgetSnapshot())
    }

    @Test
    fun aSaveCarryingTheSnapshotsWritesTheWidgetsToo() {
        store.saveNow(state, SaveScope.BOX_AND_SNAPSHOTS)
        assertNotNull(files.read("sw"))
        assertNotNull(files.readWidgetSnapshot())
    }

    @Test
    fun aRestoreWritesWhatTheFileCarries() {
        store.restore(StoredBoxes(mapOf("sw" to StoredBox.of(state))))
        assertNotNull(files.read("sw"))
    }
}
