package net.spross.kern.catalog

import net.spross.kern.model.Language

/**
 * What the catalog answers about SOUND and about the alphabet sheet — the recordings a
 * pack ships, how a form is spoken, and the example words a letter is taught with.
 *
 * Extensions rather than members, so the parsed [Catalog] itself stays the join and its
 * content; nothing here reads audio bytes, only paths.
 */

/**
 * Whether a recording pack ships for [lang] at all — `audio/<lang>/manifest.json` having
 * been there at load, which is the whole registry (`catalog/audio/README.md`).
 *
 * A pack answers for the FORMS it recorded and no others, so this never says a given word
 * can be heard — [pronunciation] does that. What it settles is whether recordings are a
 * source here at all: what the audio setting may offer, and whether a language has any
 * sound of its own before the device is asked (see [audioCapability]).
 */
fun Catalog.hasRecordings(lang: Language): Boolean = audio[lang]?.isEmpty == false

/**
 * How [visibleForm] is pronounced in [lang] — keyed by what stands on the card, so a
 * rotated synonym is spoken as itself. A bundled recording is returned only when it
 * speaks that form ([speechKey]); everything else falls to the app's synthesizer,
 * which is handed [Pronunciation.utterance]. Paths only: kern never reads audio bytes.
 *
 * [article] is the article the card shows in front of the word — `shownArticle`'s answer
 * on the TARGET side, null everywhere else, which is the same fact the synthesized branch
 * already asks for. Given one, a recording that speaks the article too is preferred; the
 * bare recording answers where the pack has none, and on the source side, whose grammar
 * is not what is being taught, it is the only one that can.
 */
fun Catalog.pronunciation(lang: Language, visibleForm: String, article: String? = null): Pronunciation {
    val manifest = audio[lang]
    val recording = manifest?.recording(visibleForm, article)
    return Pronunciation(
        form = visibleForm,
        utterance = utterance(visibleForm),
        lang = lang,
        recordingPath = recording?.let { manifest.path(it) },
        gain = recording?.gain ?: 0.0,
        gainPhone = recording?.gainPhone,
        cap = recording?.cap ?: 0.0,
        capPhone = recording?.capPhone,
        leadMs = recording?.leadMs ?: 0,
    )
}

/**
 * The letter's recording and how to play it; null → the drill speaks its NAME instead.
 * The letters' half of [pronunciation] — they carry no visible form to look up.
 */
fun Catalog.letterRecording(lang: Language, glyph: String): LetterRecording? {
    val manifest = audio[lang] ?: return null
    val recording = manifest.letterRecording(glyph) ?: return null
    return LetterRecording(manifest.path(recording), recording.gain, recording.gainPhone,
                           recording.cap, recording.capPhone, recording.leadMs)
}

/** Just the path, for the callers that only ask whether a letter CAN be played. */
fun Catalog.letterRecordingPath(lang: Language, glyph: String): String? =
    letterRecording(lang, glyph)?.path

/**
 * The alphabet reference sheet's content for [lang], null where no file is authored.
 * File presence IS the registry: adding a language's alphabet is dropping a file.
 */
fun Catalog.alphabet(lang: Language): Alphabet? = alphabets[lang]

/**
 * The entry's example word in the ALPHABET's own language — what the drill speaks and
 * gaps. Source-independent by design: the example must exist no matter who is reading,
 * so the join is never consulted. Null → the caller falls back to
 * [AlphabetEntry.exampleText], which carries no slug and therefore no recording.
 */
fun Catalog.alphabetExample(entry: AlphabetEntry, lang: Language): AlphabetExample? {
    val slug = entry.exampleSlug ?: return null
    val concept = slugIndex[slug] ?: return null
    val text = concept.realizations[lang]?.text ?: return null
    return AlphabetExample(slug, text, concept.emoji)
}

/**
 * Every word of [lang] this row could gap — the authored example first, then the rest
 * of the catalog in seed order, so a drill run varies its words instead of asking the
 * same one all evening. One element (or none) wherever [Alphabet.minesExamples] says
 * the glyph does not identify the row's sound on its own, which is the whole
 * correctness argument: what is swept in was never in doubt.
 *
 * A candidate is one WORD — no space, no sentence punctuation — carrying the glyph
 * exactly once, the same predicate [gapWord] applies before a question is asked.
 * Recordings still line up because every element keeps its slug.
 */
fun Catalog.alphabetExamples(entry: AlphabetEntry, lang: Language): List<AlphabetExample> {
    val authored = alphabetExample(entry, lang)
    if (alphabets[lang]?.minesExamples(entry) != true) return listOfNotNull(authored)
    val mined = slugIndex.asSequence().mapNotNull { (slug, concept) ->
        if (slug == authored?.slug) return@mapNotNull null
        val text = concept.realizations[lang]?.text ?: return@mapNotNull null
        if (!isGappableWord(text) || glyphOccurrences(text, entry.glyph) != 1) return@mapNotNull null
        AlphabetExample(slug, text, concept.emoji)
    }
    return listOfNotNull(authored) + mined
}

/**
 * What the example word MEANS to a reader of [lang] — null whenever that language does
 * not realize the concept. The sheet then omits the meaning line: graceful
 * degradation, never an error (an alphabet is not a join).
 */
fun Catalog.exampleMeaning(slug: String, lang: Language): String? =
    slugIndex[slug]?.realizations?.get(lang)?.text

/**
 * Attribution for every bundled recording, grouped by (language, author, license) —
 * BY and BY-SA cannot share a notice, so the groups ARE the credit rows. Derived from
 * the shipped manifests, so the surface can never credit what is not bundled. Order is
 * stable: languages as declared, entries as the manifest lists them.
 */
fun Catalog.audioCredits(): List<AudioCredit> {
    val files = LinkedHashMap<CreditKey, MutableList<AudioCreditFile>>()
    val deeds = mutableMapOf<CreditKey, String?>()
    for ((lang, manifest) in audio) {
        for ((label, recording) in manifest.creditRows()) {
            val key = CreditKey(lang, recording.author, recording.license)
            files.getOrPut(key) { mutableListOf() } += AudioCreditFile(label, recording.source)
            if (key !in deeds) deeds[key] = recording.licenseUrl
        }
    }
    return files.map { (key, rows) ->
        AudioCredit(key.language, key.author, key.license, deeds[key], rows)
    }
}

/** Credit identity: one author's work in one language under one license. */
private data class CreditKey(val language: Language, val author: String, val license: String)
