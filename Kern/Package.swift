// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "DuoKern",
    platforms: [.iOS(.v17), .macOS(.v14), .watchOS(.v10)],
    products: [
        .library(name: "DuoKern", targets: ["DuoKern"])
    ],
    targets: [
        .target(name: "DuoKern"),
        .testTarget(name: "DuoKernTests", dependencies: ["DuoKern"], resources: [.copy("Fixtures")]),
    ]
)
