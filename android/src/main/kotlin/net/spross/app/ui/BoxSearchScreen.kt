package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.box.AreaStatistics
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxSearch
import net.spross.kern.box.BoxSearchResults
import net.spross.kern.box.OwnWords

/**
 * The box reached by typing instead of by folding: one query over every word it holds —
 * both languages, headwords and the forms they accept — and over the area headings.
 * The matching itself is entirely kern's ([BoxSearch]); this supplies the headings the
 * learner reads and renders what comes back.
 *
 * The two result kinds offer different things, so they act differently. An area is a
 * shelf: choosing it hands the box back the area to unfold and steps aside. A word is
 * itself: it can be heard, and while it is still unpacked it can be packed right here,
 * without taking the whole shelf along.
 */
@Composable
fun BoxSearchScreen(
    model: AppModel,
    naming: AreaNaming,
    areas: List<String>,
    areaStats: Map<String, AreaStatistics>,
    onReveal: (String) -> Unit,
    onClose: () -> Unit,
) {
    val chrome = model.chrome
    val box = model.box ?: return
    var query by remember { mutableStateOf("") }
    var writing by remember { mutableStateOf<OwnWordDraft?>(null) }
    BackHandler { onClose() }

    writing?.let { draft ->
        OwnWordForm(
            model = model,
            initial = draft,
            // why: the box lands on the section the new word joined — the learner wrote it
            // to use it, not to file it. A suggestion joins no card, but it is listed in the
            // same section, so the landing is the same one.
            onDone = { writing = null; onReveal(OwnWords.AREA) },
            onCancel = { writing = null },
        )
        return
    }

    // why: the search runs on the query SNAPSHOT, not per redraw — packing one hit
    // redraws its row, and the whole box does not need re-scanning for that.
    //
    // And off the typing thread, a beat behind: it scans every card in the box, and a
    // keystroke that has to wait for the previous letter's scan is how a field comes to
    // drop characters. The pause is short enough to read as instant and long enough that
    // a word typed at speed is searched once instead of once a letter.
    var results by remember { mutableStateOf<BoxSearchResults?>(null) }
    LaunchedEffect(query, box) {
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        delay(SEARCH_SETTLE_MS)
        results = withContext(Dispatchers.Default) {
            BoxSearch.search(box, naming.searchable(areas), query)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = DlSpace.xl),
        verticalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chrome.boxSearchButton,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Icon(SprossIcons.Close, contentDescription = null) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(chrome.boxSearchPlaceholder) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(
                        onClick = { query = "" },
                        modifier = Modifier.semantics { contentDescription = chrome.boxSearchClear },
                    ) { Icon(SprossIcons.Close, contentDescription = null) }
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // why: read once into a local — the state itself is delegated, so the branches
        // below could not narrow it.
        val found = results
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
            when {
                found == null -> item {
                    SearchNote(chrome.boxSearchHint)
                }

                found.isEmpty -> item {
                    // A box with no answer is where the learner's own words come from:
                    // they have just proved the catalog holds none for what they need.
                    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.l)) {
                        SearchNote(chrome.boxSearchNothing.format(query))
                        Button(
                            // why: the KNOWN side is prefilled — someone typing into a
                            // search box is far more often naming what they want to be
                            // able to SAY than a form they already met in the wild.
                            onClick = { writing = OwnWordDraft(known = query) },
                            modifier = Modifier.pressSpring(),
                            shape = MaterialTheme.shapes.small,
                        ) { Text(chrome.boxSearchWriteOwn.format(query)) }
                    }
                }

                else -> {
                    if (found.areas.isNotEmpty()) {
                        item { Heading(chrome.boxSearchAreas) }
                        for (match in found.areas) {
                            item(key = "area:${match.area}") {
                                AreaHit(
                                    naming = naming,
                                    area = match.area,
                                    stats = areaStats[match.area],
                                    chrome = chrome,
                                    onOpen = { onReveal(match.area) },
                                )
                            }
                        }
                    }
                    if (found.cards.isNotEmpty()) {
                        item { Heading(chrome.boxSearchWords) }
                        for (card in found.cards) {
                            item(key = "card:${card.id}") {
                                BoxCardRow(
                                    model,
                                    card,
                                    pack = {
                                        model.updateBox { BoxEngine.enqueue(it, listOf(card.id)) }
                                    },
                                    onWriteOwn = { writing = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = DlSpace.s),
    )
}

@Composable
private fun SearchNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = DlSpace.l),
    )
}

/** An area hit carries its own progress: a worked shelf must be tellable from an untouched one. */
@Composable
private fun AreaHit(
    naming: AreaNaming,
    area: String,
    stats: AreaStatistics?,
    chrome: Chrome,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .panel()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(DlSpace.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AreaChip(
                name = naming.title(area),
                emoji = naming.emoji(area),
                subtitle = null,
                stats = stats,
                chrome = chrome,
                modifier = Modifier.weight(1f),
            )
            Icon(
                SprossIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How long the field settles before the box is scanned. Below a comfortable typing
 * interval, so a pause reads as instant; above the gap between two quick letters, so a
 * word typed at speed is searched once rather than once a letter.
 */
private const val SEARCH_SETTLE_MS = 120L
