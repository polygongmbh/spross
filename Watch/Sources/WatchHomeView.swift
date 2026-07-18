import SwiftUI

/// The one watch screen: "N fällig" + Start, or the all-done state.
/// The review loop itself lives in a sheet (full screen on watchOS).
struct WatchHomeView: View {
    @Bindable var model: WatchModel

    /// Current practice run — built on tap (or lazily inside the sheet for the
    /// -uitest-practice force-open path).
    @State private var practice: WatchPracticeModel?

    var body: some View {
        Group {
            if model.snapshot == nil {
                waitingForPhone
            } else if model.dueCount > 0 {
                dueState
            } else {
                doneState
            }
        }
        .sheet(isPresented: $model.reviewPresented) {
            WatchReviewView(model: model)
        }
        .sheet(isPresented: $model.practicePresented) {
            if let run = practice ?? model.makePracticeModel() {
                WatchPracticeView(model: run) { model.practicePresented = false }
                    .onAppear { if practice == nil { practice = run } }
            }
        }
        // Small version tag reserving its own strip at the bottom, so the
        // centered content never overlaps it.
        .safeAreaInset(edge: .bottom) {
            if !appVersion.isEmpty {
                Text("v\(appVersion)")
                    .font(.system(.caption2, design: .rounded))
                    .foregroundStyle(Color.wlTextSecondary.opacity(0.6))
            }
        }
    }

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    private func startPractice() {
        practice = model.makePracticeModel()
        model.practicePresented = practice != nil
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
            Button {
                model.startReview()
            } label: {
                Text("Start")
                    .font(.system(.headline, design: .rounded, weight: .bold))
                    .foregroundStyle(.black)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.wlAccent)
            .padding(.top, 6)
            if model.canPractice {
                Button { startPractice() } label: {
                    Text("Üben")
                        .font(.system(.footnote, design: .rounded, weight: .semibold))
                        .foregroundStyle(Color.wlAccent)
                }
                .buttonStyle(.bordered)
                .tint(Color.wlAccent.opacity(0.5))
            }
        }
    }

    private var doneState: some View {
        VStack(spacing: 8) {
            Text("Alles sitzt 🎉")
                .font(.system(.title3, design: .rounded, weight: .bold))
            Text(model.tomorrowDueCount > 0
                 ? "Morgen: \(model.tomorrowDueCount) fällig"
                 : "Morgen: frei")
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
            if model.canPractice {
                Button { startPractice() } label: {
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
            Text("Öffne DuoLernen auf dem iPhone, um zu starten.")
                .font(.system(.footnote, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
                .multilineTextAlignment(.center)
        }
    }
}
