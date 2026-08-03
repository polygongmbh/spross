import SwiftUI
import SprossKern

/// One area, opened from its grove in Heute's forest: what has grown, what is
/// still waiting, and the one control that packs the rest of it in.
///
/// The screen a grove leads to, so it opens on its words rather than on a fold —
/// the learner already said which area they meant by tapping it.
struct AreaView: View {
    let model: AppModel
    let area: String

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: DL.Space.l) {
                header
                ForEach(model.cards(inArea: area)) { card in
                    BoxCardRow(model: model, card: card)
                }
            }
            .padding(DL.Space.xl)
        }
        .background(Color.dlBackground.ignoresSafeArea())
        .toolbarBackground(.hidden, for: .navigationBar)
    }

    private var header: some View {
        let stats = model.areaStats(area)
        return HStack(alignment: .top, spacing: DL.Space.m) {
            AreaChip(emoji: model.areaEmoji(area), name: model.areaTitle(area),
                     progress: stats?.progress ?? .empty,
                     lockedPhrases: stats?.lockedPhrases ?? 0)
            packControl
        }
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
    }

    /// The count moved from the button's face into its label: an icon-only
    /// control keeps the header one line tall, and the bar already shows
    /// how much of the area is still untouched.
    @ViewBuilder
    private var packControl: some View {
        let count = model.enqueueableCount(area: area)
        if count > 0 {
            Button {
                model.enqueueArea(area)
            } label: {
                Image(systemName: "plus")
            }
            .buttonStyle(DLIconButtonStyle())
            .accessibilityLabel(Text("box.enqueue \(count.formatted())"))
        } else {
            Image(systemName: "checkmark.circle.fill")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlSuccess)
                .frame(width: 40, height: 40)
                .accessibilityLabel(Text("box.enqueueDone"))
        }
    }
}
