package net.spross.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Poster-derived warm palette (design.md: warm, card-centric).
val ToneRight = Color(0xFF4C9A57)
val ToneTough = Color(0xFFD9A13B)
val ToneWrong = Color(0xFFB3432E)
val TrackGrey = Color(0xFFE8E0D2)

/**
 * Article color coding der=blue / die=pink-red / das=green; neutral otherwise.
 * A two-gender language folds onto the same two hues rather than minting its own —
 * masculine blue, feminine pink-red, the plural and indefinite articles taking the
 * color of the gender they inflect — so green stays German's neuter alone.
 * `App/Sources/Design/Theme.swift` holds the canonical article list.
 */
fun articleTint(gender: String?): Color? = when (gender?.lowercase()) {
    "der", "el", "los", "un" -> Color(0xFF3B6FCB)
    "die", "la", "las", "una" -> Color(0xFFC9506E)
    "das" -> Color(0xFF3F9B57)
    else -> null
}

private val SprossLight = lightColorScheme(
    primary = Color(0xFF2E6B34),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EAD3),
    onPrimaryContainer = Color(0xFF1C4521),
    secondary = Color(0xFFB8862B),
    onSecondary = Color.White,
    background = Color(0xFFFDF8F2),
    onBackground = Color(0xFF2B2620),
    surface = Color.White,
    onSurface = Color(0xFF2B2620),
    surfaceVariant = Color(0xFFF3EAD8),
    onSurfaceVariant = Color(0xFF6B6155),
    outline = Color(0xFFC9BFAE),
    error = ToneWrong,
    onError = Color.White,
)

@Composable
fun SprossTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SprossLight, content = content)
}
