package net.spross.kern.model

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCanonicalMapping

@OptIn(BetaInteropApi::class)
internal actual fun nfcNormalized(text: String): String =
    NSString.create(string = text).precomposedStringWithCanonicalMapping
