package net.spross.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import net.spross.app.Chrome

/**
 * ONE grid, sized to the tile — `docs/surfaces.md` § Android companion.
 *
 * An Android tile is dragged to any shape, so columns and rows are cut from the size the
 * host hands over against a minimum readable cell: the smallest tile holds one word with
 * its picture, and every shape up to [GRID_CELLS] fills with as many cells as fit. The
 * cell is always the same thing — picture, word, meaning — and only its TYPE moves with
 * its own box. (iOS keeps three families because WidgetKit gives it three sizes and no
 * shape between them.)
 */

/** The tile's inner margin; the size a host hands over is the OUTER box. */
val TILE_PADDING = 12.dp

/**
 * Glance translates a container into a GENERATED RemoteViews layout that holds at most ten
 * children, and an eleventh is dropped rather than squeezed — the bug that once ate half
 * the fortnight strip ([ActivityStrip]). The grid is a Column of the stats header plus one
 * Row per grid row, so the row bound is derived from that cap rather than left to the hope
 * that no launcher ever hands over a tall enough tile.
 */
private const val GLANCE_CHILDREN = 10

/** Columns are a Row's children, rows share the grid Column with the stats header. */
const val GRID_COLUMNS: Int = 4
val GRID_ROWS: Int = minOf(4, GLANCE_CHILDREN - 1)

/** The most words one draw can want, which is what fixes [WidgetFaces.WINDOW]. */
val GRID_CELLS: Int = GRID_COLUMNS * GRID_ROWS

/**
 * The smallest a cell may be and still be read at arm's length.
 *
 * Cut from what a launcher CELL is worth rather than from round numbers: a phone grid
 * hands a widget something like 100 dp per column and 108 per row, so the narrowest tile a
 * launcher will place holds one word across, three columns of grid hold two abreast and
 * the full width three; the shortest tile holds one word down, and four grid rows want a
 * tile most of the home screen tall. The width is where a word stops being READABLE rather
 * than where it stops fitting: nothing here is set below 13 sp, and a cell narrower than
 * this truncates a pair of average length.
 */
private val MIN_CELL = DpSize(116.dp, 96.dp)

/** What the stacked cell needs before its third line is a clipped line. */
private val STACK_HEIGHT = 72.dp

/**
 * What the FOLDED cell needs. Under this the tile has room for the stats line and nothing
 * else — the shape a phone in landscape can hand a one-row tile — and it draws the stats
 * alone, since half a word is a fault where an empty tile is a small tile.
 */
private val FOLD_HEIGHT = 32.dp

/** A cell this big is most of the tile, and its word is worth setting large. */
private val HERO_CELL = DpSize(150.dp, 120.dp)

/** Below this the header has no room for the fortnight beside the flame and the count. */
private val STRIP_WIDTH = 160.dp

/**
 * What keeps one cell's word off the next one's.
 *
 * A column is only as wide as its share of the tile, and a word that spends all of it ends
 * flush against the neighbor's picture — two entries reading as one line. It is an inset
 * on the cell rather than a Spacer between cells, which would spend the child budget the
 * columns need, and only a cell that HAS a neighbor pays it: the last of a row, and every
 * cell of a one-column tile, keeps the width for its word.
 */
private val CELL_GUTTER = 8.dp

/** Shortest pair first: where a card lands is the tile's business, which ones travel is kern's. */
private val SHORTEST_FIRST =
    compareBy<WidgetWord>({ it.word.length + it.meaning.length }, { it.word.length })

/** The tile's words, in as many cells as [size] fits, under the stats line. */
@Composable
fun GridFace(face: WidgetFace, size: DpSize) {
    val inner = DpSize(
        size.width - TILE_PADDING * 2,
        size.height - TILE_PADDING * 2 - STATS_HEIGHT,
    )
    val columns = fit(inner.width, MIN_CELL.width, GRID_COLUMNS)
    val rows = fit(inner.height, MIN_CELL.height, GRID_ROWS)
    val cut = CellCut.of(DpSize(inner.width / columns, inner.height / rows))
    val cells = if (inner.height < FOLD_HEIGHT) 0 else columns * rows
    Column(modifier = GlanceModifier.fillMaxSize()) {
        StatsHeader(face, full = inner.width >= STRIP_WIDTH)
        face.words.take(cells).sortedWith(SHORTEST_FIRST).chunked(columns)
            .forEach { line ->
                // The rows carry the slack in their own weight rather than in spacers
                // between them: a Column of rows and spacers alike would spend the child
                // budget twice as fast, and an evenly filled tile is what a grid means.
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    line.forEachIndexed { index, word ->
                        val gutter = if (index < line.lastIndex) CELL_GUTTER else 0.dp
                        Box(
                            GlanceModifier.defaultWeight().padding(end = gutter),
                            contentAlignment = cut.anchor,
                        ) {
                            Cell(word, cut)
                        }
                    }
                    // A last short line keeps its column width instead of spreading.
                    repeat(columns - line.size) { Spacer(GlanceModifier.defaultWeight()) }
                }
            }
    }
}

