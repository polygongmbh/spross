import SwiftUI

/// The page a drill is started from, whichever drill it is: the tile a closed run
/// left, the sections the drill fills, and the two corners — the way out on the
/// left, the way in on the right, still in reach from deep inside the reading.
///
/// What every overview owes and none of them decides for itself: a run's result
/// arrives as a tile ABOVE the content, so the page comes up to meet it rather
/// than letting it sit off the top; a record rains confetti over the page the run
/// came back to; and a screenshot pass with no thumb opens the page where it needs
/// to photograph (`DrillUITest`).
///
/// The drill's own state — the material, the ladder, the run it launches — stays
/// on the page that owns it. This is the frame around it.
struct DrillOverviewPage<Sections: View>: View {
    /// The page's title, around the name of the language being learned.
    let title: Text
    /// What the run that just closed came to; nil until one has.
    let lastRun: DrillRunResult?
    /// Whether a run can be started at all — there is nothing to ask until the
    /// material is joined, or until at least one exercise is picked.
    var startable = true
    let start: () -> Void
    /// Where the page opens. `.top` for a learner; a screenshot pass asks for the
    /// section it came to photograph.
    var scrollAnchor: UnitPoint = .top
    /// A tile a screenshot pass needs brought into view — one that no fraction of
    /// the page finds, because what stands above it is as long as the pair's
    /// material (`DrillUITest.focus`).
    var focus: String?
    @ViewBuilder let sections: () -> Sections

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollViewReader { scroll in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: Theme.spacing.xl) {
                        if let lastRun {
                            DrillResultTile(result: lastRun)
                                .id(DrillAnchor.result)
                        }
                        sections()
                    }
                    .padding(Theme.spacing.xl)
                }
                // why: a tile inserted ABOVE the content keeps the scroll offset, so
                // what a run came back with would sit off the top of a page the
                // learner is still looking at. The page comes up to meet it.
                .onChange(of: lastRun) { _, run in
                    guard run != nil else { return }
                    withAnimation(.easeOut(duration: 0.25)) {
                        scroll.scrollTo(DrillAnchor.result, anchor: .top)
                    }
                }
                .task {
                    guard let focus else { return }
                    // why: after the first layout — a scrollTo issued while the
                    // LazyVStack is still building has nothing to scroll to.
                    try? await Task.sleep(for: .milliseconds(400))
                    scroll.scrollTo(focus, anchor: .center)
                }
            }
            .defaultScrollAnchor(scrollAnchor)
            .background(Theme.colors.background.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel(Text("common.close"))
                }
                // why: the right corner repeats the page's own start button on
                // purpose — it is the one that stays reachable once the reading has
                // been scrolled into.
                ToolbarItem(placement: .topBarTrailing) {
                    Button("trainer.overview.start", action: start)
                        .disabled(!startable)
                }
            }
        }
        .tint(Theme.colors.accent)
        // why: the record is what the confetti is for, and it rains over the page
        // the run came back to — a wave retires itself, so no dismissal and no
        // state to clear.
        .overlay {
            if lastRun?.newRecord == true {
                ConfettiView().ignoresSafeArea().allowsHitTesting(false)
            }
        }
    }
}

/// What a `fullScreenCover(item:)` carries: the run a start tap described, under an
/// identity of its own so the same run started twice presents twice. A cover rather
/// than a push — a drill is a full screen with its own close — and ONE of them per
/// page, which is what the wrapper buys.
struct DrillLaunch<Value>: Identifiable {
    let value: Value
    let id = UUID()
}

/// A section's name on an overview, in the one weight every one of them uses.
struct DrillHeading: View {
    let key: LocalizedStringKey

    init(_ key: LocalizedStringKey) { self.key = key }

    var body: some View {
        Text(key)
            .font(Theme.typography.title)
            .foregroundStyle(Theme.colors.textPrimary)
            .accessibilityAddTraits(.isHeader)
    }
}

/// The launch-argument hooks the overviews share. A screenshot pass has no thumb:
/// it opens a page at the section it came to photograph, brings a tile into view,
/// and starts a run — all three off the arguments alone. Inert outside DEBUG.
enum DrillUITest {
    /// `-uitest-section <name>`: where the page opens. Each page names the sections
    /// it has, since the reading on all of them sits below the fold.
    static func anchor(_ sections: [String: UnitPoint]) -> UnitPoint {
        #if DEBUG
        guard let name = UserDefaults.standard.string(forKey: "uitest-section") else { return .top }
        return sections[name] ?? .top
        #else
        return .top
        #endif
    }

    /// `-uitest-section <name>` for a section that is one TILE rather than a stretch
    /// of page: where it falls depends on how much material the pair has, so it is
    /// reached by its own id and not by a fraction of the page.
    static func focus(_ section: String, id: String) -> String? {
        #if DEBUG
        UserDefaults.standard.string(forKey: "uitest-section") == section ? id : nil
        #else
        nil
        #endif
    }

    /// `-uitest-run 1`: the run a screenshot pass has no way to tap into. ONCE per
    /// drill per launch, because this also runs as a cover comes down and a run that
    /// reopens itself never ends.
    @MainActor
    static func autoStart(_ key: String, ready: Bool, start: @escaping () -> Void) {
        #if DEBUG
        guard ready, !launched.contains(key),
              UserDefaults.standard.bool(forKey: "uitest-run") else { return }
        launched.insert(key)
        Task { @MainActor in
            // why: a cover raised while the sheet under it is still animating in is
            // dropped — the tap this stands in for always comes after that.
            try? await Task.sleep(for: .milliseconds(600))
            start()
        }
        #endif
    }

    #if DEBUG
    @MainActor private static var launched: Set<String> = []
    #endif
}
