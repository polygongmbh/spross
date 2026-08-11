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

// why: one tag cuts both surfaces, so the marketing version keeps one home —
// project.yml's MARKETING_VERSION, which Xcode already reads. SPROSS_VERSION wins
// when set, which is how the release workflow hands the tag to this build.
val marketingVersion: String =
    System.getenv("SPROSS_VERSION")?.takeIf { it.isNotBlank() }
        ?: Regex("""^\s*MARKETING_VERSION:\s*([0-9]+(?:\.[0-9]+)*)""", RegexOption.MULTILINE)
            .find(providers.fileContents(rootProject.layout.projectDirectory.file("project.yml")).asText.get())
            ?.groupValues?.get(1)
        ?: error("MARKETING_VERSION not found in project.yml — Android has no version to build with")

// Rises with the name as long as minor and patch stay under 100. A code that does not
// rise is an update the package manager and Obtainium both refuse to install.
val versionCodeFromName: Int = marketingVersion.split(".").let { parts ->
    parts[0].toInt() * 10000 + (parts.getOrNull(1)?.toInt() ?: 0) * 100 + (parts.getOrNull(2)?.toInt() ?: 0)
}

// why: the release key never lives in the repo, and the environment is the only place
// this build looks for it — CI writes it out of a secret, a local build sources it from
// wherever the key is kept. One route, so a locally cut APK is signed like a released one.
fun signingEnv(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseKeystore: java.io.File? = signingEnv("SPROSS_KEYSTORE")?.let(::file)

// why: an unsigned APK is not a weakly signed one — Android's installer rejects it
// outright, so a release build with no key produces a file nobody can install. The
// demand lands on the packaging task rather than at configuration time, where it would
// take the debug build and the test gates down with it.
tasks.matching { it.name == "packageRelease" }.configureEach {
    val keystore = releaseKeystore
    doFirst {
        when {
            keystore == null -> error(
                "SPROSS_KEYSTORE is unset — set it to the release keystore, plus " +
                    "SPROSS_KEYSTORE_PASSWORD (and SPROSS_KEY_ALIAS if not 'spross'). " +
                    "scripts/release-keystore.sh writes an env file to source; see docs/distribution.md."
            )
            !keystore.isFile -> error("SPROSS_KEYSTORE points at no file: $keystore")
        }
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
        versionCode = versionCodeFromName
        versionName = marketingVersion
    }

    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = releaseKeystore
            storePassword = signingEnv("SPROSS_KEYSTORE_PASSWORD")
            keyAlias = signingEnv("SPROSS_KEY_ALIAS") ?: "spross"
            keyPassword = signingEnv("SPROSS_KEY_PASSWORD") ?: signingEnv("SPROSS_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
