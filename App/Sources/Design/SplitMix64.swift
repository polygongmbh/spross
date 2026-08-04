import Foundation

/// SplitMix64 over an FNV-1a fold of the identity.
///
/// Explicitly NOT Swift's `Hasher`: the standard library seeds it randomly per
/// process, so a tree keyed on `hashValue` would be a different tree after
/// every relaunch.
struct SplitMix64 {
    private var state: UInt64

    init(seed: UInt64) { state = seed }

    init(_ identity: String) {
        var hash: UInt64 = 0xCBF2_9CE4_8422_2325
        for byte in identity.utf8 {
            hash = (hash ^ UInt64(byte)) &* 0x1000_0000_01B3
        }
        state = hash
    }

    var seed: UInt64 { state }

    static func mix(_ value: UInt64) -> UInt64 {
        var x = value &+ 0x9E37_79B9_7F4A_7C15
        x = (x ^ (x >> 30)) &* 0xBF58_476D_1CE4_E5B9
        x = (x ^ (x >> 27)) &* 0x94D0_49BB_1331_11EB
        return x ^ (x >> 31)
    }

    /// The next value in 0..<1.
    mutating func next() -> Double {
        state = state &+ 0x9E37_79B9_7F4A_7C15
        return Double(Self.mix(state) >> 11) / Double(1 << 53)
    }

    mutating func range(_ low: Double, _ high: Double) -> Double {
        low + (high - low) * next()
    }
}
