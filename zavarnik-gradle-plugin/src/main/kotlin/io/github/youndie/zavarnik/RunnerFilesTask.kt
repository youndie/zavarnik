package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.Main
import io.github.youndie.zavarnik.runner.RunnerConfig
import io.github.youndie.zavarnik.runner.WorkloadStep
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.time.Duration

/**
 * The two files the distribution's `lib/` carries so that training and verification can run
 * without Gradle — inside the runtime stage of a container image, on a bare JRE:
 *
 * - `zavarnik.properties`, the `zavarnik { }` block as the runner reads it;
 * - `zavarnik-runner.jar`, the runner with the Kotlin stdlib inside, taken from the plugin's own
 *   resources — it is the same code the `aotTrain` and `aotVerify` tasks call in-process.
 *
 * `java -cp lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main train` then does what
 * `aotTrain` does, and `verify` what `aotVerify` does, with the same messages.
 */
@CacheableTask
public abstract class RunnerFilesTask : DefaultTask() {
    @get:Input
    public abstract val cacheFileName: Property<String>

    @get:Input
    @get:Optional
    public abstract val readyUrl: Property<String>

    @get:Input
    @get:Optional
    public abstract val exitAfter: Property<Duration>

    @get:Input
    public abstract val readyTimeout: Property<Duration>

    @get:Input
    public abstract val shutdownTimeout: Property<Duration>

    @get:Input
    public abstract val workload: ListProperty<WorkloadStep>

    @get:Input
    public abstract val minCachedShare: Property<Double>

    @get:Input
    public abstract val verifyJvmArgs: ListProperty<String>

    /** For a Jib image: the flags the entrypoint starts with, which the runner has to repeat. Empty otherwise. */
    @get:Input
    public abstract val launchJvmArgs: ListProperty<String>

    /** Remote ports a CRaC checkpoint may leave open; see `crac { ignoreRemotePort(…) }`. */
    @get:Input
    public abstract val cracIgnoredRemotePorts: ListProperty<Int>

    /** The snapshot's directory name under the output directory. */
    @get:Input
    public abstract val cracImageDirName: Property<String>

    /** `build/zavarnik/runner/`, copied into `lib/` by the distribution. */
    @get:OutputDirectory
    public abstract val outputDir: DirectoryProperty

    @TaskAction
    public fun write() {
        val dir = outputDir.get().asFile
        RunnerConfig(
            cacheFileName = cacheFileName.get(),
            readyUrl = readyUrl.orNull,
            exitAfter = exitAfter.orNull,
            readyTimeout = readyTimeout.get(),
            shutdownTimeout = shutdownTimeout.get(),
            workload = workload.get(),
            minCachedShare = minCachedShare.get(),
            verifyJvmArgs = verifyJvmArgs.get(),
            launchJvmArgs = launchJvmArgs.get(),
            cracIgnoredRemotePorts = cracIgnoredRemotePorts.get(),
            cracImageDirName = cracImageDirName.get(),
        ).write(File(dir, RunnerConfig.FILE_NAME))
        val embedded =
            Main::class.java.getResourceAsStream(EMBEDDED_RUNNER)
                ?: throw GradleException(
                    "zavarnik: the plugin jar carries no $EMBEDDED_RUNNER — a broken plugin build.",
                )
        embedded.use { input -> File(dir, RUNNER_JAR_NAME).outputStream().use { input.copyTo(it) } }
    }

    public companion object {
        /** The runner jar's name inside `lib/`. */
        public const val RUNNER_JAR_NAME: String = "zavarnik-runner.jar"
        private const val EMBEDDED_RUNNER = "/META-INF/zavarnik/zavarnik-runner.jar"
    }
}
