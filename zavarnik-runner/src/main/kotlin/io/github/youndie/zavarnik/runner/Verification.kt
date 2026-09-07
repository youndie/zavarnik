package io.github.youndie.zavarnik.runner

import java.io.File

/**
 * Fails when the cache will not do what it is shipped for. Three independent checks, any one of
 * which is enough, and the message names which:
 *
 * 1. the jars in `lib/` match the manifest the training wrote — the check the JVM itself skips
 *    on the builds that carry JDK-8377932;
 * 2. the application starts under `-XX:AOTMode=on`, where a rejected cache is fatal instead of
 *    three lines on stderr and exit code 0;
 * 3. at least [RunnerConfig.minCachedShare] of the application's loaded classes came from the
 *    cache — a cache that is accepted but empty for the application means the training run did
 *    not exercise it, and neither of the checks above can tell.
 */
public class Verification(
    private val installation: Installation,
    private val config: RunnerConfig,
    private val javaHome: File,
    private val log: File,
    private val report: (String) -> Unit = ::println,
) {
    /** Returns the one-line summary of what was measured; throws [RunnerException] otherwise. */
    public fun run(): String {
        val cache = installation.cache(config)
        val manifest = installation.manifest(config)
        if (!cache.isFile) {
            throw RunnerException(
                "zavarnik: no AOT cache in ${installation.lib.path} — run aotTrain first (or the runner's `train`).",
            )
        }
        if (!manifest.isFile) {
            throw RunnerException(
                "zavarnik: ${cache.name} has no manifest next to it — run aotTrain again.",
            )
        }
        val differences = JarManifest.differences(installation.lib, manifest)
        if (differences.isNotEmpty()) {
            throw RunnerException(
                "zavarnik: the jars in lib/ are not the ones ${cache.name} was trained against:\n" +
                    differences.joinToString("\n") { "  - $it" } +
                    "\nRun aotTrain again after every change to the classpath.",
            )
        }
        val run =
            StartScriptRun(
                installation.script,
                javaHome,
                listOf("-XX:AOTMode=on", "-Xlog:class+load=info", "-Xlog:aot=info") + config.verifyJvmArgs,
                log,
            )
        run.start()
        try {
            if (config.readyUrl != null) {
                run.awaitReady(config.readyUrl, config.readyTimeout)
            } else {
                Thread.sleep(config.exitAfter!!.toMillis())
                if (!run.isAlive) {
                    throw RunnerException(
                        "zavarnik: the application exited under -XX:AOTMode=on.\n${run.logTail()}",
                    )
                }
            }
        } catch (failed: RunnerException) {
            run.kill()
            throw RunnerException(
                "zavarnik: the application did not start with the cache under -XX:AOTMode=on. " +
                    "The JVM's reasons:\n${aotLines()}\n\n${failed.message}",
                failed,
            )
        }
        run.stop(config.shutdownTimeout)
        val loaded = LoadedClasses.of(log, installation.lib)
        val summary =
            "${loaded.applicationFromCache} of ${loaded.applicationTotal} application classes " +
                "(${percent(loaded.applicationShare)}) came from ${cache.name}; " +
                "${loaded.fromCache} of ${loaded.total} loaded classes overall"
        if (loaded.applicationTotal == 0) {
            throw RunnerException(
                "zavarnik: not one application class was loaded — is the readiness URL served by this application?\n${run.logTail()}",
            )
        }
        if (loaded.applicationShare < config.minCachedShare) {
            throw RunnerException(
                "zavarnik: $summary — below the ${percent(config.minCachedShare)} minCachedShare. " +
                    "The training run did not exercise these classes; extend the workload, or lower the threshold.",
            )
        }
        report("zavarnik: $summary")
        return summary
    }

    private fun aotLines(): String {
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
