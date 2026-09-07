package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.Installation
import io.github.youndie.zavarnik.runner.RunnerConfig
import io.github.youndie.zavarnik.runner.RunnerException
import io.github.youndie.zavarnik.runner.Verification
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault

/**
 * Fails the build when the cache will not do what it is shipped for: the runner's [Verification]
 * over the `installDist` output — the manifest, `-XX:AOTMode=on`, the cached share — with the
 * configuration read back from `lib/zavarnik.properties`, exactly as `java -cp
 * lib/zavarnik-runner.jar … verify` does inside a container.
 *
 * The cache is deliberately not an `@InputFile`: its absence is the first thing reported, and
 * Gradle would refuse to run the task instead of letting it explain. Both files live inside
 * [installDir], so their content is tracked there.
 */
@DisableCachingByDefault(
    because = "runs the application; the answer depends on the machine and the JDK build, not on the inputs",
)
public abstract class AotVerifyTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val installDir: DirectoryProperty

    @get:Input
    public abstract val scriptName: Property<String>

    @get:Nested
    public abstract val javaLauncher: Property<JavaLauncher>

    /** The application's output, `-Xlog` included: `build/zavarnik/aotVerify.log`. */
    @get:OutputFile
    public abstract val logFile: RegularFileProperty

    /** One-line summary of what was measured: `build/zavarnik/aotVerify.txt`. */
    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    @TaskAction
    public fun verify() {
        val installation = Installation(installDir.get().asFile, scriptName.get())
        val config = RunnerConfig.read(installation.config)
        val javaHome =
            javaLauncher
                .get()
                .metadata.installationPath.asFile
        val summary =
            try {
                Verification(installation, config, javaHome, logFile.get().asFile, logger::lifecycle).run()
            } catch (failed: RunnerException) {
                throw GradleException(failed.message ?: failed.toString(), failed)
            }
        reportFile.get().asFile.writeText(summary + "\n")
    }
}
