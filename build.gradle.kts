plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.git.versioning)
}

/* Publishing coordinates for Maven artifacts. */
group = "de.stefan_oltmann.msstorelib"
description = "Kotlin/JVM API for Microsoft Store."
version = "0.0.0"

gitVersioning.apply {

    refs {

        /* The main branch contains the current dev version */
        branch("main") {
            version = "\${commit.short}"
        }

        /* Releases have real version numbers */
        tag("(?<version>.*)") {
            version = "\${ref.version}"
        }
    }

    /* Fallback if the branch was not found (for feature branches) */
    rev {
        version = "\${commit.short}"
    }
}

repositories {
    mavenCentral()
}

dependencies {

    /* JNA bridges JVM <-> native DLL. */
    implementation(libs.jna)

    /* JSON parsing for Store license payloads. */
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

kotlin {

    /* Ensure public API is explicitly marked. */
    explicitApi()

    /* Align with the minimum supported runtime. */
    jvmToolchain(17)
}

java {
    /* Publish sources jars to Maven Central. */
    withSourcesJar()
}

/* CMake output directory for the native DLL. */
val winrtBuildDir = layout.buildDirectory.dir("winrt")

/* Staging directory for embedding the DLL into JVM resources. */
val embeddedNativeDir = layout.buildDirectory.dir("generated-resources/native")

// region Tool resolvers

/**
 * Resolves the CMake executable path.
 *
 * Uses MSSTORE_CMAKE when provided, otherwise tries common install locations.
 * Falls back to `cmake` to rely on PATH resolution.
 */
fun resolveCmakeExe(): String {

    val override = System.getenv("MSSTORE_CMAKE")?.takeIf { it.isNotBlank() }

    if (override != null)
        return override

    val candidates = listOf(
        "C:\\Program Files\\CMake\\bin\\cmake.exe",
        "C:\\Program Files (x86)\\CMake\\bin\\cmake.exe",
        "C:\\Program Files\\Microsoft Visual Studio\\2022\\BuildTools\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe",
        "C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe",
        "C:\\Program Files\\Microsoft Visual Studio\\2022\\Professional\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe",
        "C:\\Program Files\\Microsoft Visual Studio\\2022\\Enterprise\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe",
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\BuildTools\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe",
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\Community\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe",
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\Professional\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe",
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\Enterprise\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe"
    )

    val resolved = candidates.firstOrNull { file(it).exists() }

    return resolved ?: "cmake"
}

/**
 * Resolves the Visual Studio instance used by the CMake generator.
 *
 * Uses MSSTORE_VS_INSTANCE when provided, otherwise tries common install
 * locations and returns the first match.
 */
fun resolveVisualStudioInstance(): String? {

    val override = System.getenv("MSSTORE_VS_INSTANCE")?.takeIf { it.isNotBlank() }

    if (override != null)
        return override

    val candidates = listOf(
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\BuildTools",
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\Community",
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\Professional",
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\Enterprise",
        "C:\\Program Files\\Microsoft Visual Studio\\2022\\BuildTools",
        "C:\\Program Files\\Microsoft Visual Studio\\2022\\Community",
        "C:\\Program Files\\Microsoft Visual Studio\\2022\\Professional",
        "C:\\Program Files\\Microsoft Visual Studio\\2022\\Enterprise"
    )

    return candidates.firstOrNull { file(it).exists() }
}
// endregion

// region Native build tasks for msstore_winrt.dll.

tasks.register<Exec>("configureWinrt") {

    group = "native"
    description = "Configure the C++/WinRT build for msstore_winrt.dll."

    /* The native DLL only builds on Windows. */
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }

    doFirst {

        val cmakeExe = resolveCmakeExe()
        val vsInstance = resolveVisualStudioInstance()

        val baseArgs = mutableListOf(
            cmakeExe,
            "-S", "native/winrt",
            "-B", winrtBuildDir.get().asFile.absolutePath,
            "-G", "Visual Studio 17 2022",
            "-A", "x64"
        )

        if (vsInstance != null)
            baseArgs.add("-DCMAKE_GENERATOR_INSTANCE=$vsInstance")

        commandLine(baseArgs)
    }
}

tasks.register<Exec>("buildWinrt") {

    group = "native"
    description = "Build msstore_winrt.dll (C++/WinRT)."

    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }

    dependsOn("configureWinrt")

    doFirst {
        val cmakeExe = resolveCmakeExe()

        commandLine(
            cmakeExe,
            "--build", winrtBuildDir.get().asFile.absolutePath,
            "--config", "Release"
        )
    }
}

tasks.register("buildNative") {

    group = "native"
    description = "Build msstore_winrt.dll (C++/WinRT)."

    dependsOn("buildWinrt")
}

tasks.register<Copy>("prepareEmbeddedNative") {

    group = "native"
    description = "Embed msstore_winrt.dll into the JVM resources."

    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }

    dependsOn("buildWinrt")

    /* Copy the Release DLL into a generated resources' folder. */
    from(winrtBuildDir.map { it.dir("Release").file("msstore_winrt.dll") })
    into(embeddedNativeDir.map { it.dir("native") })
}

tasks.named<ProcessResources>("processResources") {

    /* Ensure the DLL is embedded before resources are packaged. */
    dependsOn("prepareEmbeddedNative")

    from(embeddedNativeDir)
}
// endregion

// region Writing version.txt for GitHub Actions
val writeVersion: TaskProvider<Task> = tasks.register("writeVersion") {
    doLast {
        File("build/version.txt").writeText(project.version.toString())
    }
}

tasks.getByPath("build").finalizedBy(writeVersion)
// endregion

// region Maven publish

val signingEnabled: Boolean = System.getenv("SIGNING_ENABLED")?.toBoolean() ?: false

mavenPublishing {

    /* Use the Vanniktech plugin to publish to Maven Central. */
    publishToMavenCentral()

    if (signingEnabled)
        signAllPublications()

    coordinates(
        groupId = "de.stefan-oltmann",
        artifactId = "msstorelib",
        version = version.toString()
    )

    pom {

        name = "msstorelib"
        description = "Library to query Microsoft Store license info from Kotlin/JVM apps."
        url = "https://github.com/StefanOltmann/msstorelib"

        licenses {
            license {
                name = "Apache License 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                name = "Stefan Oltmann"
                url = "https://stefan-oltmann.de/"
                roles = listOf("maintainer", "developer")
                properties = mapOf("github" to "StefanOltmann")
            }
        }

        scm {
            url = "https://github.com/StefanOltmann/msstorelib"
            connection = "scm:git:git://github.com/StefanOltmann/msstorelib.git"
        }
    }
}
// endregion
