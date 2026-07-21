package net.spross.kern.model

import java.text.Normalizer

internal actual fun nfcNormalized(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC)
