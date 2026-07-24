package net.spross.app

import java.io.File

/**
 * One JSON document per target language, `box-<target>.json` (kern-design §7).
 * Android has no App Group; the app-private files dir is the single owner.
 */
class BoxFiles(private val dir: File) {

    fun fileFor(target: String): File = File(dir, "box-$target.json")

    fun read(target: String): String? =
        fileFor(target).takeIf { it.isFile }?.readText()

    // why: temp-then-rename keeps a crash mid-write from corrupting the only copy.
    fun write(target: String, json: String) {
        dir.mkdirs()
        val destination = fileFor(target)
        val temp = File(dir, "box-$target.json.tmp")
        temp.writeText(json)
        if (!temp.renameTo(destination)) {
            destination.delete()
            check(temp.renameTo(destination)) { "cannot replace ${destination.name}" }
        }
    }
}
