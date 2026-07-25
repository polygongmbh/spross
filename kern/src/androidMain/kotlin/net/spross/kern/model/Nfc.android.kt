package net.spross.kern.model

import java.text.Normalizer

// why: duplicates Nfc.jvm.kt — a shared jvm+android source set isn't worth
// fighting the default hierarchy template for one line.
internal actual fun nfcNormalized(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC)
