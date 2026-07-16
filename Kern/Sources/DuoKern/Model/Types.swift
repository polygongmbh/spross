import Foundation

// Core domain types. Owned by the root session — module agents build against this file
// and propose changes rather than editing it unilaterally.

public enum LanguagePair: String, Codable, Sendable, CaseIterable {
    case deSw = "de-sw"
    case deUk = "de-uk"
}

public enum Direction: String, Codable, Sendable, CaseIterable {
    /// Prompt German, produce/recognize target language.
    case deToTarget
    /// Prompt target language, produce German (typed production).
    case targetToDe
}

public enum CardKind: String, Codable, Sendable {
    case noun, verb, phrase
}

public struct Card: Codable, Sendable, Identifiable, Equatable {
    public var id: String
    public var kind: CardKind
    public var pair: LanguagePair
    public var area: String
    public var german: String
    public var article: String?
    public var plural: String?
    public var emoji: String?
    public var translation: String
    /// Literal gloss / usage note (e.g. "Reis = mpunga (geerntet) → mchele (roh) → wali (gekocht)!").
    public var note: String?
    /// For phrases: ids of the word cards this phrase composes. Empty for words.
    public var componentIDs: [String]

    public init(id: String, kind: CardKind, pair: LanguagePair, area: String,
                german: String, article: String? = nil, plural: String? = nil,
                emoji: String? = nil, translation: String, note: String? = nil,
                componentIDs: [String] = []) {
        self.id = id
        self.kind = kind
        self.pair = pair
        self.area = area
        self.german = german
        self.article = article
        self.plural = plural
        self.emoji = emoji
        self.translation = translation
        self.note = note
        self.componentIDs = componentIDs
    }
}

public enum Rating: Int, Codable, Sendable, CaseIterable {
    case again = 1, hard = 2, good = 3, easy = 4
}

public enum CardPhase: String, Codable, Sendable {
    case new, learning, review, relearning
}

/// FSRS memory state for one card in one direction.
public struct MemoryState: Codable, Sendable, Equatable {
    public var stability: Double
    public var difficulty: Double

    public init(stability: Double, difficulty: Double) {
        self.stability = stability
        self.difficulty = difficulty
    }
}

public struct ReviewLogEntry: Codable, Sendable, Equatable {
    public var date: Date
    public var rating: Rating
    public var elapsedDays: Double

    public init(date: Date, rating: Rating, elapsedDays: Double) {
        self.date = date
        self.rating = rating
        self.elapsedDays = elapsedDays
    }
}

/// Scheduling for one card in one direction. A card active in both directions has two of these.
public struct CardScheduling: Codable, Sendable, Equatable {
    public var cardID: String
    public var direction: Direction
    public var phase: CardPhase
    public var memory: MemoryState?
    public var due: Date?
    public var addedAt: Date
    public var log: [ReviewLogEntry]

    public init(cardID: String, direction: Direction, phase: CardPhase = .new,
                memory: MemoryState? = nil, due: Date? = nil, addedAt: Date, log: [ReviewLogEntry] = []) {
        self.cardID = cardID
        self.direction = direction
        self.phase = phase
        self.memory = memory
        self.due = due
        self.addedAt = addedAt
        self.log = log
    }
}

public struct BoxConfig: Codable, Sendable, Equatable {
    public var pair: LanguagePair
    public var direction: Direction
    public var newPerDay: Int
    public var dueSoftCap: Int
    public var sessionCap: Int
    public var desiredRetention: Double
    /// Minimum stability (days) of all components before a phrase unlocks.
    public var phraseUnlockStability: Double

    public init(pair: LanguagePair, direction: Direction = .deToTarget,
                newPerDay: Int = 5, dueSoftCap: Int = 30, sessionCap: Int = 30,
                desiredRetention: Double = 0.9, phraseUnlockStability: Double = 3.0) {
        self.pair = pair
        self.direction = direction
        self.newPerDay = newPerDay
        self.dueSoftCap = dueSoftCap
        self.sessionCap = sessionCap
        self.desiredRetention = desiredRetention
        self.phraseUnlockStability = phraseUnlockStability
    }
}

/// The single persisted aggregate.
public struct BoxState: Codable, Sendable, Equatable {
    public var config: BoxConfig
    /// All cards known to the box (imported seed), keyed by id.
    public var cards: [String: Card]
    /// Scheduling for active cards, keyed by "\(cardID)|\(direction.rawValue)".
    public var scheduling: [String: CardScheduling]
    /// User-enqueued card ids waiting to enter (priority queue, front first).
    public var enqueued: [String]
    /// ISO date (yyyy-MM-dd) → number of new cards introduced that day.
    public var newIntroduced: [String: Int]

    public init(config: BoxConfig, cards: [String: Card] = [:],
                scheduling: [String: CardScheduling] = [:],
                enqueued: [String] = [], newIntroduced: [String: Int] = [:]) {
        self.config = config
        self.cards = cards
        self.scheduling = scheduling
        self.enqueued = enqueued
        self.newIntroduced = newIntroduced
    }

    public static func schedulingKey(cardID: String, direction: Direction) -> String {
        "\(cardID)|\(direction.rawValue)"
    }
}
