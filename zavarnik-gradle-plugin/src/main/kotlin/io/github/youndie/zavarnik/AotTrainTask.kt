package io.github.youndie.zavarnik

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Trains the AOT cache: runs the installed application through its start script with
 * `-XX:AOTCacheOutput`, waits until it is ready, runs the workload, stops it with `SIGTERM`, and
 * waits for the JVM's second sub-invocation to assemble the cache.
 *
 * Before the run, the jars in `lib/` get one fixed modification time, `1970-01-01T00:00:01Z` —
 * the JVM checks jar mtimes against the cache, Docker `COPY` preserves them and Jib rewrites them
 * to exactly this constant by default, so a cache trained here survives both (research D3). After
 * the run, `lib/app.aot.jars` records the SHA-256 of every jar for `aotVerify`.
 */
@DisableCachingByDefault(
    because =
        "the cache is specific to the JDK build and the CPU that trained it, and a cache restored " +
            "from the build cache for the wrong machine is exactly what this plugin exists to prevent",
)
public abstract class AotTrainTask : DefaultTask() {
    /** The `installDist` output: `bin/` and `lib/`. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val installDir: DirectoryProperty

    /** Name of the start script inside `bin/` — the application name. */
    @get:Input
    public abstract val scriptName: Property<String>

    /** The toolchain JDK; the script is pointed at it through `JAVA_HOME`. */
    @get:Nested
    public abstract val javaLauncher: Property<JavaLauncher>

    /** Name of the cache file inside `lib/`. */
    @get:Input
    public abstract val cacheFileName: Property<String>

    /** URL polled until it answers `200`; the workload runs after that. */
    @get:Input
    @get:Optional
    public abstract val readyUrl: Property<String>

    /** Commands run against the ready application, in order. */
    @get:Input
    public abstract val workload: ListProperty<List<String>>

    /** Stop this long after start instead of waiting for [readyUrl]. */
    @get:Input
    @get:Optional
    public abstract val exitAfter: Property<Duration>

    @get:Input
    public abstract val readyTimeout: Property<Duration>

    @get:Input
    public abstract val shutdownTimeout: Property<Duration>

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
        val install = installDir.get().asFile
        val lib = File(install, "lib")
        val cache = cacheFile.get().asFile
        val manifest = manifestFile.get().asFile
        val script = File(install, "bin/${scriptName.get()}")
        require(script.isFile) { "zavarnik: start script not found at $script — is `installDist` up to date?" }
        if (!readyUrl.isPresent && !exitAfter.isPresent) {
            throw GradleException(
                "zavarnik: the training run needs a way to know when to stop — set " +
                    "`training { readyWhen.url(\"http://…/health\") }` or `training { exitAfter = … }`.",
            )
        }

        // A cache that already exists would make the start script add -XX:AOTCache, and the JVM
        // refuses that flag next to -XX:AOTCacheOutput ("Only one of AOTCache or AOTCacheOutput can
        // be specified"). The manifest goes with it so a failed run leaves no half-truth behind.
        cache.delete()
        manifest.delete()
        normaliseJarTimestamps(lib)

        val run =
            StartScriptRun(
                script = script,
                javaHome =
                    javaLauncher
                        .get()
                        .metadata.installationPath.asFile,
                javaOpts = listOf("-XX:AOTCacheOutput=${cache.absolutePath}"),
                log = logFile.get().asFile,
            )
        run.start()
        try {
            exercise(run, cache, manifest, lib)
        } catch (failed: GradleException) {
            run.kill()
            cache.delete()
            manifest.delete()
            throw failed
        }
    }

    private fun exercise(
        run: StartScriptRun,
        cache: File,
        manifest: File,
        lib: File,
    ) {
        val readyMillis =
            if (readyUrl.isPresent) {
                run.awaitReady(readyUrl.get(), readyTimeout.get())
            } else {
                Thread.sleep(exitAfter.get().toMillis())
                run.elapsedMillis()
            }
        logger.lifecycle("zavarnik: application ready after $readyMillis ms")
        val workloadStarted = System.nanoTime()
        runWorkload(run)
        val workloadMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - workloadStarted)
        run.stop(shutdownTimeout.get())
        awaitCacheAssembled(run, cache)
        JarManifest.write(lib, manifest)
        logger.lifecycle(
            "zavarnik: cache ${cache.name} is ${cache.length() / KIB} KiB after a $workloadMillis ms workload; " +
                "manifest ${manifest.name} lists ${manifest.readLines().count { it.isNotBlank() }} jars",
        )
    }

    private fun runWorkload(run: StartScriptRun) {
        for (command in workload.get()) {
            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.get().asFile))
                    .start()
            val exit = process.waitFor()
            if (exit != 0) {
                run.kill()
                throw GradleException(
                    "zavarnik: workload command ${command.joinToString(" ")} exited with $exit.\n${run.logTail()}",
                )
            }
        }
    }

    /**
     * The launcher's second sub-invocation writes the cache after the training JVM has exited, so
     * "the process is gone" is not "the cache is there". The JVM announces both outcomes in its
     * output, and the file appears in place only on success.
     */
    private fun awaitCacheAssembled(
        run: StartScriptRun,
        cache: File,
    ) {
        val deadline = System.nanoTime() + shutdownTimeout.get().toNanos()
        val log = logFile.get().asFile
        while (System.nanoTime() < deadline) {
            val text = if (log.exists()) log.readText() else ""
            if (CREATION_COMPLETE in text && cache.isFile) return
            if (CREATION_FAILED in text ||
                (ERROR_MARKER in text && !run.isAlive && !cache.exists() && !text.contains(ASSEMBLING))
            ) {
                throw GradleException("zavarnik: the JVM did not write the cache.\n${run.logTail()}")
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw GradleException(
            "zavarnik: ${cache.name} was not assembled within ${shutdownTimeout.get().toSeconds()} s of shutdown.\n${run.logTail()}",
        )
    }

    private fun normaliseJarTimestamps(lib: File) {
        val jars = lib.listFiles { file -> file.isFile && file.name.endsWith(".jar") }.orEmpty()
        for (jar in jars) Files.setLastModifiedTime(jar.toPath(), JAR_MTIME)
    }

    public companion object {
        /**
         * `1970-01-02T00:00:00Z` — the constant Gradle stamps on every entry of a reproducible tar
         * (`TarCopyAction.CONSTANT_TIME_FOR_TAR_ENTRIES`, the default since Gradle 9). The JVM
         * compares jar mtimes against the cache; with this constant a `distTar` unpacked anywhere
         * matches the cache trained in `installDist` without any configuration, and Docker `COPY`
         * preserves it. Zip cannot carry it at all: DOS timestamps start in 1980 and are local time.
         */
        public val JAR_MTIME: FileTime = FileTime.fromMillis(86_400_000)
        private const val CREATION_COMPLETE = "AOTCache creation is complete"
        private const val CREATION_FAILED = "AOTCache creation failed"
        private const val ASSEMBLING = "to assemble AOT cache"
        private const val ERROR_MARKER = "Error"
        private const val POLL_MILLIS = 100L
        private const val KIB = 1024
    }
}
