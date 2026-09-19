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
    // Deliberately outside the Integer cache (-128..127). The first version used 7 and 11, whose
    // sum is 18, so every box the suspend path needed came back from the cache and the arm meant to
    // measure boxing measured nothing. A value the cache cannot serve is the whole difference.
    @Volatile private var seed = 1000

    // The pair that says WHICH object the 16 B/op is. `seed` produces ((1000+3)*2)+1 = 2007, which
    // `Integer.valueOf` cannot serve from its cache and must allocate; `seedCached` produces
    // ((10+3)*2)+1 = 27, which it can. Everything else about the two arms is identical - the same
    // continuation classes, the same chain, the same call. If the allocation is the boxed return,
    // the cached arm drops to zero; if it is the continuation, both arms pay the same.
    //
    // This exists because the first reading of this benchmark attributed 16 B/op to the state
    // machine. `javap -p` gives Continuations$hoisted$1 six fields - completion, _context,
    // intercepted, arity, label, this$0 - so 12 + 24 = 36, padded to 40. A continuation cannot
    // weigh 16 bytes. java.lang.Integer, with one int field, weighs exactly 16.
    @Volatile private var seedCached = 10

    private val one = Array<Op>(256) { A() }
    private val two = Array<Op>(256) { if (it % 2 == 0) A() else B() }
    private val eight = Array<Op>(256) { mk(it % 8) }

    @Setup fun setup() = Unit

    // --- RQ3: a suspend chain that never suspends, against the same chain as plain calls ---

    // Two versions on purpose. The first writes the `suspend { }` literal inside the benchmark
    // method, so the lambda captures the receiver and is allocated on every invocation; the second
    // hoists the same lambda into a field, created once. Their DIFFERENCE is the lambda, and
    // whatever the hoisted one still allocates is the machinery — the state-machine copy that
    // `create()` makes on entry. Measuring only the first, which is what the previous run did,
    // cannot tell those apart: it reported 16 B/op that could have been either.
    @Benchmark
    fun suspendChainFastPath(bh: Blackhole) {
        val r = suspend { chainSuspend(seed) }.startCoroutineUninterceptedOrReturn(Noop)
        bh.consume(r)
    }

    private val hoisted: suspend () -> Int = { chainSuspend(seed) }
    private val hoistedCached: suspend () -> Int = { chainSuspend(seedCached) }

    @Benchmark
    fun suspendChainHoisted(bh: Blackhole) {
        val r = hoisted.startCoroutineUninterceptedOrReturn(Noop)
        bh.consume(r)
    }

    @Benchmark
    fun suspendChainHoistedCached(bh: Blackhole) {
        val r = hoistedCached.startCoroutineUninterceptedOrReturn(Noop)
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

    // --- what the 16 B/op actually is ---

    // The first reading of this pair said "the boxed primitive is removed, the continuation is
    // not". The field layout says that cannot be true. With compressed oops the smallest object on
    // this path is `Continuations$hoisted$1`: a 12-byte header, then `completion` from
    // BaseContinuationImpl, `_context` and `intercepted` from ContinuationImpl, `arity` from
    // SuspendLambda, and the generated `label` and `this$0` - 36 bytes, padded to 40. A 16-byte
    // allocation is a header and ONE four-byte field, which is a `java.lang.Integer` and cannot be
    // any continuation. So the arithmetic says the opposite: the state machine is scalar-replaced
    // and the box survives.
    //
    // [allocatesOneInteger] is the positive control that fixes the unit: it allocates exactly one
    // escaping Integer and nothing else, so whatever B/op it reports IS one box. Without it, "16"
    // is a number whose unit was inferred rather than measured.
    @Benchmark
    fun allocatesOneInteger(bh: Blackhole) {
        val boxed: Any = java.lang.Integer.valueOf(seed)
        bh.consume(boxed)
    }

    // And the other half: whether that box belongs to the suspend path or to the harness holding
    // it. `startCoroutineUninterceptedOrReturn` is declared to return `Any?`, so the fast-path
    // value arrives boxed either way - but real Kotlin unboxes it on the next instruction, while
    // `bh.consume(r)` takes an Object and forces it to escape. Consuming it as an Int is the same
    // call with the escape removed; if B/op falls to zero here, the box was never the suspend
    // path's, it was the blackhole's.
    @Benchmark
    fun suspendChainHoistedInt(bh: Blackhole) {
        val r = hoisted.startCoroutineUninterceptedOrReturn(Noop)
        bh.consume(r as Int)
    }

    @Benchmark
    fun suspendReturningIntUnboxed(bh: Blackhole) {
        val r = suspend { addSuspend(seed, 11) }.startCoroutineUninterceptedOrReturn(Noop)
        bh.consume(r as Int)
    }

    // --- RQ2, the other dispatch table ---

    // [mega] sends eight receivers through an INTERFACE method, which is an itable lookup. The site
    // the number was carried over to - `BaseContinuationImpl.resumeWith` calling the abstract
    // `invokeSuspend` - is a virtual call on a class, which is a vtable lookup, and the two are not
    // priced the same. Same eight receivers, same work per call, dispatched the other way.
    @Benchmark fun megaVirtual(bh: Blackhole) = bh.consume(driveAbs(eightAbs))

    @Benchmark fun monoVirtual(bh: Blackhole) = bh.consume(driveAbs(oneAbs))

    private val oneAbs = Array<AbsOp>(256) { A2() }
    private val eightAbs = Array<AbsOp>(256) { mkAbs(it % 8) }

    private fun driveAbs(ops: Array<AbsOp>): Int {
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

/** The same eight receivers behind an abstract class, so the call is a vtable rather than itable. */
abstract class AbsOp { abstract fun apply(x: Int): Int }

private class A2 : AbsOp() { override fun apply(x: Int) = x + 1 }
private class B2 : AbsOp() { override fun apply(x: Int) = x + 2 }
private class C2 : AbsOp() { override fun apply(x: Int) = x + 3 }
private class D2 : AbsOp() { override fun apply(x: Int) = x + 4 }
private class E2 : AbsOp() { override fun apply(x: Int) = x + 5 }
private class F2 : AbsOp() { override fun apply(x: Int) = x + 6 }
private class G2 : AbsOp() { override fun apply(x: Int) = x + 7 }
private class H2 : AbsOp() { override fun apply(x: Int) = x + 8 }

private fun mkAbs(i: Int): AbsOp = when (i) {
    0 -> A2(); 1 -> B2(); 2 -> C2(); 3 -> D2(); 4 -> E2(); 5 -> F2(); 6 -> G2(); else -> H2()
}
