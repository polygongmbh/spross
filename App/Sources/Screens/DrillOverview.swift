import SwiftUI
import SprossKern

/// A typed drill's hub entry: the material as the two languages name it, and
/// the place its drill is started from — the Countries page and the Dates page
/// are this one page with two faces.
///
/// Two sections, in the order every overview uses — start first, reading after:
/// the Sprossen a run climbs and the button that opens it, then the table
/// itself, both sides of every name beside each other.
///
/// Named in TWO languages: a country's name is a pair rather than a property of
/// the language being learned, and a date is spelled out by one side while the
/// other lends its digits — so both drills exist only where the catalog carries
/// the material on BOTH sides.
///
/// Nothing on this page is earned. The drills are ungated — every run opens at
/// Sprosse 1 and climbs by itself — so the Sprosse rows say what a Sprosse ASKS and
/// never carry a padlock. The one thing that persists is the highest Sprosse
/// reached, which this page reads and never writes.
///
/// The Sprossen and the toggles live in DrillOverview+Practice.swift, the table
/// in the face's own reference view; split purely for file size.
struct DrillOverview<Face: DrillFace>: View {
    let model: AppModel
    /// The language the learner KNOWS — one half of every row, and the prompt
    /// side of a forward run.
    let source: String
    /// The language being learned — the other half, and what a forward run
    /// answers in.
    let target: String

    @Environment(\.dismiss) private var dismiss
    // why: internal, not private — the practice section reads the reader's locale.
    @Environment(\.locale) var locale

    /// The joined material. Held rather than recomputed per row: the join walks
    /// the whole manifest, and the Sprossen and the table read the very same
    /// content the run grades against.
    // why: internal, not private — the practice section renders from it.
    @State var content: Face.Content?
    /// Which way round a run asks. Offered from the first run: nothing here is
    /// bought, so there is no ladder for it to sit behind.
    @State var reverse = false
    /// Whether a Sprosse falls on one clean win instead of three. Unlike reverse
    /// this one IS earned — the top Sprosse has to have been stood on once — so it
    /// stays off, and out of reach, until `bestSprosse` says otherwise.
    @State var fast = false
    /// The furthest Sprosse any run has reached — read on every appearance, since a
    /// closing run books its own.
    @State var bestSprosse = 0
    @State private var launch: Launch?
    /// What the run that just closed came to — one tile above the Sprossen, the
    /// shape every overview uses.
    @State private var lastRun: DrillRunResult?

    /// The run the start button opens, wrapped so ONE `fullScreenCover(item:)`
    /// carries it — the shape every overview uses.
    private struct Launch: Identifiable {
        let reverse: Bool
        let fast: Bool
        let id = UUID()
    }

    /// Where the Sprosse and the record are kept — one key per PAIR, because the
    /// material is a pair's and not a language's.
    var storageKey: String { "\(Face.key).\(source)-\(target)" }

