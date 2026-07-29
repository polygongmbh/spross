import SwiftUI

/// The one watch screen: "N fällig" + Start, or the all-done state. The single
/// graded multiple-choice session lives in a sheet (full screen on watchOS).
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
        }
        // Small version tag reserving its own strip at the bottom, so the
        // centered content never overlaps it.
        .safeAreaInset(edge: .bottom) {
            if !appVersion.isEmpty {
                // why: no opacity — at 60 % the tag drops to ~4:1 on black;
                // caption2 already makes it read as secondary.
                Text("v\(appVersion)")
                    .font(.system(.caption2, design: .rounded))
                    .foregroundStyle(Color.wlTextSecondary)
            }
        }
    }

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    private var dueState: some View {
        VStack(spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 5) {
                Text("\(model.dueCount)")
                    .font(.system(.largeTitle, design: .rounded, weight: .bold))
                    .foregroundStyle(Color.wlAccent)
                Text("fällig")
                    .font(.system(.headline, design: .rounded))
                    .foregroundStyle(Color.wlTextSecondary)
            }
            if model.canStart {
                Button { model.startSession() } label: {
                    Text("Start")
                        .font(.system(.headline, design: .rounded, weight: .bold))
                        .foregroundStyle(.black)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.wlAccent)
                .padding(.top, 6)
            }
        }
    }

    /// Nothing due — offer free practice, which recycles the whole snapshot.
    private var restState: some View {
        VStack(spacing: 8) {
            Text("Alles sitzt 🎉")
                .font(.system(.title3, design: .rounded, weight: .bold))
            // A day with nothing waiting says so by staying silent — "Morgen:
            // frei" was a line that told the reader nothing they could act on.
            if model.tomorrowDueCount > 0 {
                Text("Morgen: \(model.tomorrowDueCount) fällig")
                    .font(.system(.footnote, design: .rounded))
                    .foregroundStyle(Color.wlTextSecondary)
            }
            if model.canPractice {
                Button { model.startPractice() } label: {
                    Text("Üben")
                        .font(.system(.headline, design: .rounded, weight: .bold))
                        .foregroundStyle(.black)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.wlAccent)
                .padding(.top, 6)
            }
        }
        .multilineTextAlignment(.center)
    }

    private var waitingForPhone: some View {
        VStack(spacing: 8) {
            Text("📲")
                .font(.system(size: 36))
            Text("Öffne Spross auf dem iPhone, um zu starten.")
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
                .multilineTextAlignment(.center)
        }
    }
}
