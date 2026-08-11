plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.git.versioning)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
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

    testImplementation(kotlin("test"))
}

detekt {
    /* Only Kotlin sources; the resources dir receives the native DLL output. */
    source.setFrom("src/main/kotlin", "src/test/kotlin", "build.gradle.kts")
    config.setFrom("detekt.yml")
    allRules = true
    parallel = true
}

kover {
    reports {
        verify {
            rule {
                /* Everything except the native boundary is covered; this gate
                   protects the license logic and parsing from regressions. */
                minBound(60)
            }
        }
    }
}

kotlin {

    /* Ensure public API is explicitly marked. */
    explicitApi()

    /* We use Java 25 because it comes with FFM. */
    jvmToolchain(25)

    compilerOptions {

        /* Make the code safer */
        progressiveMode = true
        extraWarnings = true
        allWarningsAsErrors = true
    }

    sourceSets["main"].kotlin.srcDirs(
        file("build/generated/src/main/kotlin/")
    )
}

java {
    /* Publish sources jars to Maven Central. */
    withSourcesJar()
}

tasks.named<Test>("test") {

    /* Prove the FFM usage stays compatible with the future JEP 472 default. */
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--illegal-native-access=deny")

    /*
     * The blocked-access error contract is guarded by denyNativeAccessTest,
     * whose JVM runs without the enable flag; here those calls would reach
     * the real native layer, so they are excluded.
     */
    exclude("**/DeniedNativeAccess*Test.class")

    dependsOn("denyNativeAccessTest")
}

tasks.register<Test>("denyNativeAccessTest") {

    group = "verification"
    description = "Run the public API with native access denied (JEP 472)."

    /* Custom Test tasks are not wired by the plugin; use the main test output. */
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    /*
     * The future JEP 472 default blocks restricted FFM calls; these tests
     * assert that both public entry points then fail with the launch-option
     * help text. The DLL must load before the restricted check fires, which
     * only works on Windows, so the task is gated accordingly.
     */
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }

    jvmArgs("--illegal-native-access=deny")

    include("**/DeniedNativeAccess*Test.class")

    /*
     * One fresh JVM per test class, so both entry points observe the first
     * (ExceptionInInitializerError) initialization failure of MsStoreNative
     * instead of the cached NoClassDefFoundError of later attempts.
     */
    forkEvery = 1

    dependsOn("testClasses")
}

/* CMake output directory for the native DLL. */
val winrtBuildDir = layout.buildDirectory.dir("winrt")

/* Shared DLL filename used by native build/copy tasks. */
val winrtDllFileName = "msstore_winrt.dll"

/* Standard resource location for the Windows x64 native DLL. */
val windowsX64ResourceDir = layout.projectDirectory.dir("src/main/resources/windows-x86_64")

// region Tool resolvers

/* Common suffix of the CMake executable bundled with Visual Studio. */
val vsCmakeSuffix = """Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"""

/* CMake generator name for Visual Studio 2022. */
val vs2022CmakeGenerator = "Visual Studio 17 2022"

/* CMake generator name for Visual Studio 2026 (supported by CMake >= 4.2). */
val vs2026CmakeGenerator = "Visual Studio 18 2026"

/* Visual Studio version folders probed in order. */
val vsVersions = listOf("2026", "2022")

/* Visual Studio editions probed in order. */
val vsEditions = listOf("BuildTools", "Community", "Professional", "Enterprise")

/* Root directories that may contain Visual Studio installations. */
val vsRootDirs = listOf(
    """C:\Program Files\Microsoft Visual Studio""",
    """C:\Program Files (x86)\Microsoft Visual Studio"""
)

/* Candidate Visual Studio instance directories. */
val vsInstanceCandidates = vsRootDirs.flatMap { root ->
    vsVersions.flatMap { version ->
        vsEditions.map { edition -> "$root\\$version\\$edition" }
    }
}

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
        """C:\Program Files\CMake\bin\cmake.exe""",
        """C:\Program Files (x86)\CMake\bin\cmake.exe"""
    ) + vsInstanceCandidates.map { instance -> "$instance\\$vsCmakeSuffix" }

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

    return vsInstanceCandidates.firstOrNull { file(it).exists() }
}

/**
 * Resolves the CMake generator name matching the Visual Studio instance.
 *
 * VS 2026 requires the `Visual Studio 18 2026` generator, which is only
 * supported by CMake >= 4.2. Falls back to the VS 2022 generator.
 */
fun resolveCmakeGenerator(vsInstance: String?): String =
    if (vsInstance?.contains("\\2026\\") == true)
        vs2026CmakeGenerator
    else
        vs2022CmakeGenerator
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
            "-G", resolveCmakeGenerator(vsInstance),
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

/* Versions become Kotlin source literals, file names, and Maven coordinates. */
private val versionPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

val generatedBuildInfoFile = layout.buildDirectory.file(
    "generated/src/main/kotlin/de/stefan_oltmann/msstore/BuildInfo.kt"
)

val generateBuildInfo = tasks.register("generateBuildInfo") {

    group = "build"
    description = "Generate BuildInfo.kt with LIB_VERSION."

    outputs.file(generatedBuildInfoFile)

    doLast {

        val releaseVersion = version.toString()

        /*
         * Fail fast on unsafe version strings instead of breaking the
         * release build with a confusing compile or file-name error.
         */
        if (!versionPattern.matches(releaseVersion))
            throw GradleException(
                "Invalid project version '$releaseVersion'. " +
                    "Version must match $versionPattern (letters, digits, '.', '-', '_')."
            )

        val outputFile = generatedBuildInfoFile.get().asFile

        outputFile.parentFile.mkdirs()

        outputFile.printWriter().use { writer ->
            writer.println("package de.stefan_oltmann.msstore")
            writer.println()
            writer.println("internal const val LIB_VERSION: String = \"$releaseVersion\"")
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildInfo)
}
// endregion

// region Writing version.txt for GitHub Actions
val writeVersion: TaskProvider<Task> = tasks.register("writeVersion") {
    group = "build"
    description = "Write the current project version to version.txt."
    doLast {
        val versionFile = layout.buildDirectory.file("version.txt").get().asFile
        versionFile.parentFile.mkdirs()
        versionFile.writeText(project.version.toString())
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
            connection = "scm:git:https://github.com/StefanOltmann/msstorelib.git"
            developerConnection = "scm:git:ssh://git@github.com/StefanOltmann/msstorelib.git"
        }
    }
}
// endregion
