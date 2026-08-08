package net.spross.kern.model

internal actual fun nfcNormalized(text: String): String =
    text.asDynamic().normalize("NFC") as String
