package net.spross.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import net.spross.app.Chrome
import net.spross.kern.model.emojiCue
import net.spross.kern.model.Language

/**
 * The atlas question, on the same card face as every other session card: a caption naming
 * what is asked, the flag BESIDE the words where the question is about a country, and the
 * name itself — then the answer growing below it once it is out.
 *
 * The caption stays here rather than on the field, because a bare name says nothing about
 * which of the four things is being asked: "Deutschland" is the prompt whether the country,
 * its people or its language is owed. It says THAT and no more — which language the answer
 * is owed in is the placeholder's to say, and saying it here too would be the third telling
 * of what one tap already settled (`docs/surfaces.md`).
 *
 * One question has no name on it at all: where kern hands over a flag and no [text], the
 * flag IS the question and stands where the name would, at the size the name would have had.
 *
 * The other way round, a flag can be the ANSWER — a reversed run is answered in the
 * learner's own language, so showing it would settle the question. It is held to the reveal
 * rather than dropped ([emojiIsGiveaway]): the learner still finds out which country they
 * were asked about, which is the whole point of having carried the picture. That is a rule
 * about WHEN a picture appears, so it lives on the card and never on the caller.
 */
@Composable
fun CountryPromptCard(
    /** What is being asked. Never names a language ([net.spross.app.countryAsk]). */
    ask: String,
    /** The country's flag; null where the question is about a language. */
    emoji: String?,
    /** Whether showing [emoji] while the answer is owed would ANSWER the question. */
    emojiIsGiveaway: Boolean,
    /** The name asked about; null where the flag alone is the question. */
    text: String?,
    /** What language [text] is written in — never shown; it tags the name for TalkBack. */
    language: Language?,
    /** The answer, once the learner has stopped owing it. */
    reveal: CountryReveal?,
    chrome: Chrome,
) {
    val revealed = reveal != null
    // The flag takes the leading slot exactly as a word's picture does on a review card —
    // never above the words, where it pushes the name into the space the reveal needs. A
    // flag with no name beside it is not that picture: it IS the question (below).
    VocabCard(
        emoji = if (text == null) null else emoji,
        cue = emojiCue(givesAnswerAway = emojiIsGiveaway),
        revealed = revealed,
        modifier = Modifier.heightIn(min = Theme.reserve.drillCard),
        note = reveal?.note,
    ) {
        Text(
            ask,
            style = MaterialTheme.typography.bodySmall,
            color = Theme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (text != null) {
            // Tagged with the language it is WRITTEN in, so TalkBack reads a Ukrainian name
            // in a Ukrainian voice; a name with no language named is read as it stands.
            Headword(if (language == null) AnnotatedString(text) else localizedTarget(text, language))
        } else if (emoji != null) {
            // why: no side slot here — the flag is not the picture beside the question, it
            // IS the question, so it takes the place and the size the name would have had.
            // Nor is it ever a giveaway: a question whose whole content is the flag has
            // nothing left to show if the flag is held back, which is why kern builds this
            // kind forward only.
            Text(emoji, fontSize = Theme.prompt.glyph, textAlign = TextAlign.Center)
        }
        reveal?.let {
            CardReveal {
                SpokenWord(it.pronounce, chrome) {
                    Text(
                        localizedTarget(it.word, it.language),
                        style = MaterialTheme.typography.titleLarge,
                        color = Theme.colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

/** The answer an atlas card grows, and the neighboring form kern hands over beside it. */
data class CountryReveal(
    val word: String,
    /**
     * The answer side's neighboring form — the people beside the country, the country
     * beside the language. Never shown before the answer is in.
     */
    val note: String?,
    /** The language the ANSWER is in — the other side of the pair from the card's. */
    val language: Language,
    /** Null where this device can neither play nor say the form; the speaker is then absent. */
    val pronounce: (() -> Unit)?,
)
