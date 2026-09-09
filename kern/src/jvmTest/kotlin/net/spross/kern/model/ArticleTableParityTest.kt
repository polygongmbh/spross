package net.spross.kern.model

import net.spross.kern.repoText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which article marks which gender is [articleGender]'s alone, but the watch app and the
 * two widget extensions do not link kern — they hand-copy the list into a `switch` and
 * paint it straight — so this reads those three switches as text and grades every article
 * they name through [articleGender].
 *
 * A copy may know FEWER articles than kern does (a widget shows what it shows), so the
 * check runs one way: every article a copy lists must resolve to the gender that copy
 * paints it in. The three are then held to each other, because the drift that actually
 * happens is one surface being taught a new article and the other two not.
 *
 * `kern/build.gradle.kts` names these Swift trees as test inputs so an edit re-runs this.
 */
class ArticleTableParityTest {

    @Test
    fun everyCopiedArticleArmGradesThroughTheKernTable() {
        for (path in COPIES) {
            val arms = arms(path)
            assertEquals(
                setOf(Gender.Masculine, Gender.Feminine, Gender.Neuter),
                arms.keys,
                "$path: the article switch this check reads has been reshaped",
            )
            for ((gender, articles) in arms) {
                assertTrue(articles.isNotEmpty(), "$path: the $gender arm lists no article")
                for (article in articles) {
                    assertEquals(
                        gender, articleGender(article),
                        "$path: `$article` is painted $gender, $CANON says otherwise",
                    )
                }
            }
        }
    }

    @Test
    fun theThreeCopiesListTheSameArticles() {
        val tables = COPIES.associateWith { arms(it) }
        val reference = tables.getValue(COPIES.first())
        for ((path, arms) in tables) {
            assertEquals(
                reference, arms,
                "$path: this copy learned an article the others did not — teach all three from $CANON",
            )
        }
    }
}

private const val CANON = "kern/src/commonMain/kotlin/net/spross/kern/model/Article.kt"

private val COPIES = listOf(
    "Watch/Sources/WatchTheme.swift",
    "Widgets/Sources/WordWidgetView.swift",
    "WatchWidgets/Sources/WatchWordWidgetView.swift",
)

/** `case "der", "el": return colors.der` — the hand-copied arm, with or without `return`. */
private val ARM = Regex("""case ((?:"[^"]+"(?:, )?)+):\s*(?:return )?\w+\.(der|die|das)\b""")

private val PAINTED = mapOf(
    "der" to Gender.Masculine,
    "die" to Gender.Feminine,
    "das" to Gender.Neuter,
)

/** Gender the copy paints → the articles it paints in it. */
private fun arms(path: String): Map<Gender, Set<String>> =
    ARM.findAll(repoText(path)).associate { match ->
        PAINTED.getValue(match.groupValues[2]) to
            Regex("\"([^\"]+)\"").findAll(match.groupValues[1])
                .map { it.groupValues[1] }.toSet()
    }
