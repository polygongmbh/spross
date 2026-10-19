package net.spross.kern.catalog

import java.io.File
import net.spross.kern.repoRoot

internal class FileCatalogSource(private val root: File) : CatalogSource {
    override fun read(path: String): String? =
        File(root, path).takeIf { it.isFile }?.readText()
}

/** The repo's real `catalog/`. */
internal object RealCatalog {
    val root: File get() = File(repoRoot, "catalog")

    val catalog: Catalog by lazy { Catalog.load(FileCatalogSource(root)) }
}
