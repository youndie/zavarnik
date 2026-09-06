// A Ktor server on the `application` plugin with zavarnik: `./gradlew -p samples/ktor check` trains
// the cache through the start script, verifies that it is accepted and covers the application, and
// `distTar` ships it. This is the stand from experiments/ktor-readiness on the plugin instead of on
// hand-written glue.
plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    application
    id("io.github.youndie.zavarnik")
}

dependencies {
    implementation("io.ktor:ktor-server-cio:3.5.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

kotlin {
    jvmToolchain(25)
    // `-PlambdasClass`: compile lambdas as classes instead of invokedynamic (Kotlin 2.0's default),
    // for the RQ4 measurement in docs/research — which of the two the cache covers better.
    if (providers.gradleProperty("lambdasClass").isPresent) {
        compilerOptions.freeCompilerArgs.add("-Xlambdas=class")
    }
}

application { mainClass = "sample.MainKt" }

zavarnik {
    // Both the training run and production, through the start script's DEFAULT_JVM_OPTS.
    jvmArgs("-Dsample.port=18090")
    training {
        readyWhen.url("http://127.0.0.1:18090/health")
        // What the workload touches is what the cache holds: two routes, JSON in and out. Built-in
        // requests rather than curl, so the same build runs inside the Dockerfile's build stage.
        workload {
            get("http://127.0.0.1:18090/api/warm")
            post(
                "http://127.0.0.1:18090/api/order",
                "application/json",
                """{"items":[{"id":1,"name":"x","tags":["a"],"price":2.5}],"note":"n"}""",
            )
        }
    }
}
