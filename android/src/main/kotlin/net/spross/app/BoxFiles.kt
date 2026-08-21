package net.spross.app

import java.io.File

/**
 * One JSON document per target language, `box-<target>.json` (kern/docs/snapshots.md).
 * Android has no App Group; the app-private files dir is the single owner.
 */
class BoxFiles(private val dir: File) {

    fun fileFor(target: String): File = File(dir, "box-$target.json")

    fun read(target: String): String? =
        fileFor(target).takeIf { it.isFile }?.readText()

    fun write(target: String, json: String) = writeAtomically(fileFor(target), json)

    /**
     * The home-screen widget's pre-resolved read model, one file for the whole app
     * rather than one per target: the widget draws the box the learner is IN, and the
     * app rewrites this whenever that box changes ([net.spross.kern.snapshot.WidgetSnapshotBuilder]).
     */
    fun readWidgetSnapshot(): String? = widgetSnapshot.takeIf { it.isFile }?.readText()

    fun writeWidgetSnapshot(json: String) = writeAtomically(widgetSnapshot, json)

    private val widgetSnapshot: File get() = File(dir, "widget-snapshot.json")

    // why: temp-then-rename keeps a crash mid-write from corrupting the only copy.
    private fun writeAtomically(destination: File, json: String) {
        dir.mkdirs()
        val temp = File(dir, "${destination.name}.tmp")
        temp.writeText(json)
        if (!temp.renameTo(destination)) {
            destination.delete()
            check(temp.renameTo(destination)) { "cannot replace ${destination.name}" }
        }
    }
}
