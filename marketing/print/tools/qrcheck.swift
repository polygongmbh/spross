// Decodes every QR code in an image and prints the payloads, one per line.
// Used to prove the rendered print artwork still scans — run it on a raster of the
// final PDF page, not on the source SVG.
//
//   swift qrcheck.swift page.png
//
// Exits 1 when no code is found, so it can gate a build.

import CoreImage
import Foundation

guard CommandLine.arguments.count > 1 else {
    FileHandle.standardError.write("usage: qrcheck.swift <image>...\n".data(using: .utf8)!)
    exit(2)
}

let context = CIContext()
let detector = CIDetector(
    ofType: CIDetectorTypeQRCode,
    context: context,
    options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]
)!

var found = 0
for path in CommandLine.arguments.dropFirst() {
    guard let image = CIImage(contentsOf: URL(fileURLWithPath: path)) else {
        FileHandle.standardError.write("unreadable: \(path)\n".data(using: .utf8)!)
        continue
    }
    let features = detector.features(in: image).compactMap { $0 as? CIQRCodeFeature }
    for feature in features {
        let payload = feature.messageString ?? "<undecodable>"
        print("\(path)\t\(payload)")
        found += 1
    }
    if features.isEmpty { print("\(path)\t<none>") }
}

exit(found > 0 ? 0 : 1)
