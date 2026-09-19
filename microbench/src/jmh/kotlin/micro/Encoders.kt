package micro

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * RQ6: what does the step from one encoder to three cost?
 *
 * §1.5 counted it. A process that only calls `encodeToString` loads exactly one concrete `Encoder`;
 * a single `encodeToJsonElement` anywhere loads three more. `TypeProfileWidth` is 2 (§1.2), so that
 * one line is the whole distance between a call site C2 can inline and a megamorphic one.
 *
 * The comparison works only because **JMH forks each benchmark into its own JVM**. Class loading is
 * per process, so the two states cannot coexist in one run; they have to be separate JVMs, which is
 * what forks are. That is also why the arms are two classes rather than two methods with a flag —
 * a flag would be read inside one process, where the loading has already happened.
 *
 * Both arms then do the identical thing with the same serialiser and the same rows. Nothing differs
 * except what else the JVM has loaded.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class EncoderClean {
    @Benchmark fun encode(bh: Blackhole) = bh.consume(Fixture.json.encodeToString(Fixture.serializer, Fixture.rows))
}

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class EncoderPolluted {
    /**
     * One call, once, before anything is measured — the shape a health endpoint or a log line would
     * have. It is never called again; its whole effect is that the tree encoders are now loaded and
     * the shared call sites have seen three receivers instead of one.
     */
    @Setup(Level.Trial)
    fun pollute() {
        Fixture.json.encodeToJsonElement(Fixture.serializer, Fixture.rows)
    }

    @Benchmark fun encode(bh: Blackhole) = bh.consume(Fixture.json.encodeToString(Fixture.serializer, Fixture.rows))
}

/**
 * The arm the first two needed.
 *
 * Loading a class is not polluting a profile: a type profile is kept per call site and records the
 * receivers that site has *executed with*, not what the JVM has loaded. One `encodeToJsonElement`
 * in a setup is one pass out of millions, and C2 treats a receiver at that frequency as an outlier —
 * a guard and an uncommon trap, not a megamorphic site. Which is why the polluted arm measured
 * exactly the same as the clean one.
 *
 * Sustained mixed traffic is the thing that would move a profile, so here it is: every measured
 * invocation goes through both encoders. If the receiver distribution is what matters, this is where
 * it shows; if this also comes out flat, the mechanism does not reach the generated serialiser's
 * call sites at all.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class EncoderMixed {
    @Benchmark
    fun encode(bh: Blackhole) {
        bh.consume(Fixture.json.encodeToString(Fixture.serializer, Fixture.rows))
        bh.consume(Fixture.json.encodeToJsonElement(Fixture.serializer, Fixture.rows))
    }
}

/**
 * The control for [EncoderMixed]: the same two calls' worth of work, but both through the streaming
 * encoder, so the arm differs by the receiver distribution and not by how much work it does.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class EncoderDoubleClean {
    @Benchmark
    fun encode(bh: Blackhole) {
        bh.consume(Fixture.json.encodeToString(Fixture.serializer, Fixture.rows))
        bh.consume(Fixture.json.encodeToString(Fixture.serializer, Fixture.rows))
    }
}

private object Fixture {
    val json = Json { ignoreUnknownKeys = true }
    val serializer = ListSerializer(Row.serializer())
    val rows = (1..50).map { Row(it, "SKU-%04d".format(it), it * 1.5, listOf("a", "b")) }
}

@Serializable
data class Row(val id: Int, val sku: String, val price: Double, val tags: List<String>)
