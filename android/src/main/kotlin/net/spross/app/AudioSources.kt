package net.spross.app

import net.spross.kern.catalog.AudioCapability
import net.spross.kern.catalog.audioCapability
import net.spross.kern.model.Language

/**
 * What can carry [lang]'s sound on this device — kern's rule ([AudioCapability]) over the two
 * halves this side owns: the catalog it loaded and the voice table the synthesizer reports.
 *
 * Named apart from kern's own `audioCapability` so a call here never reads as the rule itself:
 * the rule is kern's, this is only where its two inputs are found.
 */
fun AppModel.audioSources(lang: Language): AudioCapability =
    catalog?.let { audioCapability(it, lang, pronouncer.canSpeak(lang)) }
    // No catalog yet is the absence of the rule's INPUT, not an audio ruling of this side's
    // own — so it falls back to the answer kern would give for a language it knows nothing
    // about, rather than deciding anything here.
        ?: AudioCapability.None
