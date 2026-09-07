// The Ktor Gradle plugin's container path — `ktor { docker { } }`, which is Jib underneath — with
// zavarnik on top. Two invocations, because the image with the cache is the image without it plus
// one layer, and Jib's configuration is read once per build:
//
//     ./gradlew -p samples/ktor-jib jibAotTrain        # jibDockerBuild, train inside the image
//     ./gradlew -p samples/ktor-jib jibAotVerify       # jibDockerBuild again, now with the cache, verify
//
// `jib-check.sh` does both and then starts the image with the cache made mandatory.
plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("io.ktor.plugin") version "3.5.2"
    id("io.github.youndie.zavarnik")
}

dependencies {
    implementation("io.ktor:ktor-server-cio:3.5.2")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

kotlin { jvmToolchain(25) }

application { mainClass = "sample.MainKt" }

ktor {
    docker {
        jreVersion = JavaVersion.VERSION_25
        localImageName = "zavarnik-ktor-jib-sample"
        imageTag = "latest"
    }
}

// Jars, not exploded classes: the JVM writes no AOT cache for a classpath with a directory on it,
// and Jib's default layout has two. zavarnik refuses the default at configuration time.
jib { containerizingMode = "packaged" }

zavarnik {
    jvmArgs("-Dsample.port=18090")
    training {
        readyWhen.url("http://127.0.0.1:18090/health")
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
