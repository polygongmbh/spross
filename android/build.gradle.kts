import javax.inject.Inject

// AGP 9 built-in Kotlin: no kotlin("android") plugin, but the Compose compiler
// plugin is still required alongside it.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        // why: the pronunciation player reads the recordings straight out of the APK
        // (openFd), which answers only for a STORED entry — pinned here rather than
        // left to whatever AAPT's default no-compress list happens to carry.
        noCompress += "mp3"
    }
}

// why: catalog/ is the single in-repo content master — bundling goes through
// this task so the APK can never drift from it (mirrors the iOS folder resource).
abstract class SyncCatalogAssetsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogDir: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun run() {
        fs.sync {
            from(catalogDir) { exclude("README.md") }
            into(outputDir.dir("catalog"))
        }
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val syncTask = tasks.register<SyncCatalogAssetsTask>("sync${variantName}CatalogAssets") {
            catalogDir.from(rootProject.layout.projectDirectory.dir("catalog"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(syncTask, SyncCatalogAssetsTask::outputDir)
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
