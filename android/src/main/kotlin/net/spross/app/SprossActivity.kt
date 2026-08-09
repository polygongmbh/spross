package net.spross.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import net.spross.app.ui.HeuteScreen
import net.spross.app.ui.LetterDrillScreen
import net.spross.app.ui.LettersOverviewScreen
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
        super.onCreate(savedInstanceState)
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
     * why: what the letter drill can ASK changes while the app sleeps — a voice installed
     * in Settings must turn the start button on without a relaunch, and the sweep behind it
     * is a catalog walk, so it is asked here rather than per composition.
     */
    override fun onResume() {
        super.onResume()
        model.refreshWerkstatt()
    }
}

/**
 * How far under Heute a screen sits — the only thing a push or a pop needs to tell them apart.
 *
 * Not a route stack: the model holds ONE screen and the app has no back stack to read a
 * direction off, so depth is what says whether the learner went in or came back out. Heute is
 * the floor, everything reached from it is one down, and About is one further because the only
 * way in is through the box's own settings.
 */
private fun Screen.depth(): Int = when (this) {
    Screen.Loading, is Screen.Onboarding, Screen.Heute -> 0
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
            is Screen.Onboarding -> OnboardingScreen(model)
            Screen.Heute -> HeuteScreen(model)
            Screen.Session -> SessionScreen(model)
            Screen.About -> AboutScreen(model)
            Screen.Numbers -> NumbersOverviewScreen(model)
            Screen.Letters -> LettersOverviewScreen(model)
            is Screen.Trainer -> TrainerSessionScreen(model, screen.mode)
            Screen.LetterDrill -> LetterDrillScreen(model)
            is Screen.Box -> BoxScreen(model, openAt = screen.area)
        }
    }
}
