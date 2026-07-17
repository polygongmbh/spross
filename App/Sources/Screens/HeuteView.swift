import SwiftUI
import DuoKern
import DuoKernTrainer

/// The north star screen: one glance = what to do right now.
struct HeuteView: View {
    let model: AppModel
    var openBox: () -> Void = {}

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
                              message: "Stell „Neue Karten pro Tag“ ein oder pack einen Bereich direkt hinein.",
                              action: ("Zur Box", openBox))
                }
                TrainerHubView(model: model)
                FortschrittSection(model: model)
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
            sessionStats
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

    /// Due reviews only — new cards are announced in the summary text instead.
    private var dueRemaining: Int {
        max(model.todayPlan.reviews.count, model.dueNowCount)
    }

    /// Ring + flame hero; either hides when it has nothing to say
    /// (all-new-card plan → no ring, streak 0 → no flame).
    @ViewBuilder
    private var sessionStats: some View {
        let streak = model.stats?.streak ?? 0
        if dueRemaining == 0 && streak == 0 {
            Text("✨")
                .font(.system(size: 56))
                .accessibilityHidden(true)
        } else {
            HStack(spacing: DL.Space.l) {
                if dueRemaining > 0 {
                    DueCountRing(remaining: dueRemaining,
                                 total: dueRemaining + model.reviewsDoneToday,
                                 size: 80)
                }
                if streak > 0 {
                    StreakFlameView(days: streak)
                }
            }
        }
    }

    private var sessionSummary: String {
        let plan = model.todayPlan
        let due = dueRemaining
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
            // User agency: another round is always available — soonest-due
            // cards reviewed ahead, plus anything freshly packed into the box.
            Button("Extra-Runde üben") {
                model.startExtraSession()
            }
            .buttonStyle(DLSoftButtonStyle())
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

    private func stateCard(emoji: String, title: String, message: String,
                           action: (label: String, run: () -> Void)? = nil) -> some View {
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
            if let action {
                Button(action.label, action: action.run)
                    .buttonStyle(DLSoftButtonStyle())
            }
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
