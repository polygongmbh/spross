package net.spross.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import net.spross.app.ui.SectionIcons
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import net.spross.app.ui.AboutScreen
import net.spross.app.ui.BoxScreen
import net.spross.app.ui.CountriesOverviewScreen
import net.spross.app.ui.CountryDrillScreen
import net.spross.app.ui.DateDrillScreen
import net.spross.app.ui.DatesOverviewScreen
import net.spross.app.ui.HomeScreen
import net.spross.app.ui.LetterDrillScreen
import net.spross.app.ui.MeasuresWindowPanes
import net.spross.app.ui.MeasuresWindowType
import net.spross.app.ui.LettersOverviewScreen
import net.spross.app.ui.ListeningScreen
import net.spross.app.ui.NumbersOverviewScreen
import net.spross.app.ui.OnboardingScreen
import net.spross.app.ui.OppositesScreen
import net.spross.app.ui.SentenceScrambleScreen
import net.spross.app.ui.SessionScreen
import net.spross.app.ui.SettingsScreen
import net.spross.app.ui.singlePane
import net.spross.app.ui.SprossTheme
import net.spross.app.ui.Theme
import net.spross.app.ui.NumbersRunScreen
import net.spross.app.ui.WordScrambleScreen

class SprossActivity : ComponentActivity() {

    // The very model the composition below resolves: `viewModel()` reads this activity's
    // store, so the lifecycle and the screen step the same run.
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // why: both bars fully transparent, and the navigation bar's own contrast scrim
        // switched off — the default styles lay a light scrim under it, which paints a band
        // across the bottom of a stone-paper app. The window background is the paper
        // (`@color/spross_window_background`), so with no scrim the bars simply show it.
        // `uiMode` is deliberately OUT of the manifest's configChanges: a live light/dark
        // switch recreates the activity, which is what re-resolves the theme-qualified
        // window background and reapplies this styling for the new column — the model
        // survives the recreate ([viewModels]), so nothing the learner was doing is lost.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // why: asking for a transparent navigation bar is not enough — since API 29 the
        // system re-imposes its own scrim unless contrast enforcement is switched off,
        // and that scrim is white, which is a band across the bottom of a stone-paper app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        // why: `--es readAloud off` (scripts/run-emu.sh --mute) starts a driven run
        // silent, so an unattended machine never speaks up by itself. Only on a FRESH
        // create — a rotation replays this intent, and re-muting there would undo a
        // toggle the learner had just reached for.
        if (savedInstanceState == null && intent?.getStringExtra(EXTRA_READ_ALOUD) == "off") {
            model.pronouncer.muted = true
        }
        // why: `--ef treesAge 0.55` on a debug build stands a fabricated box of that age in
        // the Trees picture and on a round's summary, so every age can be looked at without months
        // of reviews behind it.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent?.hasExtra(EXTRA_TREES_AGE) == true) {
            model.sampleTreesAge = intent.getFloatExtra(EXTRA_TREES_AGE, 0f).toDouble()
        }
        if (savedInstanceState == null) model.openChallengeLink(intent?.data?.toString())
        setContent {
            SprossTheme {
                MeasuresWindowType {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Root(model)
                    }
                }
            }
        }
    }

    // why: a challenge link tapped while the app runs arrives here rather than in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        model.openChallengeLink(intent.data?.toString())
    }

    /**
     * why: onStop is the last callback an evicted app is promised — a session left mid-run
     * writes what has been answered here, or the day's streak-bearing reviews die with the
     * process. onPause would fire for a dialog on top too, writing while the learner is
     * still sitting there.
     */
    override fun onStop() {
        super.onStop()
        model.saveNow()
    }

    /**
     * why: what this device can SAY changes while the app sleeps — a voice installed in
     * Settings must turn a start button on without a relaunch, and none of these can be
     * asked per composition.
     */
    override fun onResume() {
        super.onResume()
        model.refreshTrainer()
        // why: whether either side of a turn can be spoken decides the listening card;
        // two lookups and two probes, cheap enough to ride every return.
        model.refreshListening()
        // why: the letter drill's own question is a catalog walk, so only the page that
        // reads it pays for it — and only while that page is the one on screen.
        if (model.screen == Screen.Letters) model.refreshLetters()
    }

    private companion object {
        /**
         * Launch extra worded exactly like the iOS launch argument it mirrors, so one
         * sentence in `scripts/` and the verify skill covers both phones.
         */
        const val EXTRA_READ_ALOUD = "readAloud"
        const val EXTRA_TREES_AGE = "treesAge"
    }
}

