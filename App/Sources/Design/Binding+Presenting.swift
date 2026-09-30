import SwiftUI

extension Binding where Value == Bool {
    /// A presentation flag over an optional: shown while it holds a value, emptied on dismiss.
    init<T>(presenting value: Binding<T?>) {
        self.init(get: { value.wrappedValue != nil }, set: { if !$0 { value.wrappedValue = nil } })
    }
}
