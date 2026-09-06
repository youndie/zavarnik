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

dependencies {
    // The unit tests touch GradleException and Property; `java-gradle-plugin` puts the Gradle API on
    // the main classpath but, with these conventions, not on the unit-test runtime.
    testImplementation(gradleApi())
    testImplementation(kotlin("test"))
    "functionalTestImplementation"(kotlin("test-junit5"))
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
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
