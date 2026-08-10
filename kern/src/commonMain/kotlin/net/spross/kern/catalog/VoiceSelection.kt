package net.spross.kern.catalog

import net.spross.kern.model.Language

/**
 * Which synthesized voice speaks a language.
 *
 * Spanish is taught in the peninsular variety (distinción) — a Latin-American voice would
 * teach seseo — so the bare code widens to "es-ES", and where a device's voices can be
 * searched, a peninsular one outranks even a higher-quality Latin-American voice: the
 * variety is what the catalog teaches, not a preference about how a device sounds.
 *
 * Enumerating the voices, and what a platform's quality scale means, stays app-side.
 */
object VoiceSelection {

    private const val SPANISH = "es"
    private const val PENINSULAR_SPANISH = "es-ES"
    private const val PENINSULAR_SPANISH_KEY = "es-es"

    /** One voice a device offers, in the terms the rule needs. */
    data class Candidate(
        /** BCP-47 tag as the platform reports it ("es-ES", "de-AT"); matched case-insensitively. */
        val languageTag: String,
        /** The platform's own quality scale, higher being better — only the ORDER of it is read. */
        val quality: Int,
        /** Stable per-voice identity; ties break on it so one device always picks the same voice. */
        val identifier: String,
    )

    /**
     * The tag to request for [language]: "es" widens to "es-ES", every other code stands as it is.
     *
     * This is the half a platform that cannot search its voices still needs —
     * asking for the variety is all the say it has in which voice answers.
     */
    fun preferredTag(language: Language): String =
        if (language.lowercase() == SPANISH) PENINSULAR_SPANISH else language

    /**
     * The voice [language] is spoken in, or null where the device offers none.
     *
     * Candidates are the voices whose tag IS the code or a region of it ("de" takes "de-AT").
     * For Spanish the pool narrows to the peninsular voices wherever the device has any,
     * so a higher-quality Latin-American voice loses to them.
     * Within the pool the highest quality wins, and ties go to the lower identifier:
     * one device picks the same voice every time, which is what makes a word sound like itself twice.
     */
    fun select(language: Language, candidates: List<Candidate>): Candidate? {
        val code = language.lowercase()
        val matching = candidates.filter {
            val tag = it.languageTag.lowercase()
            tag == code || tag.startsWith("$code-")
        }
        val peninsular =
            if (code == SPANISH) matching.filter { it.languageTag.lowercase() == PENINSULAR_SPANISH_KEY }
            else emptyList()
        return peninsular.ifEmpty { matching }
            .sortedWith(compareByDescending<Candidate> { it.quality }.thenBy { it.identifier })
            .firstOrNull()
    }
}
