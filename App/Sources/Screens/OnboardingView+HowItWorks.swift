import SwiftUI

/// The picker's second page: what a round asks of you, before the first one starts.
///
/// It stands BETWEEN the pick and the box being built, which is the only place it can:
/// activating the profile ends onboarding (`phase → .ready`) and takes the sheet with it,
/// so a page shown afterwards would have nothing to stand on. Reading it also covers the
/// join, so the wait for a first box is spent on something.
///
/// Three lines, in the order the learner will meet them — recall, grade, write — and
/// nothing about scheduling: the app's one job here is that a blank card is not a test
/// you can fail. The session then coaches the same three at the moment each applies
/// (`SessionCoach`), which is why these stay short enough to be read once and left.
extension OnboardingView {

    var howItWorksPage: some View {
        VStack(alignment: .leading, spacing: DL.Space.l) {
            VStack(alignment: .leading, spacing: DL.Space.xs) {
                Text(verbatim: "🌱")
                    .font(.system(size: 44))
                Text("onboarding.howItWorks.title")
                    .font(DL.Fonts.title)
                    .foregroundStyle(Color.dlTextPrimary)
            }
            VStack(alignment: .leading, spacing: DL.Space.m) {
                step("onboarding.howItWorks.recall")
                step("onboarding.howItWorks.grade")
                step("onboarding.howItWorks.write")
            }
            startButton
        }
    }

    private func step(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(DL.Fonts.body)
            .foregroundStyle(Color.dlTextSecondary)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
