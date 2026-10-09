package net.spross.kern.catalog

/**
 * The first run's pages, in the order they are walked: the pair ([LanguageChoices]),
 * what the box is for, then what a round asks of the learner.
 * Only the last one commits — the two before it merely turn the page —
 * so the box is joined once, behind something worth reading.
 */
enum class OnboardingPage {
    Languages, Why, FirstRound;

    /** The page the forward button turns to; null on the last, whose button joins the box. */
    val next: OnboardingPage?
        get() = when (this) {
            Languages -> Why
            Why -> FirstRound
            FirstRound -> null
        }

    /**
     * The page the way back turns to; null on the first,
     * and on the last while the box is [joining] — the join is under way and leaves the screen itself.
     */
    fun back(joining: Boolean): OnboardingPage? = when (this) {
        Languages -> null
        Why -> Languages
        FirstRound -> Why.takeUnless { joining }
    }

    /** The glyph heading the page. */
    val emoji: String
        get() = when (this) {
            Languages -> "👋"
            Why -> "🌱"
            FirstRound -> "🌿"
        }
}

object Onboarding {
    /** Long enough to read as a page turn, short enough that the tap still feels answered. */
    const val PAGE_FADE_MS: Int = 200

    /** Where the pages open: a restart of a pair already made has nothing left to ask on the pair's own page. */
    fun openingPage(restart: Boolean): OnboardingPage = if (restart) OnboardingPage.Why else OnboardingPage.Languages
}
