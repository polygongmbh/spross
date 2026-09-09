package net.spross.kern.trainer

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the per-language number-form specs assert through — `Trainer<Language>FormsTests`,
 * one file per language, each authored from that language's own sources.
 *
 * The readings come from the shipped pack and the expectations from the research, never the
 * other way round: a helper here that started deriving an expectation would let the spec
 * agree with whatever the generator does.
 */
internal fun readings(language: String, value: NumberValue): List<String> =
    Trainer.pack(language).formReading(value)

/** The one reading the drill SHOWS: first in the pack's list. */
internal fun assertCanonical(language: String, value: NumberValue, expected: String) {
    assertEquals(expected, readings(language, value).first(), "$language $value")
}

/** Readings a learner may type and be graded right on. */
internal fun assertAccepts(language: String, value: NumberValue, vararg forms: String) {
    val all = readings(language, value)
    for (form in forms) assertTrue(form in all, "$language $value: \"$form\" missing from $all")
}

/** Readings the language does not have — grading one would teach the wrong word. */
internal fun assertRejects(language: String, value: NumberValue, vararg forms: String) {
    val all = readings(language, value)
    for (form in forms) assertFalse(form in all, "$language $value: \"$form\" must not grade")
}
