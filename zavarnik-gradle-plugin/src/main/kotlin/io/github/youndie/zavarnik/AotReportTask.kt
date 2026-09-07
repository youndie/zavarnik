package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.JitStats
import io.github.youndie.zavarnik.runner.StartScriptRun
import io.github.youndie.zavarnik.runner.Workload
import io.github.youndie.zavarnik.runner.WorkloadStep
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Cold versus cached, in two tables: time to the first `200` on the readiness URL, [runs] times
 * each way, sorted rows and medians; and what the JIT still has to do after the start — methods
 * compiled by C1 and C2 during [loadSeconds] of the workload — because the cache holds classes,
 * heap objects and method profiles, not compiled code, and a report that only shows readiness
 * would promise a warm service the JVM does not deliver.
 *
 * Medians and full rows, not means: the first run after a pause is regularly several times
 * slower than the rest, and one such outlier moves a mean by more than the cache does. Both
 * columns are milliseconds — a percentage without the absolute numbers decides nothing.
 */
@DisableCachingByDefault(because = "a measurement of this machine, right now")
public abstract class AotReportTask : DefaultTask() {
    @get:Internal
    public abstract val installDir: DirectoryProperty

    @get:Input
    public abstract val scriptName: Property<String>

    @get:Nested
    public abstract val javaLauncher: Property<JavaLauncher>

    @get:Internal
    public abstract val cacheFile: RegularFileProperty

    @get:Input
    public abstract val readyUrl: Property<String>

    @get:Input
    public abstract val readyTimeout: Property<Duration>

    @get:Input
    public abstract val shutdownTimeout: Property<Duration>

    /** Runs per variant. Ten by default; `-Pzavarnik.runs=N` overrides. */
    @get:Input
    public abstract val runs: Property<Int>

    /** The steps looped over during the JIT window — the training workload. */
    @get:Input
    public abstract val workload: ListProperty<WorkloadStep>

    /** Length of the JIT window. Twenty seconds by default; `-Pzavarnik.loadSeconds=N` overrides. */
    @get:Input
    public abstract val loadSeconds: Property<Int>

    /** Threads looping over the workload during the JIT window. Eight by default. */
    @get:Input
    public abstract val loadConcurrency: Property<Int>

    /** `build/reports/zavarnik/aotReport.md`. */
    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    @TaskAction
    public fun report() {
        if (!readyUrl.isPresent) {
            throw GradleException(
                "zavarnik: aotReport measures time to the first 200 on `training { readyWhen.url(…) }`, which is not set.",
            )
        }
        val cache = cacheFile.get().asFile
        if (!cache.isFile) throw GradleException("zavarnik: no ${cache.name} to measure — run aotTrain first.")
        val cold = measureReadiness(COLD)
        val cached = measureReadiness(emptyList())
        val jitCold = measureJit(COLD)
        val jitCached = measureJit(emptyList())
        val javaVersion = javaLauncher.get().metadata.javaRuntimeVersion
        val table =
            buildString {
                appendLine("# aotReport — ${scriptName.get()}")
                appendLine()
                appendLine("Time to the first `200` on `${readyUrl.get()}`, measured from outside the process,")
                appendLine(
                    "${runs.get()} runs per variant, sorted. JDK $javaVersion, cache ${cache.length() / KIB} KiB.",
                )
                appendLine()
                appendLine("| Variant | Median, ms | Runs, ms |")
                appendLine("|---|---|---|")
                appendLine("| without the cache (`-XX:AOTMode=off`) | ${median(cold)} | ${cold.joinToString(" ")} |")
                appendLine("| with ${cache.name} | ${median(cached)} | ${cached.joinToString(" ")} |")
                appendLine()
                appendLine("## What the JIT still does after the start")
                appendLine()
                appendLine("The cache holds classes, heap objects and method profiles — not compiled code. During")
                appendLine(
                    "${loadSeconds.get()} s of the training workload on ${loadConcurrency.get()} threads, right after readiness",
                )
                appendLine("(`-XX:+CITime`; counts grow with the requests served, so read them next to that column):")
                appendLine()
                appendLine("| Variant | Requests served | C1 methods | C2 methods | C2 time, s | All compilation, s |")
                appendLine("|---|---|---|---|---|---|")
                appendLine(jitRow("without the cache", jitCold))
                appendLine(jitRow("with ${cache.name}", jitCached))
            }
        reportFile
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText(table)
        val c2Cold = jitCold.stats?.c2Methods ?: "?"
        val c2Cached = jitCached.stats?.c2Methods ?: "?"
        logger.lifecycle(
            "zavarnik: readiness median ${median(cold)} ms cold, ${median(cached)} ms with ${cache.name}; " +
                "JIT after the start: C2 $c2Cold methods cold, $c2Cached with the cache — ${reportFile.get().asFile}",
        )
    }

