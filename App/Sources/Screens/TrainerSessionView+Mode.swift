import SwiftUI
import SprossKern

/// What a run drills, and the three identities it is filed under. Its own
/// file because it is the run SPEC, not the view's state: the hub builds one
/// and hands it over, and the drill never edits it.
extension TrainerSessionView {
    /// What a run drills: bare slot values, or full sentences composed from
    /// the catalog's sentence frames + slot values. Languages are catalog
    /// codes. The frames are carried, not looked up — a run samples from the
    /// set it was opened with.
    enum Mode {
        case slots(TrainerKind, String)
        case phrases(source: String, target: String, templates: [PhraseTemplate])

        /// The language answers are typed in.
        var typedLanguage: String {
            switch self {
            case .slots(_, let language): return language
            case .phrases(_, let target, _): return target
            }
        }

        /// Catalog key for the run title.
        var titleKey: LocalizedStringKey {
            switch self {
            case .slots(let kind, _): return kind.trainerTitleKey
            case .phrases: return "trainer.phrases"
            }
        }

        /// Identity a record is kept under (`TrainerRecords`): what is drilled
        /// and in which pair — a sentence run typed in German is not the same
        /// feat as the same frames typed in Swahili.
        var recordKey: String {
            switch self {
            case .slots(let kind, let language): return "\(kind.name).\(language)"
            case .phrases(let source, let target, _): return "phrases.\(source)-\(target)"
            }
        }

        /// Identity a rung is kept under (`TrainerProgress`): variant and the
        /// language being learned. Deliberately NOT `recordKey` — a record
        /// belongs to a run's whole selection, a rung to one variant, so the
        /// sentence drill books against its answer language alone and the
        /// unlock ladder can ask one language for every variant at once.
        var progressKey: String {
            switch self {
            case .slots(let kind, let language): return "\(kind.name).\(language)"
            case .phrases(_, let target, _): return "phrases.\(target)"
            }
        }
    }
}
