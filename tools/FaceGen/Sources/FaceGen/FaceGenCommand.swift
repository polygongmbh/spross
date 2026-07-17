import ArgumentParser
import DuoKern
import Foundation

extension LanguagePair: @retroactive ExpressibleByArgument {}

extension RenderSize: ExpressibleByArgument {
    init?(argument: String) {
        let parts = argument.lowercased().split(separator: "x")
        guard parts.count == 2,
              let w = Int(parts[0]), let h = Int(parts[1]),
              w > 0, h > 0 else { return nil }
        self.init(width: w, height: h)
    }

    var defaultValueDescription: String { "\(width)x\(height)" }
}

struct ManifestEntry: Codable {
    var id: String
    var german: String
    var file: String
}

struct Manifest: Codable {
    var generatedAt: Date
    var count: Int
    var cards: [ManifestEntry]
}

@main
struct FaceGenCommand: AsyncParsableCommand {
    static let configuration = CommandConfiguration(
        commandName: "facegen",
        abstract: "Render vocabulary card PNGs for the Apple Watch Photos face."
    )

    @Option(help: "Directory containing vocab-*.json seed files.")
    var seed: String

    @Option(help: "BoxState JSON file; selects attention-worthy cards for its direction.")
    var box: String?

    @Option(help: "Language pair to render from seed (ignored with --box).")
    var pair: LanguagePair = .deSw

    @Option(help: "Number of cards to render (Photos face album cap is 24).")
    var count: Int = 24

    @Option(help: "Output directory (created if missing).")
    var out: String = "faces"

    @Option(help: "Pixel size of each image as WxH.")
    var size: RenderSize = RenderSize(width: 1170, height: 1521)

    @Option(name: .customLong("time-safe-top"),
            help: "Proportion of image height left empty at the top, because the watch face overlays the time there.")
    var timeSafeTop: Double = 0.28

    func validate() throws {
        guard count > 0 else { throw ValidationError("--count must be positive.") }
        guard (0...0.6).contains(timeSafeTop) else {
            throw ValidationError("--time-safe-top must be between 0 and 0.6.")
        }
    }

    func run() async throws {
        let cards: [Card]
        if let box {
            cards = try Selection.fromBox(file: URL(filePath: box), count: count)
            guard !cards.isEmpty else {
                throw ValidationError("Box has no active cards for its direction.")
            }
        } else {
            cards = try Selection.fromSeed(
                directory: URL(filePath: seed, directoryHint: .isDirectory),
                pair: pair, count: count)
            guard !cards.isEmpty else {
                throw ValidationError("No seed cards found for pair \(pair.rawValue) in \(seed).")
            }
        }

        let outDir = URL(filePath: out, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)

        var entries: [ManifestEntry] = []
        for (index, card) in cards.enumerated() {
            let file = String(format: "face-%02d.png", index + 1)
            try await FaceRenderer.renderPNG(
                card: card, size: size, timeSafeTop: timeSafeTop,
                to: outDir.appending(path: file))
            entries.append(ManifestEntry(id: card.id, german: card.german, file: file))
            print("\(file)  \(card.german)")
        }

        let manifest = Manifest(generatedAt: Date(), count: entries.count, cards: entries)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        try encoder.encode(manifest).write(to: outDir.appending(path: "manifest.json"))
        print("Rendered \(entries.count) faces to \(outDir.path())")
    }
}
