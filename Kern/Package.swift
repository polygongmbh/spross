// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "DuoKern",
    platforms: [.iOS(.v17), .macOS(.v14), .watchOS(.v10)],
    products: [
        .library(name: "DuoKern", targets: ["DuoKern"]),
        // Drill generators (numbers/clock/year + phrase templates). Split out
        // so watch/widgets link only the core and never compile the drill code.
        .library(name: "DuoKernTrainer", targets: ["DuoKernTrainer"]),
    ],
    targets: [
        .target(name: "DuoKern"),
        .target(name: "DuoKernTrainer", dependencies: ["DuoKern"]),
        .testTarget(name: "DuoKernTests",
                    dependencies: ["DuoKern", "DuoKernTrainer"],
                    resources: [.copy("Fixtures")]),
    ]
)
