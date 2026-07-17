// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "FaceGen",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "facegen", targets: ["FaceGen"])
    ],
    dependencies: [
        .package(path: "../../Kern"),
        .package(url: "https://github.com/apple/swift-argument-parser", from: "1.3.0"),
    ],
    targets: [
        .executableTarget(
            name: "FaceGen",
            dependencies: [
                .product(name: "DuoKern", package: "Kern"),
                .product(name: "ArgumentParser", package: "swift-argument-parser"),
            ]
        )
    ]
)
