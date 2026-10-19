import SwiftUI
import SprossKern

extension NumbersReading {
    /// Catalog key for the drill title.
    var trainerTitleKey: LocalizedStringKey { LocalizedStringKey(trainerTitleName) }

    /// The same key as a plain string, for a sentence that names the drill.
    var trainerTitleName: String {
        switch self {
        case .cardinal, .phone: return "trainer.drill.numbers"
        case .year: return "trainer.drill.numbers.exercise.years"
        case .clock: return "trainer.drill.numbers.exercise.clock"
        case .form, .fraction: return "trainer.drill.numbers.exercise.forms"
        }
    }
}

/// What an EXERCISE is called, wherever one has to be named on its own — the
/// score line of a mixed run today, the overview's rows next. Numbers, Clock and
/// Forms deliberately borrow the slot kind's title: they are the same exercise.
/// The matching FACE is kern's `numbersExerciseEmoji` — one glyph table, not two.
extension NumbersExercise {
    var trainerTitleKey: LocalizedStringKey { LocalizedStringKey(trainerTitleName) }

    var trainerTitleName: String {
        switch self {
        case .phrases: return "trainer.drill.numbers.exercise.phrases"
        case .counting, .clock, .forms: return reading?.trainerTitleName ?? "trainer.drill.numbers"
        }
    }
}

/// How a run is PLAYED, as the overview offers it. A modifier has no face of its
/// own: it changes every exercise alike, so it is named and explained in words.
extension DrillModifier {
    var trainerTitleKey: LocalizedStringKey { LocalizedStringKey(trainerTitleName) }

    var trainerTitleName: String {
        switch self {
        case .reverse: return "trainer.modifier.reverse"
        case .fast: return "trainer.modifier.fast"
        case .mix: return "trainer.modifier.mix"
        }
    }

    /// One line saying what it does to a run — the settings-row caption pattern.
    var trainerHintKey: LocalizedStringKey {
        switch self {
        case .reverse: return "trainer.modifier.reverse.hint"
        case .fast:
            return "trainer.modifier.fast.hint \(Int(Numbers.shared.winsToAdvance(fast: false)))"
        case .mix: return "trainer.modifier.mix.hint"
        }
    }
}
