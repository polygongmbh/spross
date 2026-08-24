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
        VStack(alignment: .leading, spacing: DL.Space.l) {
            heading("numbers.reference")
            NumberReferenceTable(language: language, voice: numberVoice)
        }
    }

    /// Every reading on the page, said on request. The readings are generated
    /// and no recording carries them, so what answers is the live voice — and
    /// where the language has none, `pronounceAction` hands back nil and the
    /// table offers nothing to tap for.
    var numberVoice: DLVoice {
        DLVoice(pronounce: { model.pronounceAction(for: $0, lang: language) },
                isPlaying: { model.isPronouncing($0, lang: language) })
    }

    // MARK: - What to watch out for

    /// Two to four authored lines per language, straight from the catalog
    /// (`phrases/<lang>.json`), picked for the reader — kern falls back to
    /// English where their own language carries no wording.
    @ViewBuilder
    var notesSection: some View {
        let lines = model.catalog?.numberNotes(language: language, reader: model.sourceLanguage) ?? []
        if !lines.isEmpty {
            VStack(alignment: .leading, spacing: DL.Space.l) {
                heading("numbers.notes")
                VStack(alignment: .leading, spacing: DL.Space.m) {
                    ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                        HStack(alignment: .firstTextBaseline, spacing: DL.Space.s) {
                            Text(verbatim: "·")
                                .foregroundStyle(Color.dlTextSecondary)
                                .accessibilityHidden(true)
                            Text(verbatim: line)
                                .font(DL.Fonts.subheadline)
                                .foregroundStyle(Color.dlTextPrimary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(DL.Space.l)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                        .fill(Color.dlSurface)
                )
            }
        }
    }
}
