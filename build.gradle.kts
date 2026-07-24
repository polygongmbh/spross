// why: plugins load once in the root classloader — :kern and :android otherwise
// get sibling copies of KGP whose shared build services cannot see each other.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
