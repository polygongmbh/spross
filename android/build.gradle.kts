import javax.inject.Inject

// AGP 9 built-in Kotlin: no kotlin("android") plugin, but the Compose compiler
// plugin is still required alongside it.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// why: kern compiles on the JDK 21 toolchain — unpinned, this module would inherit
// whatever JVM launched the daemon and its unit tests could not load kern's classes.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

android {
    namespace = "net.spross.app"
    compileSdk = 36

    buildFeatures {
        compose = true
    }

    defaultConfig {
        applicationId = "net.spross.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0" // Android surface versions independently for now
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    androidResources {
        // why: the pronunciation player and the cue pool read their clips straight out of
        // the APK (openFd), which answers only for a STORED entry — pinned here rather
        // than left to whatever AAPT's default no-compress list happens to carry.
        noCompress += listOf("mp3", "wav")
    }
}

// why: catalog/ and the feedback sounds are in-repo masters authored once for both
// platforms — bundling goes through this task so the APK can never drift from either.
abstract class SyncAssetsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: ConfigurableFileCollection

    /** Where under `assets/` the copy lands — what the app opens it by. */
    @get:Input
    abstract val subdir: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun run() {
        fs.sync {
            from(sourceDir) { exclude("README.md") }
            into(outputDir.dir(subdir.get()))
        }
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val assets = variant.sources.assets
        val syncCatalog = tasks.register<SyncAssetsTask>("sync${variantName}CatalogAssets") {
            sourceDir.from(rootProject.layout.projectDirectory.dir("catalog"))
            subdir.set("catalog")
        }
        assets?.addGeneratedSourceDirectory(syncCatalog, SyncAssetsTask::outputDir)
        // The chimes `scripts/sounds.py` writes; they live under the iOS app's resources
        // because that is where the script has always put them, and CueSounds plays those
        // very bytes so a re-tune by ear reaches both platforms at once.
        val syncSounds = tasks.register<SyncAssetsTask>("sync${variantName}SoundAssets") {
            sourceDir.from(rootProject.layout.projectDirectory.dir("App/Resources/Sounds"))
            subdir.set("sounds")
        }
        assets?.addGeneratedSourceDirectory(syncSounds, SyncAssetsTask::outputDir)
    }
}

dependencies {
    implementation(project(":kern"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    testImplementation(libs.kotlin.test.junit)
}
