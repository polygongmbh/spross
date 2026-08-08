package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel

/**
 * The box browser: the shelves, the words standing on them, and the settings under them.
 *
 * The shell only — the door in, the door out, and the heading. What the shelves show is
 * kern's to answer (`BoxBrowser.sections` / `cardsInArea` / `enqueueableCardIds` /
 * `cardRowState`), and this screen renders those answers; the box is changed through
 * [AppModel.updateBox], never by walking `state.cards` here.
 *
 * [openAt] is the area the browser was reached BY — a search hit, later a tree — and it
 * says where the screen OPENS, not where it stands afterwards, which is why it arrives as
 * a parameter and is read once.
 */
@Composable
fun BoxScreen(model: AppModel, openAt: String? = null) {
    val chrome = model.chrome
    BackHandler { model.closeBox() }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chrome.boxTitle,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { model.closeBox() }) { Text("✕") }
        }
    }
}
