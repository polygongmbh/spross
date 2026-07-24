package net.spross.app

import android.content.res.AssetManager
import java.io.IOException
import net.spross.kern.catalog.CatalogSource

/** Path-based catalog reader over the APK assets bundled by `syncCatalogAssets`. */
class AssetCatalogSource(private val assets: AssetManager) : CatalogSource {
    override fun read(path: String): String? =
        try {
            assets.open("catalog/$path").bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            null // kern treats absent files as "language not authored here"
        }
}
