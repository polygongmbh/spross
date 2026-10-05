package net.spross.kern.catalog

import kotlinx.serialization.json.JsonObject
import net.spross.kern.trainer.NumbersReading
import net.spross.kern.trainer.PhraseTemplate
import net.spross.kern.trainer.SwahiliConcord

/** The phrase frames' half of the catalog: `phrases/frames.json` and each language's realizations. */
internal object FrameParser {

    /**
     * The frame manifest. Frame slugs live in the same namespace as concept slugs —
     * [conceptSlugs] keeps them disjoint, so nothing can address both a card and a drill.
     */
    fun parseFrames(path: String, text: String, conceptSlugs: Set<String>): List<CatalogFrame> {
        val frames = parseJson(path, text).arr(path, "root").mapIndexed { i, el ->
            val o = el.obj(path, "[$i]")
            o.rejectUnknownKeys(path, "[$i]", setOf("slug", "slot"))
            val slug = o.requireString(path, "[$i]", "slug")
            if (slug.isEmpty() || '|' in slug || '/' in slug) parseError(path, "[$i]: bad slug \"$slug\"")
            if (slug in conceptSlugs) parseError(path, "$slug: frame slug also names a concept")
            val slot = when (val raw = o.requireString(path, slug, "slot")) {
                "numbers" -> NumbersReading.Cardinal
                "years" -> NumbersReading.Year
                "clock" -> NumbersReading.Clock
                "fraction" -> NumbersReading.Fraction
                "phone" -> NumbersReading.Phone
                else -> parseError(path, "$slug: unknown slot \"$raw\"")
            }
            CatalogFrame(slug, slot)
        }
        val slugs = frames.map { it.slug }
        if (slugs.size != slugs.toSet().size) parseError(path, "duplicate frame slug")
        return frames
    }

    /**
     * The language's number notes plus its frame realizations; validates slugs and markers
     * against [slots]. `numberNotes` is a ROOT key, so it never enters the slug namespace —
     * a frame may still be called that, and would be realized inside `frames` like any other.
     */
    fun parseFrameLanguageFile(
        path: String,
        text: String,
        slots: Map<String, NumbersReading>,
    ): RawDrills {
        val root = parseJson(path, text).obj(path, "root")
        root.rejectUnknownKeys(path, "root", setOf("numberNotes", "frames"))
        val notes = root.stringListMap(path, "root", "numberNotes")
        for ((reader, lines) in notes) {
            if (lines.isEmpty()) parseError(path, "numberNotes.$reader: no lines")
            for (line in lines) {
                if (line.isBlank() || line.trim() != line) parseError(path, "numberNotes.$reader: bad line \"$line\"")
            }
        }
        val framesObj = root["frames"]?.obj(path, "frames") ?: parseError(path, "missing \"frames\"")
        val frames = framesObj.entries.associate { (slug, el) ->
            val slot = slots[slug] ?: parseError(path, "frame for unknown slug \"$slug\"")
            slug to parseFrame(path, slug, slot, el.obj(path, slug))
        }
        return RawDrills(numberNotes = notes, frames = frames)
    }

    private fun parseFrame(path: String, slug: String, slot: NumbersReading, o: JsonObject): RawFrame {
        o.rejectUnknownKeys(
            path, slug,
            setOf("text", "accepts", "count", "masculineNumeral", "swahiliNounClass", "notes"),
        )
        val text = o.requireString(path, slug, "text")
        val accepts = o.stringList(path, slug, "accepts")
        val count = o["count"]?.let { el ->
            if (slot != NumbersReading.Cardinal) parseError(path, "$slug: count on a ${slot.name.lowercase()} frame")
            val co = el.obj(path, "$slug.count")
            co.rejectUnknownKeys(path, "$slug.count", setOf("one", "few", "many"))
            PhraseTemplate.CountForms(
                one = co.requireString(path, "$slug.count", "one"),
                few = co.requireString(path, "$slug.count", "few"),
                many = co.requireString(path, "$slug.count", "many"),
            )
        }
        val nounClass = o.optionalString(path, slug, "swahiliNounClass")?.let { raw ->
            if (slot != NumbersReading.Cardinal) {
                parseError(path, "$slug: swahiliNounClass on a ${slot.name.lowercase()} frame")
            }
            SwahiliConcord.NounClass.entries.firstOrNull { it.name == raw }
                ?: parseError(path, "$slug: unknown swahiliNounClass \"$raw\"")
        }
        for (frame in listOf(text) + accepts) {
            if (frame.isBlank()) parseError(path, "$slug: blank frame")
            LanguageNames.markerError(frame)?.let { parseError(path, "$slug: $it") }
            if (occurrences(frame, PhraseTemplate.SLOT_MARKER) != 1) {
                parseError(path, "$slug: \"$frame\" needs exactly one ${PhraseTemplate.SLOT_MARKER}")
            }
            if (occurrences(frame, PhraseTemplate.COUNT_MARKER) != (if (count == null) 0 else 1)) {
                parseError(path, "$slug: \"$frame\" carries ${PhraseTemplate.COUNT_MARKER} iff \"count\" is authored")
            }
        }
        return RawFrame(
            text = text,
            accepts = accepts,
            count = count,
            masculineNumeral = o.optionalBoolean(path, slug, "masculineNumeral") ?: false,
            swahiliNounClass = nounClass,
            notes = o.stringMap(path, slug, "notes"),
        )
    }

    private fun occurrences(text: String, marker: String): Int {
        var found = 0
        var index = text.indexOf(marker)
        while (index >= 0) {
            found++
            index = text.indexOf(marker, index + marker.length)
        }
        return found
    }
}
