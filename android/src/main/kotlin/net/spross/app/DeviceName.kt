package net.spross.app

import android.content.ContentResolver
import android.provider.Settings

/**
 * The learner's name as the DEVICE already knows it — a prefill for the onboarding ask,
 * never a value of its own: nothing is stored until the learner leaves it standing.
 *
 * Only a POSSESSIVE device name yields anything. "Tims Pixel" and "Tim's Galaxy" are
 * someone naming a phone after themselves; "Pixel 7" and "Galaxy S24" are the model it
 * came out of the box with, and a model is never a person. Everything that is not plainly
 * someone's name is left alone, so the field opens empty rather than greeting a handset.
 *
 * Read from the settings table alone — the device name the system's own "Device name"
 * writes, else the bluetooth name beside it. Both are plain reads that cost no permission;
 * accounts and contacts know the name too and are deliberately never asked.
 */
object DeviceName {

    fun suggestedLearnerName(resolver: ContentResolver): String? =
        listOfNotNull(
            Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME),
            Settings.Secure.getString(resolver, BLUETOOTH_NAME),
        ).firstNotNullOfOrNull { possessiveOwner(it) }

    /**
     * Whoever the device is named after, or null where it is named after nothing.
     *
     * The possessive is the whole of the evidence: an apostrophe ("Tim's Galaxy") or the
     * German bare s ("Tims Pixel"), on a capitalized first word that something else
     * follows. A single-word name is the model talking, and so is any first word from
     * [DEVICE_WORDS], however it ends.
     */
    internal fun possessiveOwner(deviceName: String): String? {
        val words = deviceName.trim().split(WHITESPACE)
        if (words.size < 2) return null
        val first = words.first()
        val owner = when {
            POSSESSIVE.any { first.endsWith(it) } -> first.dropLast(2)
            first.length > 3 && first.endsWith('s') -> first.dropLast(1)
            else -> return null
        }
        if (owner.length < 2 || !owner.all { it.isLetter() }) return null
        if (!owner.first().isUpperCase()) return null
        return owner.takeIf { it.lowercase() !in DEVICE_WORDS }
    }

    /** The bluetooth name has no public constant; this is the key the system files it under. */
    private const val BLUETOOTH_NAME = "bluetooth_name"

    private val WHITESPACE = Regex("\\s+")

    /** Straight, curly, and the acute some keyboards send instead. */
    private val POSSESSIVE = listOf("'s", "’s", "´s")

    /** Words that are the handset talking, even when they end in an s. */
    private val DEVICE_WORDS = setOf(
        "android", "galaxy", "handy", "honor", "huawei", "iphone", "ipad", "moto", "nexus",
        "nokia", "oneplus", "oppo", "phone", "pixel", "realme", "redmi", "samsung", "sony",
        "tablet", "telefon", "vivo", "xiaomi",
    )
}
