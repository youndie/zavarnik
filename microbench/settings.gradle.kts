// A build of its own, like bench/. It does not need the zavarnik plugin - nothing here is packaged
// or trained - so it declares its repositories directly and stays independent of the root build.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories { mavenCentral() }
}

rootProject.name = "microbench"
