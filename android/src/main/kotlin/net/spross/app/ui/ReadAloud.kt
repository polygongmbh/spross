package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import net.spross.app.AppModel
import net.spross.app.CHIME_CLEARANCE_MS
import net.spross.app.say
import net.spross.kern.session.Reading

/**
 * The one place a question's [Reading] becomes sound, for a review card and a drill alike:
 * the prompt at once, the answer once the verdict's chime has landed, each at most once per
 * [Reading.key] (`docs/read-aloud.md`). What is said is kern's; this only times it.
 *
 * Returns whether the answer is still sounding — what an armed beat waits out, so a clean
 * answer is never cut off by its own advance. The iOS twin is `Reader`.
 */
@Composable
fun rememberReadAloud(model: AppModel, reading: Reading?): () -> Boolean {
    val key = reading?.key
    // why: once per key — an approval withdrawn by typing past it and given again must not
    // say the word a second time.
    val said = remember(key) { mutableSetOf<Boolean>() }
    var answerSounding by remember(key) { mutableStateOf(false) }
    val prompt = reading?.prompt
    LaunchedEffect(key, prompt != null) {
        if (prompt != null && said.add(false)) model.say(prompt)
    }
    val answer = reading?.answer
    LaunchedEffect(key, answer != null) {
        if (answer == null || !said.add(true)) return@LaunchedEffect
        answerSounding = true
        delay(CHIME_CLEARANCE_MS)
        model.say(answer) { answerSounding = false }
    }
    return { answerSounding }
}
