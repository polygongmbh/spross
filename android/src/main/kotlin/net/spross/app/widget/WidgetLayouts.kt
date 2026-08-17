package net.spross.app.widget

import androidx.compose.runtime.Composable
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
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import net.spross.app.Chrome

/**
 * The families differ in KIND, not in row count — `docs/surfaces.md` § Watch & widgets.
 *
 * Small is one word with its picture and a single stats line. Medium is a short list whose
 * rows meet at a fixed emoji column, so a pair is read in place instead of scanned across
 * the tile. Large is a poster of stacked cells: equal rows have no hierarchy, and a cell
 * gives each side the full column width a shared line denies it.
 */

/** The picture on the small tile, which is the one that has room to be looked at. */
private val HERO_EMOJI = 40.sp

/** The poster cell's picture. */
private val CELL_EMOJI = 22.sp

/**
 * The emoji spine of a medium row. Fixed, because advance widths differ (☀️ against 🚪)
 * and an unsized column would let the spine wander from row to row.
 */
private val SPINE = 24.dp

/** The fallback the article line measures itself by when a style names no size. */
private val WORD_SIZE = 16.sp

/** One word with room to be looked at, over a single quiet stats line. */
@Composable
fun SmallFace(face: WidgetFace) {
    val word = face.words.first()
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(GlanceModifier.defaultWeight())
        Text(word.emoji, style = TextStyle(fontSize = HERO_EMOJI))
        Spacer(GlanceModifier.height(4.dp))
        WordLine(word, WidgetType.hero)
        Text(word.meaning, style = WidgetType.meaningSmall, maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        StatsLine(face, withLabel = false)
    }
}

/**
 * The list family: the fortnight in the header, three pairs meeting at their pictures.
 *
 * The gaps carry the slack rather than the bottom edge — a tile is any height the learner
 * drags it to, and three rows stacked under the header of a tall one read as a list that
 * ran out rather than a list that fits.
 */
@Composable
fun MediumFace(face: WidgetFace) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        StatsHeader(face)
        face.words.take(MEDIUM_ROWS).forEach { word ->
            Spacer(GlanceModifier.defaultWeight())
            MediumRow(word)
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

private const val MEDIUM_ROWS = 3

@Composable
private fun MediumRow(word: WidgetWord) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterEnd) {
            WordLine(word, WidgetType.word)
        }
        Box(GlanceModifier.width(SPINE), contentAlignment = Alignment.Center) {
            Text(word.emoji, style = TextStyle(fontSize = 15.sp))
        }
        Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterStart) {
            Text(word.meaning, style = WidgetType.meaning, maxLines = 1)
        }
    }
}

/**
 * A poster rather than a longer list. Rows are spaced apart rather than stacked from the
 * top — a poster ending two thirds up the tile reads as a truncated list — and a final odd
 * cell keeps its column width instead of spreading across the row.
 */
@Composable
fun LargeFace(face: WidgetFace) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        StatsHeader(face)
        face.words.take(WidgetFaces.WINDOW).chunked(2).forEach { pair ->
            Spacer(GlanceModifier.defaultWeight())
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                pair.forEach { word ->
                    Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.TopStart) {
                        PosterCell(word)
                    }
                }
                if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun PosterCell(word: WidgetWord) {
    Column {
        Text(word.emoji, style = TextStyle(fontSize = CELL_EMOJI))
        WordLine(word, WidgetType.word)
        Text(word.meaning, style = WidgetType.meaningSmall, maxLines = 1)
    }
}

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
 */
@Composable
fun AwaitingFace(chrome: Chrome) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(GlanceModifier.defaultWeight())
        Text("🌱", style = TextStyle(fontSize = HERO_EMOJI))
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
