package net.spross.kern.design

/**
 * One tile of a multiple-choice grid (the letters ladder, the calendar's warm-up) and its verdict skin.
 * The platform draws [Answer] on the success [Palette.WASH] with a check mark, [WrongPick] on the wrong wash
 * with a cross, and the rest on the plain tile fill — correctness is never color alone.
 * Every tile but an [Open] one is dead: one pick per question.
 */
enum class ChoiceTile {
    /** The question is still owed: the tile takes a pick. */
    Open,
    /** The right option, once a pick has landed — whoever picked it. */
    Answer,
    /** The learner's pick, and it was not the answer. */
    WrongPick,
    /** Neither picked nor right, on an answered question. */
    Dead;

    /** What a screen reader hears after the option's name; null for a tile with nothing to report. */
    val verdict: ChoiceVerdict?
        get() = when (this) {
            Answer -> ChoiceVerdict.Correct
            WrongPick -> ChoiceVerdict.Wrong
            Open, Dead -> null
        }

    companion object {
        fun of(option: String, answer: String, chosen: String?): ChoiceTile = when {
            chosen == null -> Open
            option == answer -> Answer
            option == chosen -> WrongPick
            else -> Dead
        }
    }
}

/** A tile's spoken verdict; the words are each platform's string table's. */
enum class ChoiceVerdict { Correct, Wrong }
