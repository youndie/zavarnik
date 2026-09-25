---
id: jit-recipes
title: "The JIT is not where your CPU goes — what that means for Kotlin you are about to write"
type: research
status: active
date: 2026-09-20
---

# The JIT is not where your CPU goes

The fifth phase set out to rank Kotlin constructs by how badly HotSpot C2 handles them, and to come
back with a list of things to avoid. **The list is two items long, and neither is one anybody warns
about.** Nine constructs that people routinely contort code to avoid were measured against their
hand-written equivalents and found to cost nothing at all.

The premise did not survive: **construct choice is not what moves C2 on this stack.** What does move
the JIT-related part of the bill is warm-up — 60 to 95 CPU-seconds per process, once — and that is a
deployment question, not a coding one.

So this page is a recipe in three parts, and the first is the largest: **stop paying readability for
a cost that is not there.**

Everything below was measured on one stand — bench-a/bench-b, 4 cores each, JDK 25.0.4, Ktor 3.5.2 on
Netty, Exposed 1.4.0 over PostgreSQL, Kotlin 2.4.10, kotlinx 1.11.0 — and every row names the section
of [research-jit-constructs](research-jit-constructs.md) that measured it. Nothing here is repeated
from a blog post or from memory.

---

## 1. Write it the readable way. These nine cost nothing.

"Nothing" means **within the chain's resolution — about 0.3 ns, or 5 % at that scale — and allocating
zero bytes**. Each was measured as a *pair* against the hand-written form it is supposed to compile
to, because a number on its own cannot tell "fast" from "the benchmark folded it away".

| What people avoid | Measured | Its pair | Where |
|---|---|---|---|
| Value class through a **generic**, unwrapped on the spot | 0.866 ns, **0 B/op** | raw `Int` through the same generic, 0.865 ns | §1.18 |
| Value class through a **nullable**, unwrapped on the spot | 0.866 ns, **0 B/op** | used directly, 0.848 ns | §1.18 |
| **Default arguments** (the `$default` synthetic) | 0.997 ns | an explicit overload, 0.925 ns | §1.18 |
| **Capturing lambda** into a non-`inline` function | 302.9 ns / 256 elements, **0.001 B/op** | the loop written out, 305.5 ns | §1.18 |
| **Null-check intrinsics** | 1.670 ns | the unchecked variant, 1.863 ns | §1.14 |
| A **suspend function that does not suspend** | 0.911 ns, **0 B/op** | the same call written plain, 0.917 ns | §1.15 |
| A method **over `FreqInlineSize`** | raising the dial to 2000 moves CPU/request 1.3 %, inside a 2.8–4.3 % ruler | the default 325 | §1.17 |
| The **megamorphic `resumeWith`** site every coroutine shares | 4.006 ns against 0.693 monomorphic — **0.52–0.87 % of request CPU** | the brief's 2 % line | §1.19, §1.20 |
| `Encoder`/`Decoder` sites under **mixed traffic** | two receivers, which `TypeProfileWidth=2` covers | the megamorphic threshold | §1.16 |

Two of these are worth a sentence, because they are the ones that change how code gets written.

**A value class box is free where it does not escape, and real where it does.** This is the row most
worth reading carefully, because it is the easiest to over-read.

*What was measured.* `identity(Cents(n)).v` — a `@JvmInline value class` through
`private fun <T> identity(t: T): T`, unwrapped with `.v` on the next expression. The box is created and
consumed inside one region C2 inlines, so escape analysis deletes it: **0 B/op**, identical to the raw
`Int` through the same generic. The boxing is real in the bytecode — the census counts **1175
`box-impl`/`unbox-impl` call sites** on this classpath — and C2 removes it.

