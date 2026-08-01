package net.spross.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import net.spross.app.ui.AboutScreen
import net.spross.app.ui.HeuteScreen
import net.spross.app.ui.OnboardingScreen
import net.spross.app.ui.SessionScreen
import net.spross.app.ui.SprossTheme

class SprossActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SprossTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(Modifier.safeDrawingPadding()) { Root() }
                }
            }
        }
    }
}

@Composable
private fun Root(model: AppModel = viewModel()) {
    when (model.screen) {
        Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is Screen.Onboarding -> OnboardingScreen(model)
        Screen.Heute -> HeuteScreen(model)
        Screen.Session -> SessionScreen(model)
        Screen.About -> AboutScreen(model)
    }
}
