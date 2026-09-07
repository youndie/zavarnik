package io.github.youndie.zavarnik.runner

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

/**
 * The training run: delete what a previous run left, pin the jar mtimes, start the application
 * through its start script with `-XX:AOTCacheOutput`, wait for readiness or the timer, run the
 * workload, `SIGTERM`, wait for the JVM's second sub-invocation to assemble the cache, write the
 * manifest. Failure paths kill the JVM and delete the cache: a half-trained cache is worse than
 * none.
 *
 * Before the run, the jars in `lib/` get one fixed modification time, [JAR_MTIME] — the JVM
 * checks jar mtimes against the cache, Docker `COPY` preserves them and Gradle's reproducible tar
 * stamps exactly this constant, so a cache trained here survives both (research D3). Jib stamps a
 * different constant, `1970-01-01T00:00:01Z`, and needs `filesModificationTime` set to this one.
 * After the run, `lib/<cache>.jars` records the SHA-256 of every jar for [Verification].
 */
public class Training(
    private val installation: Installation,
    private val config: RunnerConfig,
    private val log: File,
    private val report: (String) -> Unit = ::println,
) {
    public fun run() {
        if (config.readyUrl == null && config.exitAfter == null) {
            throw RunnerException(
                "zavarnik: the training run needs a way to know when to stop — set " +
                    "`training { readyWhen.url(\"http://…/health\") }` or `training { exitAfter = … }`.",
            )
        }
        val cache = installation.cache(config)
        val manifest = installation.manifest(config)
        val launch = installation.launch
        if (launch is Launch.Script && !launch.script.isFile) {
            throw RunnerException(
                "zavarnik: start script not found at ${launch.script} — is the installation complete?",
            )
        }
        // A cache that already exists would make the start script add -XX:AOTCache, and the JVM
        // refuses that flag next to -XX:AOTCacheOutput ("Only one of AOTCache or AOTCacheOutput can
        // be specified"). The manifest goes with it so a failed run leaves no half-truth behind.
        cache.delete()
        manifest.delete()
        cache.parentFile.mkdirs()
        if (installation.pinsJarTimestamps) for (dir in installation.jarDirs) pinJarTimestamps(dir)
        val run = ApplicationRun(launch, listOf("-XX:AOTCacheOutput=${cache.absolutePath}"), log)
        run.start()
        try {
            exercise(run, cache)
        } catch (failed: RunnerException) {
            run.kill()
            cache.delete()
            manifest.delete()
            throw failed
        }
        JarManifest.write(installation.dir, installation.jarDirs, manifest)
        report(
            "zavarnik: cache ${cache.name} is ${cache.length() / KIB} KiB; " +
                "manifest ${manifest.name} lists ${manifest.readLines().count { it.isNotBlank() }} jars",
        )
    }

    private fun exercise(
        run: ApplicationRun,
        cache: File,
    ) {
        val readyMillis =
            if (config.readyUrl != null) {
                run.awaitReady(config.readyUrl, config.readyTimeout)
            } else {
                Thread.sleep(config.exitAfter!!.toMillis())
                run.elapsedMillis()
            }
        report("zavarnik: application ready after $readyMillis ms")
        val workloadStarted = System.nanoTime()
        val workload = Workload(File(log.parentFile, "${log.nameWithoutExtension}.workload.log"))
        for (step in config.workload) {
            try {
                workload.run(step)
            } catch (failed: RunnerException) {
                throw RunnerException("${failed.message}\n${run.logTail()}", failed)
            }
        }
        report("zavarnik: workload took ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - workloadStarted)} ms")
        run.stop(config.shutdownTimeout)
        awaitCacheAssembled(run, cache)
    }

    /**
     * The launcher's second sub-invocation writes the cache after the training JVM has exited, so
     * "the process is gone" is not "the cache is there". The JVM announces both outcomes in its
     * output, and the file appears in place only on success.
     */
    private fun awaitCacheAssembled(
        run: ApplicationRun,
        cache: File,
    ) {
        val deadline = System.nanoTime() + config.shutdownTimeout.toNanos()
        while (System.nanoTime() < deadline) {
            val text = if (log.exists()) log.readText() else ""
            if (CREATION_COMPLETE in text && cache.isFile) return
            if (DIRECTORY_ON_CLASSPATH in text) {
                throw RunnerException(
                    "zavarnik: the classpath has a directory on it, and the JVM writes no AOT cache for a " +
                        "classpath that is not jars only (\"$DIRECTORY_ON_CLASSPATH\"). Look for a " +
                        "`files(\"…\")` directory dependency; the start script's CLASSPATH line names it.\n${run.logTail()}",
                )
            }
            if (CREATION_FAILED in text ||
                (ERROR_MARKER in text && !run.isAlive && !cache.exists() && ASSEMBLING !in text)
            ) {
                throw RunnerException("zavarnik: the JVM did not write the cache.\n${run.logTail()}")
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw RunnerException(
            "zavarnik: ${cache.name} was not assembled within ${config.shutdownTimeout.toSeconds()} s of shutdown.\n${run.logTail()}",
        )
    }

    public companion object {
        /**
         * Sets every `*.jar` in [lib] to [JAR_MTIME]. The plugin does this to `installDist` as well,
         * so an image built from the installed distribution carries the constant before any
         * training — the runner then pins inside its own container, which the image never sees.
         */
        public fun pinJarTimestamps(lib: File) {
            val jars = lib.listFiles { file -> file.isFile && file.name.endsWith(".jar") }.orEmpty()
            for (jar in jars) Files.setLastModifiedTime(jar.toPath(), JAR_MTIME)
        }

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
        private const val DIRECTORY_ON_CLASSPATH = "Cannot have non-empty directory in paths"
        private const val ASSEMBLING = "to assemble AOT cache"
        private const val ERROR_MARKER = "Error"
        private const val POLL_MILLIS = 100L
        private const val KIB = 1024
    }
}
