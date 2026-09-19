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

/**
 * B-44: the calibration controls, and they run before any candidate.
 *
 * The brief names five constructs it expects C2 to handle perfectly, and their job is not to be
 * interesting. Each is a PAIR — the Kotlin construct against the hand-written equivalent it should
 * compile to — and green means the pair is within noise. If a control comes out red the harness is
 * suspect and no candidate verdict taken with it means anything, which is the brief's kill
 * criterion 2.
 *
 * The sixth pair is not the brief's. [knownOrderBase] and [knownOrderPlusOne] differ by one extra
 * multiply, so their order is fixed by the code: the second cannot be faster. An impossible ordering
 * is the quickest way to find out that a stand is measuring itself rather than the code, and it
 * fires before the spread does.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class Controls {
    private val ints = IntArray(1024) { it }
    private lateinit var shapes: Array<Shape>
    private val point = Point(3, 4)
    private val text = "benchmark"

    @Setup
    fun setup() {
        shapes = Array(1024) { i ->
            when (i % 3) {
                0 -> Shape.Circle(i.toDouble())
                1 -> Shape.Square(i.toDouble())
                else -> Shape.Empty
            }
        }
    }

    // --- 1. inline function with a lambda, against the loop it should become ---

    @Benchmark fun inlineLambda(bh: Blackhole) = bh.consume(ints.sumOf { it * 2 })

    @Benchmark
    fun handWrittenLoop(bh: Blackhole) {
        var acc = 0
        for (v in ints) acc += v * 2
        bh.consume(acc)
    }

    // --- 2. Intrinsics parameter checks: a non-null parameter against a nullable one ---
    // A non-null Kotlin parameter emits Intrinsics.checkNotNullParameter at entry; the nullable
    // variant does not. Same body, same call, one null check apart.

    @Benchmark fun checkedParam(bh: Blackhole) = bh.consume(lengthOf(text))

    @Benchmark fun uncheckedParam(bh: Blackhole) = bh.consume(lengthOfNullable(text))

    // --- 3. when over a sealed hierarchy, against an int switch ---

    @Benchmark
    fun sealedWhen(bh: Blackhole) {
        var acc = 0.0
        for (s in shapes) {
            acc += when (s) {
                is Shape.Circle -> s.r * 2
                is Shape.Square -> s.side * 4
                Shape.Empty -> 0.0
            }
        }
        bh.consume(acc)
    }

    @Benchmark
    fun intSwitch(bh: Blackhole) {
        var acc = 0.0
        for (s in shapes) {
            acc += when (s.tag) {
                0 -> (s as Shape.Circle).r * 2
                1 -> (s as Shape.Square).side * 4
                else -> 0.0
            }
        }
        bh.consume(acc)
    }

    // --- 4. for over a range and over an array, against while ---

    @Benchmark
    fun forOverRange(bh: Blackhole) {
        var acc = 0
        for (i in 0 until ints.size) acc += ints[i]
        bh.consume(acc)
    }

    @Benchmark
    fun whileLoop(bh: Blackhole) {
        var acc = 0
        var i = 0
        while (i < ints.size) { acc += ints[i]; i++ }
        bh.consume(acc)
    }

    // --- 5. data class accessors and copy, against a plain class ---

    @Benchmark fun dataAccessors(bh: Blackhole) = bh.consume(point.x + point.y)

    @Benchmark fun dataCopy(bh: Blackhole) = bh.consume(point.copy(x = point.x + 1))

    @Benchmark fun plainCopy(bh: Blackhole) = bh.consume(Point(point.x + 1, point.y))

    // --- 6. the known-order pair: the second does everything the first does, plus one multiply ---

    @Benchmark
    fun knownOrderBase(bh: Blackhole) {
        var acc = 0L
        for (v in ints) acc += (v + 1).toLong()
        bh.consume(acc)
    }

    @Benchmark
    fun knownOrderPlusOne(bh: Blackhole) {
        var acc = 0L
        for (v in ints) acc += ((v + 1).toLong() * 31L)
        bh.consume(acc)
    }
}

private fun lengthOf(s: String): Int = s.length

private fun lengthOfNullable(s: String?): Int = s?.length ?: 0

data class Point(val x: Int, val y: Int)

sealed class Shape(val tag: Int) {
    class Circle(val r: Double) : Shape(0)
    class Square(val side: Double) : Shape(1)
    object Empty : Shape(2)
}

/**
 * Isolating what the controls turned up by accident.
 *
 * [Controls.handWrittenLoop] sums 1024 ints in 74.9 ns while [Controls.forOverRange] takes 393.5 —
 * five times longer for strictly less work, since the first one also multiplies. 0.073 ns an element
 * is a fraction of a cycle, so the fast one is vectorised and the slow one is not. The two differ in
 * exactly one thing: direct iteration against indexed.
 *
 * These four benchmarks take the multiply out of it, so that indexing is the only variable left.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class ArrayIteration {
    private val ints = IntArray(1024) { it }

    @Benchmark
    fun direct(bh: Blackhole) {
        var acc = 0
        for (v in ints) acc += v
        bh.consume(acc)
    }

    @Benchmark
    fun directTimesTwo(bh: Blackhole) {
        var acc = 0
        for (v in ints) acc += v * 2
        bh.consume(acc)
    }

    @Benchmark
    fun indexedOverIndices(bh: Blackhole) {
        var acc = 0
        for (i in ints.indices) acc += ints[i]
        bh.consume(acc)
    }

    @Benchmark
    fun indexedOverSize(bh: Blackhole) {
        var acc = 0
        for (i in 0 until ints.size) acc += ints[i]
        bh.consume(acc)
    }
}
