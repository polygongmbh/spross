import SwiftUI
import SprossKern

/// Who spoke the bundled pronunciation recordings, under which licence, and —
/// once a group is unfolded — every single file with its page on Commons.
/// The list comes from `Catalog.audioCredits()`, derived from the shipped
/// manifests, so it can neither credit what is not bundled nor miss what is.
/// BY and BY-SA stay separate groups: one notice cannot carry both.
///
/// The same screen carries the app's own legal identity below the credits
/// (`CreditsView+Legal.swift`) — the two obligations are read alike and are
/// looked for in the same place.
struct CreditsView: View {
    let model: AppModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: DL.Space.xl) {
                    legalSection
                    ForEach(sections) { section in
                        languageSection(section)
                    }
                }
                .padding(DL.Space.xl)
            }
            .background(Color.dlBackground.ignoresSafeArea())
            .navigationTitle("credits.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
        .tint(.dlAccent)
    }

    private func languageSection(_ section: CreditSection) -> some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            Text(verbatim: languageTitle(section.language))
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            ForEach(section.credits) { credit in
                CreditGroupRow(credit: credit)
            }
            footer
        }
    }

    /// The second licence obligation, beside naming the speaker: the files are
    /// the Commons transcodes, and nothing here re-encoded them.
    private var footer: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text("credits.unmodified")
            Text("credits.commonsNote")
        }
        .font(DL.Fonts.caption)
        .foregroundStyle(Color.dlTextSecondary)
        .padding(.horizontal, DL.Space.xs)
    }

    /// "🇺🇦 Ukrainisch" — the flag from `languages.json`, the name in chrome
    /// language, like every other language label outside the pickers.
    private func languageTitle(_ code: String) -> String {
        let name = LanguageNames.display(code, locale: locale, catalog: model.catalog)
        guard let flag = model.languageInfo(code)?.flag else { return name }
        return "\(flag) \(name)"
    }

    /// Kern emits the groups in language-declaration order, so the distinct
    /// languages in first-seen order are the sections.
    private var sections: [CreditSection] {
        var order: [String] = []
        var grouped: [String: [AudioCredit]] = [:]
        for credit in model.catalog?.audioCredits() ?? [] {
            if grouped[credit.language] == nil { order.append(credit.language) }
            grouped[credit.language, default: []].append(credit)
        }
        return order.map { CreditSection(language: $0, credits: grouped[$0] ?? []) }
    }
}

private struct CreditSection: Identifiable {
    let language: String
    let credits: [AudioCredit]

    var id: String { language }
}

// MARK: - One (author, licence) group

/// One speaker under one licence, foldable open to the recordings themselves:
/// a bare count is weaker attribution than the files, and both BY and BY-SA
/// ask for a link to the work where giving one is reasonable.
private struct CreditGroupRow: View {
    let credit: AudioCredit

    @State private var expanded = false

    var body: some View {
        DisclosureGroup(isExpanded: $expanded) {
            VStack(alignment: .leading, spacing: DL.Space.xs) {
                // why: one Commons recording fetched for two slugs ships twice,
                // so neither the label nor the source is a unique identity.
                ForEach(credit.files.indices, id: \.self) { index in
                    fileRow(credit.files[index])
                }
            }
            .padding(.top, DL.Space.s)
        } label: {
            header
        }
        .tint(.dlTextSecondary)
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text(verbatim: credit.author)
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            HStack(spacing: DL.Space.xs) {
                Text("credits.recordings \(credit.files.count)")
                Text(verbatim: "·")
                licence
            }
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
        }
        // why: a button centres a label that wraps, and this one is the
        // fold-open control — "Wikimedia Commons user …" breaks over two lines.
        // The rule does not cross into a label, so it is set inside each.
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The deed itself where the licence has one — public-domain files have none.
    @ViewBuilder
    private var licence: some View {
        if let url = credit.licenceUrl.flatMap(URL.init(string:)) {
            Link(destination: url) {
                Text(verbatim: credit.licence)
                    .foregroundStyle(Color.dlAccent)
            }
        } else {
            Text(verbatim: credit.licence)
        }
    }

    /// The word the recording speaks (a letter's glyph for the alphabet files)
    /// over its Commons filename. The whole row leads to the file's page rather
    /// than the filename alone: a column of tinted filenames reads as a wall of
    /// links, and the row is the easier tap target either way.
    @ViewBuilder
    private func fileRow(_ file: AudioCreditFile) -> some View {
        let row = VStack(alignment: .leading, spacing: 0) {
            Text(verbatim: file.label)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
            Text(verbatim: file.source)
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)

        // Percent-encoded: those names carry spaces and Cyrillic.
        if let encoded = file.source.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
           let url = URL(string: "https://commons.wikimedia.org/wiki/File:\(encoded)") {
            Link(destination: url) { row }
        } else {
            row
        }
    }
}
