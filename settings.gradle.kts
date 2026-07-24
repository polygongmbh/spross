rootProject.name = "spross"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":kern")
// why: root-level kern/ would case-collide with Swift Kern/ on APFS — module lives under kmp/.
project(":kern").projectDir = file("kmp/kern")
