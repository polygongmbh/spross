package net.spross.kern.model

/** Unicode NFC normalization (no common-stdlib support — platform actuals). */
internal expect fun nfcNormalized(text: String): String
