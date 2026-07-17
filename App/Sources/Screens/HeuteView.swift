import SwiftUI
import DuoKern

/// The north star screen: one glance = what to do right now.
struct HeuteView: View {
    let model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DL.Space.xl) {
                header
                if let message = model.loadErrorMessage {
                    stateCard(emoji: "🫤",
                              title: "Ups",
                              message: message)
                } else if model.sessionAvailable {
                    sessionCard
                } else if (model.stats?.activeCount ?? 0) > 0 {
                    doneCard
                } else {
                    stateCard(emoji: "📦",
                              title: "Deine Box ist noch leer",
                              message: "Schau im Box-Tab vorbei: Stell „Neue Karten pro Tag“ ein oder pack einen Bereich direkt hinein.")
                }
            }
            .padding(DL.Space.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.dlBackground.ignoresSafeArea())
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text(Date().formatted(
                Date.FormatStyle(locale: Locale(identifier: "de_DE"))
                    .weekday(.wide).day().month(.wide)
            ))
            .font(DL.Fonts.caption)
            .foregroundStyle(Color.dlTextSecondary)
            .textCase(.uppercase)
            Text("Heute")
                .font(DL.Fonts.hero)
                .foregroundStyle(Color.dlTextPrimary)
        }
    }

    // MARK: - Session available

    private var sessionCard: some View {
        VStack(spacing: DL.Space.l) {
            Text("✨")
                .font(.system(size: 56))
                .accessibilityHidden(true)
            Text("Deine Sitzung ist gepackt")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            Text(sessionSummary)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            Button {
                model.startSession()
            } label: {
                Text("Los geht's!")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(DLPrimaryButtonStyle())
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
    }

    private var sessionSummary: String {
        let plan = model.todayPlan
        let due = max(plan.reviews.count, model.dueNowCount)
        let fresh = plan.unlockedPhrases.count + plan.newWords.count
        var parts: [String] = []
        if due > 0 {
            parts.append(due == 1 ? "1 Wiederholung" : "\(due) Wiederholungen")
        }
        if fresh > 0 {
            parts.append(fresh == 1 ? "1 neue Karte" : "\(fresh) neue Karten")
        }
        return parts.isEmpty ? "Ein paar Karten warten auf dich." : parts.joined(separator: " · ")
    }

    // MARK: - Done for today

    private var doneCard: some View {
        VStack(spacing: DL.Space.l) {
            Text("🎉")
                .font(.system(size: 56))
                .accessibilityHidden(true)
            Text("Alles sitzt für heute")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            StreakFlameView(days: model.stats?.streak ?? 0)
            Text(tomorrowText)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
    }

    private var tomorrowText: String {
        switch model.tomorrowDueCount {
        case 0: return "Morgen gibt es frische Karten. Bis dann! 👋"
        case 1: return "Morgen wartet 1 Karte auf dich."
        case let n: return "Morgen warten \(n) Karten auf dich."
        }
    }

    // MARK: - Generic state card (error / empty box)

    private func stateCard(emoji: String, title: String, message: String) -> some View {
        VStack(spacing: DL.Space.l) {
            Text(emoji)
                .font(.system(size: 56))
                .accessibilityHidden(true)
            Text(title)
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            Text(message)
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(DL.Space.xl)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: DL.Radius.card, style: .continuous)
                .fill(Color.dlSurface)
        )
        .dlCardShadow()
    }
}
