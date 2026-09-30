import SwiftUI
import SprossKern

/// The two faces of the card on screen, resolved for the role kern shows it in.
extension SessionView {

    /// Grammar (article coloring, plural) renders TARGET-side only; on
    /// recognition it decorates the prompt only when the canonical form is
    /// the one prompted (synonym rotations carry no citation grammar).
    func promptSide(_ card: Card, role: PresentationRole) -> VocabCardView.Side {
        switch role {
        case .produce:
            // why: a settled word is sometimes asked by ear alone — the
            // meaning is withheld ON PURPOSE, so no cue rides along with it
            // either; what stands is the replay glyph and nothing else.
            //
            // The word takes the glyph's place once there is nothing left to
            // withhold: the learner said they cannot listen, or the answer is
            // out and the spelling is what the reveal owes. It then renders as
            // any other target word does — article, plural, and the speaker.
            if askedByEar(card) {
                let written = promptInText || cardRevealed
                return .init(text: card.target.text,
                             article: written
                                 ? CardDisplay.articleLabel(of: card.target, shown: card.target.text)
                                 : nil,
                             plural: written
                                 ? CardDisplay.plural(of: card.target, locale: locale)
                                 : nil,
                             language: model.targetLanguage,
                             pronounce: pronounceAction(for: card.target.text),
                             isPlaying: isPronouncing(card.target.text),
                             listening: !written)
            }
            return .init(text: card.source.text,
                         // why: the area title IS the disambiguating cue, in the source
                         // language — it is a plain name, so nothing is trimmed off it.
                         context: card.promptAmbiguous ? model.areaTitle(card.area) : nil,
                         femMarker: card.promptFeminineMarker)
        case .recognize:
            // why: deliberately NO context cue here — the prompt is the target form, so
            // any cue precise enough to disambiguate would reveal the answer (same
            // reasoning as the emoji matrix). Self-grading absorbs the ambiguity.
            let form = model.promptForm(for: card)
            let canonical = form == card.target.text
            return .init(text: form,
                         article: CardDisplay.articleLabel(of: card.target, shown: form),
                         plural: canonical ? CardDisplay.plural(of: card.target, locale: locale) : nil,
                         language: model.targetLanguage,
                         pronounce: pronounceAction(for: form),
                         isPlaying: isPronouncing(form))
        }
    }

    /// The reveal always shows the full family: produce reveals the target
    /// citation + `teaches`; recognize reveals the source meaning (its `teaches`
    /// joined informatively) + the remaining target forms as "auch: …".
    func answerSide(_ card: Card, role: PresentationRole) -> VocabCardView.Side {
        switch role {
        case .produce:
            let alternates = CardDisplay.alternates(of: card.target,
                                                    shown: card.target.text,
                                                    locale: locale)
            // why: a card asked by ear owes the MEANING back, so its reveal is
            // shaped like the recognition one — the answer where the answer
            // goes, and the word that played standing above it in writing
            // (`promptSide`), which is where a retype has something to finish
            // against rather than a prompt to copy.
            if askedByEar(card) {
                return .init(text: meaning(card),
                             alternates: alternates,
                             femMarker: card.promptFeminineMarker)
            }
            return .init(text: card.target.text,
                         article: CardDisplay.articleLabel(of: card.target,
                                                           shown: card.target.text),
                         plural: CardDisplay.plural(of: card.target, locale: locale),
                         alternates: alternates,
                         language: model.targetLanguage,
                         pronounce: pronounceAction(for: card.target.text),
                         isPlaying: isPronouncing(card.target.text))
        case .recognize:
            return .init(text: meaning(card),
                         alternates: CardDisplay.alternates(of: card.target,
                                                            shown: model.promptForm(for: card),
                                                            locale: locale),
                         femMarker: card.promptFeminineMarker)
        }
    }

    /// The source meaning with its `teaches` joined on — what a reveal owes where
    /// the MEANING is the answer.
    private func meaning(_ card: Card) -> String {
        ([card.source.text] + card.source.teaches).joined(separator: " / ")
    }
}
