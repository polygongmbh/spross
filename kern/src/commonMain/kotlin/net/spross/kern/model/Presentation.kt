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

/** What a produce turn PROMPTS with — the answer it asks for is the same either way. */
enum class ProducePrompt {
    /** The known-language word. */
    Source,

    /**
     * The target word SPOKEN, and nothing on screen: the learner writes what it MEANS,
     * in the source language. Writing back the word that played would prove the ear
     * worked and nothing else — translating it is what the box is for.
     */
    Sound,
}

/**
 * When a produce review asks by ear instead of by sight.
 *
 * Not a third [PresentationRole]: the role function is a bit-exact v1 contract, and a word
 * asked from its sound is still being produced — only the side the card asks FROM moves, so
 * one FSRS schedule still sees one kind of answer. The answer moves with it: the meaning is
 * what is owed back, because a word heard and written down again has been transcribed, not
 * understood.
 *
 * Two gates and a rotation. [consolidated]
 * ([net.spross.kern.model.BoxConfig.consolidatedStability]) is the same bar
 * [emojiCue] reads, from the other side: a word that has landed can spare its meaning,
 * and one still landing must not have its only cue taken away. [audible]
 * is the device's word (a recording, a voice, and reading aloud switched on), and false
 * falls back to the source prompt rather than blocking: review has another way to ask the
 * same question, so nothing is ever hidden behind a silent phone.
 *
 * The rotation divides the count by two, as [recognitionPromptForm] does — roles alternate
 * per review, so `reviewCount % 2` is CONSTANT across one card's produce turns and would
 * make a card sound-prompted forever or never.
 */
fun producePrompt(
    cardId: String,
    reviewCount: Int,
    consolidated: Boolean,
    audible: Boolean,
): ProducePrompt {
    if (!consolidated || !audible) return ProducePrompt.Source
    val offset = (fnv1a64("$cardId|sound") % 2uL).toInt()
    return if ((reviewCount / 2 + offset) % 2 == 0) ProducePrompt.Sound else ProducePrompt.Source
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
 * The cue for a surface that already knows whether its picture ANSWERS the question.
 *
 * The one-line rule, named here so no platform writes it out: a picture that would give the
 * answer away waits for the reveal, and one that cannot is there from the start. Surfaces
 * outside the review loop — the atlas card, the listening playlist — carry the fact and would
 * otherwise each spell the mapping, which is how the two phones came to disagree once already.
 */
fun emojiCue(givesAnswerAway: Boolean): EmojiCue =
    if (givesAnswerAway) EmojiCue.OnReveal else EmojiCue.Upfront

/**
 * Emoji policy. The picture is there from the START iff role == Produce and the word
 * has not landed — the one place it supports recall without giving the answer away,
 * since a produce prompt already names the concept in the source language and asks
 * for the other one. Everywhere else it waits for the reveal, in every phase: once
 * the answer is out the picture can leak nothing, and binding it to the meaning is
 * exactly what a word still matched on novelty needs.
 *
 * A RECOGNITION prompt never carries it, the first exposure included. There the
 * picture depicts the very thing being asked for, so on a self-graded card it is not
 * a cue but the answer — and "the emoji was obvious" is a verdict about the picture
 * that the schedule cannot tell apart from one about the word. First exposure is
 * where that costs most: it is the answer that decides how long the word goes away
 * for, and a Good bought off an obvious picture buys the same interval a real recall
 * does.
 *
 * Nothing is withheld from a learner meeting the word. The target form is on screen,
 * its sound plays ([pronunciationCue] is Upfront on every recognition prompt), and
 * the reveal brings meaning and picture together — which is where a first sight
 * teaches. Only WHERE the picture lands moved: off the prompt no one can grade
 * honestly, onto the typed produce turn that comes next, which is the first review
 * that actually asks the learner to know the word.
 */
fun emojiCue(
    role: PresentationRole,
    consolidated: Boolean,
): EmojiCue =
    if (role == PresentationRole.Produce && !consolidated) {
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
 * that very form, so the word waits for the reveal — unless the SOUND is the prompt
 * ([ProducePrompt.Sound]), where holding it back would leave the card asking nothing.
 */
fun pronunciationCue(
    role: PresentationRole,
    prompt: ProducePrompt = ProducePrompt.Source,
): PronunciationCue =
    if (role == PresentationRole.Recognize || prompt == ProducePrompt.Sound) {
        PronunciationCue.Upfront
    } else {
        PronunciationCue.OnReveal
    }

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
