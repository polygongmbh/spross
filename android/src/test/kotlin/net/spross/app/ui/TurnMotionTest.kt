package net.spross.app.ui

import androidx.compose.animation.core.TweenSpec
import kotlin.test.Test
import kotlin.test.assertTrue
import net.spross.kern.session.ADVANCE_LIVE_MS

class TurnMotionTest {

    /** A verdict is fully drawn before the shortest beat can move the card on. */
    @Test
    fun aVerdictSettlesBeforeTheLiveBeatEnds() {
        val spec = turnTween<Float>() as TweenSpec<Float>
        assertTrue(spec.durationMillis + spec.delay < ADVANCE_LIVE_MS)
    }
}
