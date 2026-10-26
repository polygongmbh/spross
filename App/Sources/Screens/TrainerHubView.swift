import SwiftUI
import SprossKern

/// Compact "Wiese" card on the Home screen: 🔢 Numbers, 🔤 Letters,
/// 🌍 Countries, 📅 Dates, 🔀 the word scramble and 🧩 the sentence one. The
/// first four open an overview — what the language does with numbers or
/// letters, what the world is called in it, how it says a date — with the run
/// started from the same page; the two scrambles have nothing to read beside
/// them and open their run directly. Offerings stay registry-driven: numbers
/// appears only when Kern's trainer supports the learned language, letters only
/// where an alphabet file was authored, the atlas and the calendar only where
/// the pair joins one, and each scramble only where the BOX holds enough to ask
/// — an empty card hides entirely. Trainers are stateless: they never touch
/// BoxState or FSRS.
struct TrainerHubView: View, LanguageNaming {
    let model: AppModel

    // why: internal, not private — LanguageNaming names the drilled
    // language through it.
    @Environment(\.locale) var locale

    /// What is open. Home holds it, so the card leading the day opens a chip's
    /// destination through the same presentations the chips use.
    @Binding var destination: HubDestination?

    /// The figures a scramble run handed back as it closed — the scrambles have
    /// no page of their own, so the hub wears their tile. A run too short to
    /// report clears it rather than leaving an older one standing.
    @State private var lastRun: DrillRunResult?
    /// Rains confetti over the screen the hub stands on — a card cannot hold it.
    var celebrate: () -> Void = {}

    /// The language being learned — every drill runs in it.
    var drillLanguage: String? { model.targetLanguage }

    // why: internal, not private — the UI-test hook in
    // TrainerHubView+Destinations.swift gates the numbers overview on it.
    var slotsAvailable: Bool {
        drillLanguage.map { Numbers.shared.supports(language: $0) } ?? false
    }

    /// The sentence drill this profile can run: the frames the catalog joins
    /// for the pair, always asked known-language prompt → learned-language answer.
    var phraseDrill: (source: String, target: String, templates: [PhraseTemplate])? {
        guard let target = drillLanguage else { return nil }
        let templates = model.pairContent.phraseTemplates
        return templates.isEmpty ? nil : (source: model.sourceLanguage, target: target,
                                          templates: templates)
    }

    /// The pair whose atlas this profile can drill, or nil where the catalog
    /// joins none — registry by FILE, exactly as the alphabet's is. Kern is the
    /// only judge of that, so nothing here counts countries.
    var atlasPair: (source: String, target: String)? {
        guard model.pairContent.atlasJoinsPair, let target = drillLanguage else { return nil }
        return (source: model.sourceLanguage, target: target)
    }

    /// The pair whose calendars this profile can drill, or nil where the
    /// catalog joins none — the atlas' registry rule, on the dates files.
    var datesPair: (source: String, target: String)? {
        guard model.pairContent.datesJoinPair, let target = drillLanguage else { return nil }
        return (source: model.sourceLanguage, target: target)
    }

    /// Whether the box holds enough settled single words to be worth
    /// mixing. Kern's own floor, read — this side counts nothing.
    var wordScrambleAvailable: Bool { WordScrambleAvailability(model: model).drillAvailable }

    /// Whether the box has unlocked enough long-enough phrases to arrange.
    var sentenceScrambleAvailable: Bool {
        SentenceScrambleAvailability(model: model).drillAvailable
    }

    /// Whether the box holds enough pairs whose both words have arrived.
    var oppositesAvailable: Bool { OppositesAvailability(model: model).drillAvailable }

    var body: some View {
        Group {
            if !chips.isEmpty {
                card
            }
        }
        // The pages: a sheet, because an overview is read from and swiped away.
        .paneSheet(item: presented { !$0.isRun }) { surface($0) }
    }

    /// What a destination opens, under the known language every trainer surface
    /// is chromed in.
    @ViewBuilder
    private func surface(_ destination: HubDestination) -> some View {
        Group {
            switch destination {
            case let .numbers(language):
                NumbersOverview(model: model, language: language,
                                phraseDrill: phraseDrill.map { ($0.source, $0.templates) })
            case let .letters(language):
                LettersOverview(model: model, language: language)
            case let .countries(source, target):
                CountriesOverview(model: model, source: source, target: target)
            case let .dates(source, target):
                DatesOverview(model: model, source: source, target: target)
            case let .wordScramble(language):
                WordScrambleView(model: model, language: language, onFinish: report)
            case let .sentenceScramble(language):
                SentenceScrambleView(model: model, language: language, onFinish: report)
            case let .opposites(language):
                OppositesView(model: model, language: language, onFinish: report)
            }
        }
        .environment(\.locale, model.knownLocale)
    }

