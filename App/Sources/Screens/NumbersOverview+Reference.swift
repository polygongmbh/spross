import SwiftUI
import SprossKern

/// The reading half of the numbers overview: the generated table, and the
/// handful of prose notes beside it. State lives on NumbersOverview; split out
/// purely for file size.
extension NumbersOverview {

    // MARK: - How this language counts

    /// The generated table, under its heading. The table itself is
    /// `NumberReferenceTable` — the same component the in-run "?" opens, so the
    /// two surfaces cannot show a learner two different pages.
    var referenceSection: some View {
        NumberReferenceTable(language: language, heading: "numbers.reference", voice: numberVoice)
    }

    /// Every reading on the page, said on request. The readings are generated
    /// and no recording carries them, so what answers is the live voice — and
    /// where the language has none, `pronounceAction` hands back nil and the
    /// table offers nothing to tap for.
    var numberVoice: Voice {
        Voice(pronounce: { model.pronounceAction(for: $0, lang: language) },
                isPlaying: { model.isPronouncing($0, lang: language) })
    }

    // MARK: - What to watch out for

    /// The handful of things that trip a learner up counting, straight from the
    /// catalog (`phrases/<lang>.json`).
    var notesSection: some View {
        ReferenceNotes(lines: model.catalog?.numberNotes(language: language,
                                                         reader: model.sourceLanguage) ?? [])
    }
}
