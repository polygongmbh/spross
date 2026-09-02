package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.box.AreaGroupSection
import net.spross.kern.box.BoxBrowser
import net.spross.kern.box.BoxState
import net.spross.kern.box.BoxStatistics
import net.spross.kern.box.OwnWords
import net.spross.kern.catalog.Catalog

/**
 * The box browser: the shelves, the words standing on them, and the settings under them.
 *
 * What the shelves show is kern's to answer ([BoxBrowser.sections] / [BoxBrowser.cardsInArea] /
 * [BoxBrowser.enqueueableCardIds] / [BoxBrowser.cardRowState]), and this screen renders those
 * answers; the box is changed through [AppModel.updateBox], never by walking `state.cards` here.
 *
 * [openAt] is the area the browser was reached BY — a search hit, later a tree — and it says
 * where the screen OPENS, not where it stands afterwards, which is why it arrives as a
 * parameter and is read once.
 */
@Composable
fun BoxScreen(model: AppModel, openAt: String? = null) {
    BackHandler { model.closeBox() }
    val catalog = model.catalog
    val box = model.box
    val stats = model.stats
    if (catalog == null || box == null || stats == null) {
        Column(Modifier.fillMaxSize().padding(Theme.spacing.xl)) {
            BoxTopBar(model.chrome, onSearch = null, onClose = model::closeBox)
        }
        return
    }
    BoxBrowserScreen(model, catalog, box, stats, openAt)
}

/** One entry of the scrolling box; the flat list is what lets a reveal find its row. */
private sealed interface BoxItem {
    data class Group(val section: AreaGroupSection) : BoxItem
    data class Area(val area: String) : BoxItem
    data object OwnContent : BoxItem
    data object Settings : BoxItem
}

