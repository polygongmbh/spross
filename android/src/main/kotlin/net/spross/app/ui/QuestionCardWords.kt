package net.spross.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import net.spross.app.Chrome
import net.spross.app.countryAsk
import net.spross.app.dateAsk
import net.spross.kern.model.ClosingNote
import net.spross.kern.model.PluralForm
import net.spross.kern.session.Question
import net.spross.kern.session.QuestionAsk
import net.spross.kern.session.QuestionHint

/** What [Question] leaves unworded — the asks, the hints, the closing lines — and the sizes a side's form picks. */

/** What is asked picks the size: there is room for one numeral where there is none for a whole line. */
internal fun promptSize(form: Question.Form): TextUnit = when (form) {
    Question.Form.Numeral -> Theme.prompt.digits
    Question.Form.Sentence -> Theme.prompt.sentence
    Question.Form.Glyph -> Theme.prompt.letter
    else -> Theme.prompt.word
}

internal fun promptLines(form: Question.Form): Int = if (form == Question.Form.Sentence) 4 else 1

/**
 * The prompt as written, the opening letters a mixed word keeps standing set bold,
 * tagged with its language — save a numeral, which read in the learned language would say the answer.
 */
internal fun promptText(side: Question.Side, text: String): AnnotatedString {
    val lead = minOf(side.fixedLeading, text.length)
    val written = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.take(lead)) }
        append(text.substring(lead))
    }
    val lang = side.lang
    return if (side.form == Question.Form.Numeral || lang == null) written else localizedTarget(written, lang)
}

/** A reveal under a sentence is set no larger than a line of it. */
@Composable
internal fun CardContext.answerStyle(): TextStyle =
    if (question.prompt.form == Question.Form.Sentence || question.answer.form == Question.Form.Sentence) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.titleLarge
    }

internal fun askText(ask: QuestionAsk, chrome: Chrome): String = when (ask) {
    is QuestionAsk.Country -> chrome.countryAsk(ask.kind)
    is QuestionAsk.Date -> chrome.dateAsk(ask.kind)
    QuestionAsk.LetterHear -> chrome.lettersAskHear
    QuestionAsk.LetterSpell -> chrome.lettersAskSpell
    QuestionAsk.LetterDictation -> chrome.lettersAskDictation
}

internal fun hintText(hint: QuestionHint, chrome: Chrome): String = when (hint) {
    is QuestionHint.NewForm -> chrome.numbersNewForm.format(hint.word)
    is QuestionHint.NewPlace -> chrome.numbersNewPlace.format(hint.word)
    is QuestionHint.NewWord -> chrome.datesNewWord.format(hint.word)
}

/** The card's last line: its own note, or what the prompted form also means. */
internal fun CardContext.noteText(): String? = when (val note = question.closing.note) {
    null -> null
    is ClosingNote.Own -> note.text
    is ClosingNote.AlsoMeans -> chrome.sessionMeansAlso.format(note.meanings.joinToString(" / "))
}

/** Which plural is a sentinel is kern's ([PluralForm]); the labels each one wears are chrome. */
internal fun pluralText(plural: PluralForm, chrome: Chrome): String = when (plural) {
    PluralForm.SameAsSingular -> chrome.sessionGrammarPluralEquals
    PluralForm.PluralOnly -> chrome.sessionGrammarPluralOnly
    is PluralForm.Form -> chrome.sessionGrammarPlural.format(plural.text)
}

/** "auch: Amt / Verwaltung" — the word's family beyond the forms already on the card. */
internal fun CardContext.alternatesText(): String? =
    question.closing.alternates.takeIf { it.isNotEmpty() }
        ?.let { chrome.sessionGrammarAlso.format(it.joinToString(" / ")) }
