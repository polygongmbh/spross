package net.spross.kern.store

/**
 * The file the learner exports and imports: every language in ONE document under a single
 * schema version, minus what belongs to one device alone. Today's crossings stay behind —
 * the box it lands in has its own day.
 *
 * A restore replaces every language the file carries and leaves the rest alone
 * ([StoredBoxes.restoring]); a file with one unreadable box is refused whole, so nothing
 * ever lands half.
 */
object BoxBackup {

    fun encode(boxes: StoredBoxes): String = encodeStore(
        StoredBoxes(boxes.boxes.mapValues { (_, box) -> box.copy(consolidatedToday = null) }),
    )

    @Throws(StoreFormatException::class)
    fun decode(json: String): StoredBoxes = decodeStore(json)
}
