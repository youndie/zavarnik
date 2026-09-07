package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.Installation
import io.github.youndie.zavarnik.runner.RunnerConfig
import io.github.youndie.zavarnik.runner.RunnerException
import io.github.youndie.zavarnik.runner.Training
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
 * Trains the AOT cache: the runner's [Training] over the `installDist` output, with the
 * configuration read back from the `lib/zavarnik.properties` the distribution carries — the same
 * path `java -cp lib/zavarnik-runner.jar … train` takes inside a container, so that what this
 * task proves holds there too.
 */
@DisableCachingByDefault(
    because =
        "the cache is specific to the JDK build and the CPU that trained it, and a cache restored " +
            "from the build cache for the wrong machine is exactly what this plugin exists to prevent",
)
public abstract class AotTrainTask : DefaultTask() {
    /** The `installDist` output: `bin/`, `lib/`, and the runner's files in `lib/`. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val installDir: DirectoryProperty

    /** Name of the start script inside `bin/` — the application name. */
    @get:Input
    public abstract val scriptName: Property<String>

    /** The toolchain JDK; the script is pointed at it through `JAVA_HOME`. */
    @get:Nested
    public abstract val javaLauncher: Property<JavaLauncher>

    /** `lib/<cacheFileName>`. */
    @get:OutputFile
    public abstract val cacheFile: RegularFileProperty

    /** `lib/<cacheFileName>.jars`. */
    @get:OutputFile
    public abstract val manifestFile: RegularFileProperty

    /** Where the application's output goes: `build/zavarnik/aotTrain.log`. */
    @get:OutputFile
    public abstract val logFile: RegularFileProperty

    @TaskAction
    public fun train() {
        val installation = Installation(installDir.get().asFile, scriptName.get())
        val config = RunnerConfig.read(installation.config)
        val javaHome =
            javaLauncher
                .get()
                .metadata.installationPath.asFile
        try {
            Training(installation, config, javaHome, logFile.get().asFile, logger::lifecycle).run()
        } catch (failed: RunnerException) {
            throw GradleException(failed.message ?: failed.toString(), failed)
        }
    }
}