    private func report(_ result: DrillRunResult) {
        lastRun = result.summary.worthReporting ? result : nil
        if result.summary.celebrated { celebrate() }
    }

    /// The one `destination`, seen through ONE presentation: a binding that
    /// holds it only while it belongs there, so the sheet and the cover cannot
    /// both claim the same tap and a dismissal from either clears the state.
    /// Two `@State`s would be two sources of truth for one chip.
    private func presented(
        _ belongs: @escaping (HubDestination) -> Bool
    ) -> Binding<HubDestination?> {
        Binding(get: {
                    guard let open = destination, belongs(open) else { return nil }
                    return open
                },
                set: { destination = $0 })
    }

    // MARK: - Card

    private var card: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.lg) {
            Text("trainer.hub.title")
                .font(Theme.typography.title)
                .foregroundStyle(Theme.colors.textPrimary)
            Text("trainer.hub.subtitle")
                .font(Theme.typography.subheadline)
                .foregroundStyle(Theme.colors.textSecondary)
            if let lastRun { DrillResultTile(result: lastRun, onHub: true) }
            VStack(spacing: Theme.spacing.md) {
                ForEach(Array(chipRows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: Theme.spacing.md) {
                        ForEach(row) { chip(for: $0) }
                    }
                }
            }
        }
        .homeCard(alignment: .leading)
        // The runs: a cover, so the drill is a full screen whose corner ✕ is
        // the one way out — a swipe would leave the run without booking the
        // Sprosse it reached, which is what the ✕ does (`WordScrambleView`).
        // why: on the CARD rather than beside the sheet above — a second
        // presentation stacked on the same view is not reliably honored by
        // SwiftUI, and the symptom is a chip that does nothing.
        .fullScreenCover(item: presented { $0.isRun }) { surface($0) }
        #if DEBUG
        // UI-test hook: `-uitest-trainer numbers|letters|countries|dates|
        // wordscramble|sentencescramble` opens that surface (in the learned
        // language, like the chips).
        // Attached HERE because the card only appears once the box is loaded;
        // resolved in TrainerHubView+Destinations.swift.
        .onAppear {
            guard destination == nil,
                  let raw = UserDefaults.standard.string(forKey: "uitest-trainer") else { return }
            destination = uitestDestination(raw)
        }
        #endif
    }

    var namingCatalog: Catalog? { model.catalog }

    // MARK: - The entries, and how many lines they take

    /// Every entry this profile can reach, in the order the card offers them —
    /// kern's `Drill` roster, cut down to what this profile can open
    /// (`destination(for:)`). A VALUE per chip rather than a view apiece,
    /// because the row has to COUNT them before it can decide how many lines
    /// it needs.
    var chips: [HubChip] {
        Drill.allCases.compactMap { drill in
            destination(for: drill).map {
                HubChip(emoji: drill.emoji, title: drill.titleKey, destination: $0)
            }
        }
    }

    /// The chips cut into the lines kern sizes (`ChipRows`), each line's chips equal width.
    private var chipRows: [[HubChip]] {
        let chips = chips
        var start = 0
        return ChipRows.shared.of(count: Int32(chips.count)).map { size in
            let row = Array(chips[start..<start + Int(truncating: size)])
            start += Int(truncating: size)
            return row
        }
    }

    private func chip(for chip: HubChip) -> some View {
        Button {
            destination = chip.destination
        } label: {
            chipLabel(emoji: chip.emoji, title: Text(chip.title))
        }
        .buttonStyle(ChipButtonStyle())
    }

    /// One chip's face.
    func chipLabel(emoji: String, title: Text) -> some View {
        VStack(spacing: Theme.spacing.sm) {
            Text(emoji)
                .font(.system(size: 30)) // card-parity: the hub chip's own glyph, not a card prompt
                .accessibilityHidden(true)
            title
                .font(Theme.typography.caption)
                .foregroundStyle(Theme.colors.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, minHeight: Theme.reserve.tile)
        .padding(.vertical, Theme.spacing.sm)
        .padding(.horizontal, Theme.spacing.xs)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius.tile, style: .continuous)
                .fill(Theme.colors.surfaceTint)
        )
    }
}
