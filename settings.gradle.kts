rootProject.name = "spross"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":kern")
// why: root-level kern/ would case-collide with Swift Kern/ on APFS — module lives under kmp/.
project(":kern").projectDir = file("kmp/kern")
