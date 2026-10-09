package net.spross.kern.design

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** The theme colors a confetti piece is cut from: the poster palette, minus the wrong-answer brick. */
enum class ConfettiInk { ACCENT, TEAL, SUCCESS, AMBER, DER, DIE, DAS }

/**
 * Paper confetti falling across a whole screen, in waves: the platform runs the clock,
 * throws a wave per celebration (keeping at most [MAX_WAVES], dropping the oldest),
 * retires each once [retired], and draws what [fill] writes for each wave at its age.
 *
 * Nothing is stored per piece. Every property — lane, speed, sway, spin, color — is derived
 * from (wave, index) through a hash, so the pieces are varied but reproducible.
 * Motion is deliberately not uniform: real confetti falls at different rates,
 * swings on its own phase, and flickers as it turns edge-on —
 * those three together are what separate falling paper from falling dots.
 *
 * One frame of a wave is one call writing plain numbers into [values], [STRIDE] per drawn piece,
 * so a frame crosses into the platform as no objects at all.
 */
class ConfettiFrame {
    /** Per drawn piece, in points (dp), at the offsets below. */
    val values = DoubleArray(PIECES * STRIDE)

    /**
     * Writes every piece of [wave] still on a [width]×[height] canvas [elapsed] seconds after it was thrown;
     * returns how many, from the start of [values].
     */
    fun fill(wave: Int, elapsed: Double, width: Double, height: Double): Int {
        var n = 0
        for (index in 0 until PIECES) {
            // Squaring the launch spread front-loads it: most of the handful is away in the first moment,
            // the rest keeps trickling after it.
            val age = elapsed - random(wave, index, 1).pow(2.2) * EMISSION
            if (age <= 0) continue

            // Constant fall plus a gentle pull, so late pieces are visibly quicker than they started —
            // paper does not settle into one terminal speed.
            val speed = 210 + random(wave, index, 2) * 210
            val y = -40 + age * speed + 30 * age * age
            if (y >= height + 40) continue

            val sway = 6 + random(wave, index, 4) * 26
            val phase = (age + random(wave, index, 6) * 6) * (0.35 + random(wave, index, 5) * 0.7)
            val scale = 0.7 + random(wave, index, 10) * 0.7
            val o = n * STRIDE
            values[o + X] = random(wave, index, 3) * width + sin(phase * 2 * PI) * sway
            values[o + Y] = y
            values[o + WIDTH] = 7.0 * scale
            values[o + HEIGHT] = (if (index % 3 == 2) 16.0 else 10.0) * scale
            values[o + ROTATION] = random(wave, index, 9) * PI
            // The horizontal squash IS the tumble: a piece turning edge-on narrows to nothing and flickers back,
            // which reading as depth is the whole difference from a spinning sticker.
            values[o + TUMBLE] = cos(age * (1.6 + random(wave, index, 8) * 3.4))
            values[o + ALPHA] = 0.75 + random(wave, index, 11) * 0.25
            values[o + OVAL] = if (index % 3 == 1) 1.0 else 0.0
            values[o + INK] = (index % INKS.size).toDouble()
            n++
        }
        return n
    }

    companion object {
        /** Pieces per wave. */
        const val PIECES = 130
        /** How long a wave keeps launching pieces, front-loaded across it, so it opens thick and thins out. */
        const val EMISSION = 4.0
        /** Emission window plus the longest fall still to come after it. */
        const val LIFE = EMISSION + 3.2
        /** Taps can come faster than waves retire; past this many in the air the oldest, the thinnest, is dropped. */
        const val MAX_WAVES = 4

        /** A piece's offsets in [values]: center, size, rotation in radians, horizontal scale, opacity, 1 for an oval, its [INKS] index. */
        const val STRIDE = 9
        const val X = 0
        const val Y = 1
        const val WIDTH = 2
        const val HEIGHT = 3
        const val ROTATION = 4
        const val TUMBLE = 5
        const val ALPHA = 6
        const val OVAL = 7
        const val INK = 8

        /** A rectangular piece's corner, in points (dp): a paper scrap's, not a card's. */
        const val CORNER = 1.5

        val INKS: List<ConfettiInk> = ConfettiInk.entries

        /** Whether a wave thrown [elapsed] seconds ago has nothing left to draw. */
        fun retired(elapsed: Double): Boolean = elapsed >= LIFE

        /** The whole wave's opacity: the tail fade catches whatever is still airborne as it retires, so nothing pops out mid-screen. */
        fun fade(elapsed: Double): Double = ((LIFE - elapsed) / 1.0).coerceIn(0.0, 1.0)

        /**
         * Stable 0..<1 noise for one (wave, piece, property) — SplitMix64 finish,
         * which decorrelates neighboring indices well enough that the pieces never fall in visible rows.
         */
        private fun random(wave: Int, index: Int, salt: Int): Double {
            var x = (index * 0x9E3779B1L + salt * 0x85EBCA77L + wave * 0x2545F491L).toULong()
            x = (x xor (x shr 33)) * 0xFF51AFD7ED558CCDuL
            x = (x xor (x shr 33)) * 0xC4CEB9FE1A85EC53uL
            x = x xor (x shr 33)
            return (x shr 11).toDouble() / (1L shl 53).toDouble()
        }
    }
}
