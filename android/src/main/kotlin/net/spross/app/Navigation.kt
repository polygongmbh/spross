package net.spross.app

import net.spross.kern.trainer.NumbersChallenge
import net.spross.kern.trainer.NumbersMode

sealed interface Screen {
    data object Loading : Screen
    data object Onboarding : Screen
    data object Home : Screen
    data object Session : Screen

    /** The profile, the backup and the one destructive door, on a screen of their own. */
    data object Settings : Screen

    data object About : Screen

    /**
     * A listening run: a full screen like every other Android run, and the one made
     * entirely of sound. Back mirrors its ✕.
     */
    data object Listening : Screen

    data object Numbers : Screen

    data object Letters : Screen

    data object Countries : Screen

    data object Dates : Screen

    /** A slot run with its page's spec, or a [challenge] whose script replaces the ramp. */
    data class NumbersRun(val mode: NumbersMode, val challenge: NumbersChallenge? = null) : Screen

    data object LetterDrill : Screen

    /**
     * A word-scramble run. No page in front of it: the two scrambles have nothing to be read
     * beside them — the box IS their material — so the chip opens the run itself.
     */
    data object WordScramble : Screen

    /** A sentence-scramble run, opened straight from its chip like its word sibling. */
    data object SentenceScramble : Screen

    /** An opposites run, opened straight from its chip like the scrambles. */
    data object Opposites : Screen

    /**
     * An atlas run, carrying the two things the page settled before it opened: which way
     * round the questions are asked, and whether a Sprosse falls on one clean win.
     */
    data class CountryDrill(val reverse: Boolean, val fast: Boolean, val sprosse: Int) : Screen

    /** A dates run, carrying the same two settled things the atlas run does. */
    data class DateDrill(val reverse: Boolean, val fast: Boolean, val sprosse: Int) : Screen

    /**
     * The box browser. [area] is the shelf it opens UNFOLDED — the screen was reached by
     * naming that area, from a search hit or a tree — and null opens where the learner
     * left off ([net.spross.kern.box.BoxBrowser.defaultExpandedGroupId]).
     */
    data class Box(val area: String? = null) : Screen
}

/**
 * The three sections the bar switches between. A tab is named for its screen, not for the
 * word on it: what the learner reads under the box's icon is [Chrome.boxName].
 */
enum class Tab { Home, Box, Settings }

/** The tab the bar stands on — null on the screens that hide it: a run, a drill, the story. */
fun Screen.asTab(): Tab? = when (this) {
    Screen.Home -> Tab.Home
    is Screen.Box -> Tab.Box
    Screen.Settings -> Tab.Settings
    else -> null
}

/**
 * The story pages again, on demand (the box settings' own row). The pair and the box
 * are untouched — [net.spross.app.ui.OnboardingScreen] reads the standing join and
 * opens past the language pick, which has nothing left to ask.
 */
fun AppModel.restartOnboarding() {
    navigate(Screen.Onboarding)
}

fun AppModel.openAbout() {
    navigate(Screen.About)
}

fun AppModel.closeAbout() {
    goBack()
}

/**
 * The screen behind this one: where back lands, and what a back swipe shows while it is
 * dragged. The tabs and every drill's page sit on Home; About sits on the settings, the only
 * way in being their own footer ([net.spross.app.ui.AboutFooter]).
 *
 * Null where back is not a plain step. Home, the story and Loading have nothing behind them,
 * so back leaves the app. A session, a run and a listening run save or stop something on the
 * way out, so each keeps its own handler and says where it goes itself.
 */
fun Screen.back(): Screen? = when (this) {
    is Screen.Box, Screen.Settings -> Screen.Home
    Screen.About -> Screen.Settings
    Screen.Numbers, Screen.Letters, Screen.Countries, Screen.Dates -> Screen.Home
    Screen.Loading, Screen.Onboarding, Screen.Home -> null
    Screen.Session, Screen.Listening -> null
    is Screen.NumbersRun, Screen.LetterDrill, Screen.WordScramble, Screen.SentenceScramble, Screen.Opposites,
    is Screen.CountryDrill, is Screen.DateDrill -> null
}

/** One step back to [Screen.back]; nothing where the screen has no plain step. */
fun AppModel.goBack() {
    screen.back()?.let { navigate(it) }
}

/**
 * The box browser. [area] names the shelf to open unfolded, for the surfaces that
 * reach the box BY an area; null opens wherever the learner left off.
 */
fun AppModel.openBox(area: String? = null) {
    navigate(Screen.Box(area))
}

/**
 * A tap on the bar. Every other way into these three carries something with it — the area
 * a tree named, the word a search found — and keeps its own entry point; this is the bare
 * switch, so the box opens wherever the learner left off rather than on a named shelf.
 */
fun AppModel.selectTab(tab: Tab) {
    navigate(
        when (tab) {
            Tab.Home -> Screen.Home
            Tab.Box -> Screen.Box()
            Tab.Settings -> Screen.Settings
        },
    )
}
