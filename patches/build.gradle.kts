import java.io.File
import java.util.Properties

group = "app.prathxm.chess"

patches {
    about {
        name = "Prathxm Patches"
        description = "Custom Morphe patches for Chess.com — Ad-free experience and local offline engine tools"
        source = "git@github.com:PrathxmOp/Prathxm-Patches.git"
        author = "Prathxm"
        contact = "github.com/PrathxmOp"
        website = "github.com/PrathxmOp/Prathxm-Patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

fun findAndroidSdkDir(): File {
    // 1. Check local.properties in root directory
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        val properties = Properties()
        localPropertiesFile.inputStream().use { properties.load(it) }
        val sdkDir = properties.getProperty("sdk.dir")
        if (sdkDir != null) {
            val file = File(sdkDir)
            if (file.exists()) return file
        }
    }

    // 2. Check environment variables
    for (envVar in listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
        val sdkPath = System.getenv(envVar)
        if (sdkPath != null) {
            val file = File(sdkPath)
            if (file.exists()) return file
        }
    }

    // 3. System-specific default fallbacks
    val homeDir = System.getProperty("user.home")
    val fallbacks = listOf(
        "$homeDir/Android/Sdk",
        "/usr/local/lib/android/sdk" // GitHub Actions runner location
    )
    for (path in fallbacks) {
        val file = File(path)
        if (file.exists()) return file
    }

    throw GradleException("Android SDK not found! Please set ANDROID_HOME environment variable or sdk.dir in local.properties")
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    compileOnly(libs.gson)

    val sdkDir = findAndroidSdkDir()
    val platformsDir = File(sdkDir, "platforms")
    val platformDir = platformsDir.listFiles()
        ?.filter { it.name.startsWith("android-") }
        ?.sortedByDescending { it.name }
        ?.firstOrNull()
        ?: throw GradleException("No Android platforms found in ${platformsDir.absolutePath}")

    val androidJar = File(platformDir, "android.jar")
    if (!androidJar.exists()) {
        throw GradleException("android.jar not found in ${platformDir.absolutePath}")
    }

    compileOnly(files(androidJar))
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        val sdkDir = findAndroidSdkDir()
        val platformsDir = File(sdkDir, "platforms")
        val platformDir = platformsDir.listFiles()
            ?.filter { it.name.startsWith("android-") }
            ?.sortedByDescending { it.name }
            ?.firstOrNull()
            ?: throw GradleException("No Android platforms found in ${platformsDir.absolutePath}")

        val androidJar = File(platformDir, "android.jar")

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath + files(androidJar)
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }

}