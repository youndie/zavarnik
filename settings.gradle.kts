rootProject.name = "zavarnik"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Written out by hand, and it has to be: `pluginManagement` is evaluated before any settings
        // plugin is applied — including the sborka one, which is fetched through it.
        maven("https://reposilite.kotlin.website/snapshots") {
            name = "wip-snapshots"
            content { includeGroupByRegex("io\\.github\\.youndie.*") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // Repositories with content filters, the shared `wip` catalog, and the check that this
    // repository's `.editorconfig` is the one the rest of the portfolio uses.
    id("io.github.youndie.sborka.settings") version "0.3.0.31"
}

include(":zavarnik-gradle-plugin")
