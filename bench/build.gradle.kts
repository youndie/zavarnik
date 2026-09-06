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
    // `-PnoAssertions`: RQ1's compiler-flag half — no null checks on parameters, calls and receivers.
    if (providers.gradleProperty("noAssertions").isPresent) {
        compilerOptions.freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
        )
    }
}

application { mainClass = "bench.MainKt" }

// One payload, shared with profile/run.sh through bench/profile/order.json — the file is what oha posts.
val orderPayload: String = file("profile/order.json").readText()

zavarnik {
    jvmArgs("-Dbench.port=18100")
    training {
        readyWhen.url("http://127.0.0.1:18100/health")
        workload {
            get("http://127.0.0.1:18100/echo?msg=warm")
            post(
                "http://127.0.0.1:18100/items",
                "application/json",
                """{"sku":"AB-1234","name":"warm","price":1.5,"tags":["a"]}""",
            )
            get("http://127.0.0.1:18100/items?limit=10")
            post("http://127.0.0.1:18100/business", "application/json", orderPayload)
        }
    }
}

