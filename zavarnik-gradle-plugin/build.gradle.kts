plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    alias(libs.plugins.sborkaJvm)
    alias(libs.plugins.sborkaLint)
    alias(libs.plugins.sborkaPublish)
}

// Functional tests run real Gradle builds through TestKit against real JDKs — they train real AOT
// caches — and take minutes, so they live in their own source set and their own task rather than
// slowing `test` down. `check` still runs both.
val functionalTest: SourceSet = sourceSets.create("functionalTest")

gradlePlugin {
    plugins {
        create("zavarnik") {
            id = "io.github.youndie.zavarnik"
            implementationClass = "io.github.youndie.zavarnik.ZavarnikPlugin"
            displayName = "zavarnik — Leyden AOT cache for application-plugin apps"
            description =
                "Trains a Project Leyden AOT cache through the real start script, verifies that the " +
                "cache will be accepted, and ships it inside the distribution."
        }
    }
    testSourceSets(functionalTest)
}

// The functional tests compile against the plugin's own classes (JdkVersion decides what a test
// expects of the JDK it runs on) and run on JUnit 5. `kotlin("test")` picks the JUnit 5 variant
// only for the source sets KGP knows about, so the variant is named here.
functionalTest.compileClasspath += sourceSets.main.get().output
functionalTest.runtimeClasspath += sourceSets.main.get().output

// The runner is the implementation: the tasks call it in-process, and the distribution carries
// it as one self-contained jar so that `train` and `verify` also run where there is no Gradle —
// the runtime stage of a container image. The fat jar comes from the runner project's own
// consumable configuration and is embedded as a resource, so the published plugin needs nothing
// but its ordinary dependency on the runner at build time.
val runnerJar: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(project(":zavarnik-runner"))
    runnerJar(project(mapOf("path" to ":zavarnik-runner", "configuration" to "runnerJar")))
    // The unit tests touch GradleException and Property; `java-gradle-plugin` puts the Gradle API on
    // the main classpath but, with these conventions, not on the unit-test runtime.
    testImplementation(gradleApi())
    testImplementation(kotlin("test"))
    "functionalTestImplementation"(project(":zavarnik-runner"))
    "functionalTestImplementation"(kotlin("test-junit5"))
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from(runnerJar) {
        into("META-INF/zavarnik")
        rename { "zavarnik-runner.jar" }
    }
}

val functionalTestTask =
    tasks.register<Test>("functionalTest") {
        description = "Runs the TestKit tests: real builds, real JDKs, real caches."
        group = "verification"
        testClassesDirs = functionalTest.output.classesDirs
        classpath = functionalTest.runtimeClasspath
        useJUnitPlatform()
        shouldRunAfter(tasks.test)
    }

tasks.check { dependsOn(functionalTestTask) }
