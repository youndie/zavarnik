// Ktor stand for B-01: the application the readiness timing is measured on. Plain `application`
// plugin, no zavarnik yet — the guard below is what the plugin will insert (research D2).
plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("io.ktor:ktor-server-cio:3.5.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

kotlin { jvmToolchain(25) }

application { mainClass = "stand.MainKt" }

tasks.startScripts {
    doLast {
        val anchor = "# Collect all arguments for the java command:"
        val guard = """
            |if [ -f "${'$'}APP_HOME/lib/app.aot" ]; then
            |    DEFAULT_JVM_OPTS="${'$'}DEFAULT_JVM_OPTS \"-XX:AOTCache=${'$'}APP_HOME/lib/app.aot\""
            |fi
            |
            |$anchor""".trimMargin()
        unixScript.writeText(unixScript.readText().replace(anchor, guard))
    }
}
