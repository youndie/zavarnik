plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.sborkaJvm)
    alias(libs.plugins.sborkaLint)
    alias(libs.plugins.sborkaPublish)
}

// The runner is what trains and verifies a cache; the Gradle tasks are wrappers over it. It has
// no dependency but the Kotlin stdlib, so that `java -cp lib/zavarnik-runner.jar … train` works on
// the bare JRE of a runtime image — where there is no Gradle and, in Temurin's images, no curl.
dependencies {
    testImplementation(kotlin("test"))
}

// One jar with the stdlib inside, for the distribution's lib/. Nothing else is ever on its
// classpath, so nothing it carries can clash with the application's own jars.
val fatJar by tasks.registering(Jar::class) {
    archiveClassifier = "all"
    manifest { attributes("Main-Class" to "io.github.youndie.zavarnik.runner.Main") }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.map { classpath -> classpath.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/versions/**")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// What the plugin embeds: a consumable configuration, so the plugin's build declares a dependency
// on this project's output instead of reaching into its task graph.
val runnerJar: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts { add(runnerJar.name, fatJar) }
