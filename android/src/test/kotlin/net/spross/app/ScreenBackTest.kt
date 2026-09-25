package net.spross.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.spross.kern.trainer.NumbersExercise
import net.spross.kern.trainer.NumbersMode

/**
 * What back does from each screen: the plain steps land where the learner came from, and
 * the screens that save or stop something on the way out are never stepped off from under
 * their own handler.
 */
class ScreenBackTest {

    @Test
    fun theTabsAndEveryDrillPageStepBackToHome() {
        val pages = listOf(
            Screen.Box(), Screen.Box("family"), Screen.Settings,
            Screen.Numbers, Screen.Letters, Screen.Countries, Screen.Dates,
        )
        for (page in pages) assertEquals(Screen.Home, page.back(), "$page")
    }

    @Test
    fun aboutStepsBackToTheSettingsItIsReachedFrom() {
        assertEquals(Screen.Settings, Screen.About.back())
    }

    @Test
    fun backLeavesTheAppFromHomeAndTheStory() {
        assertNull(Screen.Home.back())
        assertNull(Screen.Onboarding.back())
    }

    @Test
    fun aScreenThatSavesOnTheWayOutHasNoPlainStep() {
        val runs = listOf(
            Screen.Session, Screen.Listening, Screen.LetterDrill, Screen.WordScramble,
            Screen.SentenceScramble, Screen.NumbersRun(NumbersMode(NumbersExercise.Counting, "de")),
            Screen.CountryDrill(reverse = false, fast = false, level = 1),
            Screen.DateDrill(reverse = false, fast = false, level = 1),
        )
        for (run in runs) assertNull(run.back(), "$run")
    }
}
