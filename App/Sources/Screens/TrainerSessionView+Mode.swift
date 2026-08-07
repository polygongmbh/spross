import SwiftUI
import SprossKern

/// What a run drills, what it draws, and the identities it is filed under. Its
/// own file because it is the run SPEC, not the view's state: the hub builds one
/// and hands it over, and the drill never edits it.
extension TrainerSessionView {

    /// A run: the variants it may ask, the language it is answered in, the frames
    /// a sentence is composed from, and how it is played. Several variants already
    /// interleave — a draw picks one per task — which is why `Mix` is a modifier
    /// about direction and magnitude rather than about variety.
    struct Mode {
        /// Never empty: a run with nothing to ask is not a run.
        let variants: [DrillVariant]
        /// The language answers are typed in — the one being learned.
        let language: String
        /// The prompt side of a sentence; nil where Phrases is not on offer.
        let phraseSource: String?
        /// The frames Phrases draws from — carried, not looked up, so a run
        /// samples from the set it was opened with.
        let templates: [PhraseTemplate]
        let modifiers: Set<DrillModifier>

        init(variants: [DrillVariant], language: String, phraseSource: String? = nil,
             templates: [PhraseTemplate] = [], modifiers: Set<DrillModifier> = []) {
            // why: Phrases without frames would draw from an empty list — the
            // selection drops it rather than letting a task crash on the draw.
            let offered = variants.filter { $0 != .phrases || !templates.isEmpty }
            self.variants = offered.isEmpty ? [.numbers] : offered
            self.language = language
            self.phraseSource = phraseSource
            self.templates = templates
            self.modifiers = modifiers
        }

        /// One slot variant, played plain — the hub's entry until the overview lands.
        static func slots(_ kind: TrainerKind, _ language: String) -> Mode {
            Mode(variants: [kind.drillVariant], language: language)
        }

        static func phrases(source: String, target: String, templates: [PhraseTemplate]) -> Mode {
            Mode(variants: [.phrases], language: target,
                 phraseSource: source, templates: templates)
        }

        /// One clean win per rung instead of two.
        var isFast: Bool { modifiers.contains(.fast) }

        /// Catalog key for the run title — the variant's own where the run asks
        /// one thing, and the trainer's own name where it asks several.
        var titleKey: LocalizedStringKey {
            variants.count == 1 ? variants[0].trainerTitleKey : "trainer.title"
        }

        /// Identity a streak record is kept under (`TrainerRecords`): the whole
        /// selection, because a run that interleaves numbers and clock times is a
        /// different feat from either alone. A single-variant key is byte-identical
        /// to the one that shipped, so no standing record is lost.
        var recordKey: String {
            "\(variants.map(\.storageTag).joined(separator: "+")).\(recordLanguage)"
        }

        /// Identity a rung is kept under (`TrainerProgress`), per variant.
        /// Deliberately NOT `recordKey`: a record belongs to a selection, a rung to
        /// one variant, which is what lets the unlock ladder ask one language for
        /// every variant's rung at once.
        func progressKey(_ variant: DrillVariant) -> String {
            "\(variant.storageTag).\(language)"
        }

        /// A sentence record is kept per PAIR — the same frames typed in Swahili
        /// are not the feat they are typed in German — everything else per language.
        private var recordLanguage: String {
            guard let phraseSource else { return language }
            return "\(phraseSource)-\(language)"
        }
    }

    /// A drawn task and the variant that offered it. Carried rather than derived:
    /// a phrase task's own `kind` names the slot generator behind the sentence, not
    /// the variant the run picked, so the two can never be read back off the task.
    struct DrawnTask {
        let variant: DrillVariant
        let task: TrainerTask
    }

    // MARK: - Drawing a task

    /// One fresh random task from the selection, each variant at its own rung; a
    /// prompt never repeats back-to-back (resample once when it equals the previous).
    static func sampleTask(mode: Mode, levels: [DrillVariant: Int],
                           avoiding previousPrompt: String?) -> DrawnTask {
        var drawn = sampleOnce(mode: mode, levels: levels)
        if drawn.task.prompt == previousPrompt {
            drawn = sampleOnce(mode: mode, levels: levels)
        }
        return drawn
    }

    private static func sampleOnce(mode: Mode, levels: [DrillVariant: Int]) -> DrawnTask {
        let rng = KotlinRandom.companion
        let variant = mode.variants.randomElement() ?? .numbers
        let level = Int32(levels[variant] ?? 1)
        guard let kind = variant.slotKind else {
            // why: non-empty by construction (Mode.init drops a frameless Phrases).
            let template = mode.templates[Int.random(in: 0..<mode.templates.count)]
            // Leveled slot values — same ramp semantics as the plain drills;
            // Kern clamps the level to each frame's own slot kind.
            return DrawnTask(variant: variant,
                             task: PhraseSlots.shared.sample(template: template, level: level, rng: rng))
        }
        // Mix's second half: a form takes its magnitude from the numbers rung the
        // run is standing on, so a topped-out climb reads "−4 072 918", not "−7".
        if kind == .forms, mode.mixesForms {
            return DrawnTask(variant: variant,
                             task: Trainer.shared.sampleForms(language: mode.language, level: level,
                                                              magnitudeDigits: Int32(levels[.numbers] ?? 1),
                                                              rng: rng))
        }
        return DrawnTask(variant: variant,
                         task: Trainer.shared.sample(kind: kind, language: mode.language,
                                                     level: level, rng: rng))
    }

    /// Ramp ceiling of one variant: kern's per-kind ceiling, and for sentences the
    /// highest ceiling among the frames the run happens to carry.
    func maxLevel(_ variant: DrillVariant) -> Int {
        guard let kind = variant.slotKind else {
            return mode.templates
                .map { Int(Trainer.shared.maxLevel(kind: $0.slotKind)) }
                .max() ?? 1
        }
        return Int(Trainer.shared.maxLevel(kind: kind))
    }
}

private extension TrainerSessionView.Mode {
    /// Mix widens Forms out of the Numbers rung — which only means something when
    /// the run is climbing one. Without Numbers selected, Forms keeps its own ladder.
    var mixesForms: Bool { modifiers.contains(.mix) && variants.contains(.numbers) }
}

// MARK: - Variant ↔ slot kind

extension DrillVariant {
    /// The generator behind the variant — nil for Phrases, whose slot kind is named
    /// by each FRAME rather than by the variant, and differs between them.
    var slotKind: TrainerKind? {
        switch self {
        case .numbers: return .numbers
        case .clock: return .clock
        case .forms: return .forms
        case .phrases: return nil
        }
    }

    /// The word a record or a rung is filed under in UserDefaults. Kotlin's own
    /// spelling for the slot variants and the lowercase word for Phrases, because
    /// those exact strings are already stored and a tidier scheme would silently
    /// reset every rung a learner has climbed.
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
    /// redundant, and years live on only as a phrase slot.
    var drillVariant: DrillVariant {
        switch self {
        case .numbers, .years: return .numbers
        case .clock: return .clock
        case .forms: return .forms
        }
    }
}
