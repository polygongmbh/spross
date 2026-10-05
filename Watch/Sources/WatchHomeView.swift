import SwiftUI

/// The one watch screen: the due count + Start, or the all-done state. The single
/// graded multiple-choice session lives in a sheet (full screen on watchOS).
/// Chrome follows the snapshot's chrome language (`GlanceChrome`), sheet included.
struct WatchHomeView: View {
    @Bindable var model: WatchModel

    var body: some View {
        // why: the counts read the timeline's clock — a card that comes due
        // overnight, with no phone sync, flips the screen to Start on its own.
        TimelineView(.explicit(model.countChanges(after: .now))) { context in
            if model.snapshot == nil {
                waitingForPhone
            } else if model.canStart(at: context.date) {
                dueState(now: context.date)
            } else {
                restState(now: context.date)
            }
        }
        .sheet(isPresented: $model.sessionPresented) {
            WatchQuizView(model: model)
                .environment(\.locale, chromeLocale)
        }
        // Small version tag reserving its own strip at the bottom, so the
        // centered content never overlaps it.
        .safeAreaInset(edge: .bottom) {
            if !appVersion.isEmpty {
                // why: no opacity — at 60 % the tag drops to ~4:1 on black;
                // caption2 already makes it read as secondary.
                Text(verbatim: "v\(appVersion)")
                    .font(.system(.caption2, design: .rounded))
                    .foregroundStyle(WatchTheme.colors.textSecondary)
            }
        }
        .environment(\.locale, chromeLocale)
    }

    private var chromeLocale: Locale { GlanceChrome.locale(model.snapshot?.chromeLanguage) }

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    private func dueState(now: Date) -> some View {
        VStack(spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 5) {
                Text("\(model.roundCount(at: now))")
                    .font(.system(.largeTitle, design: .rounded, weight: .bold))
                    .foregroundStyle(WatchTheme.colors.accent)
                Text("watch.due", tableName: GlanceChrome.table)
                    .font(.system(.headline, design: .rounded))
                    .foregroundStyle(WatchTheme.colors.textSecondary)
            }
            Button { model.startSession() } label: {
                Text("watch.start", tableName: GlanceChrome.table)
                    .font(.system(.headline, design: .rounded, weight: .bold))
                    .foregroundStyle(.black)
            }
            .buttonStyle(.borderedProminent)
            .tint(WatchTheme.colors.accent)
            .padding(.top, 6)
        }
    }

    /// Too little due or missed for a round — offer free practice, which opens
    /// with it and then recycles the whole snapshot.
    private func restState(now: Date) -> some View {
        VStack(spacing: 8) {
            Text("watch.allDone", tableName: GlanceChrome.table)
                .font(.system(.title3, design: .rounded, weight: .bold))
            // A day with nothing waiting says so by staying silent — a line saying
            // tomorrow is free tells the reader nothing they could act on.
            let tomorrow = model.tomorrowDueCount(at: now)
            if tomorrow > 0 {
                Text("watch.tomorrow \(tomorrow)", tableName: GlanceChrome.table)
                    .font(.system(.footnote, design: .rounded))
                    .foregroundStyle(WatchTheme.colors.textSecondary)
            }
            if model.canPractice {
                Button { model.startPractice() } label: {
                    Text("watch.practice", tableName: GlanceChrome.table)
                        .font(.system(.headline, design: .rounded, weight: .bold))
                        .foregroundStyle(.black)
                }
                .buttonStyle(.borderedProminent)
                .tint(WatchTheme.colors.accent)
                .padding(.top, 6)
            }
        }
        .multilineTextAlignment(.center)
    }

    private var waitingForPhone: some View {
        VStack(spacing: 8) {
            Text("📲")
                .font(.system(size: 36))
            Text("watch.awaiting", tableName: GlanceChrome.table)
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(WatchTheme.colors.textSecondary)
                .multilineTextAlignment(.center)
        }
    }
}
