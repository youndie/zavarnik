// The Ktor Gradle plugin's own container path — `ktor { docker { } }`, which is Jib underneath —
// with zavarnik on top. The plugin comes from the build two directories up, as in samples/ktor.
pluginManagement {
    includeBuild("../..")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories { mavenCentral() }
}

rootProject.name = "ktor-jib-sample"
