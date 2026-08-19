package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a device name is allowed to say about its owner.
 *
 * The prefill is a guess offered into an editable field, so the cost of being wrong is
 * small — but greeting someone as "Pixel" is worse than greeting them as nobody, which
 * is why the possessive has to be there before a name is read out of a device name.
 */
class DeviceNameTest {

    @Test
    fun aPossessiveDeviceNameNamesItsOwner() {
        assertEquals("Tim", DeviceName.possessiveOwner("Tims Pixel"))
        assertEquals("Tim", DeviceName.possessiveOwner("Tim's Galaxy"))
        assertEquals("Tim", DeviceName.possessiveOwner("Tim’s iPhone"))
        assertEquals("Anna", DeviceName.possessiveOwner("Annas Handy"))
    }

    @Test
    fun aModelNameNamesNobody() {
        assertNull(DeviceName.possessiveOwner("Pixel 7 Pro"))
        assertNull(DeviceName.possessiveOwner("Galaxy S24"))
        assertNull(DeviceName.possessiveOwner("iPhone"))
        assertNull(DeviceName.possessiveOwner("SM-G991B"))
        // The possessive is not enough on its own: the device word stays a device word.
        assertNull(DeviceName.possessiveOwner("Pixels Besitzer"))
    }

    @Test
    fun aNameWithNothingToOwnIsLeftAlone() {
        assertNull(DeviceName.possessiveOwner("Tims"))
        assertNull(DeviceName.possessiveOwner("Tim"))
        assertNull(DeviceName.possessiveOwner(""))
        // A first word that is not a possessive at all decides nothing about the second.
        assertNull(DeviceName.possessiveOwner("Mein Telefon"))
    }
}
