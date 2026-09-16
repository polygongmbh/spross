import SwiftUI
import SprossKern

/// What a run drills is kern's `NumbersMode` — the run SPEC, never edited once
/// the run is open, and the one place the draw, the ramp ceilings and the two
/// storage identities are decided.
///
/// What stays on this side is only what kern will not carry: the spelling the
/// overview builds a mode with (run-through hooks folded in), the chrome names,
/// and the grader a run is played with.
extension NumbersRunView {

    /// The spec, under the name the surfaces that open a run already use.
    typealias Mode = NumbersMode
}

extension NumbersMode {

    /// A selection as the overview picks it. ONE place builds a mode out of
    /// picks, so `-uitest-exercises` / `-uitest-modifiers` reach every run that
    /// is ever started; kern drops a frameless Phrases itself.
    convenience init(exercises: [NumbersExercise], language: String, phraseSource: String? = nil,
                     templates: [PhraseTemplate] = [], modifiers: Set<DrillModifier> = []) {
        #if DEBUG
        let asked = NumbersMode.uitestExercises ?? exercises
        let played = modifiers.union(NumbersMode.uitestModifiers)
        #else
        let asked = exercises
        let played = modifiers
        #endif
        self.init(selection: asked, language: language, phraseSource: phraseSource,
                  templates: templates, modifiers: played)
    }

    /// One reading, played plain.
    static func slots(_ reading: NumbersReading, _ language: String) -> NumbersMode {
        NumbersMode(exercises: [reading.exercise], language: language)
    }

    static func phrases(source: String, target: String, templates: [PhraseTemplate]) -> NumbersMode {
        NumbersMode(exercises: [.phrases], language: target,
                    phraseSource: source, templates: templates)
    }

    /// Catalog key for the run title — the exercise's own where the run asks one
    /// thing, and the trainer's own name where it asks several. Chrome, so it
    /// lives here: kern names the rule, never the rendering.
    var titleKey: LocalizedStringKey {
        exercises.count == 1 ? exercises[0].trainerTitleKey : "trainer.hub.title"
    }

    /// The grader this run is played with — one home, because both surfaces that
    /// open a run owe it the same one. Drills grade word by word (no article
    /// forgiveness, one slip per word, digits exact-only), so a sentence may
    /// fumble one word while no number can pass for another.
    @MainActor func normalizer(_ model: AppModel?) -> AnswerNormalizer? {
        model?.languageInfo(language)
            .map { AnswerNormalizer.companion.drill(answerLanguage: $0) }
    }
}

#if DEBUG
private extension NumbersMode {
    /// Run-through hooks, applied wherever a mode is built out of picks: the
    /// overview starts with counting selected, so `-uitest-exercises
    /// numbers,clock,forms,phrases` and `-uitest-modifiers rev,fast,mix` are the
    /// only way to photograph a selection or a modifier. An unknown word is ignored.
    static var uitestExercises: [NumbersExercise]? {
        let known: [String: NumbersExercise] = ["numbers": .counting, "clock": .clock,
                                             "phrases": .phrases, "forms": .forms]
        let picked = uitestWords("uitest-exercises").compactMap { known[$0] }
        return picked.isEmpty ? nil : picked
    }

    static var uitestModifiers: Set<DrillModifier> {
        let known: [String: DrillModifier] = ["rev": .reverse, "fast": .fast, "mix": .mix]
        return Set(uitestWords("uitest-modifiers").compactMap { known[$0] })
    }

    static func uitestWords(_ key: String) -> [String] {
        (UserDefaults.standard.string(forKey: key) ?? "")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
    }
}
#endif

extension NumbersReading {
    /// The ladder a reading is climbed on. Year maps onto Counting because it has no
    /// Sprosse of its own: the standalone years drill was dropped as redundant, and
    /// years live on only as a phrase slot. Fraction is a phrase slot too, and belongs
    /// to Forms — a fraction is one of the number forms.
    var exercise: NumbersExercise {
        switch self {
        case .cardinal, .year: return .counting
        case .clock: return .clock
        case .form, .fraction: return .forms
        }
    }
}
