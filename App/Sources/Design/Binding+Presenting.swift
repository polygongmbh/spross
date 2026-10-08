import SwiftUI

extension Binding where Value == Bool {
    /// A presentation flag over an optional: shown while it holds a value, emptied on dismiss.
    init<T>(presenting value: Binding<T?>) {
        self = value.isPresent
    }
}

private extension Optional {
    var isPresent: Bool {
        get { self != nil }
        set { if !newValue { self = nil } }
    }
}
