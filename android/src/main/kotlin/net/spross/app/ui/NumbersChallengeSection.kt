package net.spross.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import net.spross.app.AppModel
import net.spross.app.startChallenge
import net.spross.kern.trainer.ChallengeReading
import net.spross.kern.trainer.NumbersChallenge
import net.spross.kern.trainer.NumbersMode
import net.spross.kern.trainer.TimedRun

/**
 * The challenge half of the numbers overview: a timed run two learners play on the same
 * questions, started here and sent as a code, or started from a code someone sent. What a
 * code carries, and what refuses one, is kern's ([NumbersChallenge]); [picks] is the run the
 * page's picks describe, which a fresh challenge is cut from.
 */
@Composable
fun NumbersChallengeSection(model: AppModel, picks: NumbersMode) {
    val chrome = model.chrome
    var code by rememberSaveable { mutableStateOf("") }
    var refusal by remember { mutableStateOf<String?>(null) }
    // Phrases, prompted in the learner's own language, stays home.
    val offered = NumbersChallenge.offered(picks)
    val accept = {
        refusal = when (val reading = NumbersChallenge.read(code, picks.language)) {
            is ChallengeReading.Ready -> {
                model.startChallenge(reading.challenge)
                null
            }
            is ChallengeReading.OtherLanguage ->
                chrome.trainerChallengeOtherLanguage.format(model.languageName(reading.language))
            ChallengeReading.Unreadable -> chrome.trainerChallengeUnreadable
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        OverviewHeading(chrome.trainerChallengeTitle)
        OverviewNote(chrome.trainerChallengeHint.format(TimedRun.SECONDS))
        OutlinedButton(
            onClick = { NumbersChallenge.create(picks, Random.Default)?.let(model::startChallenge) },
            enabled = offered,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressSpring(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(chrome.trainerChallengeStart)
        }
        if (!offered) OverviewNote(chrome.trainerChallengePhrases)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(chrome.trainerChallengeCode) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { accept() }),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = accept,
                enabled = code.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp).pressSpring(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(chrome.trainerChallengeAccept)
            }
        }
        refusal?.let { OverviewNote(it) }
    }
}
