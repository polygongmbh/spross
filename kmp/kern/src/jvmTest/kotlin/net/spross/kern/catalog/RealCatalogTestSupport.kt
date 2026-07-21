package net.spross.kern.catalog

import java.io.File

internal class FileCatalogSource(private val root: File) : CatalogSource {
    override fun read(path: String): String? =
        File(root, path).takeIf { it.isFile }?.readText()
}

/** Locates the repo's real `catalog/` by walking up from the test working directory. */
internal object RealCatalog {
    val root: File by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "catalog/areas.json")
            if (candidate.isFile) return@lazy candidate.parentFile
            dir = dir.parentFile
        }
        error("catalog/areas.json not found above ${System.getProperty("user.dir")}")
    }

    val catalog: Catalog by lazy { Catalog.load(FileCatalogSource(root)) }
}
