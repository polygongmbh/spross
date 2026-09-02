import SwiftUI
import SprossKern

/// The "Länder" hub entry: the world as the two languages name it, and the place
/// its drill is started from.
///
/// Two sections, in the order the other two overviews use — start first, reading
/// after: the Sprossen a run climbs and the button that opens it, then the atlas
/// itself, both sides of every country beside each other.
///
/// The one surface here that is named in TWO languages: a country's name is a
/// pair, not a property of the language being learned, and the reference table
/// is the join the drill grades against (`CountryDrill.reference`) rather than a
/// second table beside it.
///
/// Nothing on this page is earned. The drill is ungated — every run opens at
/// Sprosse 1 and climbs by itself — so the Sprosse rows say what a Sprosse ASKS and never
/// carry a padlock. The one thing that persists is the highest Sprosse reached,
/// which this page reads and never writes.
///
/// The Sprossen and the toggle live in CountriesOverview+Practice.swift, the table
/// in CountriesOverview+Reference.swift; split purely for file size.
struct CountriesOverview: View {
    let model: AppModel
    /// The language the learner KNOWS — one half of every row.
    let source: String
    /// The language being learned — the other half, and what a forward run answers in.
    let target: String

    @Environment(\.dismiss) private var dismiss
    // why: internal, not private — both extensions read the reader's locale.
    @Environment(\.locale) var locale

    /// The joined atlas. Held rather than recomputed per row: the join walks the
    /// whole manifest, and the Sprossen and the table read the very same content.
    // why: internal, not private — both extensions render from it.
    @State var content: CountryDrillContent?
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
    /// shape both other overviews use.
    @State private var lastRun: DrillRunResult?

    /// The run the start button opens, wrapped so ONE `fullScreenCover(item:)`
    /// carries it — the shape both other overviews use.
    private struct Launch: Identifiable {
        let reverse: Bool
        let fast: Bool
        let id = UUID()
    }

    /// Scroll target for the tile a closed run leaves.
    static let resultAnchor = "result"

    /// Scroll target for the tile carrying the reverse and fast switches.
    static let modifierAnchor = "modifiers"

    /// Where the Sprosse and the record are kept — one key per PAIR, because the
    /// atlas is a pair's material and not a language's.
    var storageKey: String { "countries.\(source)-\(target)" }

    var body: some View {
        NavigationStack {
            ScrollViewReader { scroll in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: DL.Space.xl) {
                        if let lastRun {
                            DrillResultTile(result: lastRun)
                                .id(Self.resultAnchor)
                        }
                        practiceSection
                        referenceSection
                    }
                    .padding(DL.Space.xl)
                }
                // why: the other overviews' rule — a tile inserted above the
                // content keeps the offset, so the page comes up to meet it.
                .onChange(of: lastRun) { _, run in
                    guard run != nil else { return }
                    withAnimation(.easeOut(duration: 0.25)) {
                        scroll.scrollTo(Self.resultAnchor, anchor: .top)
                    }
                }
                #if DEBUG
                // why: after the first layout — a scrollTo issued while the
                // LazyVStack is still building has nothing to scroll to.
                .task {
                    guard Self.uitestWantsModifiers else { return }
                    try? await Task.sleep(for: .milliseconds(400))
                    scroll.scrollTo(Self.modifierAnchor, anchor: .center)
                }
                #endif
            }
            #if DEBUG
            .defaultScrollAnchor(Self.uitestAnchor)
            #endif
            .background(Color.dlBackground.ignoresSafeArea())
            .navigationTitle(Text("countries.title \(languageName)"))
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
        .tint(.dlAccent)
        .onAppear { reload() }
        // why: a closing run books the Sprosse it reached, so the line under the
        // ladder is stale the moment the cover comes down.
        .fullScreenCover(item: $launch, onDismiss: reload) { launch in
            Group {
                if let content {
                    CountryDrillView(model: model, content: content, reverse: launch.reverse,
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

    // MARK: - Starting a run

    func start() {
        launch = Launch(reverse: reverse, fast: fast && fastUnlocked)
    }

    /// Whether fast mode may be picked at all — kern's rule on the stored best,
    /// never a Sprosse number written down beside it.
    var fastUnlocked: Bool { CountryDrill.shared.fastUnlocked(bestLevel: bestSprosse) }

    /// Reads the join and the standing Sprosse at once. Both change under this page
    /// — the catalog when the profile does, the Sprosse when a run closes.
    func reload() {
        content = model.catalog?.countryDrillContent(source: source, target: target)
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
        if launch == nil, !Self.uitestLaunched, UserDefaults.standard.bool(forKey: "uitest-run"),
           content != nil {
            // why: a cover raised while the sheet under it is still animating in
            // is dropped — the tap this stands in for always comes after that.
            // ONCE: a run that reopens itself on every foreground never ends.
            Self.uitestLaunched = true
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

    func heading(_ key: LocalizedStringKey) -> some View {
        Text(key)
            .font(DL.Fonts.title)
            .foregroundStyle(Color.dlTextPrimary)
            .accessibilityAddTraits(.isHeader)
    }
}

#if DEBUG
extension CountriesOverview {
    /// One auto-start per launch — see `reload`.
    @MainActor static var uitestLaunched = false

    /// `-uitest-section table` opens the page at the atlas instead of at the
    /// top — the other overviews' hook, reused, because the reading sits below
    /// the fold here too and no tap driver is installed.
    static var uitestAnchor: UnitPoint {
        UserDefaults.standard.string(forKey: "uitest-section") == "table" ? .bottom : .top
    }

    /// `-uitest-section modifiers` brings the switches into view. A fraction of
    /// the page would not do it: the atlas under them is dozens of rows long, so
    /// where the tile falls depends on how much content the pair has. Scrolling
    /// to the tile's own id lands on it whatever the table's length.
    static var uitestWantsModifiers: Bool {
        UserDefaults.standard.string(forKey: "uitest-section") == "modifiers"
    }

    /// `-uitest-modifiers rev,fast` — the numbers page's hook, worded the same,
    /// because neither switch can be tapped from a launch argument otherwise.
    static var uitestModifiers: Set<String> {
        Set((UserDefaults.standard.string(forKey: "uitest-modifiers") ?? "")
            .split(whereSeparator: { $0 == "," || $0 == " " }).map(String.init))
    }

    /// `-uitest-countries-best 9` stands the ladder at a Sprosse a fresh install
    /// has not climbed, which is the only way to photograph fast mode OPEN.
    /// Booked through `TrainerProgress` so it meets exactly the rule a real run
    /// would have written, rather than a second door into the same state.
    static func uitestSeedProgress(key: String) {
        let best = UserDefaults.standard.integer(forKey: "uitest-countries-best")
        if best > 0 { TrainerProgress.record(best, for: key) }
    }
}
#endif
