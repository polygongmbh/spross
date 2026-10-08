package net.spross.kern.session

import net.spross.kern.catalog.Fixture
import net.spross.kern.model.Card
import net.spross.kern.model.CardKind
import net.spross.kern.model.FormTag
import net.spross.kern.model.Realization
import net.spross.kern.model.TaggedForm
import kotlin.test.Test
import kotlin.test.assertEquals

/** How a prompt's inflection carries over to the forms a produce answer may take. */
class FormAgreementGradingTests {
    private val de = AnswerNormalizer(Fixture.catalog().languages.getValue("de"))

    private fun forms(vararg pairs: Pair<String, String>) = pairs.map { (tag, text) -> TaggedForm(FormTag.parse(tag)!!, text) }

    private val myDe = Realization("de", "mein", forms = forms("f" to "meine", "n" to "mein", "f.dat" to "meiner"))

    private fun card(source: Realization) = Card(
        id = "my", kind = CardKind.Adjective, area = "pronouns", emoji = null, seedIndex = 0,
        components = emptyList(), feminineOf = null, source = source, target = myDe, promptFeminineMarker = false,
    )

    @Test
    fun aSourceThatDoesNotInflectTakesEveryForm() {
        val fromEn = card(Realization("en", "my"))
        assertEquals(Match.Exact, de.evaluate("meiner", fromEn))
        assertEquals(Match.Exact, de.evaluate("meine", fromEn))
    }

    @Test
    fun aFormDisagreeingWithTheInflectedPromptCorrectsToTheCitation() {
        val fromEs = card(Realization("es", "mío", forms = forms("f" to "mía")))
        assertEquals(Match.Exact, de.evaluate("mein", fromEs))
        assertEquals(Match.Typo("mein"), de.evaluate("meiner", fromEs))
    }

    @Test
    fun aTaggedPromptAsksForItsAgreeingForm() {
        val fromEs = card(Realization("es", "mío", forms = forms("f" to "mía")))
        val f = FormTag.FEMININE
        assertEquals(Match.Exact, de.evaluate("meine", fromEs, f))
        assertEquals(Match.Typo("meine"), de.evaluate("mein", fromEs, f))
    }

    @Test
    fun aDimensionOnlyTheSourceInflectsPinsNothing() {
        val fromUk = card(Realization("uk", "мій", forms = forms("pl" to "мої")))
        assertEquals(Match.Exact, de.evaluate("meiner", fromUk))
    }

    @Test
    fun aPluralIsPinnedEvenFromASourceThatDoesNotInflect() {
        val mouse = Card(
            id = "mouse", kind = CardKind.Noun, area = "animals", emoji = null, seedIndex = 0,
            components = emptyList(), feminineOf = null, promptFeminineMarker = false,
            source = Realization("en", "mouse"),
            target = Realization("de", "Maus", forms = forms("pl" to "Mäuse")),
        )
        assertEquals(Match.Typo("Maus"), de.evaluate("Mäuse", mouse))
    }
}
