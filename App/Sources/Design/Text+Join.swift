import SwiftUI

extension Text {
    /// Composes two or more parts with a verbatim separator (default " · "), so
    /// each part keeps localizing via the environment locale while the
    /// punctuation stays out of the String Catalog. Use for fixed compositions;
    /// for a dynamic `[Text]` use the `Sequence` variant below.
    static func joined(_ first: Text, _ rest: Text..., separator: String = " · ") -> Text {
        rest.reduce(first) { $0 + Text(verbatim: separator) + $1 }
    }
}

extension Sequence where Element == Text {
    /// Joins the parts with a verbatim separator (default " · "), or returns
    /// `nil` when there are none so the caller can supply a context-specific
    /// fallback. Each part keeps localizing independently (with catalog plural
    /// handling) instead of being flattened into a single `String`.
    func joined(separator: String = " · ") -> Text? {
        reduce(nil) { acc, part in
            acc.map { $0 + Text(verbatim: separator) + part } ?? part
        }
    }
}