    private fun jitRow(
        label: String,
        window: JitWindow,
    ): String {
        val s = window.stats ?: return "| $label | ${window.requests} | (no CITime block in the log) | | | |"
        return "| $label | ${window.requests} | ${s.c1Methods} | ${s.c2Methods} | ${"%.1f".format(
            s.c2Seconds,
        )} | ${"%.1f".format(s.totalSeconds)} |"
    }

    private fun measureReadiness(javaOpts: List<String>): List<Long> =
        List(runs.get()) {
            val run = start(javaOpts, File(temporaryDir, "readiness.log"))
            val millis = run.awaitReady(readyUrl.get(), readyTimeout.get())
            run.stop(shutdownTimeout.get())
            millis
        }.sorted()

    private class JitWindow(
        val requests: Long,
        val stats: JitStats?,
    )

    /** One run under `-XX:+CITime`: readiness, then the workload in a loop, then SIGTERM and the block the JVM prints on exit. */
    private fun measureJit(javaOpts: List<String>): JitWindow {
        val log = File(temporaryDir, "jit.log")
        val run = start(javaOpts + CITIME, log)
        run.awaitReady(readyUrl.get(), readyTimeout.get())
        // Command output goes to its own file: appended to the JVM's log it would sit between the
        // JVM's lines — curl prints bodies without a newline — and break the CITime block apart.
        val workload = Workload(File(temporaryDir, "workload.log"))
        val steps = this.workload.get()
        val served = AtomicLong()
        if (steps.isNotEmpty()) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(loadSeconds.get().toLong())
            val pool = Executors.newFixedThreadPool(loadConcurrency.get())
            try {
                repeat(loadConcurrency.get()) {
                    pool.execute {
                        while (System.nanoTime() < deadline) {
                            for (step in steps) {
                                workload.run(step)
                                served.incrementAndGet()
                            }
                        }
                    }
                }
                pool.shutdown()
                pool.awaitTermination(loadSeconds.get().toLong() + WORKLOAD_GRACE_SECONDS, TimeUnit.SECONDS)
            } finally {
                pool.shutdownNow()
            }
        }
        run.stop(shutdownTimeout.get())
        return JitWindow(served.get(), JitStats.parse(log.readText()))
    }

    private fun start(
        javaOpts: List<String>,
        log: File,
    ): StartScriptRun {
        val script = File(installDir.get().asFile, "bin/${scriptName.get()}")
        val run =
            StartScriptRun(
                script,
                javaLauncher
                    .get()
                    .metadata.installationPath.asFile,
                javaOpts,
                log,
            )
        run.start()
        return run
    }

    private fun median(sorted: List<Long>): Long =
        if (sorted.size % 2 ==
            1
        ) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }

    private companion object {
        const val KIB = 1024
        const val WORKLOAD_GRACE_SECONDS = 30L
        val COLD = listOf("-XX:AOTMode=off")
        val CITIME = listOf("-XX:+CITime")
    }
}