@Composable
private fun Root(model: AppModel = viewModel()) {
    val tab = model.screen.asTab()
    val screens = rememberScreenTransition(model)
    // why: a challenge link waits for a loaded box, then opens the numbers page, which accepts its code.
    val linked = model.pendingChallengeCode != null && model.numbersOffered
    LaunchedEffect(linked, tab != null) {
        if (linked && tab != null && model.screen != Screen.Numbers) model.openNumbers()
    }
    // The sections switch on a bar along the bottom, or on a rail along the leading edge —
    // whichever Material's adaptive navigation names for this window.
    val rail = sectionsOnRail()
    Row(Modifier.fillMaxSize()) {
        if (tab != null && rail) TabRail(model, tab)
        RootScaffold(model, screens, tab, rail)
    }
}

@Composable
private fun RowScope.RootScaffold(model: AppModel, screens: ScreenTransition, tab: Tab?, rail: Boolean) {
    // why: the Scaffold owns the insets rather than a padding around it, so the tab bar reaches
    // under the system navigation area instead of leaving a strip of paper below it.
    Scaffold(
        modifier = Modifier.weight(1f),
        bottomBar = { if (tab != null && !rail) TabBar(model, tab) },
        // why: the rail stands over the leading inset itself.
        contentWindowInsets = if (tab != null && rail) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
        } else {
            WindowInsets.safeDrawing
        },
    ) { insets ->
        MeasuresWindowPanes(Modifier.padding(insets).consumeWindowInsets(insets).imePadding()) {
            screens.transition.AnimatedContent(transitionSpec = screens.motion) { screen ->
                // The screen is the lambda's own parameter rather than a property read, so the Box
                // case can hand its area on: a `mutableStateOf` property is never smart-cast — and
                // an outgoing screen keeps drawing the state it left with instead of the new one.
                // why: each screen is its own page of paper, so a screen being backed out of
                // hides the one behind it rather than showing through it.
                // why: a screen with no pane design of its own stands in kern's single pane.
                Box(
                    Modifier.fillMaxSize().clip(screenShape).background(MaterialTheme.colorScheme.background)
                        .then(if (screen.laysOutWidth()) Modifier else Modifier.singlePane()),
                ) {
                    when (screen) {
                        Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        Screen.Onboarding -> OnboardingScreen(model)
                        Screen.Home -> HomeScreen(model)
                        Screen.Session -> SessionScreen(model)
                        Screen.Listening -> ListeningScreen(model)
                        Screen.About -> AboutScreen(model)
                        Screen.Numbers -> NumbersOverviewScreen(model)
                        Screen.Letters -> LettersOverviewScreen(model)
                        Screen.Countries -> CountriesOverviewScreen(model)
                        Screen.Dates -> DatesOverviewScreen(model)
                        is Screen.NumbersRun -> NumbersRunScreen(model, screen.mode, screen.challenge)
                        Screen.LetterDrill -> LetterDrillScreen(model)
                        Screen.WordScramble -> WordScrambleScreen(model)
                        Screen.SentenceScramble -> SentenceScrambleScreen(model)
                        Screen.Opposites -> OppositesScreen(model)
                        is Screen.CountryDrill -> CountryDrillScreen(model, screen.reverse, screen.fast, screen.sprosse)
                        is Screen.DateDrill -> DateDrillScreen(model, screen.reverse, screen.fast, screen.sprosse)
                        Screen.Settings -> SettingsScreen(model)
                        is Screen.Box -> BoxScreen(model, openAt = screen.area)
                    }
                }
            }
        }
    }
}

