import SprossKern

/// What the catalog offers the profile's pair beyond its cards.
///
/// Every answer is a catalog walk — the atlas one builds every country row and every
/// language name — and the trainer card and the settings ask on every redraw, so they
/// are resolved when the PROFILE changes (`AppModel.refreshTrainerContent`) and never
/// per composition.
struct PairContent {
    /// Whether the pair joins a country atlas.
    let atlasJoinsPair: Bool
    /// The joined calendars, which set how tall the dates ladder runs — what the
    /// suggestion reads "mastered" against, out of the same join.
    let dates: DateDrillContent?
    /// The sentence frames the pair joins.
    let phraseTemplates: [PhraseTemplate]
    /// The target languages the settings picker offers. Resolving it is a full
    /// catalog JOIN per candidate language — every card of every pair built and
    /// thrown away.
    let targetChoices: [String]
    /// Every shelf's heading, resolved for the reader once per join (`AreaNaming`).
    let areaNaming: AreaNaming?

    /// Whether the pair joins the two calendars the dates drill needs.
    var datesJoinPair: Bool { dates != nil }

    static var none: PairContent {
        PairContent(atlasJoinsPair: false, dates: nil, phraseTemplates: [],
                    targetChoices: [], areaNaming: nil)
    }

    /// Nil-safe over any pair: kern throws on an unknown or self-paired language rather
    /// than returning empty, and a Kotlin throw crossing back is a crash.
    static func resolve(catalog: Catalog?, source: String, target: String?,
                        areaNaming: (Catalog) -> AreaNaming) -> PairContent {
        guard let catalog, let target, source != target,
              catalog.languages[source] != nil, catalog.languages[target] != nil
        else { return .none }
        return PairContent(
            atlasJoinsPair: catalog.countryDrillContent(source: source, target: target) != nil,
            dates: catalog.dateDrillContent(source: source, target: target),
            phraseTemplates: catalog.phraseTemplates(source: source, target: target),
            targetChoices: LanguageChoices.shared.targetChoices(
                catalog: catalog,
                selection: LanguageChoices.Selection(source: source, target: target)),
            areaNaming: areaNaming(catalog))
    }
}
