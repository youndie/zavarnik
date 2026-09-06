package io.github.youndie.zavarnik

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.time.Duration

/**
 * Fails the build when the cache will not do what it is shipped for. Three independent checks,
 * any one of which is enough, and the message names which:
 *
 * 1. the jars in `lib/` match the manifest `aotTrain` wrote — the check the JVM itself skips on
 *    the builds that carry JDK-8377932;
 * 2. the application starts under `-XX:AOTMode=on`, where a rejected cache is fatal instead of
 *    three lines on stderr and exit code 0;
 * 3. at least [minCachedShare] of the application's loaded classes came from the cache — a
 *    cache that is accepted but empty for the application means the training run did not
 *    exercise it, and neither of the checks above can tell.
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

    /**
     * The cache `aotTrain` produced; its absence is the first thing reported, which is why this is
     * not an `@InputFile` — Gradle would refuse to run the task instead of letting it explain.
     * Both files live inside [installDir], so their content is tracked there.
     */
    @get:Internal
    public abstract val cacheFile: RegularFileProperty

    @get:Internal
    public abstract val manifestFile: RegularFileProperty

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

    /** `0.0`–`1.0`. */
    @get:Input
    public abstract val minCachedShare: Property<Double>

    /** The application's output, `-Xlog` included: `build/zavarnik/aotVerify.log`. */
    @get:OutputFile
    public abstract val logFile: RegularFileProperty

    /** One-paragraph summary of what was measured: `build/zavarnik/aotVerify.txt`. */
    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    @TaskAction
    public fun verify() {
        val install = installDir.get().asFile
        val lib = File(install, "lib")
        val cache = cacheFile.orNull?.asFile
        val manifest = manifestFile.orNull?.asFile
        if (cache == null || !cache.isFile) {
            throw GradleException("zavarnik: no AOT cache in ${lib.path} — run aotTrain first.")
        }
        if (manifest == null || !manifest.isFile) {
            throw GradleException("zavarnik: ${cache.name} has no manifest next to it — run aotTrain again.")
        }
        val differences = JarManifest.differences(lib, manifest)
        if (differences.isNotEmpty()) {
            throw GradleException(
                "zavarnik: the jars in lib/ are not the ones ${cache.name} was trained against:\n" +
                    differences.joinToString("\n") { "  - $it" } +
                    "\nRun aotTrain again after every change to the classpath.",
            )
        }

        val run =
            StartScriptRun(
                script = File(install, "bin/${scriptName.get()}"),
                javaHome =
                    javaLauncher
                        .get()
                        .metadata.installationPath.asFile,
                javaOpts = listOf("-XX:AOTMode=on", "-Xlog:class+load=info", "-Xlog:aot=info"),
                log = logFile.get().asFile,
            )
        run.start()
        try {
            if (readyUrl.isPresent) {
                run.awaitReady(readyUrl.get(), readyTimeout.get())
            } else {
                Thread.sleep(exitAfter.get().toMillis())
                if (!run.isAlive) {
                    throw GradleException(
                        "zavarnik: the application exited under -XX:AOTMode=on.\n${run.logTail()}",
                    )
                }
            }
        } catch (failed: GradleException) {
            run.kill()
            throw GradleException(
                "zavarnik: the application did not start with the cache under -XX:AOTMode=on. " +
                    "The JVM's reasons:\n${aotLines()}\n\n${failed.message}",
                failed,
            )
        }
        run.stop(shutdownTimeout.get())

        val loaded = LoadedClasses.of(logFile.get().asFile, lib)
        val summary =
            "${loaded.applicationFromCache} of ${loaded.applicationTotal} application classes " +
                "(${percent(loaded.applicationShare)}) came from ${cache.name}; " +
                "${loaded.fromCache} of ${loaded.total} loaded classes overall"
        reportFile.get().asFile.writeText(summary + "\n")
        if (loaded.applicationTotal == 0) {
            throw GradleException(
                "zavarnik: not one application class was loaded — is the readiness URL served by this application?\n${run.logTail()}",
            )
        }
        if (loaded.applicationShare < minCachedShare.get()) {
            throw GradleException(
                "zavarnik: $summary — below the ${percent(minCachedShare.get())} minCachedShare. " +
                    "The training run did not exercise these classes; extend the workload, or lower the threshold.",
            )
        }
        logger.lifecycle("zavarnik: $summary")
    }

    private fun aotLines(): String {
        val log = logFile.get().asFile
        if (!log.exists()) return "  (no output)"
        val lines = log.readLines().filter { AOT_TAG in it && (ERROR in it || WARNING in it) }
        return if (lines.isEmpty()) "  (no [aot] errors in the log)" else lines.joinToString("\n") { "  $it" }
    }

    private fun percent(share: Double): String = "%.1f%%".format(share * PERCENT)

    private companion object {
        const val AOT_TAG = "[aot"
        const val ERROR = "error"
        const val WARNING = "warning"
        const val PERCENT = 100
    }
}
