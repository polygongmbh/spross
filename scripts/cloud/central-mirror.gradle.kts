// Cloud-only: Maven Central rate-limits the shared egress IP (429); Google's mirror of it does not.
// Google's own repository goes next, so its artifacts never fall through to the plugin portal's Central redirect.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.mirrorFirst() {
    val central = maven { name = "CentralMirror"; url = uri(mirror) }
    val googleRepo = maven { name = "GoogleFirst"; url = uri("https://dl.google.com/dl/android/maven2/") }
    remove(central); remove(googleRepo)
    add(0, central); add(1, googleRepo)
}
settingsEvaluated {
    pluginManagement.repositories.mirrorFirst()
    dependencyResolutionManagement.repositories.mirrorFirst()
}
