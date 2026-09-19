package micro

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

/**
 * RQ5: which Kotlin codegen patterns defeat C2.
 *
 * Same shape as the calibration controls, and for the same reason — each pattern is a PAIR against
 * the hand-written equivalent it is supposed to compile to, so a verdict is a difference rather
 * than a number. The chain's resolution is known from B-44: differences below about **0.3 ns, or
 * 5 % at that scale**, are not distinguishable here, and any "green" has to be read against that.
 *
 * Inputs come from a `@Volatile` field rather than literals. That is not caution, it is the fix for
 * a mistake this phase already made twice: a literal lets the plain side fold to a constant and the
 * pair then measures folding (§1.15).
 *
 * D5 rescoped this question to all owners, not just application code. This measures the patterns
 * themselves; what the profile says about where they occur is a separate half the phase has not done.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class Codegen {
    @Volatile private var n = 1000
    private val list = (1..256).toList()

    // --- 1. a value class through a generic, which has to box, against the raw Int ---

    @Benchmark fun valueClassGeneric(bh: Blackhole) = bh.consume(identity(Cents(n)).v)

    @Benchmark fun valueClassDirect(bh: Blackhole) = bh.consume(takeCents(Cents(n)))

    @Benchmark fun rawIntGeneric(bh: Blackhole) = bh.consume(identity(n))

    // --- 2. a value class through a nullable, which also boxes ---

    @Benchmark fun valueClassNullable(bh: Blackhole) = bh.consume(nullableCents(Cents(n))?.v ?: 0)

    // --- 3. a capturing lambda passed to a NON-inline function, against the loop ---

    @Benchmark
    fun capturingLambda(bh: Blackhole) {
        val bias = n
        bh.consume(applyAll(list) { it + bias })
    }

    @Benchmark
    fun handWrittenApply(bh: Blackhole) {
        val bias = n
        var acc = 0
        for (v in list) acc += v + bias
        bh.consume(acc)
    }

    // --- 4. a call that goes through the synthetic $default, against an explicit overload ---

    @Benchmark fun defaultArgs(bh: Blackhole) = bh.consume(withDefaults(n))

    @Benchmark fun explicitArgs(bh: Blackhole) = bh.consume(withDefaults(n, 1, 2))

    // --- 5. a collection chain, the same as a Sequence, and the same by hand ---

    @Benchmark fun collectionChain(bh: Blackhole) =
        bh.consume(list.map { it + n }.filter { it % 2 == 0 }.sum())

    @Benchmark fun sequenceChain(bh: Blackhole) =
        bh.consume(list.asSequence().map { it + n }.filter { it % 2 == 0 }.sum())

    @Benchmark
    fun handWrittenChain(bh: Blackhole) {
        var acc = 0
        for (v in list) { val m = v + n; if (m % 2 == 0) acc += m }
        bh.consume(acc)
    }

    // --- 6. delegated properties, against plain fields ---

    private val lazyValue: Int by lazy { 42 }
    private var observed: Int by Delegates.observable(0) { _, _, _ -> }
    private var plainField: Int = 0

    @Benchmark fun delegatedLazy(bh: Blackhole) = bh.consume(lazyValue)

    @Benchmark fun delegatedObservable(bh: Blackhole) { observed = n; bh.consume(observed) }

    @Benchmark fun plainProperty(bh: Blackhole) { plainField = n; bh.consume(plainField) }
}

@JvmInline value class Cents(val v: Int)

private fun <T> identity(t: T): T = t
private fun takeCents(c: Cents): Int = c.v
private fun nullableCents(c: Cents): Cents? = c

/** Deliberately NOT inline: an inline function has no lambda object to allocate. */
private fun applyAll(xs: List<Int>, f: (Int) -> Int): Int {
    var acc = 0
    for (x in xs) acc += f(x)
    return acc
}

private fun withDefaults(a: Int, b: Int = 1, c: Int = 2): Int = a + b + c
