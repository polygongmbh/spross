package net.spross.kern.session

import net.spross.kern.model.Alternate
import net.spross.kern.model.ClosingNote
import net.spross.kern.model.EmojiCue
import net.spross.kern.model.FormTag
import net.spross.kern.model.Language
import net.spross.kern.model.PluralForm
import net.spross.kern.trainer.CountryTaskKind
import net.spross.kern.trainer.DateTaskKind

/**
 * What one question on screen shows — a review card and a drill task alike — so each app draws
 * every card from one component instead of composing its faces per screen.
 *
 * [answer] and [closing] are shown only while [opens]; before it they are the reveal held back —
 * save [Closing.note] alone where [growsNote].
 * Nothing here is worded: asks, hints, plural sentinels and closing labels are structures each
 * app words from its string table, and nothing here names a position on screen.
 */
data class Question(
    /** One per question on screen — what the card switch and the reader key on. */
    val key: String,
    /** The caption over the prompt, where the prompt alone does not say what is wanted; null on a review card. */
    val ask: QuestionAsk?,
    val prompt: Side,
    val answer: Side,
    val emoji: String?,
    /** WHEN [emoji] shows; it always sits in the card's one picture slot. */
    val emojiCue: EmojiCue,
    /** The picture IS the question ([CountryTaskKind.FlagCountry]): it is set as the prompt, not beside it. */
    val emojiIsQuestion: Boolean = false,
    /** A fact about this prompt shown until the card opens; null on nearly every question. */
    val hint: QuestionHint? = null,
    /**
     * The card carries its answer — the one rule for when it grows [answer] and [closing]:
     * on a miss or a reveal, never on an accepted answer, which already stands in the learner's own text.
     */
    val opens: Boolean,
    /**
     * The card stays closed yet grows [Closing.note] alone: an accepted answer to a question
     * whose meaning never stood on screen — the scrambles, the opposites, a letter heard.
     */
    val growsNote: Boolean = false,
    val closing: Closing = Closing(),
    /** What a refused answer actually named — only beside a revealed miss. */
    val otherWord: Match.OtherWord? = null,
) {
    /**
     * One side of the card: the prompt it asks with, or the answer it opens onto.
     * Grammar ([article], [plural]) is set only where the side stands in the language being learned.
     */
    data class Side(
        /** What stands written; null where the side has no words — a flag asked alone, a sound, an arrangement. */
        val text: String?,
        val lang: Language?,
        val form: Form,
        /** The article set in front of [text] and tinted by its gender; null where none shows. */
        val article: String? = null,
        val plural: PluralForm? = null,
        /** Which form [text] stands for where it cannot show it itself (`teacher` ♀ for `Lehrerin`); null marks none. */
        val marker: FormTag? = null,
        /** The area key named over an ambiguous prompt; the app shows that area's title. */
        val context: String? = null,
        /** How many letters at the head of [text] stand as written — a word scramble's kept opening. */
        val fixedLeading: Int = 0,
        /** What the speaker beside [text] says; null draws no speaker. */
        val saying: Saying? = null,
    )

    /** What a side is made of, which decides how large it is set and what stands in for missing text. */
    enum class Form {
        /** One word or a short phrase. */
        Word,

        /** A numeral the whole card is about. */
        Numeral,

        /** Words wrapped over lines. */
        Sentence,

        /** A name or a dated line — the atlas and the calendar. */
        Name,

        /** A bare letterform, which nothing may be asked to say. */
        Glyph,

        /** The side IS a sound: a replay control stands where [Side.text] would. */
        Sound,

        /** A heard word with its asked grapheme blanked in [Side.text], under the replay control. */
        Gap,
    }

    /**
     * The lines the card closes on once it opens, after the answer.
     * [alternates] are the word's family beyond every form already on the card;
     * the app labels both and joins the forms.
     */
    data class Closing(
        val alternates: List<Alternate> = emptyList(),
        val note: ClosingNote? = null,
    )
}

/** What a caption asks for, where the prompt alone cannot say it. Each app words it. */
sealed interface QuestionAsk {
    data class Country(val kind: CountryTaskKind) : QuestionAsk

    data class Date(val kind: DateTaskKind) : QuestionAsk

    /** A letter heard by its name. */
    data object LetterHear : QuestionAsk

    /** The grapheme missing from a heard word. */
    data object LetterSpell : QuestionAsk

    /** A whole heard word, written down. */
    data object LetterDictation : QuestionAsk
}

/** A first-sight fact about the prompt — always a word in the language being learned. Each app words the line. */
sealed interface QuestionHint {
    val word: String

    /** The place word a digit length adds, the first time that length is asked ([net.spross.kern.trainer.Numbers.placeValueHint]). */
    data class NewPlace(override val word: String) : QuestionHint

    /** The word a number form adds, the first time that form is asked. */
    data class NewForm(override val word: String) : QuestionHint

    /** The word a calendar pattern adds, the first time that pattern is asked. */
    data class NewWord(override val word: String) : QuestionHint
}
