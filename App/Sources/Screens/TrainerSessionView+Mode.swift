import SwiftUI
import SprossKern

/// What a run drills is kern's `TrainerMode` — the run SPEC, never edited once
/// the run is open, and the one place the draw, the ramp ceilings and the two
/// storage identities are decided.
///
/// What stays on this side is only what kern will not carry: the spelling the
/// overview builds a mode with (run-through hooks folded in), the chrome names,
/// and the grader a run is played with.
extension TrainerSessionView {

    /// The spec, under the name the surfaces that open a run already use.
    typealias Mode = TrainerMode
}

extension TrainerMode {

    /// A selection as the overview picks it. ONE place builds a mode out of
    /// picks, so `-uitest-variants` / `-uitest-modifiers` reach every run that
    /// is ever started; kern drops a frameless Phrases itself.
    convenience init(variants: [DrillVariant], language: String, phraseSource: String? = nil,
                     templates: [PhraseTemplate] = [], modifiers: Set<DrillModifier> = []) {
        #if DEBUG
        let asked = TrainerMode.uitestVariants ?? variants
        let played = modifiers.union(TrainerMode.uitestModifiers)
        #else
        let asked = variants
        let played = modifiers
        #endif
        self.init(selection: asked, language: language, phraseSource: phraseSource,
                  templates: templates, modifiers: played)
    }

    /// One slot variant, played plain.
    static func slots(_ kind: TrainerKind, _ language: String) -> TrainerMode {
        TrainerMode(variants: [kind.drillVariant], language: language)
    }

    static func phrases(source: String, target: String, templates: [PhraseTemplate]) -> TrainerMode {
        TrainerMode(variants: [.phrases], language: target,
                    phraseSource: source, templates: templates)
    }

    /// Catalog key for the run title — the variant's own where the run asks one
    /// thing, and the trainer's own name where it asks several. Chrome, so it
    /// lives here: kern names the rule, never the rendering.
    var titleKey: LocalizedStringKey {
        variants.count == 1 ? variants[0].trainerTitleKey : "trainer.hub.title"
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
private extension TrainerMode {
    /// Run-through hooks, applied wherever a mode is built out of picks: the
    /// overview starts with counting selected, so `-uitest-variants
    /// numbers,clock,forms,phrases` and `-uitest-modifiers rev,fast,mix` are the
    /// only way to photograph a selection or a modifier. An unknown word is ignored.
    static var uitestVariants: [DrillVariant]? {
        let known: [String: DrillVariant] = ["numbers": .numbers, "clock": .clock,
                                             "phrases": .phrases, "forms": .forms]
        let picked = uitestWords("uitest-variants").compactMap { known[$0] }
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

// MARK: - Variant ↔ storage

extension DrillVariant {
    /// The word a rung is filed under in UserDefaults. Kotlin's own spelling for
    /// the slot variants and the lowercase word for Phrases, matching
    /// `TrainerMode.progressKey` — those exact strings are already stored.
    var storageTag: String {
        switch self {
        case .numbers, .clock, .forms: return name
        case .phrases: return "phrases"
        }
    }
}

extension TrainerKind {
    /// The ladder variant a slot kind belongs to. Years maps onto Numbers because
    /// it has no rung of its own: the standalone years drill was dropped as
    /// redundant, and years live on only as a phrase slot. Fraction is a phrase slot
    /// too, and belongs to Forms — a fraction is one of the number forms.
    var drillVariant: DrillVariant {
        switch self {
        case .numbers, .years: return .numbers
        case .clock: return .clock
        case .forms, .fraction: return .forms
        }
    }
}
