package net.spross.kern.trainer

import kotlin.random.Random
import net.spross.kern.model.Language

/**
 * What a slot was drawn AS, before any language renders it.
 *
 * The drill used to recover its value from the rendered prompt (`slot.prompt.toLong()`),
 * which only works while every slot kind renders as a run of digits — no fraction survives
 * it, and a Kotlin throw crossing the ObjC boundary is an app crash. Drawing the value
 * first and instantiating from it removes the round-trip: nothing parses a display string
 * back into a number.
 */
internal sealed interface SlotValue {
    data class Count(val n: Long) : SlotValue

    data class Year(val y: Long) : SlotValue

    data class Time(val hour: Int, val minute: Int) : SlotValue

    /** Always reduced, and never a half — see [drawFractionSlot]. */
    data class Part(val numerator: Long, val denominator: Long) : SlotValue
}

/**
 * Full-difficulty draw: the biases ported from the prototype — numbers favour 2–3 digits,
 * years cluster around 1950–2050 with rarer historic outliers, and the clock reads the
 * whole face (its top rung, which IS any minute).
 */
internal fun drawSlot(kind: TrainerKind, language: Language, rng: Random): SlotValue = when (kind) {
    TrainerKind.Numbers -> SlotValue.Count(drawSampleNumber(rng))
    TrainerKind.Years -> SlotValue.Year(drawSampleYear(rng))
    TrainerKind.Clock -> drawSlot(kind, language, CLOCK_MAX_LEVEL, rng)
    TrainerKind.Fraction -> drawSlot(kind, language, FRACTION_MAX_LEVEL, rng)
    TrainerKind.Forms -> noSlotGenerator(kind)
}

/** Leveled draw, with the level semantics [Trainer.sample] documents. */
internal fun drawSlot(kind: TrainerKind, language: Language, level: Int, rng: Random): SlotValue {
    val l = level.coerceIn(1, Trainer.maxLevel(kind))
    return when (kind) {
        TrainerKind.Numbers -> SlotValue.Count(drawNumber(l, rng))
        TrainerKind.Years -> SlotValue.Year(drawSampleYear(l, rng))
        TrainerKind.Clock -> SlotValue.Time(rng.nextInt(24), drawClockMinute(l, rng))
        TrainerKind.Fraction -> drawFractionSlot(language, l, rng)
        TrainerKind.Forms -> noSlotGenerator(kind)
    }
}

/**
 * A number form is not a slot: it needs the frame to decline around it, and no agreement
 * device runs that way ([PhraseTemplate]'s init bars it at catalog-build time).
 */
private fun noSlotGenerator(kind: TrainerKind): Nothing =
    throw IllegalArgumentException("no slot generator for $kind")

private fun drawSampleNumber(rng: Random): Long {
    val r = rng.nextDouble()
    return when {
        r < 0.35 -> rng.nextLong(10, 100)
        r < 0.75 -> rng.nextLong(100, 1_000)
        else -> rng.nextLong(1_000, 10_000)
    }
}

private fun drawSampleYear(rng: Random): Long {
    val r = rng.nextDouble()
    return when {
        r < 0.55 -> rng.nextLong(1950, 2051)
        r < 0.85 -> rng.nextLong(1700, 2201)
        else -> rng.nextLong(1000, 2200)
    }
}

private fun drawSampleYear(level: Int, rng: Random): Long = when (level) {
    1 -> rng.nextLong(1990, 2030)
    2 -> rng.nextLong(1900, 2100)
    else -> rng.nextLong(1100, 2100)
}

/** Two rungs: unit fractions the size of a recipe step, then any the language reads. */
internal const val FRACTION_MAX_LEVEL = 2

/**
 * A fraction a frame can carry as a BARE NOUN, drawn from the pack's own denominators.
 *
 * Halves are excluded at every rung, which is the whole reason this is not simply the
 * Forms ladder's fraction draw: German and Spanish read 1/2 adjectivally ("ein halb",
 * "medio"), so a half has to agree with the noun beside it ("ein halbes Kilo") — and the
 * agreement device runs the other way round, from the numeral to the noun. Everything from
 * a third up is a noun in its own right and drops into a sentence unchanged.
 */
private fun drawFractionSlot(language: Language, level: Int, rng: Random): SlotValue.Part {
    val limits = Trainer.pack(language).formLimits
    val pool = fractionPool(limits, wide = level >= 2, minDenominator = 3)
    val drawn = pool[rng.nextInt(pool.size)]
    return SlotValue.Part(drawn.numerator, drawn.denominator)
}

/**
 * Level-sized number with zeros biased to ~40% on the non-leading digits,
 * so the drill favours rounder values (less tedious than typing arbitrary
 * long numbers). The leading digit stays 1–9 so the value keeps exactly
 * [digits] digits.
 *
 * Shared with the forms ladder, which draws its Mix-sized magnitudes from it —
 * a second copy would give the two drills different-looking numbers.
 */
internal fun drawNumber(digits: Int, rng: Random): Long {
    if (digits <= 1) return rng.nextLong(0, 10)
    var value = rng.nextLong(1, 10)
    repeat(digits - 1) {
        val d = if (rng.nextInt(10) < 4) 0L else rng.nextLong(1, 10)
        value = value * 10 + d
    }
    return value
}
