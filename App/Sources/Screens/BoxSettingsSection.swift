import SwiftUI
import DuoKern

/// Settings block at the bottom of the Box tab: language pair, which
/// language is being learned (+ mixed-direction toggle), daily new-card
/// budget. All values persist inside `BoxState.config`.
struct BoxSettingsSection: View {
    let model: AppModel

    @State private var confirmingReset = false

    var body: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            Text("Einstellungen")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)

            VStack(alignment: .leading, spacing: DL.Space.l) {
                pairRow
                Divider().overlay(Color.dlSeparator)
                directionRow
                Divider().overlay(Color.dlSeparator)
                newPerDayRow
                Divider().overlay(Color.dlSeparator)
                resetRow
            }
            .padding(DL.Space.l)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
            .dlCardShadow()

            aboutFooter
        }
    }

    // MARK: About / feedback

    private static let feedbackAddress = "lang@polygon.gmbh"

    private var versionText: String {
        // why: the build number is always 1 here — showing "(1)" reads odd;
        // the marketing version alone identifies feedback mails fine.
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        return "DuoLernen v\(version)"
    }

    private var aboutFooter: some View {
        VStack(spacing: DL.Space.s) {
            Text(versionText)
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
                .monospacedDigit()
            if let url = feedbackURL {
                Link(destination: url) {
                    Label("Feedback senden", systemImage: "envelope")
                        .font(DL.Fonts.subheadline)
                        .foregroundStyle(Color.dlAccent)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, DL.Space.m)
    }

    private var feedbackURL: URL? {
        let subject = versionText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "mailto:\(Self.feedbackAddress)?subject=\(subject)")
    }

    private var pair: LanguagePair {
        model.box?.config.pair ?? .deSw
    }

    // MARK: Rows

    private var pairRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("Sprache")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            Picker("Sprache", selection: pairBinding) {
                ForEach(LanguagePair.allCases, id: \.self) { candidate in
                    Text("\(candidate.flag) \(candidate.targetName)").tag(candidate)
                }
            }
            .pickerStyle(.segmented)
            Text("Jede Sprache hat ihre eigene Box.")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    /// "Ich lerne": which language the user is acquiring. `.deToTarget`
    /// = learning the target language, `.targetToDe` = learning German.
    private var directionRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("Ich lerne")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            Picker("Ich lerne", selection: directionBinding) {
                Text(pair.targetName).tag(Direction.deToTarget)
                Text("Deutsch").tag(Direction.targetToDe)
            }
            .pickerStyle(.segmented)
            Toggle(isOn: mixedDirectionsBinding) {
                Text("Beide Richtungen üben")
                    .font(DL.Fonts.headline)
                    .foregroundStyle(Color.dlTextPrimary)
            }
            .tint(Color.dlAccent)
            .padding(.top, DL.Space.s)
            Text("Empfohlen — beide Übersetzungsrichtungen festigen die Vokabel.")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private var newPerDayRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Stepper(value: newPerDayBinding, in: 0...20) {
                HStack {
                    Text("Neue Karten pro Tag")
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlTextPrimary)
                    Spacer()
                    Text("\(newPerDayBinding.wrappedValue)")
                        .font(DL.Fonts.statValue)
                        .foregroundStyle(Color.dlAccent)
                        .monospacedDigit()
                }
            }
            Text("So schnell wächst deine Box — nur solange der Stoff sitzt.")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    /// Fresh start with the CURRENT seed content (early testers' boxes carry
    /// pre-basics ordering; a reset re-bootstraps from today's seed).
    private var resetRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Button(role: .destructive) {
                confirmingReset = true
            } label: {
                Text("Box zurücksetzen …")
                    .font(DL.Fonts.headline)
            }
            .confirmationDialog(
                "Alle Lernfortschritte für \(pair.flag) \(pair.targetName) löschen und neu mit den ersten Wörtern beginnen?",
                isPresented: $confirmingReset,
                titleVisibility: .visible
            ) {
                Button("Zurücksetzen", role: .destructive) {
                    Task { await model.resetBox() }
                }
                Button("Abbrechen", role: .cancel) {}
            }
            Text("Löscht Fortschritt und Verlauf dieser Box — die Inhalte bleiben.")
                .font(DL.Fonts.caption)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    // MARK: Bindings

    private var pairBinding: Binding<LanguagePair> {
        Binding(
            get: { model.box?.config.pair ?? .deSw },
            set: { model.switchPair($0) }
        )
    }

    private var directionBinding: Binding<Direction> {
        Binding(
            get: { model.box?.config.direction ?? .deToTarget },
            set: { model.setDirection($0) }
        )
    }

    private var mixedDirectionsBinding: Binding<Bool> {
        Binding(
            get: { model.box?.config.mixedDirections ?? true },
            set: { model.setMixedDirections($0) }
        )
    }

    private var newPerDayBinding: Binding<Int> {
        Binding(
            get: { model.box?.config.newPerDay ?? 5 },
            set: { model.setNewPerDay($0) }
        )
    }
}
