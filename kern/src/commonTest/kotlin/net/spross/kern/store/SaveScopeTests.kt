package net.spross.kern.store

import kotlin.test.Test
import kotlin.test.assertEquals

/** Two saves written as one owe whatever either of them owed. */
class SaveScopeTests {
    @Test
    fun aSnapshotSaveOvertakenByABoxSaveStillCarriesTheSnapshots() {
        assertEquals(SaveScope.BOX_AND_SNAPSHOTS, SaveScope.SNAPSHOTS + SaveScope.BOX)
    }

    @Test
    fun twoBoxSavesStayABoxSave() {
        assertEquals(SaveScope.BOX, SaveScope.BOX + SaveScope.BOX)
    }
}
