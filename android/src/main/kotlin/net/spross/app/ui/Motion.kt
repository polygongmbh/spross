package net.spross.app.ui

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI
import kotlin.math.pow

/** A spring given as iOS gives it — seconds to settle and a damping fraction — in Compose's terms. */
fun <T> responseSpring(response: Double, damping: Double): SpringSpec<T> =
    // A spring's response converts to Compose's stiffness as (2π / response)².
    spring(dampingRatio = damping.toFloat(), stiffness = (2 * PI / response).pow(2).toFloat())
