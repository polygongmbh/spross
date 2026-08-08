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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.box.AreaStatistics
import net.spross.kern.box.BoxEngine
import net.spross.kern.box.BoxSearch
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
    var writing by remember { mutableStateOf(false) }
    BackHandler { onClose() }

    if (writing) {
        OwnWordForm(
            model = model,
            query = query,
            onCancel = { writing = false },
            // why: the box lands on the shelf the new word joined — the learner wrote it
            // to use it, not to file it.
            onAdded = { writing = false; onReveal(OwnWords.AREA) },
        )
        return
    }

    // why: the search runs on the query SNAPSHOT, not per redraw — packing one hit
    // redraws its row, and the whole box does not need re-scanning for that.
    val results = remember(query, box) {
        if (query.isBlank()) null else BoxSearch.search(box, naming.searchable(areas), query)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = DlSpace.xl),
        verticalArrangement = Arrangement.spacedBy(DlSpace.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chrome.search,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("✕") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(chrome.searchPlaceholder) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(
                        onClick = { query = "" },
                        modifier = Modifier.semantics { contentDescription = chrome.searchClear },
                    ) { Text("✕") }
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(DlSpace.s)) {
            when {
                results == null -> item {
                    SearchNote(chrome.searchHint)
                }

                results.isEmpty -> item {
                    // A box with no answer is where the learner's own words come from:
                    // they have just proved the catalog holds none for what they need.
                    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.l)) {
                        SearchNote(chrome.searchNothing.format(query))
                        Button(
                            onClick = { writing = true },
                            shape = MaterialTheme.shapes.small,
                        ) { Text(chrome.searchWriteOwn.format(query)) }
                    }
                }

                else -> {
                    if (results.areas.isNotEmpty()) {
                        item { Heading(chrome.searchAreas) }
                        for (match in results.areas) {
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
                    if (results.cards.isNotEmpty()) {
                        item { Heading(chrome.searchWords) }
                        for (card in results.cards) {
                            item(key = "card:${card.id}") {
                                BoxCardRow(model, card, pack = {
                                    model.updateBox { BoxEngine.enqueue(it, listOf(card.id)) }
                                })
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
