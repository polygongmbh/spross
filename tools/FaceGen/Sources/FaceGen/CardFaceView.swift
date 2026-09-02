import DuoKern
import SwiftUI

/// Spross design language (docs/design.md): warm cream, article color coding,
/// emoji as illustration.
enum Palette {
    static let cream = Color(red: 0xFA / 255, green: 0xF3 / 255, blue: 0xE8 / 255)
    static let ink = Color(red: 0x33 / 255, green: 0x30 / 255, blue: 0x2B / 255)
    static let muted = Color(red: 0x8A / 255, green: 0x84 / 255, blue: 0x7A / 255)
    static let accent = Color(red: 0xE8 / 255, green: 0x59 / 255, blue: 0x0C / 255)
    static let der = Color(red: 0x19 / 255, green: 0x71 / 255, blue: 0xC2 / 255)
    static let die = Color(red: 0xC2 / 255, green: 0x25 / 255, blue: 0x5C / 255)
    static let das = Color(red: 0x1E / 255, green: 0x7A / 255, blue: 0x32 / 255)

    static func article(_ article: String) -> Color {
        switch article {
        case "der": der
        case "die": die
        case "das": das
        default: muted
        }
    }
}

/// One vocabulary card, poster-style, sized for the Apple Watch Photos face.
/// The watch overlays the time at the top of the photo, so the top
/// `timeSafeTop` proportion of the canvas is kept empty; the emoji sits just
/// below that line and the headword lands in the middle band.
struct CardFaceView: View {
    let card: Card
    let canvas: CGSize
    let timeSafeTop: Double

    var body: some View {
        let h = canvas.height
        let w = canvas.width
        VStack(spacing: h * 0.018) {
            Color.clear.frame(height: h * timeSafeTop) // time zone — keep empty

            Text(card.emoji ?? "🗂️")
                .font(.system(size: h * 0.14))

            if let article = card.article {
                Text(article)
                    .font(.system(size: h * 0.034, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.horizontal, h * 0.028)
                    .padding(.vertical, h * 0.010)
                    .background(Capsule().fill(Palette.article(article)))
                    .padding(.top, h * 0.008)
            }

            Text(card.german)
                .font(.system(size: h * 0.088, weight: .bold, design: .rounded))
                .foregroundStyle(Palette.ink)
                .lineLimit(card.kind == .phrase ? 3 : 2)
                .minimumScaleFactor(0.3)
                .multilineTextAlignment(.center)
                .padding(.horizontal, w * 0.06)

            if let plural = card.plural {
                Text(plural)
                    .font(.system(size: h * 0.032, weight: .medium, design: .rounded))
                    .foregroundStyle(Palette.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .padding(.horizontal, w * 0.08)
            }

            Rectangle()
                .fill(Palette.muted.opacity(0.35))
                .frame(width: w * 0.22, height: 2)
                .padding(.vertical, h * 0.012)

            Text(card.translation)
                .font(.system(size: h * 0.052, weight: .semibold, design: .rounded))
                .foregroundStyle(Palette.accent)
                .lineLimit(2)
                .minimumScaleFactor(0.4)
                .multilineTextAlignment(.center)
                .padding(.horizontal, w * 0.08)

            Spacer(minLength: 0)

            if let note = card.note {
                Text(note)
                    .font(.system(size: h * 0.024, design: .rounded))
                    .italic()
                    .foregroundStyle(Palette.muted)
                    .lineLimit(3)
                    .minimumScaleFactor(0.6)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, w * 0.08)
                    .padding(.bottom, h * 0.045)
            }
        }
        .frame(width: w, height: h)
        .background(Palette.cream)
    }
}
