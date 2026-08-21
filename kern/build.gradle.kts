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

// why: two corpus sweeps are 110 s of the suite's 145 s — ClockCollisionSweepTests walks every
// minute of the day in every language, TrainerFormsTypoBridgeGuardTests every authored form.
// Only a catalog or trainer-forms edit can move them, so the commit gate leaves them out;
// `-Psweeps` puts them back, and the release workflow always passes it.
val corpusSweeps = listOf("*ClockCollisionSweepTests", "*TrainerFormsTypoBridgeGuardTests")

tasks.named<Test>("jvmTest") {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    if (!project.hasProperty("sweeps")) {
        filter { corpusSweeps.forEach { excludeTestsMatching(it) } }
    }
}
