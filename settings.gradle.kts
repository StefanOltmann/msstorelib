pluginManagement {
    repositories {
        /* Resolve Gradle plugins from official sources. */
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    /* Ensures JVM toolchains can be resolved from Foojay. */
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

/* Published project name. */
rootProject.name = "msstorelib"
