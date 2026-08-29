import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.skie)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    jvm()

    androidLibrary {
        namespace = "net.spross.kern"
        compileSdk = 36
        minSdk = 26
    }

    js {
        browser()
        // why: one webpack bundle (jsBrowserDistribution) is what web/ deploys —
        // the marketing page loads it as a single classic script, no module graph.
        binaries.executable()
    }

    val xcf = XCFramework("SprossKern")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SprossKern"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// why: the day-part sweep walks every minute of the day in every language, far too slow for
// the commit gate. Only a catalog or trainer edit can move it, so the commit gate leaves it
// out; `-Psweeps` puts it back, and the release workflow always passes it.
val corpusSweeps = listOf("*ClockDayPartSweepTests")

tasks.named<Test>("jvmTest") {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // why: the palette-parity, layer-boundary and catalog lints read these trees as text, which
    // Gradle cannot see from the classpath — without naming them the task reports up-to-date
    // after a Swift or catalog edit, and the gate silently stops running for exactly the change
    // it guards. The layer gate reads BOTH app trees whole, which is why these are the roots
    // rather than the design and ui packages the palette check alone would need.
    inputs.files(
        rootProject.fileTree("App/Sources"),
        rootProject.fileTree("android/src/main"),
        rootProject.fileTree("Shared/Sources"),
        rootProject.fileTree("Watch/Sources"),
        rootProject.fileTree("Widgets/Sources"),
        rootProject.fileTree("WatchWidgets/Sources"),
        rootProject.fileTree("catalog") { exclude("audio/**") },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    if (!project.hasProperty("sweeps")) {
        filter { corpusSweeps.forEach { excludeTestsMatching(it) } }
    }
}