    var body: some View {
        NavigationStack {
            ScrollViewReader { scroll in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: Theme.spacing.xl) {
                        if let lastRun {
                            DrillResultTile(result: lastRun)
                                .id(DrillAnchor.result)
                        }
                        practiceSection
                        referenceSection
                    }
                    .padding(Theme.spacing.xl)
                }
                // why: the other overviews' rule — a tile inserted above the
                // content keeps the offset, so the page comes up to meet it.
                .onChange(of: lastRun) { _, run in
                    guard run != nil else { return }
                    withAnimation(.easeOut(duration: 0.25)) {
                        scroll.scrollTo(DrillAnchor.result, anchor: .top)
                    }
                }
                #if DEBUG
                // why: after the first layout — a scrollTo issued while the
                // LazyVStack is still building has nothing to scroll to.
                .task {
                    guard Self.uitestWantsModifiers else { return }
                    try? await Task.sleep(for: .milliseconds(400))
                    scroll.scrollTo(DrillAnchor.modifiers, anchor: .center)
                }
                #endif
            }
            #if DEBUG
            .defaultScrollAnchor(Self.uitestAnchor)
            #endif
            .background(Theme.colors.background.ignoresSafeArea())
            .navigationTitle(Text(Face.title(languageName)))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: { Image(systemName: "xmark") }
                        .accessibilityLabel(Text("common.close"))
                }
                // why: the app's corners — the way out left, the way in right,
                // still in reach from inside the table.
                ToolbarItem(placement: .topBarTrailing) {
                    Button("trainer.overview.start") { start() }
                        .disabled(content == nil)
                }
            }
        }
        .tint(Theme.colors.accent)
        .onAppear { reload() }
        // why: a closing run books the Sprosse it reached, so the line under the
        // ladder is stale the moment the cover comes down.
        .fullScreenCover(item: $launch, onDismiss: reload) { launch in
            Group {
                if let content {
                    DrillRunView<Face>(model: model, content: content, reverse: launch.reverse,
                                       fast: launch.fast, storageKey: storageKey,
                                       onFinish: { result in
                                           withAnimation(.easeOut(duration: 0.25)) { lastRun = result }
                                       })
                }
            }
            .environment(\.locale, model.knownLocale)
        }
        // why: the record is what the confetti is for, and it rains over the
        // page the run came back to — a wave retires itself, so no dismissal
        // and no state to clear.
        .overlay {
            if lastRun?.newRecord == true { ConfettiView().ignoresSafeArea().allowsHitTesting(false) }
        }
    }

    /// The reading matter: the drill's own table, which is the one thing here
    /// that is genuinely a different page per drill.
    @ViewBuilder
    private var referenceSection: some View {
        if let content {
            Face.reference(model: model, content: content, source: source, target: target)
        }
    }

    // MARK: - Starting a run

    func start() {
        launch = Launch(reverse: reverse, fast: fast && fastUnlocked)
    }

    /// Whether fast mode may be picked at all — kern's rule on the stored best,
    /// never a Sprosse number written down beside it. The price is the ladder the
    /// switches stand for RIGHT NOW: where reversing shortens the ladder, kern
    /// prices it as such.
    var fastUnlocked: Bool {
        guard let content else { return false }
        return Face.fastUnlocked(best: bestSprosse, content: content, reverse: reverse)
    }

    /// Reads the join and the standing Sprosse at once. Both change under this page
    /// — the catalog when the profile does, the Sprosse when a run closes.
    func reload() {
        content = Face.content(model.catalog, source: source, target: target)
        bestSprosse = TrainerProgress.best(for: storageKey)
        // why: the numbers page's `normalizePicks` rule — a ladder that grew
        // under a stored best puts fast back out of reach, and a toggle must
        // never outlive the price that bought it.
        if !fastUnlocked { fast = false }
        #if DEBUG
        Self.uitestSeedProgress(key: storageKey)
        bestSprosse = max(bestSprosse, TrainerProgress.best(for: storageKey))
        // why: the numbers page's `-uitest-modifiers` hook — a toggle nobody can
        // tap from a script is a surface no screenshot can reach. Fast still
        // answers to its price, so seeding the Sprosse is what opens it.
        let asked = Self.uitestModifiers
        if asked.contains("rev") { reverse = true }
        if asked.contains("fast"), fastUnlocked { fast = true }
        if launch == nil, !DrillUITest.launched.contains(Face.key),
           UserDefaults.standard.bool(forKey: "uitest-run"), content != nil {
            // why: a cover raised while the sheet under it is still animating in
            // is dropped — the tap this stands in for always comes after that.
            // ONCE: a run that reopens itself on every foreground never ends.
            DrillUITest.launched.insert(Face.key)
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(600))
                start()
            }
        }
        #endif
    }

    // MARK: - Chrome

    /// The language being LEARNED — the page is opened to practice it, however
    /// evenly the table shows both sides.
    var languageName: String {
        LanguageNames.display(target, locale: locale, catalog: model.catalog)
    }
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

#if DEBUG
extension DrillOverview {
    /// `-uitest-section table` opens the page at the table instead of at the
    /// top — the other overviews' hook, reused, because the reading sits below
    /// the fold here too and no tap driver is installed.
    static var uitestAnchor: UnitPoint {
        UserDefaults.standard.string(forKey: "uitest-section") == "table" ? .bottom : .top
    }

    /// `-uitest-section modifiers` brings the switches into view. A fraction of
    /// the page would not do it: the table under them can be dozens of rows
    /// long, so where the tile falls depends on how much content the pair has.
    /// Scrolling to the tile's own id lands on it whatever the table's length.
    static var uitestWantsModifiers: Bool {
        UserDefaults.standard.string(forKey: "uitest-section") == "modifiers"
    }

    /// `-uitest-modifiers rev,fast` — the numbers page's hook, worded the same,
    /// because neither switch can be tapped from a launch argument otherwise.
    static var uitestModifiers: Set<String> {
        Set((UserDefaults.standard.string(forKey: "uitest-modifiers") ?? "")
            .split(whereSeparator: { $0 == "," || $0 == " " }).map(String.init))
    }

    /// `-uitest-<drill>-best 7` stands the ladder at a Sprosse a fresh install
    /// has not climbed, which is the only way to photograph fast mode OPEN.
    /// Booked through `TrainerProgress` so it meets exactly the rule a real run
    /// would have written, rather than a second door into the same state.
    static func uitestSeedProgress(key: String) {
        let best = UserDefaults.standard.integer(forKey: Face.uitestBestKey)
        if best > 0 { TrainerProgress.record(best, for: key) }
    }
}

/// One auto-start per drill per launch — see `reload`. Off the page itself
/// because a generic type can hold no stored static.
enum DrillUITest {
    @MainActor static var launched: Set<String> = []
}
#endif
