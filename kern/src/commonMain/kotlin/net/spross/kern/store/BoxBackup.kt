package net.spross.kern.store

import net.spross.kern.box.dayKey
import net.spross.kern.model.Language

/**
 * The file the learner exports and imports: the languages they have something in, under a
 * single schema version, minus what belongs to one device alone. Today's crossings stay
 * behind — the box it lands in has its own day.
 *
 * A restore replaces every language the file carries and leaves the rest alone
 * ([StoredBoxes.restoring]); a file with one unreadable box is refused whole, so nothing
 * ever lands half.
 */
object BoxBackup {

    /**
     * The languages an export would carry, sorted. An untouched box says nothing a restore
     * could use and would only empty the box it lands in, so it is not carried at all.
     */
    fun carried(boxes: StoredBoxes): List<Language> =
        boxes.boxes.filterValues { it.hasContent }.keys.sorted()

    /** Every carried language, or [only] alone where the learner exports the one box. */
    fun encode(boxes: StoredBoxes, only: Language? = null): String = encodeStore(
        StoredBoxes(
            boxes.boxes
                .filterKeys { only == null || it == only }
                .filterValues { it.hasContent },
        ),
    )

    /**
     * The name an export offers its file under, without an extension:
     * what it carries ([only] that language, or every one) and the learner's local day it was taken,
     * so two of them in one folder are told apart before either is opened.
     */
    fun fileName(only: Language?, nowEpochMillis: Long, tzId: String): String =
        "Spross-" + (only?.let { "$it-" } ?: "") + dayKey(nowEpochMillis, tzId)

    @Throws(StoreFormatException::class)
    fun decode(json: String): StoredBoxes = decodeStore(json)
}
