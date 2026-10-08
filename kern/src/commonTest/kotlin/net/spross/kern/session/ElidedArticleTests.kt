package net.spross.kern.session

import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.FormTag
import net.spross.kern.model.LanguageInfo
import net.spross.kern.model.Realization
import net.spross.kern.model.TaggedForm
import kotlin.test.Test
import kotlin.test.assertEquals

/** An elided article (`l'`) is an article like any other: optional, and read back where it is wrong. */
class ElidedArticleTests {
    private val fr = AnswerNormalizer(
        LanguageInfo(
            code = "fr", name = "Français", englishName = "French", flag = "🇫🇷",
            articles = listOf("le", "la", "les", "l'", "un", "une"),
        ),
    )

    private val guest = Card(
        id = "guest", kind = CardKind.Noun, area = "hall", emoji = null, seedIndex = 0,
        components = emptyList(), feminineOf = null, promptFeminineMarker = false,
        source = Realization("en", "guest"),
        target = Realization(
            "fr", "invité", grammar = mapOf("gender" to "l'"),
            // As the join leaves it: the authored `l'invitée` with its article set apart.
            forms = listOf(TaggedForm(FormTag.FEMININE, "invitée", article = "l'")),
        ),
    )

    @Test
    fun theWordAloneAnswersAFormWrittenWithAnElidedArticle() {
        assertEquals(Match.Exact, fr.evaluate("invitée", guest))
        assertEquals(Match.Exact, fr.evaluate("l’invitée", guest))
    }

    @Test
    fun aWrongArticleOnAnElidedFormIsAlmost() {
        assertEquals(Match.Typo("l'invitée"), fr.evaluate("la invitée", guest))
    }
}
