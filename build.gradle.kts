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

    /* JSON parsing for Store license payloads. */
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

kotlin {

    /* Ensure public API is explicitly marked. */
    explicitApi()

    /* We use Java 25, because it comes with FFM. */
    jvmToolchain(25)

    sourceSets["main"].kotlin.srcDirs(
        file("build/generated/src/main/kotlin/")
    )
}

java {
    /* Publish sources jars to Maven Central. */
    withSourcesJar()
}

/* CMake output directory for the native DLL. */
val winrtBuildDir = layout.buildDirectory.dir("winrt")

/* Shared DLL filename used by native build/copy tasks. */
val winrtDllFileName = "msstore_winrt.dll"

/* Standard resource location for the Windows x64 native DLL. */
val windowsX64ResourceDir = layout.projectDirectory.dir("src/main/resources/windows-x86_64")

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
    description = "Configure the C++/WinRT build for $winrtDllFileName."

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
    description = "Build $winrtDllFileName (C++/WinRT)."

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

tasks.register<Copy>("buildNativeLib") {

    group = "native"
    description = "Build $winrtDllFileName and copy it to src/main/resources/windows-x86_64."

    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }

    dependsOn("buildWinrt")

    from(winrtBuildDir.map { it.dir("Release").file(winrtDllFileName) })
    into(windowsX64ResourceDir)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("buildNativeLib")
}

tasks.matching { it.name == "sourcesJar" || it.name == "kotlinSourcesJar" }.configureEach {
    dependsOn("buildNativeLib")
    dependsOn("generateBuildInfo")
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn("buildNativeLib")
}

tasks.withType<PublishToMavenLocal>().configureEach {
    dependsOn("buildNativeLib")
}
// endregion

// region BuildInfo.kt
val generatedBuildInfoFile = layout.buildDirectory.file(
    "generated/src/main/kotlin/de/stefan_oltmann/msstore/BuildInfo.kt"
)

val generateBuildInfo = tasks.register("generateBuildInfo") {

    group = "build"
    description = "Generate BuildInfo.kt with LIB_VERSION."

    outputs.file(generatedBuildInfoFile)

    doLast {

        val outputFile = generatedBuildInfoFile.get().asFile

        outputFile.parentFile.mkdirs()

        outputFile.printWriter().use { writer ->
            writer.println("package de.stefan_oltmann.msstore")
            writer.println()
            writer.println("internal const val LIB_VERSION: String = \"$version\"")
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildInfo)
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
                url = "https://stefan-oltmann.de"
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
