import SwiftUI
import SprossKern

/// Tiny first-launch sheet: pick the language you already speak (source)
/// and the one you want to learn (target, with its concept count).
/// Coverage-driven: sources are languages with at least one learnable
/// target; targets come from `Catalog.availableTargets` (≥ 50 concepts).
/// Chrome speaks the language being picked: the device language greets first
/// (it seeds the source pick), and every later tap re-renders in that pick —
/// English for a source we have no chrome for yet.
/// Neither side hides the other's pick: choosing it swaps the selections.
struct OnboardingView: View {
    let model: AppModel

    @State private var source: String
    @State private var target: String?
    @State private var starting = false

    init(model: AppModel) {
        self.model = model
        let source = model.defaultSource
        _source = State(initialValue: source)
        _target = State(initialValue: model.catalog?
            .availableTargets(source: source).first?.code)
    }

    private var sources: [String] {
        model.catalog?.coveredSources() ?? []
    }

    private var targets: [AvailableTarget] {
        model.catalog?.availableTargets(source: source) ?? []
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DL.Space.l) {
                header
                sourceSection
                targetSection
                startButton
            }
            .padding(DL.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.dlBackground.ignoresSafeArea())
        .environment(\.locale, AppModel.chromeLocale(source: source))
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DL.Space.xs) {
            Text(verbatim: "👋")
                .font(.system(size: 44))
            Text("onboarding.welcome")
                .font(DL.Fonts.title)
                .foregroundStyle(Color.dlTextPrimary)
            Text("onboarding.subtitle")
                .font(DL.Fonts.subheadline)
                .foregroundStyle(Color.dlTextSecondary)
        }
    }

    private func languageName(_ code: String) -> String {
        LanguageNames.pickerRow(code, catalog: model.catalog)
    }

    /// Tapping the language the other side holds exchanges the two selections.
    private func swapSelections() {
        guard let oldTarget = target else { return }
        target = source
        source = oldTarget
    }

    // MARK: - Which language you already speak

    private var sourceSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("onboarding.source.question")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            ForEach(sources, id: \.self) { candidate in
                selectionRow(title: Text(verbatim: languageName(candidate)),
                             caption: nil,
                             selected: source == candidate) {
                    if candidate == target {
                        swapSelections()
                        return
                    }
                    source = candidate
                    // why: the target list is source-dependent — keep the
                    // pick valid under the new source.
                    if !targets.contains(where: { $0.code == target }) {
                        target = targets.first?.code
                    }
                }
            }
        }
    }

    // MARK: - Which language you want to learn

    /// Learnable targets PLUS the current source — picking it swaps the pair
    /// (its count is the swapped pair's, which is symmetric).
    private struct TargetChoice: Identifiable {
        let code: String
        let conceptCount: Int
        var id: String { code }
    }

    private var targetChoices: [TargetChoice] {
        var choices = targets.map { TargetChoice(code: $0.code, conceptCount: Int($0.conceptCount)) }
        if let target,
           let swapped = model.catalog?.availableTargets(source: target)
               .first(where: { $0.code == source }) {
            choices.append(TargetChoice(code: source, conceptCount: Int(swapped.conceptCount)))
        }
        return choices.sorted { $0.code < $1.code }
    }

    private var targetSection: some View {
        VStack(alignment: .leading, spacing: DL.Space.s) {
            Text("onboarding.target.question")
                .font(DL.Fonts.headline)
                .foregroundStyle(Color.dlTextPrimary)
            ForEach(targetChoices) { candidate in
                selectionRow(title: Text(verbatim: languageName(candidate.code)),
                             caption: Text("onboarding.termsCount \(candidate.conceptCount.formatted())"),
                             selected: target == candidate.code) {
                    if candidate.code == source {
                        swapSelections()
                    } else {
                        target = candidate.code
                    }
                }
            }
        }
    }

    private func selectionRow(title: Text, caption: Text?, selected: Bool,
                              action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: DL.Space.m) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(selected ? Color.dlAccent : Color.dlTextSecondary)
                VStack(alignment: .leading, spacing: 2) {
                    title
                        .font(DL.Fonts.headline)
                        .foregroundStyle(Color.dlTextPrimary)
                    if let caption {
                        caption
                            .font(DL.Fonts.caption)
                            .foregroundStyle(Color.dlTextSecondary)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(DL.Space.m)
            .background(selectionBackground(selected))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func selectionBackground(_ selected: Bool) -> some View {
        RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
            .fill(selected ? Color.dlSurfaceTint : Color.dlSurface)
            .overlay(
                RoundedRectangle(cornerRadius: DL.Radius.tile, style: .continuous)
                    .strokeBorder(selected ? Color.dlAccent : Color.dlSeparator,
                                  lineWidth: selected ? 2 : 1)
            )
    }

    private var startButton: some View {
        Button {
            guard !starting, let target else { return }
            starting = true
            Task {
                await model.completeOnboarding(source: source, target: target)
            }
        } label: {
            Group {
                if starting {
                    ProgressView().tint(.white)
                } else {
                    Text("onboarding.start")
                }
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(DLPrimaryButtonStyle())
        .disabled(target == nil)
        .padding(.top, DL.Space.l)
    }
}

#Preview {
    OnboardingView(model: AppModel())
}
