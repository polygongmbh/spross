import SwiftUI

/// The one watch screen: the due count + Start, or the all-done state. The single
/// graded multiple-choice session lives in a sheet (full screen on watchOS).
/// Chrome follows the snapshot's chrome language (`GlanceChrome`), sheet included.
struct WatchHomeView: View {
    @Bindable var model: WatchModel

    var body: some View {
        Group {
            if model.snapshot == nil {
                waitingForPhone
            } else if model.dueCount > 0 {
                dueState
            } else {
                restState
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

    private var dueState: some View {
        VStack(spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 5) {
                Text("\(model.dueCount)")
                    .font(.system(.largeTitle, design: .rounded, weight: .bold))
                    .foregroundStyle(WatchTheme.colors.accent)
                Text("watch.due", tableName: GlanceChrome.table)
                    .font(.system(.headline, design: .rounded))
                    .foregroundStyle(WatchTheme.colors.textSecondary)
            }
            if model.canStart {
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
    }

    /// Nothing due — offer free practice, which recycles the whole snapshot.
    private var restState: some View {
        VStack(spacing: 8) {
            Text("watch.allDone", tableName: GlanceChrome.table)
                .font(.system(.title3, design: .rounded, weight: .bold))
            // A day with nothing waiting says so by staying silent — a line saying
            // tomorrow is free tells the reader nothing they could act on.
            if model.tomorrowDueCount > 0 {
                Text("watch.tomorrow \(model.tomorrowDueCount)", tableName: GlanceChrome.table)
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