@Composable
private fun BoxBrowserScreen(
    model: AppModel,
    catalog: Catalog,
    box: BoxState,
    stats: BoxStatistics,
    openAt: String?,
) {
    val chrome = model.chrome
    val source = box.joinStamp.source
    val sections = remember(catalog, stats, source) { BoxBrowser.sections(catalog, stats, source) }
    val areaNames = remember(catalog, stats) { BoxBrowser.areaNames(catalog, stats) }
    val naming = remember(catalog, chrome, source) {
        AreaNaming(
            chrome = chrome,
            catalogTitle = { catalog.areaTitle(it, source) },
            catalogSubtitle = { catalog.areaSubtitle(it, source) },
            catalogEmoji = { catalog.areaEmoji(it) },
        )
    }
    val areaStats = remember(stats) { stats.areas.associateBy { it.name } }

    // why: the opening fold reads the box ONCE — a group that folded itself shut again as
    // the learner works would be worse than one that opened on the wrong shelf. An area
    // named on the way in opens INSTEAD of the default: the learner already said which.
    var openGroups by remember {
        mutableStateOf(
            setOfNotNull(
                openAt?.let { area -> sections.firstOrNull { area in it.areas }?.id }
                    ?: BoxBrowser.defaultExpandedGroupId(sections, stats),
            ),
        )
    }
    var openAreas by remember { mutableStateOf(setOfNotNull(openAt)) }
    var scrollTo by remember { mutableStateOf(openAt) }
    var searching by remember { mutableStateOf(false) }
    // The own-word form, on whatever draft opened it: blank from the section's add button,
    // a copy of a card or a word being rewritten from a row's menu.
    var writing by remember { mutableStateOf<OwnWordDraft?>(null) }
    val listState = rememberLazyListState()

    val items = remember(sections, openGroups) {
        buildList {
            sections.forEach { section ->
                add(BoxItem.Group(section))
                if (section.id in openGroups) section.areas.forEach { add(BoxItem.Area(it)) }
            }
            // why: the learner's own words get no shelf of their own — they are packed the
            // moment they are written, so a shelf's controls and progress bar would say
            // nothing over them. They stand in the section below instead, after everything
            // the catalog brought. Kern still lists the area; only the box stops drawing it.
            add(BoxItem.OwnContent)
            add(BoxItem.Settings)
        }
    }

    fun reveal(area: String) {
        sections.firstOrNull { area in it.areas }?.let { openGroups = openGroups + it.id }
        openAreas = openAreas + area
        scrollTo = area
    }

    LaunchedEffect(scrollTo, items) {
        val target = scrollTo ?: return@LaunchedEffect
        // The own words are the one "area" with no shelf: a reveal aimed at them lands on
        // the section that lists them instead.
        val index = items.indexOfFirst { item ->
            if (target == OwnWords.AREA) {
                item is BoxItem.OwnContent
            } else {
                item is BoxItem.Area && item.area == target
            }
        }
        // why: revealing is two moves — unfold, then bring the shelf up to the thumb.
        if (index >= 0) listState.animateScrollToItem(index)
        scrollTo = null
    }

    writing?.let { draft ->
        OwnWordForm(
            model = model,
            initial = draft,
            // why: the box comes back on the section the word landed in — written, rewritten
            // or still half-written, that is the one place it can now be found.
            onDone = { writing = null; reveal(OwnWords.AREA) },
            onCancel = { writing = null },
        )
        return
    }

    if (searching) {
        BoxSearchScreen(
            model = model,
            naming = naming,
            areas = areaNames,
            areaStats = areaStats,
            onReveal = { area -> searching = false; reveal(area) },
            onClose = { searching = false },
        )
        return
    }

    // The hint is only honest where something can actually be heard — a box whose language
    // has neither a recording nor a device voice for a single word must not promise a tap
    // that would do nothing everywhere. Remembered: it answers once per box, not per frame.
    val anyWordCanBeHeard = remember(catalog, box.cards) {
        box.cards.values.any { model.boxPronounceAction(it.target) != null }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = Theme.spacing.xl)) {
        BoxTopBar(chrome, onSearch = { searching = true }, onClose = model::closeBox)
        Text(
            chrome.boxSubtitle.format(stats.activeCount, box.cards.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // why: same disclosure as the number and country reference tables — said once for
        // the page rather than as a glyph competing with every row. Withheld where not one
        // word can be heard, so the box never promises a tap that would do nothing.
        if (anyWordCanBeHeard) {
            TapToHearHint(chrome, chrome.boxTapToHear)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
            contentPadding = PaddingValues(vertical = Theme.spacing.lg),
        ) {
            itemsIndexed(items, key = { _, item -> itemKey(item) }) { _, item ->
                when (item) {
                    is BoxItem.Group -> GroupHeader(
                        section = item.section,
                        emojis = item.section.areas.joinToString("") { naming.emoji(it) },
                        open = item.section.id in openGroups,
                        chrome = chrome,
                        onToggle = {
                            openGroups = if (item.section.id in openGroups) {
                                openGroups - item.section.id
                            } else {
                                openGroups + item.section.id
                            }
                        },
                    )

                    is BoxItem.Area -> AreaSection(
                        model = model,
                        area = item.area,
                        naming = naming,
                        stats = areaStats[item.area],
                        expanded = item.area in openAreas,
                        onToggle = {
                            openAreas = if (item.area in openAreas) {
                                openAreas - item.area
                            } else {
                                openAreas + item.area
                            }
                        },
                        onWriteOwn = { writing = it },
                    )

                    BoxItem.OwnContent -> BoxOwnSection(model, onWriteOwn = { writing = it })

                    BoxItem.Settings -> BoxSettingsSection(model, catalog, box)
                }
            }
        }
    }
}

private fun itemKey(item: BoxItem): String = when (item) {
    is BoxItem.Group -> "group:${item.section.id}"
    is BoxItem.Area -> "area:${item.area}"
    BoxItem.OwnContent -> "own"
    BoxItem.Settings -> "settings"
}

@Composable
private fun BoxTopBar(chrome: Chrome, onSearch: (() -> Unit)?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            chrome.boxTitle,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f),
        )
        onSearch?.let {
            TextButton(
                onClick = it,
                modifier = Modifier.semantics { contentDescription = chrome.boxSearchButton },
            ) { Text("🔍") }
        }
        TextButton(onClick = onClose) { Icon(SprossIcons.Close, contentDescription = null) }
    }
}
