import SwiftUI

/// One home for the addresses the app publishes: the feedback mail is also the
/// Impressum's contact line, and both surfaces have to name the same one.
enum Legal {
    static let contactAddress = "feedback@spross.net"
    static let privacyUrl = "https://spross.net/privacy"
}

/// Who publishes the app, and where its privacy policy stands — the two things
/// a German provider (§ 5 DDG) and App Review both ask to be findable in the
/// app itself. Split out of CreditsView purely for file size.
///
/// Laid out the way a German Impressum reads: the company over its address,
/// then one labelled line per registry fact. Nothing here is a link except the
/// two that lead somewhere, so the block stays a block.
extension CreditsView {

    var legalSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            Text("legal.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            imprintCard
        }
    }

    private var imprintCard: some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            VStack(alignment: .leading, spacing: 2) {
                Text("legal.company")
                    .font(DL.Fonts.headline)
                Text("legal.address.value")
                    .font(DL.Fonts.body)
            }
            .foregroundStyle(Color.dlTextPrimary)

            VStack(alignment: .leading, spacing: 2) {
                line("legal.director.label") { Text("legal.director.value") }
                line("legal.register.label") { Text("legal.register.value") }
                line("legal.vat.label") { Text("legal.vat.value") }
                line("legal.contact.label") { contactValue }
            }
            privacyLink
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
    }

    /// "Registergericht: Amtsgericht Coburg, HRB 7580" — label and fact on one
    /// line, which is how the notice is read and half the height of stacking them.
    private func line<Value: View>(_ label: LocalizedStringKey,
                                   @ViewBuilder value: () -> Value) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: DL.Space.xs) {
            Text(label)
                .foregroundStyle(Color.dlTextSecondary)
            value()
                .foregroundStyle(Color.dlTextPrimary)
        }
        .font(DL.Fonts.caption)
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The one fact that is a live address rather than a line of text, so it is
    /// written once (`Legal.contactAddress`) and offered as a mail.
    @ViewBuilder
    private var contactValue: some View {
        if let url = URL(string: "mailto:\(Legal.contactAddress)") {
            Link(Legal.contactAddress, destination: url)
                .foregroundStyle(Color.dlAccent)
        } else {
            Text(verbatim: Legal.contactAddress)
        }
    }

    @ViewBuilder
    private var privacyLink: some View {
        if let url = URL(string: Legal.privacyUrl) {
            Link(destination: url) {
                Label("legal.privacy", systemImage: "hand.raised")
                    .font(DL.Fonts.caption)
                    .foregroundStyle(Color.dlAccent)
            }
        }
    }
}