/**
 * The screens that lay the window's width out themselves — Home and the Box into the panes,
 * a run gives its card the whole width, a drill's page spans its bar across the window over its column;
 * every other screen stands in one readable column ([singlePane]).
 */
private fun Screen.laysOutWidth(): Boolean = when (this) {
    Screen.Home, is Screen.Box, Screen.Numbers, Screen.Letters, Screen.Countries, Screen.Dates, Screen.Session, is Screen.NumbersRun, Screen.LetterDrill, Screen.WordScramble, Screen.SentenceScramble,
    Screen.Opposites, is Screen.CountryDrill, is Screen.DateDrill -> true
    else -> false
}

/**
 * The three sections, always one tap apart — and out of the way of anything the learner is
 * being asked to answer, which is every screen [asTab] returns null for.
 *
 * Material's expressive bar on a tonal fill of its own, which sets it off the cards above
 * without a hairline: an outlined mark, filled and on the accent's pill while its section is shown.
 * Marks only; the section's name is what TalkBack reads.
 */
@Composable
private fun TabBar(model: AppModel, current: Tab) {
    ShortNavigationBar(containerColor = Theme.colors.surfaceTint) {
        sections(model).forEach { section ->
            val selected = current == section.tab
            ShortNavigationBarItem(
                selected = selected,
                onClick = { model.selectTab(section.tab) },
                label = null,
                icon = { SectionMark(section, selected) },
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIconColor = Theme.colors.accent,
                    unselectedIconColor = Theme.colors.textSecondary,
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/** Whether Material's adaptive navigation puts the sections on a rail rather than a bottom bar. */
@Composable
private fun sectionsOnRail(): Boolean =
    when (NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())) {
        NavigationSuiteType.ShortNavigationBarCompact, NavigationSuiteType.ShortNavigationBarMedium,
        NavigationSuiteType.NavigationBar, NavigationSuiteType.None -> false
        else -> true
    }

/**
 * The same three sections on a rail at the leading edge, worn as the bar wears them,
 * each named under its mark — a wide window has the height a phone's bar saves.
 */
@Composable
private fun TabRail(model: AppModel, current: Tab) {
    WideNavigationRail(
        modifier = Modifier.fillMaxHeight(),
        colors = WideNavigationRailDefaults.colors(containerColor = Theme.colors.surfaceTint),
    ) {
        sections(model).forEach { section ->
            val selected = current == section.tab
            WideNavigationRailItem(
                railExpanded = false,
                selected = selected,
                onClick = { model.selectTab(section.tab) },
                label = { Text(section.name) },
                icon = { SectionMark(section, selected, named = true) },
                colors = WideNavigationRailItemDefaults.colors(
                    selectedTextColor = Theme.colors.accent,
                    unselectedTextColor = Theme.colors.textSecondary,
                    selectedIconColor = Theme.colors.accent,
                    unselectedIconColor = Theme.colors.textSecondary,
                    selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/** A section as the bar and the rail show it: its marks, outlined and filled, and the name TalkBack reads. */
private class Section(val tab: Tab, val mark: ImageVector, val shown: ImageVector, val name: String)

private fun sections(model: AppModel): List<Section> {
    val chrome = model.chrome
    return listOf(
        Section(Tab.Home, SectionIcons.Home, SectionIcons.HomeFilled, chrome.homeName),
        Section(Tab.Box, SectionIcons.Tree, SectionIcons.TreeFilled, chrome.boxName),
        Section(Tab.Settings, SectionIcons.Gear, SectionIcons.GearFilled, chrome.settingsTitle),
    )
}

/** The section's mark; TalkBack reads its name from the mark unless a label under it already says it. */
@Composable
private fun SectionMark(section: Section, selected: Boolean, named: Boolean = false) {
    Icon(if (selected) section.shown else section.mark, contentDescription = if (named) null else section.name)
}
