// The sample asks for the plugin by id, the way a consumer will, and gets it from the build two
// directories up — so `./gradlew -p samples/ktor check` exercises the plugin as published, not
// as a project dependency.
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

rootProject.name = "ktor-sample"
