package net.spross.app

import net.spross.kern.box.BoxState
import net.spross.kern.store.StoreCodec
import net.spross.kern.store.StoredBox
import net.spross.kern.store.StoredBoxes

/** Every language's stored box on disk ([files]), and what this launch has read or written of them. */
class BoxStore(val files: BoxFiles) {

    /** What has been read or written this launch, by target ([StoredBoxes]). */
    var boxes: StoredBoxes = StoredBoxes.EMPTY
        private set

    /** Every box the device holds, read in where this launch has not read it yet. */
    fun everyLanguage(): StoredBoxes {
        files.targets().filter { it !in boxes.boxes }.forEach { runCatching { load(it) } }
        return boxes
    }

    /**
     * One language's stored box, or null where the device holds none.
     * Throws a [net.spross.kern.store.StoreFormatException] where the file exists but cannot be read.
     * A v1 document converts as it is read and is written back under the same name —
     * a conversion that never reaches disk would convert again on every launch.
     */
    fun load(target: String): StoredBox? {
        val json = files.read(target) ?: return null
        val loaded = StoreCodec.load(json)
        if (loaded.converted) files.write(target, StoreCodec.encode(loaded.box))
        boxes = StoredBoxes(boxes.boxes + (target to loaded.box))
        return loaded.box
    }

    /**
     * Answers per day in every language but [excluding] — the cross-language streak's input.
     * A sibling that cannot be read is skipped:
     * its own load path surfaces the real error when the learner switches to it.
     */
    fun answerDays(excluding: String, tzId: String): Map<String, Int> {
        files.targets().filter { it != excluding && it !in boxes.boxes }
            .forEach { runCatching { load(it) } }
        return boxes.answerDaysExcept(excluding, tzId)
    }

    /** Takes [imported] in over what is held; writing the targets it carries is [write]'s. */
    fun restoring(imported: StoredBoxes) {
        boxes = boxes.restoring(imported)
    }

    /** Takes [state] in over what is held, and hands back the document it now stands as. */
    fun hold(state: BoxState): StoredBox {
        boxes = boxes.with(state)
        return boxes.boxes.getValue(state.joinStamp.target)
    }

    fun write(target: String, box: StoredBox) = files.write(target, StoreCodec.encode(box))
}