*What was not measured, and where the worry belongs.* Nothing here shows a box surviving a boundary
being free, and the cases people actually fear are exactly those: a `List<Cents>`, a `Map<K, Cents>`,
a value class as a suspend function's return type, one crossing into a `Flow`. Those are collections
of boxes and scalar replacement does not reach them. This document's own §1.15 is the measured
counterexample — a boxed primitive forced to escape allocates **16 B/op on every call**, and it cannot
be saved by the `Integer` cache, because `kotlin.coroutines.jvm.internal.Boxing.boxInt` compiles to
`new Integer(i)` rather than `Integer.valueOf(i)` on purpose (*"This allows HotSpot JIT to eliminate
allocations completely in coroutines code with primitives"*). That is the same mechanism read from the
other side: a fresh allocation scalar-replaces well, and pays in full when it cannot.

*So the recipe is about escape, not about value classes.* Wrap and unwrap freely inside a computation.
Do not assume a `List<Cents>` is free — that one has not been measured here and the mechanism says it
is not.

**Suspend functions cost nothing on the path where they do not suspend**, which is the common case on
a request path. Escape analysis removes the state machines outright: the same benchmark under
`-XX:-DoEscapeAnalysis` allocates **168 B/op** instead of zero, and those 152 bytes are the three
continuations plus the boxes between them (§1.15).

---

## 2. Two things to avoid, and both are smaller than they look

| Construct | Cost | Its pair | Where |
|---|---|---|---|
| **`Delegates.observable`** | **14.3 ns and one 16-byte box per write — 15×** | a plain field, 0.948 ns | §1.18 |
| **Eager `map { }.filter { }.sum()`** | **4592 ns and 7184 B over 256 elements — 3.8×** | the loop written out, 1216 ns and 0 B | §1.18 |

**`Delegates.observable` is 15× a field write**, because every write goes through
`ReadWriteProperty.setValue` with both values boxed for the callback. Use it where the notification
is the point; not as a convenience on a hot field. `by lazy` is much cheaper — +1.0 ns over a plain
field — but not free, so it does not belong on a path that reads it per request.

**An eager chain costs its intermediate lists, and `Sequence` is the cheaper form here** — 1750 ns
and 4096 B against 4592 ns and 7184 B. That is the **reverse** of the usual ranking, and the bytecode
explains where the usual ranking came from: `List.map` and `List.filter` are `inline` and leave no
call site at all, while `Sequence.map` and `Sequence.filter` are ordinary calls. Count call sites and
you rank them backwards. What dominates is the two intermediate `ArrayList`s and 256 boxed `Integer`s
the eager form materialises (§1.18).

**Neither is a JIT failure**, and that decides the fix: write the loop, or accept the cost — not
"help the compiler". And both are bounded on a real request at **0.24 % and 0.18 % of request CPU**,
because there is room for at most 0.79 such chains and 163 such writes before a request would
allocate more than it actually does (§1.20). Worth fixing in a tight loop; not worth auditing a
codebase for.

---

## 3. Where the cost actually is

None of this is construct shape, and the first row is larger than everything in sections 1 and 2 put
together.

| | Cost | Where |
|---|---|---|
| **Reaching JIT steady state** | **60–95 CPU-seconds, once per process**, over 7336 to 7921 compilations | §1.23 |
| A co-located database | a third of a four-core machine | §1.13 |
| An explicit transaction wrapper around one query | **~64 µs per request**, flat in row count | §1.11 |
| Exposed's per-row `ThreadLocal` lookup for the current transaction | **+12.5 µs per request** at 50 rows | §1.22 |
| `Dispatchers.IO` sized 64 rather than 16 *on this four-core box* | 27 % more CPU per request, and **not monotonic** in the parameter | §1.13 |

**Warm-up is fixed capital, not a rate.** After it the compiler drips at **0.02 cores** — about one
per cent of the service's CPU, comparable to the collector rather than several times it. Which means:

> **Significance follows pod lifetime and deploy frequency, not request rate.** Sixty CPU-seconds
> against a pod that lives an hour is under two per cent of its lifetime. Against a pod that lives ten
> minutes it is ten per cent. Against a one-core container at 50 rps it is the **61 %** of self CPU
> that §1.1 measured — the same capital against a much smaller denominator.

The recipe that follows is not about Kotlin at all: **if JIT cost shows up in your bill, the lever is
fewer restarts, longer-lived pods, or a class-data / AOT archive — not rewriting constructs.** That
is the phase this repository's plugin exists for.

**And two of the rows above are library design, not compiler behaviour.** Wrapping a single query in
an explicit transaction costs about as much as everything else Exposed adds on a one-row read, and
the per-row `ThreadLocal` lookup is the single largest component of Exposed's gap over hand-written
JDBC. Both are worth knowing; neither is fixed by how you write Kotlin.

---

## 4. One condition that flips a result

**`JobCancellationException` is constructed and thrown once per request** — 1.031 per request, on an
unthrottled counter, with construction and throw agreeing to 59 events in 1.28 million (§1.21, B-54).

It costs **0.06–0.15 % of request CPU** only because `fillInStackTrace` is overridden to do nothing,
and that override is **conditional**:

```
fillInStackTrace():
  if (DebugKt.getDEBUG()) return super.fillInStackTrace()   // the full stack walk
  setStackTrace(new StackTraceElement[0]); return this      // stackless
```

`getDEBUG()` follows `CoroutineId.class.desiredAssertionStatus()` and the `kotlinx.coroutines.debug`
system property. **So `-ea` turns one free exception per request into one stack walk per request.**
Fine in a test JVM; worth knowing before assertions are enabled in something serving traffic.

---

## 5. What section 1 is scoped to

Every row there was measured in JMH, where values do not escape the benchmark method and call sites
are monomorphic. That is the case C2 optimises best, and it is not automatically the case inside a
request.

What extends those claims past the microbenchmark is the allocation census (§1.20): a real request on
this stack allocates 23–75 KB, and the products of the section-1 constructs are **absent** from it.
That is a bound from the other direction, not a repetition of the same measurement. Where a claim has
only the microbenchmark behind it, "costs nothing" means "costs nothing where nothing escapes".

**And all of it is one stand**, one JDK, one version of each library. Shares travel between stands;
absolute figures do not.

**The named gap.** The escaping case is measured for a boxed primitive (§1.15) and *not* for a value
class: no arm here holds a `List<Cents>`, or returns a `Cents` from a suspend function, or pushes one
through a `Flow`. The mechanism predicts those allocate, and the prediction is not a measurement.
Until it is, section 1's value-class rows mean only what they say — unwrapped on the spot, inside one
inlined region.

---

## 6. Why this kind of result is rare, and how to get one

Nineteen published claims in this study were withdrawn before it ended, and
[§2.2](research-jit-constructs.md) says what killed each. That number is the real explanation for why
folklore about JIT behaviour persists: **these things are hard to measure, and a wrong measurement
produces a plausible number rather than an obvious error.** Five habits did more than any harness.

1. **Measure a pair, never a number.** Every construct goes against the hand-written equivalent it is
   supposed to compile to. A number alone cannot distinguish "fast" from "constant-folded away" — the
   first version of the suspend benchmark measured folding against not-folding and read 0.701 ns.
2. **Control the unit before believing it.** "16 B/op" was read as a continuation for a week. A
   continuation is 40 bytes by field layout; a benchmark that allocates exactly one escaping `Integer`
   reads 16.000. Without that control the unit was inferred, not measured.
3. **Check that the instrument reports.** JFR's `jdk.Compilation` ships with a 100 ms threshold and
   reported **zero** events on a run with 7268 compilations. `jdk.JavaExceptionThrow` is throttled at
   300/s and sat on that ceiling, understating throws 6- to 17-fold. A default that silently caps
   looks exactly like an answer.
4. **Give a share a time axis before calling it a cost.** The compiler's "4.9–6.1 % of CPU" was a
   decaying curve averaged over a window that began inside warm-up. Per ten seconds it is 26 %, then
   near zero.
5. **Send it to someone who did not run it.** Two review rounds by the person who commissioned the
   study produced **ten of the nineteen retractions** — more than the study's own discipline caught
   unaided. Three were wrong operationalisations: interpreted frames for failed inlining, the
   collector alone for failed scalar replacement, a flat profile for a time-varying share. A wrong
   operationalisation is invisible from inside, because it answers a question nobody asked and
   answers it plausibly.

The harnesses are committed and take one command each: `microbench/` for the JMH pairs,
`bench/profile/` for the macro runs.
