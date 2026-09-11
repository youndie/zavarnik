package bench

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The mechanism of the engine phase without an engine: coroutines that do nothing but resume on a
 * dispatcher. A Ktor CIO call is a handful of such resumptions — every socket read and write
 * completes through the engine's dispatcher — so this measures what one of them costs, with no
 * HTTP, no sockets and no serialisation in the way.
 *
 * Usage: bench.DispatchBenchKt <variant> [measureSeconds] [concurrency] [hopsPerOp] [warmupSeconds]
 *   io          Dispatchers.IO as it is (`kotlinx.coroutines.io.parallelism` applies)
 *   io-view-N   Dispatchers.IO.limitedParallelism(N) — the per-call-site view, the shape of the
 *               fix Ktor's own KTOR-6462 asks for, with the process-wide default left alone
 *   default     Dispatchers.Default — the scheduler without a LimitedDispatcher in front of it
 */
fun main(args: Array<String>) {
    val variant = args.getOrElse(0) { "io" }
    val measureSeconds = args.getOrElse(1) { "20" }.toLong()
    val concurrency = args.getOrElse(2) { "64" }.toInt()
    val hops = args.getOrElse(3) { "4" }.toInt()
    val warmupSeconds = args.getOrElse(4) { "10" }.toLong()

    val dispatcher: CoroutineDispatcher =
        when {
            variant == "io" -> Dispatchers.IO

            variant == "default" -> Dispatchers.Default

            variant.startsWith(
                "io-view-",
            ) -> Dispatchers.IO.limitedParallelism(variant.removePrefix("io-view-").toInt())

            else -> error("unknown variant: $variant")
        }

    run(dispatcher, warmupSeconds, concurrency, hops)
    val started = System.nanoTime()
    val cpu0 = cpuSeconds()
    val ops = run(dispatcher, measureSeconds, concurrency, hops)
    val cpu = cpuSeconds() - cpu0
    val elapsed = (System.nanoTime() - started) / 1e9

    println(
        "variant=$variant io.parallelism=${System.getProperty("kotlinx.coroutines.io.parallelism") ?: "default"} " +
            "processors=${Runtime.getRuntime().availableProcessors()} concurrency=$concurrency hops=$hops " +
            "ops=$ops ops_per_s=${"%.0f".format(ops / elapsed)} " +
            "us_cpu_per_op=${"%.2f".format(cpu / ops * 1e6)} " +
            "us_cpu_per_hop=${"%.2f".format(cpu / (ops * hops) * 1e6)} " +
            "cores=${"%.2f".format(cpu / elapsed)} threads=${Thread.getAllStackTraces().size}",
    )
}

private fun run(
    dispatcher: CoroutineDispatcher,
    seconds: Long,
    concurrency: Int,
    hops: Int,
): Long =
    runBlocking {
        val stop = AtomicBoolean(false)
        val scope = CoroutineScope(dispatcher)
        val workers =
            (1..concurrency).map {
                scope.async {
                    var done = 0L
                    while (!stop.get()) {
                        // Each yield() is one dispatch through the dispatcher under test: the
                        // continuation goes into its queue and is picked up by one of its workers.
                        repeat(hops) { yield() }
                        done++
                    }
                    done
                }
            }
        Thread.sleep(seconds * 1000)
        stop.set(true)
        workers.awaitAll().sum()
    }

/** utime+stime of this process, in seconds. The comm field is parenthesised, so it is cut off first. */
private fun cpuSeconds(): Double {
    val fields = File("/proc/self/stat").readText().substringAfter(") ").split(" ")
    return (fields[11].toLong() + fields[12].toLong()) / 100.0
}
