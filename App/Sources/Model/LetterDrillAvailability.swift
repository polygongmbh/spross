import Foundation
import SprossKern

/// What the letter drill can ASK on THIS device — the app half of the drill,
/// derived the same way on both platforms (contract §5.1/§5.2).
///
/// Two facts, neither of them content: which alphabet rows can be HEARD at all
/// (a bundled letter recording, or a voice for the language), and which of the
/// words the learner already holds can be dictated. Kern samples from what this
/// reports and never asks whether a device can speak.
///
/// Deliberately NOT cached beyond one read: a voice may be installed in
/// Settings while the app sleeps, so the hub rebuilds this on every foreground
/// rather than deciding once at launch that the drill does not exist.
@MainActor
struct LetterDrillAvailability {

    /// Below this many candidates the resample-once rule degenerates into the
    /// same word all evening — dictation does not exist yet and the ramp stops
    /// one rung short of it. The drill itself still exists.
    private static let dictationFloor = 5

    /// The parsed alphabet, nil where no file was authored for the language.
    let alphabet: Alphabet?
    /// Refs Kern may sample, in file order.
    let promptableRefs: [String]
    /// Consolidated, single-word, audible box cards — the dictation pool, each
    /// carrying the schedule figures Kern weighs the draw by.
    let dictationCandidates: [LetterDrill.DictationCandidate]
    /// Ref → every word this device can say the row's gap from, known words
    /// flagged. Built ONCE per run: it is a catalog sweep, and a per-question
    /// rebuild would re-audit every candidate's audio for a single draw.
    private let gapWords: [String: [LetterDrill.AlphabetExampleWord]]

    var drillAvailable: Bool { alphabet != nil && !promptableRefs.isEmpty }
    var dictationAvailable: Bool { dictationCandidates.count >= Self.dictationFloor }

    /// What Kern is handed for one row — empty for a letter row, which is asked
    /// by its spoken name and gaps nothing.
    func examples(_ entry: AlphabetEntry) -> [LetterDrill.AlphabetExampleWord] {
        gapWords[entry.ref] ?? []
    }

    init(model: AppModel, language: String) {
        guard let catalog = model.catalog, let alphabet = catalog.alphabet(lang: language) else {
            self.alphabet = nil
            promptableRefs = []
            dictationCandidates = []
            gapWords = [:]
            return
        }
        self.alphabet = alphabet
        let voice = Pronouncer.shared.canSpeak(language: language)
        let consolidated = model.consolidatedCards()
        // why: Card.id IS the concept slug, so holding a word is a set lookup.
        let known = Set(consolidated.map(\.id))
        let words = Dictionary(uniqueKeysWithValues: alphabet.entries
            .filter { $0.kind != .letter && $0.kind != .rule }
            .map { entry in
                (entry.ref, Self.exampleWords(entry, language: language, known: known,
                                              model: model, catalog: catalog))
            })
        gapWords = words
        promptableRefs = alphabet.entries
            .filter { Self.promptable($0, language: language, voice: voice,
                                      words: words[$0.ref] ?? [], catalog: catalog) }
            .map(\.ref)
        dictationCandidates = consolidated
            // why: a transcription task is ONE word — a phrase card would ask
            // the learner to type a sentence from a single hearing.
            .filter { !$0.target.text.contains(" ") }
            .filter { Self.audible($0.target.text, language: $0.target.lang, model: model, catalog: catalog) }
            .map { card in
                let memory = model.scheduling(for: card.id)
                return LetterDrill.DictationCandidate(
                    card: card,
                    difficulty: memory?.memory?.difficulty ?? 0,
                    lapses: memory?.lapses ?? 0
                )
            }
    }

    /// Every word Kern may gap for an entry, WITH its provenance: a slug only
    /// where the target language realizes the concept itself, so an
    /// `exampleText` escape hatch can never claim that concept's recording.
    /// One definition, because availability and sampling must agree on it.
    ///
    /// Kern's sweep decides WHICH words qualify; this decides which of them the
    /// device can actually say, and which the learner already holds.
    static func exampleWords(_ entry: AlphabetEntry, language: String, known: Set<String>,
                             model: AppModel, catalog: Catalog) -> [LetterDrill.AlphabetExampleWord] {
        let swept = catalog.alphabetExamples(entry: entry, lang: language)
            .filter { audible($0.text, language: language, model: model, catalog: catalog) }
            .map {
                LetterDrill.AlphabetExampleWord(text: $0.text, slug: $0.slug,
                                                known: known.contains($0.slug))
            }
        guard swept.isEmpty else { return swept }
        return entry.exampleText
            .map { [LetterDrill.AlphabetExampleWord(text: $0, slug: nil, known: false)] } ?? []
    }

    /// A letter is asked by its NAME (a bundled recording, or the voice), a
    /// digraph by a gap WORD, of which at least one must have survived above.
    ///
    /// The one predicate this cannot repeat is whether the glyph sits in that
    /// word exactly once — `gapWord` is internal to Kern's catalog package.
    /// Lint pins it on shipped content and Kern filters the pool on the same
    /// rule, so a gap that cannot be cut costs a pool entry, never a question.
    private static func promptable(_ entry: AlphabetEntry, language: String, voice: Bool,
                                   words: [LetterDrill.AlphabetExampleWord], catalog: Catalog) -> Bool {
        guard entry.drill, entry.kind != .rule else { return false }
        if entry.kind == .letter {
            // why: the NAME is what is spoken — a row without one cannot be
            // asked even where its recording exists, and Kern drops it too.
            guard entry.name != nil else { return false }
            return catalog.letterRecordingPath(lang: language,
                                               glyph: entry.glyph.lowercased()) != nil || voice
        }
        return !words.isEmpty
    }

    /// Whether a form can be heard at all: a recording that speaks THIS very
    /// form, or a voice for the language.
    private static func audible(_ form: String, language: String,
                                model: AppModel, catalog: Catalog) -> Bool {
        let pronunciation = catalog.pronunciation(lang: language, visibleForm: form)
        return Pronouncer.shared.canPronounce(pronunciation,
                                              recordingURL: model.audioURL(pronunciation.recordingPath))
    }
}
