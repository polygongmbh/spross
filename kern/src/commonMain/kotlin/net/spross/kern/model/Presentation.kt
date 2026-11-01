package net.spross.kern.model

/**
 * How a card's next review is PRESENTED. Every role feeds the single FSRS
 * schedule — presentation never touches scheduling state.
 */
enum class PresentationRole {
    /** Source prompt → typed target answer. */
    Produce,

    /** Target prompt in writing → flip + self-grade (never typed). */
    Recognize,

    /**
     * The target word SPOKEN, and nothing on screen: the learner gives what it MEANS,
     * typed in the source language or recalled ([turnAnswer]). Writing back the word that played
     * would prove the ear worked and nothing else — understanding it is what the turn asks.
     */
    Hear,
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
 * session as production").
 *
 * Thereafter a word still landing alternates Recognize and Produce; one that has
 * [arrived] ([net.spross.kern.box.GROWING_STABILITY]) cycles Recognize, Produce, Hear —
 * asking by ear WITHDRAWS the meaning, which a word still landing cannot spare.
 * [audible] is the device's word (a recording, a voice, and reading aloud switched on);
 * false turns that Hear into Recognize, the same question by sight, so nothing is ever
 * hidden behind a silent phone. A stable per-card phase offset ([fnv1a64] over the card id)
 * keeps the box from turning in sync.
 */
fun presentationRole(cardId: String, reviewCount: Int, arrived: Boolean, audible: Boolean): PresentationRole {
    if (reviewCount == 0) return PresentationRole.Recognize
    if (reviewCount == 1) return PresentationRole.Produce
    val cycle = roleCycle(arrived)
    val role = CYCLE[(reviewCount + (fnv1a64(cardId) % cycle.toULong()).toInt()) % cycle]
    return if (role == PresentationRole.Hear && !audible) PresentationRole.Recognize else role
}

private val CYCLE = listOf(PresentationRole.Produce, PresentationRole.Recognize, PresentationRole.Hear)

/** How many reviews one turn of the role cycle spans: two while the word lands, three once it has. */
internal fun roleCycle(arrived: Boolean): Int = if (arrived) 3 else 2

/** How a typed-or-recalled turn — production, hearing — is ANSWERED; the question it asks is the same either way. */
enum class TurnAnswer {
    /** Written out in the field and graded. */
    Typed,

    /** Recalled in the head, then revealed and self-graded: no field. */
    Recalled,
}

/**
 * Whether a produce or hear review asks for the answer in writing.
 *
 * A word that has not [settled] ([net.spross.kern.box.SETTLED_STABILITY]) is always typed:
 * writing it is the strongest recall the box has, and the one that teaches the spelling.
 * Past that bar every other such turn is recalled instead, so a word the learner has
 * long known costs a glance rather than a sentence of typing, and still gets written
 * often enough to keep its spelling. A lapse drops it under the bar, back to typing.
 *
 * Rotates once per turn of the three-role cycle (a settled word has arrived), with its own per-card offset,
 * so a role's consecutive turns alternate.
 */
fun turnAnswer(cardId: String, reviewCount: Int, settled: Boolean): TurnAnswer {
    if (!settled) return TurnAnswer.Typed
    val offset = (fnv1a64("$cardId|recall") % 2uL).toInt()
    return if ((reviewCount / roleCycle(arrived = true) + offset) % 2 == 0) TurnAnswer.Recalled else TurnAnswer.Typed
}

/**
 * The target form to PROMPT on a recognition review — [turnPrompt]'s, without its tag.
 * `accepts` never rotates (accept-only).
 */
fun recognitionPromptForm(card: Card, reviewCount: Int, arrived: Boolean): String =
    turnPrompt(card, PresentationRole.Recognize, reviewCount, arrived).text

/** The member of [forms] that review [reviewCount] of [cardId] shows, rotating once per two reviews. */
fun rotatedForm(cardId: String, forms: List<String>, reviewCount: Int): String =
    rotated(cardId, forms, reviewCount, arrived = false)

/**
 * Rotates deterministically at zero extra scheduling cost: the first exposure takes the first item,
 * afterwards the index advances once per turn of the role cycle ([roleCycle]), so a role's
 * consecutive prompts step through every item, offset per card by the id hash.
 */
internal fun <T> rotated(cardId: String, items: List<T>, reviewCount: Int, arrived: Boolean): T {
    if (items.size == 1 || reviewCount == 0) return items.first()
    val offset = (fnv1a64(cardId) % items.size.toULong()).toInt()
    return items[(reviewCount / roleCycle(arrived) + offset) % items.size]
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
 * outside the review loop — the atlas card, the listening playlist — carry the fact,
 * and this mapping is the one both phones read rather than each spelling its own.
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
    arrived: Boolean,
): EmojiCue =
    if (role == PresentationRole.Produce && !arrived) {
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
 * consume rather than re-deriving the role each in their own way.
 * Recognition prompts the target itself, so hearing it teaches, and a hear turn's sound
 * IS its prompt; production asks for that very form, so the word waits for the reveal.
 */
fun pronunciationCue(role: PresentationRole): PronunciationCue =
    if (role == PresentationRole.Produce) PronunciationCue.OnReveal else PronunciationCue.Upfront

/**
 * WHEN the learner's own side — the meaning — is said: the mirror of [pronunciationCue].
 * A produce card says it as the question, which gives nothing away;
 * everywhere else the meaning is what the card reveals, so it waits for the reveal.
 * Each side is said once, so every card pairs the word with its meaning.
 */
fun meaningCue(role: PresentationRole): PronunciationCue =
    if (role == PresentationRole.Produce) PronunciationCue.Upfront else PronunciationCue.OnReveal

/**
 * FNV-1a 64-bit over UTF-8
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
