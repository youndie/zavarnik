import org.gradle.api.file.ArchiveOperations
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    alias(libs.plugins.sborkaJvm)
    alias(libs.plugins.sborkaLint)
    alias(libs.plugins.sborkaPublish)
    // The Gradle Plugin Portal: what `id("io.github.youndie.zavarnik") version "…"` resolves from
    // with no repository declared. Applied here, not by the conventions, because the metadata below
    // is this repository's own; the upload runs from sborka's portal.yaml, where the key lives.
    alias(libs.plugins.pluginPublish)
}

// Functional tests run real Gradle builds through TestKit against real JDKs — they train real AOT
// caches — and take minutes, so they live in their own source set and their own task rather than
// slowing `test` down. `check` still runs both.
val functionalTest: SourceSet = sourceSets.create("functionalTest")

gradlePlugin {
    website = "https://github.com/youndie/zavarnik"
    vcsUrl = "https://github.com/youndie/zavarnik.git"
    plugins {
        create("zavarnik") {
            id = "io.github.youndie.zavarnik"
            implementationClass = "io.github.youndie.zavarnik.ZavarnikPlugin"
            displayName = "zavarnik — Leyden AOT cache for application-plugin apps"
            description =
                "Trains a Project Leyden AOT cache (JDK 25+) through the real start script, verifies that the " +
                "JVM will accept it, and ships it inside the distribution or the container image — for Ktor " +
                "and any other plain JVM application on the application plugin."
            tags = listOf("leyden", "aot", "startup", "jvm", "ktor", "docker", "jib")
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
// the runtime stage of a container image. Its classes go *into* the plugin jar and the fat jar
// goes in as a resource, so the published plugin is one artifact with no dependency of its own
// to resolve: the runner is not published separately.
val runnerJar =
    configurations.create("runnerJar") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
val runnerClasses =
    configurations.create("runnerClasses") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }

dependencies {
    compileOnly(project(":zavarnik-runner"))
    runnerClasses(project(":zavarnik-runner"))
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

// What the portal shows next to the version: compatibility with the configuration cache. True,
// and tested — the distribution tests run with --configuration-cache since a consumer with it on
// found the zip warning. Gradle 9.7 writes `compatibility.feature.configuration-cache=UNDECLARED`
// into the plugin descriptor and `PluginDeclaration` has no field to change it, so the value is
// replaced after Gradle has written it; `DECLARED_SUPPORTED` is what plugin-publish's own
// descriptor carries and what its validation accepts.
tasks.pluginDescriptors {
    val descriptors = outputDirectory
    doLast {
        descriptors
            .get()
            .asFile
            .listFiles { file -> file.name.endsWith(".properties") }
            ?.forEach { file ->
                file.writeText(
                    file.readText().replace(
                        "compatibility.feature.configuration-cache=UNDECLARED",
                        "compatibility.feature.configuration-cache=DECLARED_SUPPORTED",
                    ),
                )
            }
    }
}

tasks.processResources {
    from(runnerJar) {
        into("META-INF/zavarnik")
        rename { "zavarnik-runner.jar" }
    }
}

tasks.jar {
    // `zipTree` from the script would drag the script object into the task; the injected service does not.
    val archives = serviceOf<ArchiveOperations>()
    from(runnerClasses.elements.map { jars -> jars.map { jar -> archives.zipTree(jar.asFile) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// TestKit's plugin classpath is the main runtime classpath, which a compileOnly dependency is
// not on; the runner's classes have to be there for the plugin under test to load.
tasks.pluginUnderTestMetadata { pluginClasspath.from(runnerClasses) }

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
