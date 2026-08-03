import SwiftUI
import SprossKern

/// The box, reached by typing instead of by folding: one query over every word
/// it holds — both languages, headwords and the forms they accept — and over
/// the area headings.
///
/// The two result kinds offer different things, so they act differently. An area
/// is a shelf: choosing it hands the box back the area to unfold and steps
/// aside. A word is itself: it can be heard, and if it is still unpacked it can
/// be packed right here, without taking the whole area along.
struct BoxSearchView: View {
    let model: AppModel
    /// Hand an area back to the box, which unfolds it and scrolls it into reach.
    let reveal: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: BoxSearchResults?
    @State private var writingOwnWord = false
    /// Set once a word has been written; the box is then sent to the area holding it.
    @State private var wrote = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                DLSearchField(placeholder: "box.search.placeholder", text: $query)
                    .padding(.horizontal, DL.Space.xl)
                    .padding(.bottom, DL.Space.l)
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: DL.Space.l) {
                        content
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, DL.Space.xl)
                    .padding(.bottom, DL.Space.xl)
                }
            }
            .padding(.top, DL.Space.l)
            .background(Color.dlBackground.ignoresSafeArea())
            .navigationTitle("box.search.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.done") { dismiss() }
                }
            }
        }
        .tint(.dlAccent)
        // why: searching in the body would re-run on every unrelated redraw —
        // packing a word redraws its row, and the whole box does not need
        // re-scanning for it.
        .onChange(of: query) { _, typed in
            results = model.searchBox(typed)
        }
        // why: the reveal waits for the form to be gone — two sheets closing at
        // once leaves the box scrolling behind one of them.
        .sheet(isPresented: $writingOwnWord, onDismiss: sendToTheNewWord) {
            OwnWordFormView(model: model, query: query) { _ in wrote = true }
        }
    }

    private func sendToTheNewWord() {
        guard wrote else { return }
        reveal(model.ownArea)
        dismiss()
    }

    @ViewBuilder
    private var content: some View {
        if let results, !results.isEmpty {
            if !results.areas.isEmpty {
                heading("box.search.areas")
                ForEach(results.areas, id: \.area) { match in
                    areaRow(match.area)
                }
            }
            if !results.cards.isEmpty {
                heading("box.search.words")
                ForEach(results.cards) { card in
                    BoxCardRow(model: model, card: card, pack: { model.enqueueCard(card.id) })
                }
            }
        } else if results != nil {
            // A box with no answer is where the learner's own words come from:
            // they have just proved the catalog has none for what they need.
            VStack(alignment: .leading, spacing: DL.Space.l) {
                Text("box.search.nothing \(query)")
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextSecondary)
                Button("box.search.writeOwn \(query)") { writingOwnWord = true }
                    .buttonStyle(DLSoftButtonStyle())
            }
            .padding(.top, DL.Space.l)
        } else {
            Text("box.search.hint")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
                .padding(.top, DL.Space.l)
        }
    }

    private func heading(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .textCase(.uppercase)
            .padding(.top, DL.Space.s)
    }

    /// An area hit carries its own progress, so the learner can tell a shelf
    /// they have worked from one they have not before choosing it.
    private func areaRow(_ area: String) -> some View {
        let stats = model.areaStats(area)
        let consolidated = stats?.consolidatedCards ?? 0

        return Button {
            reveal(area)
            dismiss()
        } label: {
            HStack(alignment: .top, spacing: DL.Space.s) {
                AreaChip(emoji: model.areaEmoji(area), name: model.areaTitle(area),
                         consolidated: consolidated,
                         learning: max(0, (stats?.activeCards ?? 0) - consolidated),
                         total: stats?.totalCards ?? 0,
                         lockedPhrases: stats?.lockedPhrases ?? 0)
                Image(systemName: "chevron.right")
                    .font(.caption2)
                    .foregroundStyle(Color.dlTextSecondary)
                    .padding(.top, DL.Space.s)
            }
            .padding(DL.Space.l)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
