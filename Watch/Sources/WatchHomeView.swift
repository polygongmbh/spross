import SwiftUI

/// The one watch screen: "N fällig" + Start, or the all-done state.
/// The review loop itself lives in a sheet (full screen on watchOS).
struct WatchHomeView: View {
    @Bindable var model: WatchModel

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
    }

    private var dueState: some View {
        VStack(spacing: 8) {
            Text("\(model.dueCount)")
                .font(.system(.largeTitle, design: .rounded, weight: .bold))
                .foregroundStyle(Color.wlAccent)
            Text("fällig")
                .font(.system(.headline, design: .rounded))
                .foregroundStyle(Color.wlTextSecondary)
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
