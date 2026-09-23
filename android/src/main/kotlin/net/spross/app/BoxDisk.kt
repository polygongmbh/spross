package net.spross.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxState
import net.spross.kern.catalog.Catalog
import net.spross.kern.catalog.CountryDrillContent
import net.spross.kern.catalog.DateDrillContent
import net.spross.kern.catalog.countryDrillContent
import net.spross.kern.catalog.dateDrillContent
import net.spross.kern.model.BoxConfig
import net.spross.kern.model.JoinStamp
import net.spross.kern.store.StoreCodec
import net.spross.kern.store.StoreFormatException
import net.spross.kern.store.StoredBox
import net.spross.kern.store.StoredBoxes
import net.spross.kern.store.rekeyingPrefixedVerbs

/** Every language's stored box on disk ([files]), and what this launch has read or written of them. */
class BoxDisk(val files: BoxFiles) {

    /** What has been read or written this launch, by target ([StoredBoxes]). */
    var boxes: StoredBoxes = StoredBoxes.EMPTY
        private set

    /** Every box the device holds, read in where this launch has not read it yet. */
    fun openEvery(): StoredBoxes {
        files.targets().filter { it !in boxes.boxes }.forEach { runCatching { open(it) } }
        return boxes
    }

    /**
     * One language's stored box, or null where the device holds none. A v1 document
     * converts as it is read and is written back under the same name — a conversion that
     * never reaches disk would convert again on every launch.
     */
    fun open(target: String): StoredBox? {
        val json = files.read(target) ?: return null
        val loaded = StoreCodec.load(json)
        if (loaded.converted) files.write(target, StoreCodec.encode(loaded.box))
        boxes = StoredBoxes(boxes.boxes + (target to loaded.box))
        return loaded.box
    }

    /**
     * Answers per day in every OTHER language. A sibling that cannot be read is skipped —
     * its own load path surfaces the real error when the learner switches to it.
     */
    fun otherLanguagesDays(target: String, tz: String): Map<String, Int> {
        files.targets().filter { it != target && it !in boxes.boxes }
            .forEach { runCatching { open(it) } }
        return boxes.answerDaysExcept(target, tz)
    }

    /** Takes [imported] in over what is held; writing the targets it carries is [write]'s. */
    fun restoring(imported: StoredBoxes) {
        boxes = boxes.restoring(imported)
    }

    /** Takes [state] in over what is held, and hands back the document it now stands as. */
    fun record(state: BoxState): StoredBox {
        boxes = boxes.with(state)
        return boxes.boxes.getValue(state.joinStamp.target)
    }

    fun write(target: String, box: StoredBox) = files.write(target, StoreCodec.encode(box))

    /**
     * Joins the pair and stands its box up on it, from disk where the device holds one —
     * a failure carrying the [StoreFormatException] where it holds one it cannot read.
     */
    suspend fun join(cat: Catalog, source: String, target: String, tz: String): Result<JoinedPair> {
        val stamp = JoinStamp(source, target, cat.fingerprint)
        val opened = withContext(Dispatchers.IO) {
            try {
                Result.success(open(target))
            } catch (e: StoreFormatException) {
                Result.failure(e)
            }
        }
        val saved = opened.getOrElse { return Result.failure(it) }
        // why: the join builds every card the profile holds, replaying the logs re-applies
        // every answer ever given, and the atlas walks the whole country manifest — none of
        // it belongs on the thread that has to draw the first frame.
        val loaded = withContext(Dispatchers.Default) {
            val cards = cat.join(source, target)
            // why: the pair only changes here — the hub reads the atlas and the
            // calendars on every composition, and a sweep per frame is one no
            // start-up should pay.
            Triple(
                // rekeyingPrefixedVerbs: TODO remove once the app is past 7.0.
                saved?.join(cards, stamp)?.rekeyingPrefixedVerbs()
                    ?: BoxEngine.bootstrap(cards, BoxConfig.product(), stamp),
                cat.countryDrillContent(source, target),
                cat.dateDrillContent(source, target),
            )
        }
        val (joined, atlas, dates) = loaded
        // why: resolved before `box` is published, so the Box screen's first recomposition
        // against the new box already carries matching stats — an IO hop between the two
        // let Compose draw the new box against the outgoing language's stats, which is
        // what jumbled its scroll.
        val days = withContext(Dispatchers.IO) { otherLanguagesDays(target, tz) }
        return Result.success(JoinedPair(joined, saved == null, atlas, dates, days))
    }
}

/** A pair joined with its box stood up on it — everything [AppModel.activate] publishes. */
class JoinedPair(
    val box: BoxState,
    /** Whether the device held no box for the pair yet, which is what owes the disk a write. */
    val fresh: Boolean,
    val atlas: CountryDrillContent?,
    val dates: DateDrillContent?,
    val otherLanguagesAnswerDays: Map<String, Int>,
)
