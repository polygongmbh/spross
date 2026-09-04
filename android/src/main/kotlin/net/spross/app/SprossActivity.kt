package net.spross.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import net.spross.app.ui.AboutScreen
import net.spross.app.ui.BoxScreen
import net.spross.app.ui.CountriesOverviewScreen
import net.spross.app.ui.CountryDrillScreen
import net.spross.app.ui.DateDrillScreen
import net.spross.app.ui.DatesOverviewScreen
import net.spross.app.ui.HomeScreen
import net.spross.app.ui.LetterDrillScreen
import net.spross.app.ui.LettersOverviewScreen
import net.spross.app.ui.ListeningScreen
import net.spross.app.ui.NumbersOverviewScreen
import net.spross.app.ui.OnboardingScreen
import net.spross.app.ui.SessionScreen
import net.spross.app.ui.SprossTheme
import net.spross.app.ui.TrainerSessionScreen

class SprossActivity : ComponentActivity() {

    // The very model the composition below resolves: `viewModel()` reads this activity's
    // store, so the lifecycle and the screen step the same run.
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // why: the window's own theme resolves the status-bar icon polarity ONCE, at
        // creation — and the manifest declares configChanges for uiMode, so a light/dark
        // switch never recreates this activity. Compose recoloured underneath while the
        // icons kept the old polarity. This owns both bars and re-applies on the change,
        // and it is also the only thing that ever sets the NAVIGATION bar's icons, which
        // the themes never named at all.
        // Both bars fully transparent: the default styles lay a light SCRIM under the
        // navigation bar, which paints a white band across the bottom of a stone-paper
        // app. The window background is the paper (`@color/spross_window_background`),
        // so with no scrim the bars simply show it.
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
            model.pronouncer.muteThisLaunch()
        }
        setContent {
            SprossTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(Modifier.safeDrawingPadding()) { Root(model) }
                }
            }
        }
    }

    /**
     * why: onStop is the last callback an evicted app is promised — a session left mid-run
     * books what has been answered here, or the day's streak-bearing reviews die with the
     * process. onPause would fire for a dialog on top too, folding while the learner is
     * still sitting there.
     */
    override fun onStop() {
        super.onStop()
        model.foldPartialSession()
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
    }
}

/**
 * How far under Home a screen sits — the only thing a push or a pop needs to tell them apart.
 *
 * Not a route stack: the model holds ONE screen and the app has no back stack to read a
 * direction off, so depth is what says whether the learner went in or came back out. Home is
 * the floor, everything reached from it is one down, and About is one further because the only
 * way in is through the box's own settings.
 */
private fun Screen.depth(): Int = when (this) {
    Screen.Loading, Screen.Onboarding, Screen.Home -> 0
    Screen.About -> 2
    else -> 1
}

/** Long enough to read as a move, short enough that a tap still feels answered. */
private const val SCREEN_MOTION_MS = 220

@Composable
private fun Root(model: AppModel = viewModel()) {
    AnimatedContent(
        targetState = model.screen,
        // why: a screen that cuts is the loudest thing separating this cut from the iOS one,
        // where every push is animated. Going deeper enters from the trailing edge and going
        // back reverses it, so the motion says which way the learner moved.
        transitionSpec = {
            val forward = targetState.depth() >= initialState.depth()
            val enterFrom = if (forward) 1 else -1
            val spec = tween<IntOffset>(SCREEN_MOTION_MS)
            (slideInHorizontally(spec) { it / 6 * enterFrom } + fadeIn(tween(SCREEN_MOTION_MS)))
                .togetherWith(
                    slideOutHorizontally(spec) { it / 6 * -enterFrom } +
                        fadeOut(tween(SCREEN_MOTION_MS)),
                )
        },
        label = "screen",
    ) { screen ->
        // The screen is the lambda's own parameter rather than a property read, so the Box
        // case can hand its area on: a `mutableStateOf` property is never smart-cast — and
        // an outgoing screen keeps drawing the state it left with instead of the new one.
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
            is Screen.Trainer -> TrainerSessionScreen(model, screen.mode)
            Screen.LetterDrill -> LetterDrillScreen(model)
            is Screen.CountryDrill -> CountryDrillScreen(model, screen.reverse, screen.fast)
            is Screen.DateDrill -> DateDrillScreen(model, screen.reverse, screen.fast)
            is Screen.Box -> BoxScreen(model, openAt = screen.area)
        }
    }
}
