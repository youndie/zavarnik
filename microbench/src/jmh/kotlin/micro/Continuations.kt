package micro

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

/**
 * RQ2 and RQ3.
 *
 * RQ3 asks what a suspend function costs on the path where it does NOT suspend — which is the
 * common case on a request path, and the one escape analysis is supposed to make free. It is
 * measured against the same chain written as plain functions, and read with `-prof gc`: B/op is the
 * proxy for whether the continuation and the boxing were scalar-replaced.
 *
 * Suspend functions are driven with [startCoroutineUninterceptedOrReturn] and a hand-written
 * [Continuation], as the brief prescribes: `runBlocking` would measure the event loop instead. The
 * intrinsic is inline-only, so its machinery lands inside the benchmark method rather than behind a
 * call — which is worth knowing when the method's own size is the subject of a neighbouring
 * question.
 *
 * RQ2 asks what a megamorphic site costs. The brief's own toggle is a receiver count of 1, 2 and 8,
 * and that is what [mono], [bi] and [mega] are: the same call site, the same work per call, and
 * only the number of implementing classes behind it changes. The number that makes this worth
 * measuring is `TypeProfileWidth = 2` — two receivers is the last case C2 keeps a profile for. The
 * service's own resume site has 580 (§1.6), so 8 is the arm that stands for it, not 1 or 2.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class Continuations {
    // Not a literal. The first version passed 7 straight into the chain, and the plain side folded
    // to a constant — 0.701 ns, about two cycles, which is the blackhole and nothing else — while
    // the suspend side did not. The pair measured folding against not-folding rather than suspend
    // against plain. A field the JIT cannot see through fixes it.
    @Volatile private var seed = 7

    private val one = Array<Op>(256) { A() }
    private val two = Array<Op>(256) { if (it % 2 == 0) A() else B() }
    private val eight = Array<Op>(256) { mk(it % 8) }

    @Setup fun setup() = Unit

    // --- RQ3: a suspend chain that never suspends, against the same chain as plain calls ---

    @Benchmark
    fun suspendChainFastPath(bh: Blackhole) {
        val r = suspend { chainSuspend(seed) }.startCoroutineUninterceptedOrReturn(Noop)
        bh.consume(r)
    }

    @Benchmark fun plainChain(bh: Blackhole) = bh.consume(chainPlain(seed))

    // The same, returning a primitive, because the brief asks separately about boxed primitives on
    // the fast path: a suspend function returning Int has to box it to pass through Continuation.
    @Benchmark
    fun suspendReturningInt(bh: Blackhole) {
        val r = suspend { addSuspend(seed, 11) }.startCoroutineUninterceptedOrReturn(Noop)
        bh.consume(r)
    }

    @Benchmark fun plainReturningInt(bh: Blackhole) = bh.consume(addPlain(seed, 11))

    // --- RQ2: one call site, 1 / 2 / 8 receiver types ---

    @Benchmark fun mono(bh: Blackhole) = bh.consume(drive(one))

    @Benchmark fun bi(bh: Blackhole) = bh.consume(drive(two))

    @Benchmark fun mega(bh: Blackhole) = bh.consume(drive(eight))

    private fun drive(ops: Array<Op>): Int {
        var acc = 0
        for (op in ops) acc += op.apply(acc)
        return acc
    }
}

private object Noop : Continuation<Any?> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(result: Result<Any?>) = Unit
}

private suspend fun chainSuspend(n: Int): Int = levelTwo(n) + 1
private suspend fun levelTwo(n: Int): Int = levelThree(n) * 2
private suspend fun levelThree(n: Int): Int = n + 3

private fun chainPlain(n: Int): Int = levelTwoPlain(n) + 1
private fun levelTwoPlain(n: Int): Int = levelThreePlain(n) * 2
private fun levelThreePlain(n: Int): Int = n + 3

private suspend fun addSuspend(a: Int, b: Int): Int = a + b
private fun addPlain(a: Int, b: Int): Int = a + b

/** Eight implementations so that one site can be given 1, 2 or 8 receivers without changing it. */
interface Op { fun apply(x: Int): Int }

private class A : Op { override fun apply(x: Int) = x + 1 }
private class B : Op { override fun apply(x: Int) = x + 2 }
private class C : Op { override fun apply(x: Int) = x + 3 }
private class D : Op { override fun apply(x: Int) = x + 4 }
private class E : Op { override fun apply(x: Int) = x + 5 }
private class F : Op { override fun apply(x: Int) = x + 6 }
private class G : Op { override fun apply(x: Int) = x + 7 }
private class H : Op { override fun apply(x: Int) = x + 8 }

private fun mk(i: Int): Op = when (i) {
    0 -> A(); 1 -> B(); 2 -> C(); 3 -> D(); 4 -> E(); 5 -> F(); 6 -> G(); else -> H()
}
