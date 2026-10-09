package net.spross.kern.catalog

/**
 * The first run's pages, in the order they are walked: the pair ([LanguageChoices]),
 * where things are ([OnboardingTourStop]), then what a round asks of the learner.
 * Only the last one commits — the two before it merely turn the page —
 * so the box is joined once, behind something worth reading.
 */
enum class OnboardingPage {
    Languages, Tour, FirstRound;

    /** The page the forward button turns to; null on the last, whose button joins the box. */
    val next: OnboardingPage?
        get() = when (this) {
            Languages -> Tour
            Tour -> FirstRound
            FirstRound -> null
        }

    /**
     * The page the way back turns to; null on the first,
     * and on the last while the box is [joining] — the join is under way and leaves the screen itself.
     */
    fun back(joining: Boolean): OnboardingPage? = when (this) {
        Languages -> null
        Tour -> Languages
        FirstRound -> Tour.takeUnless { joining }
    }

    /** The glyph heading the page. */
    val emoji: String
        get() = when (this) {
            Languages -> "👋"
            Tour -> "🧭"
            FirstRound -> "🌿"
        }
}

/**
 * The tour's stops, in the order the page lists them:
 * the day's round on Home, then the box where the learner picks what comes next.
 */
enum class OnboardingTourStop {
    Home, Box;

    /** The glyph beside the stop's name. */
    val emoji: String
        get() = when (this) {
            Home -> "🏡"
            Box -> "🌳"
        }
}

object Onboarding {
    /** Long enough to read as a page turn, short enough that the tap still feels answered. */
    const val PAGE_FADE_MS: Int = 200

    /** Where the pages open: a restart of a pair already made has nothing left to ask on the pair's own page. */
    fun openingPage(restart: Boolean): OnboardingPage = if (restart) OnboardingPage.Tour else OnboardingPage.Languages
}
