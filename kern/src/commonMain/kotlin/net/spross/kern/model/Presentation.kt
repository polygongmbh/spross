package net.spross.kern.model

/**
 * How a card's next review is PRESENTED. Both roles feed the single FSRS
 * schedule — presentation never touches scheduling state.
 */
enum class PresentationRole {
    /** Source prompt → typed target answer. */
    Produce,

    /** Target prompt → flip + self-grade (never typed). */
    Recognize,
}

/**
 * Role of a card's next review, resolved at render time from the review-log count.
 *
 * FIRST exposure (count 0) is ALWAYS recognition — the learner cannot produce a
 * word never seen; the target is prompted first with its emoji as the cue (a
 * learner who already knows it gets the moment to recall), and the reveal
 * teaches the meaning, self-graded.
 * The SECOND review (count 1) is ALWAYS production — the word
 * has been seen once, now attempt it (ruling 2026-07-22: "returns the same
 * session as production"). Thereafter roles alternate per review with a stable
 * per-card phase offset (v1's mixedDirections parity, hash bit-exact) so the
 * box does not flip in sync.
 */
fun presentationRole(cardId: String, reviewCount: Int): PresentationRole {
    if (reviewCount == 0) return PresentationRole.Recognize
    if (reviewCount == 1) return PresentationRole.Produce
    val flip = (reviewCount + (fnv1a64(cardId) % 2uL).toInt()) % 2 == 1
    return if (flip) PresentationRole.Recognize else PresentationRole.Produce
}

/**
 * The target form to PROMPT on a recognition review: rotates deterministically
 * through canonical text + synonyms at zero extra scheduling cost. First
 * exposure always prompts the canonical text; afterwards the index advances
 * once per recognition review (recognition happens every other review, so
 * `reviewCount / 2` is parity-independent), offset per card by the id hash.
 * Variants never rotate (accept/display-only). Produce prompts ignore this.
 */
fun recognitionPromptForm(card: Card, reviewCount: Int): String {
    val forms = listOf(card.target.text) + card.target.synonyms
    if (forms.size == 1 || reviewCount == 0) return forms.first()
    val offset = (fnv1a64(card.id) % forms.size.toULong()).toInt()
    return forms[(reviewCount / 2 + offset) % forms.size]
}

/** WHEN the picture is shown. Where it sits is the renderer's business and never moves. */
enum class EmojiCue {
    /** From the start — only where it cannot give the answer away. */
    Upfront,

    /** Held back until the reveal, where nothing is left to give away. */
    OnReveal,
}

/**
 * Emoji policy. The picture is there from the START iff (first exposure) OR
 * (role == Produce && the word has not settled) — the two places it supports
 * recall without revealing it, since a produce prompt already names the concept
 * in the source language. Everywhere else it waits for the reveal, in every
 * phase: once the answer is out the picture can leak nothing, and binding it to
 * the meaning is exactly what a word still being recognised by novelty needs.
 */
fun emojiCue(
    role: PresentationRole,
    settled: Boolean,
    reviewCount: Int,
): EmojiCue =
    if (reviewCount == 0 || (role == PresentationRole.Produce && !settled)) {
        EmojiCue.Upfront
    } else {
        EmojiCue.OnReveal
    }

/** WHEN target-language audio may play without giving the answer away. */
enum class PronunciationCue {
    /** From the start — the target form is on screen from frame one. */
    Upfront,

    /** Held back until the reveal, where the form the learner owes is out. */
    OnReveal,
}

/**
 * Pronunciation policy — the audio twin of [emojiCue], and the ONE rule both apps
 * consume rather than re-deriving `role == Recognize` each in their own way.
 * Recognition prompts the target itself, so hearing it teaches; production asks for
 * that very form, so the word waits for the reveal.
 */
fun pronunciationCue(role: PresentationRole): PronunciationCue =
    if (role == PresentationRole.Recognize) PronunciationCue.Upfront else PronunciationCue.OnReveal

/**
 * FNV-1a 64-bit over UTF-8 — bit-exact port of v1's `BoxEngine.stableHash`
 * (deterministic across platforms sharing state; never a runtime-seeded hash).
 */
internal fun fnv1a64(text: String): ULong {
    var hash = 0xcbf29ce484222325uL
    for (byte in text.encodeToByteArray()) {
        hash = hash xor byte.toUByte().toULong()
        hash *= 0x100000001b3uL
    }
    return hash
}
