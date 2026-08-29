package net.spross.kern

/**
 * The addresses Spross publishes about itself: where mail reaches whoever runs it,
 * and where the privacy policy stands.
 *
 * Kern's rather than each platform's for the reason a duplicated address always
 * proves in the end — one copy changes and the other keeps answering. Nothing here
 * is a rule the engine applies; it is simply the one place both apps read it from,
 * and the exception that earns its keep in a layer otherwise reserved for rules.
 */
object Legal {
    const val CONTACT_ADDRESS: String = "spross@polygon.gmbh"
    const val PRIVACY_URL: String = "https://spross.net/privacy"
}
