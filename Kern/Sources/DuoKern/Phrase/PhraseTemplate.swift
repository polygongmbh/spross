/// Slot-template model for generated phrases (design.md Phase 3 tail):
/// a curated sentence frame whose single `{slot}` is filled with a
/// Trainer-generated value — digits on the German prompt side,
/// target-language words on the answer side.
public struct PhraseTemplate: Sendable, Equatable, Identifiable {
    /// Ukrainian counted-noun agreement for a `{count}` marker following the
    /// numeral: 1/21/31… → `one`, 2–4/22–24… → `few`, everything else
    /// (incl. 11–14) → `many`.
    public struct CountForms: Sendable, Equatable {
        public var one: String
        public var few: String
        public var many: String

        public init(one: String, few: String, many: String) {
            self.one = one
            self.few = few
            self.many = many
        }

        public func form(for n: Int) -> String {
            let lastTwo = abs(n) % 100
            if (11...14).contains(lastTwo) { return many }
            switch lastTwo % 10 {
            case 1: return one
            case 2, 3, 4: return few
            default: return many
            }
        }
    }

    public var id: String
    public var pair: LanguagePair
    /// German sentence with `{slot}`; the prompt substitutes digits
    /// ("14:35", "347", "1978"), never words.
    public var deTemplate: String
    /// Target sentence with `{slot}` (and `{count}` iff `countForms` is set);
    /// display/accepted substitute the Trainer's word forms.
    public var targetTemplate: String
    public var slotKind: TrainerKind
    /// Template-level note, merged with the slot task's gloss.
    public var gloss: String?
    /// Present only on `.numbers` templates whose target noun must agree
    /// with the numeral (Ukrainian). `targetTemplate` then contains `{count}`.
    public var countForms: CountForms?
    /// Ukrainian templates counting a masculine/indeclinable noun: feminine
    /// numeral variants (одна/дві) must NOT be accepted — these templates
    /// exist to train exactly that agreement (language-review finding).
    /// Implied for all `countForms` templates.
    public var masculineSlot: Bool

    public init(id: String, pair: LanguagePair, deTemplate: String, targetTemplate: String,
                slotKind: TrainerKind, gloss: String? = nil, countForms: CountForms? = nil,
                masculineSlot: Bool = false) {
        self.id = id
        self.pair = pair
        self.deTemplate = deTemplate
        self.targetTemplate = targetTemplate
        self.slotKind = slotKind
        self.gloss = gloss
        self.countForms = countForms
        self.masculineSlot = masculineSlot || countForms != nil
    }

    public var targetLanguage: TrainerLanguage {
        switch pair {
        case .deSw: return .swahili
        case .deUk: return .ukrainian
        }
    }

    static let slotMarker = "{slot}"
    static let countMarker = "{count}"
}
