// The benchmark service for the optimizer research (docs/research/research-optimizer.md). A build
// of its own, like samples/ktor, and on the zavarnik plugin the same way, so warmup numbers can
// be taken with the AOT cache in place when the research asks for them.
pluginManagement {
    includeBuild("..")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories { mavenCentral() }
}

rootProject.name = "bench"