/** How many cells of at least [min] fit in [available] — never none, never more than [cap]. */
private fun fit(available: Dp, min: Dp, cap: Int): Int =
    (available.value / min.value).toInt().coerceIn(1, cap)

/**
 * Three cuts of the one cell, chosen off the CELL's own box rather than the tile's, so a
 * tile showing one word sets it large and a tile showing a dozen sets it small.
 *
 * Steps rather than a continuous ramp: RemoteViews has no shrink-to-fit, so a size is only
 * worth having where the line it sets has been looked at.
 */
private enum class CellCut(
    val picture: TextUnit,
    val word: TextStyle,
    val meaning: TextStyle,
    val anchor: Alignment,
) {
    /** The tile is one word: the picture is worth looking at, and the pair centers under it. */
    HERO(40.sp, WidgetType.hero, WidgetType.meaningSmall, Alignment.Center),

    /** The poster cell — picture, word, meaning stacked against the cell's leading edge. */
    ROOMY(22.sp, WidgetType.word, WidgetType.meaningSmall, Alignment.CenterStart),

    /** A cell shorter than a readable one has no third line, so the picture moves beside the pair. */
    COMPACT(16.sp, WidgetType.wordSmall, WidgetType.meaningTiny, Alignment.CenterStart);

    companion object {
        fun of(cell: DpSize) = when {
            cell.width >= HERO_CELL.width && cell.height >= HERO_CELL.height -> HERO
            cell.height >= STACK_HEIGHT -> ROOMY
            else -> COMPACT
        }
    }
}

@Composable
private fun Cell(word: WidgetWord, cut: CellCut) {
    if (cut == CellCut.COMPACT) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(word.emoji, style = TextStyle(fontSize = cut.picture))
            Spacer(GlanceModifier.width(6.dp))
            Column {
                WordLine(word, cut.word)
                Text(word.meaning, style = cut.meaning, maxLines = 1)
            }
        }
        return
    }
    Column(
        horizontalAlignment =
            if (cut == CellCut.HERO) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(word.emoji, style = TextStyle(fontSize = cut.picture))
        if (cut == CellCut.HERO) Spacer(GlanceModifier.height(4.dp))
        WordLine(word, cut.word)
        Text(word.meaning, style = cut.meaning, maxLines = 1)
    }
}

/** The fallback the article line measures itself by when a style names no size. */
private val WORD_SIZE = 16.sp

/**
 * The target word with its article word in front, colored where the box names a gender.
 *
 * Two texts rather than one: RemoteViews carries no styled spans, so a tinted article can
 * only be a view of its own.
 */
@Composable
private fun WordLine(word: WidgetWord, style: TextStyle) {
    val tint = WidgetColors.article(word.article)
    if (word.article == null || tint == null) {
        Text(word.word, style = style, maxLines = 1)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(word.article, style = WidgetType.article(tint, style.fontSize ?: WORD_SIZE), maxLines = 1)
        Spacer(GlanceModifier.width(4.dp))
        Text(word.word, style = style, maxLines = 1)
    }
}

/**
 * The tile with nothing of the learner's to draw: no snapshot, or one an app update left
 * behind in a schema this build cannot read. It says where the words come from and keeps
 * the tile recognizably Spross — sample words here would pass for somebody's own box.
 *
 * It folds where a cell would, and for the same reason: a tile the learner dragged down to
 * a strip has one line, and a sprout cropped through the middle of it says nothing.
 */
@Composable
fun AwaitingFace(chrome: Chrome, size: DpSize) {
    if (size.height - TILE_PADDING * 2 < STACK_HEIGHT) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🌱", style = TextStyle(fontSize = 16.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text(chrome.widgetAwaitingTitle, style = WidgetType.wordSmall, maxLines = 1)
        }
        return
    }
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(GlanceModifier.defaultWeight())
        Text("🌱", style = TextStyle(fontSize = 40.sp))
        Spacer(GlanceModifier.height(6.dp))
        Text(
            chrome.widgetAwaitingTitle,
            style = WidgetType.hero.copy(textAlign = TextAlign.Center),
            maxLines = 1,
        )
        Text(
            chrome.widgetAwaitingBody,
            style = WidgetType.meaningSmall.copy(textAlign = TextAlign.Center),
            maxLines = 2,
        )
        Spacer(GlanceModifier.defaultWeight())
    }
}
