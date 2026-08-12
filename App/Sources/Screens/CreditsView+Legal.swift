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
/// Every field except the company name is a TODO placeholder in the String
/// Catalog: an address, a register entry or a VAT id invented to look plausible
/// would be a false statement of identity, which is worse than an obviously
/// unfinished one. `grep TODO` over the catalog is the pre-submission gate.
extension CreditsView {

    var legalSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.m) {
            Text("legal.title")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            imprintCard
            privacyLink
        }
    }

    private var imprintCard: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            Text("legal.company")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            field("legal.address.label", "legal.address.value")
            field("legal.director.label", "legal.director.value")
            field("legal.court.label", "legal.court.value")
            field("legal.register.label", "legal.register.value")
            field("legal.vat.label", "legal.vat.value")
            contactField
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(DL.Space.l)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                .fill(Color.dlSurface)
        )
    }

    private func field(_ label: LocalizedStringKey, _ value: LocalizedStringKey) -> some View {
        fieldRow(label) {
            Text(value)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextPrimary)
        }
    }

    /// The one field that is a live address rather than a line of text, so it
    /// is written once (`Legal.contactAddress`) and offered as a mail.
    private var contactField: some View {
        fieldRow("legal.contact.label") {
            if let url = URL(string: "mailto:\(Legal.contactAddress)") {
                Link(destination: url) {
                    Text(verbatim: Legal.contactAddress)
                        .font(DL.Fonts.body)
                        .foregroundStyle(Color.dlAccent)
                }
            } else {
                Text(verbatim: Legal.contactAddress)
                    .font(DL.Fonts.body)
                    .foregroundStyle(Color.dlTextPrimary)
            }
        }
    }

    private func fieldRow<Content: View>(_ label: LocalizedStringKey,
                                         @ViewBuilder value: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
            value()
        }
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var privacyLink: some View {
        if let url = URL(string: Legal.privacyUrl) {
            Link(destination: url) {
                Label("legal.privacy", systemImage: "hand.raised")
                    .font(DL.Fonts.subheadline)
                    .foregroundStyle(Color.dlAccent)
            }
            .padding(.horizontal, DL.Space.xs)
        }
    }
}
