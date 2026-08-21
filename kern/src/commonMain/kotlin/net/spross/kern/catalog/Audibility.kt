package net.spross.kern.catalog

import net.spross.kern.model.Language

/**
 * Whether a form can be HEARD at all: a recording that speaks this very form, or a voice.
 *
 * The one audio predicate every pool shares — the letter drill's dictation candidates and the
 * listening playlist both stand or fall on it, and a second copy of it would drift silently,
 * since a pool that wrongly keeps a word only shows up as one dead beat inside a run.
 * `hasVoice` is the caller's, because whether this device can say anything in [language] is
 * the single platform fact kern cannot know (`LetterDrillAvailability`).
 */
internal fun audible(
    form: String,
    language: Language,
    catalog: Catalog,
    hasVoice: Boolean,
): Boolean = catalog.pronunciation(language, form).recordingPath != null || hasVoice
