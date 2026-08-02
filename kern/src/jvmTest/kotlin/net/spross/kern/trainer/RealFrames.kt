package net.spross.kern.trainer

import net.spross.kern.catalog.RealCatalog
import net.spross.kern.model.Language

/**
 * The real catalog's frames, joined. Frames are content now, so the phrase suites read them
 * the way the app does — `Catalog.phraseTemplates(source, target)` — instead of a Kotlin table.
 */
internal object RealFrames {

    private val catalog get() = RealCatalog.catalog

    private val languages: List<Language> get() = catalog.languages.keys.toList()

    /** Every ordered pair the catalog joins, so a frame authored in one language is swept. */
    val all: List<PhraseTemplate> by lazy {
        languages.flatMap { source ->
            languages.filter { it != source }.flatMap { target -> of(source, target) }
        }
    }

    fun of(source: Language, target: Language): List<PhraseTemplate> =
        catalog.phraseTemplates(source, target)

    /** The one joined frame with [slug]; [source] defaults to the product's German prompt side. */
    fun frame(target: Language, slug: String, source: Language = "de"): PhraseTemplate =
        of(source, target).first { it.id == slug }
}
