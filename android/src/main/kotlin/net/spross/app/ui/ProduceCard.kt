package net.spross.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.SessionUi
import net.spross.kern.model.Rating
import net.spross.kern.session.Match

private sealed interface ProduceMode {
    data object Idle : ProduceMode
    data object Correct : ProduceMode
    data class Typo(val corrected: String) : ProduceMode
    data object Wrong : ProduceMode
    data object SelfGrade : ProduceMode
}

@Composable
fun ProduceCard(model: AppModel, ui: SessionUi) {
    val card = ui.card ?: return
    val chrome = model.chrome
    val targetName = model.catalog?.languages?.get(card.target.lang)?.name ?: card.target.lang
    var input by remember(card.id) { mutableStateOf("") }
    var mode by remember(card.id) { mutableStateOf<ProduceMode>(ProduceMode.Idle) }

    fun check() {
        if (input.isBlank()) return
        mode = when (val match = model.normalizer?.evaluate(input, card) ?: return) {
            is Match.Exact -> ProduceMode.Correct
            is Match.Typo -> ProduceMode.Typo(match.corrected)
            is Match.Wrong -> ProduceMode.Wrong
        }
    }

    // why: clean correct answers auto-advance after ~1.2 s (design.md review UX);
    // typos and reveals wait for an explicit tap instead.
    LaunchedEffect(mode) {
        if (mode == ProduceMode.Correct) {
            delay(1200)
            model.answerCurrent(Rating.Good)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        if (ui.showEmoji) {
            Text(card.emoji.orEmpty(), fontSize = 64.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.source.text, style = MaterialTheme.typography.headlineLarge)
            if (card.promptFeminineMarker) {
                Text(" ♀", style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            // why: readOnly (not disabled) after grading — the keyboard stays up
            // so Enter still advances past the reveal (design.md review UX).
            readOnly = mode != ProduceMode.Idle,
            placeholder = { Text(chrome.answerPlaceholder.format(targetName)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                when (mode) {
                    ProduceMode.Idle -> check()
                    is ProduceMode.Typo -> model.answerCurrent(Rating.Hard)
                    ProduceMode.Wrong -> model.answerCurrent(Rating.Again)
                    else -> Unit
                }
            }),
            singleLine = true,
        )

        when (val current = mode) {
            ProduceMode.Idle -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        input = card.target.text
                        mode = ProduceMode.SelfGrade
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(chrome.reveal)
                }
                Button(
                    onClick = { check() },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(chrome.check)
                }
            }
            ProduceMode.Correct -> Text(
                "✓ ${card.target.text}",
                style = MaterialTheme.typography.titleLarge,
                color = ToneRight,
            )
            is ProduceMode.Typo -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(chrome.typoNote, color = ToneTough,
                    style = MaterialTheme.typography.bodyMedium)
                TargetReveal(card.target, chrome)
                Button(
                    onClick = { model.answerCurrent(Rating.Hard) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(chrome.next)
                }
            }
            ProduceMode.Wrong -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetReveal(card.target, chrome)
                Button(
                    onClick = { model.answerCurrent(Rating.Again) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(chrome.next)
                }
            }
            ProduceMode.SelfGrade -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetReveal(card.target, chrome)
                RatingButtons(chrome, onRate = { model.answerCurrent(it) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
