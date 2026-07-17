import SwiftUI
import DuoKern

/// Settings block at the bottom of the Box tab: language pair, direction,
/// daily new-card budget. All values persist inside `BoxState.config`.
struct BoxSettingsSection: View {
    let model: AppModel

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
            }
            .padding(DL.Space.l)
            .background(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .fill(Color.dlSurface)
            )
            .dlCardShadow()
        }
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

    private var directionRow: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("Richtung")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            Picker("Richtung", selection: directionBinding) {
                Text("DE → \(pair.targetShort)").tag(Direction.deToTarget)
                Text("\(pair.targetShort) → DE").tag(Direction.targetToDe)
            }
            .pickerStyle(.segmented)
            Text(directionBinding.wrappedValue == .deToTarget
                 ? "Erkennen: Du siehst Deutsch und bewertest dich selbst."
                 : "Tippen: Du schreibst das deutsche Wort.")
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

    private var newPerDayBinding: Binding<Int> {
        Binding(
            get: { model.box?.config.newPerDay ?? 5 },
            set: { model.setNewPerDay($0) }
        )
    }
}
