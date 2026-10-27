package net.spross.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import net.spross.kern.design.TypeScale

// Type on a big window.
//
// How much larger type stands is kern's (`TypeScale`), from the window's size. The window's
// root measures it once ([MeasuresWindowType]) and applies the app's factor; a run applies the
// session's ([TypeScaled]), each counted from the reader's own font scale.

/** The window's type scale as kern decides it; null until the window is measured. */
private val LocalWindowTypeScale = compositionLocalOf<TypeScale?> { null }

/** The reader's own font scale, before any window scale stepped it up. */
private val LocalReaderFontScale = compositionLocalOf<Float?> { null }

/** The factor type stands at here: kern's app or session factor, 1 on every phone. */
val LocalTypeFactor = compositionLocalOf { TypeScale.NORMAL.toFloat() }

/** The window's root measures its size for the type scale everything under it reads. */
@Composable
fun MeasuresWindowType(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    val scale = remember(size, density.density) {
        TypeScale((size.width / density.density).toDouble(), (size.height / density.density).toDouble())
    }
    CompositionLocalProvider(LocalWindowTypeScale provides scale) { TypeScaled(content = content) }
}

/**
 * Type at the window's app scale, or at its session scale for a run, each counted from the
 * reader's own font scale so the steps never stack on one another.
 */
@Composable
fun TypeScaled(session: Boolean = false, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val reader = LocalReaderFontScale.current ?: density.fontScale
    val factor = LocalWindowTypeScale.current?.let { if (session) it.session else it.app } ?: TypeScale.NORMAL
    // why: a phone keeps the platform's own density untouched.
    val scaled = if (factor == TypeScale.NORMAL) density else Density(density.density, reader * factor.toFloat())
    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalReaderFontScale provides reader,
        LocalTypeFactor provides factor.toFloat(),
        content = content,
    )
}
