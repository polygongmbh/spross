import SwiftUI
import SprossKern

/// The north star screen: one glance = what to do right now.
struct HeuteView: View {
    let model: AppModel
    var openBox: () -> Void = {}

    @Environment(\.locale) private var locale

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DL.Space.xl) {
                header
                if let message = model.loadErrorMessage {
                    stateCard(emoji: "🫤",
                              title: "Ups",
                              message: Text(message))
                } else if model.sessionAvailable {
                    sessionCard
                } else if (model.stats?.activeCards ?? 0) > 0 {
                    doneCard
                } else {
                    stateCard(emoji: "📦",
                              title: "Deine Box ist noch leer",
                              message: Text("Pack einen Bereich direkt hinein oder stell ein, wie viele Karten du gleichzeitig lernst."),
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
                Date.FormatStyle(locale: locale)
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
            Text("Deine Sitzung ist startklar")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            sessionSummary
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            Button {
                model.startSession()
            } label: {
                DLActionLabel(key: "Los geht's!", targetLocale: model.targetChromeLocale)
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
        max(model.todayPlan?.reviews.count ?? 0, model.dueNowCount)
    }

    /// Ring + flame hero; either hides when it has nothing to say
    /// (all-new-card plan → no ring, streak 0 → no flame).
    @ViewBuilder
    private var sessionStats: some View {
        let streak = model.stats?.streakDays ?? 0
        if dueRemaining == 0 && streak == 0 {
            Text(verbatim: "✨")
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

    /// Built as `Text` (not a joined String) so each part localizes via the
    /// environment locale with catalog plural handling.
    private var sessionSummary: Text {
        let plan = model.todayPlan
        let due = dueRemaining
        let fresh = (plan?.unlockedPhrases.count ?? 0) + (plan?.newCards.count ?? 0)
        var parts: [Text] = []
        if due > 0 {
            parts.append(Text("\(due) Wiederholungen"))
        }
        if fresh > 0 {
            parts.append(Text("\(fresh) neue Karten"))
        }
        return parts.joined() ?? Text("Ein paar Karten warten auf dich.")
    }

    // MARK: - Done for today

    private var doneCard: some View {
        VStack(spacing: DL.Space.l) {
            Text(verbatim: "🎉")
                .font(.system(size: 56))
                .accessibilityHidden(true)
            Text("Für heute geschafft")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            StreakFlameView(days: model.stats?.streakDays ?? 0)
            tomorrowText
                .font(DL.Fonts.body)
                .foregroundStyle(Color.dlTextSecondary)
                .multilineTextAlignment(.center)
            // User agency: an extra round is endless-style when that has
            // content (due + NEW vocab within budget/health gate), else
            // review-ahead — so it renders in every done state with active
            // cards; hidden only when even the fallback is empty.
            if model.canPracticeExtra {
                Button("Extra-Runde üben") {
                    model.startExtraSession()
                }
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

    private var tomorrowText: Text {
        model.tomorrowDueCount == 0
            ? Text("Morgen gibt es frische Karten. Bis dann! 👋")
            : Text("Morgen warten \(model.tomorrowDueCount) Karten auf dich.")
    }

    // MARK: - Generic state card (error / empty box)

    private func stateCard(emoji: String, title: LocalizedStringKey, message: Text,
                           action: (label: LocalizedStringKey, run: () -> Void)? = nil) -> some View {
        VStack(spacing: DL.Space.l) {
            Text(emoji)
                .font(.system(size: 56))
                .accessibilityHidden(true)
            Text(title)
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
                .multilineTextAlignment(.center)
            message
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
