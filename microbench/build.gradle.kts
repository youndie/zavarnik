plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("kapt") version "2.4.20"
    id("me.champeau.jmh") version "0.7.3"
}

// Pinned, like everything else this phase measures with: the brief's rule is that the stack is
// fixed before the first measurement, and a benchmark harness is part of the stack.
dependencies {
    jmh("org.openjdk.jmh:jmh-core:1.37")
    kaptJmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    jmh("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

kotlin { jvmToolchain(25) }

jmh {
    // Defaults that make a run publishable rather than indicative. Three forks because one fork
    // measures one JIT history; the machine this runs on cannot fix its governor, so the fork count
    // is what buys back some of that.
    fork = 3
    warmupIterations = 5
    iterations = 5
    timeOnIteration = "2s"
    warmup = "2s"
    resultFormat = "JSON"
    resultsFile = layout.buildDirectory.file("reports/jmh/results.json")
}
