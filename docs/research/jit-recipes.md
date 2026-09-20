---
id: jit-recipes
title: "Recipes: writing Kotlin that C2 optimises well — and the much shorter list that it does not"
type: research
status: active
date: 2026-09-20
---

# Recipes: what to avoid, what to stop avoiding

This is the consumable half of the fifth phase. The evidence, the retractions and the methodology are
in [research-jit-constructs](research-jit-constructs.md); this page is the part you act on.

**Every number here was measured on one stand** — bench-a/bench-b, 4 cores each, JDK 25.0.4, Ktor
3.5.2 on Netty, Exposed 1.4.0 over PostgreSQL, Kotlin 2.4.10, kotlinx 1.11.0 — and each row names the
section that measured it. Nothing is repeated from a blog post or from memory.

**The headline is not what the question expected.** The list of Kotlin constructs worth avoiding for
C2's sake is **two items long**, and neither is on the usual list. The list of constructs people avoid
that measurably cost nothing is far longer. And the largest JIT-related number on this stack is not a
construct at all — it is the compiler's own warm-up, and it is a deployment question rather than a
coding one.

---

## 1. Stop avoiding these. They are free.

"Free" here means: **within the measurement chain's resolution** — about 0.3 ns, or 5 % at that
scale — **and allocating nothing**. Each was measured against the hand-written equivalent it is
supposed to compile to, so the verdict is a difference rather than a number.

| Construct | Measured | Against | Where |
|---|---|---|---|
| **Value class through a generic** | 0.866 ns, **0 B/op** | raw `Int` through the same generic, 0.865 ns | §1.18 |
| **Value class through a nullable** | 0.866 ns, **0 B/op** | used directly, 0.848 ns | §1.18 |
| **`$default` synthetic** (default arguments) | 0.997 ns | explicit overload, 0.925 ns | §1.18 |
| **Capturing lambda passed to a non-`inline` function** | 302.9 ns over 256 elements, **0.001 B/op** | hand-written loop, 305.5 ns | §1.18 |
| **Null-check intrinsics** (`Intrinsics.checkNotNull*`) | 1.670 ns | the unchecked variant, 1.863 ns | §1.14 |
| **A suspend function that does not suspend** | 0.911 ns, **0 B/op** | the same call written plain, 0.917 ns | §1.15 |
| **A method over `FreqInlineSize`** | raising the dial to 2000 moves CPU/request **1.3 %**, inside a 2.8–4.3 % ruler | the default 325 | §1.17 |
| **The megamorphic `resumeWith` site** | 4.006 ns against 0.693 monomorphic — but **0.52–0.87 % of request CPU** | the 2 % line | §1.19, §1.20 |
| **`Encoder`/`Decoder` sites under mixed traffic** | two receivers, which `TypeProfileWidth=2` covers | the megamorphic threshold | §1.16 |

Two of these deserve a sentence each, because they are the ones people restructure code to avoid.

**Value classes do not box in practice.** C2 removes the wrapper entirely when it does not escape —
0 B/op, not "a cheap allocation". The stdlib deliberately helps here: `kotlin.coroutines.jvm.internal.Boxing.boxInt`
compiles to `new Integer(i)` rather than `Integer.valueOf(i)`, with a source comment saying why —
*"This allows HotSpot JIT to eliminate allocations completely in coroutines code with primitives"* —
because a fresh allocation is a better scalar-replacement candidate than a value loaded from a shared
cache array (§1.15).

**Suspend functions cost nothing on the path where they do not suspend**, which is the common case on
a request path. Escape analysis removes the state machines: the same benchmark under
`-XX:-DoEscapeAnalysis` allocates **168 B/op** instead of zero, and those 152 bytes are the three
continuations plus their intermediate boxes (§1.15).

---

## 2. The two that cost, and neither is on the usual list

| Construct | Cost | Against | Where |
|---|---|---|---|
| **`Delegates.observable`** | **14.3 ns and one 16-byte box per write — 15×** | a plain field, 0.948 ns | §1.18 |
| **Eager `map { }.filter { }.sum()`** | **4592 ns and 7184 B over 256 elements — 3.8×** | the same loop written out, 1216 ns and 0 B | §1.18 |

**`Delegates.observable` is 15× a field write.** Every write goes through `ReadWriteProperty.setValue`
with the old and new values boxed for the callback. Use it where a change notification is the point;
do not use it as a convenience on a hot field. `by lazy` is far cheaper — +1.0 ns over a plain field —
but it is not free either, so it does not belong on a per-request path that reads it repeatedly.

**Eager collection chains cost their intermediate lists, and `Sequence` is the cheaper form here** —
1750 ns and 4096 B, against the eager chain's 4592 ns and 7184 B. That is the **reverse** of how the
two are usually ranked, and the bytecode explains why the usual ranking exists: `List.map` and
`List.filter` are `inline` in the standard library and leave no call site at all, while
`Sequence.map` and `Sequence.filter` are ordinary calls. Counting call sites ranks them backwards.
What actually dominates is the two intermediate `ArrayList`s and 256 boxed `Integer`s that the eager
form materialises (§1.18).

**Neither is a JIT failure.** Both are work the code asked for, and C2 compiles both correctly. That
distinction matters because it decides the fix: write the loop, or accept the cost — not "help the
JIT".

**Both are bounded on a real request.** A request has room for at most 0.79 such chains and at most
163 observable writes before it would allocate more than the whole request does, which caps them at
**0.24 % and 0.18 % of request CPU** on this stand (§1.20). They are worth fixing in a tight loop and
not worth auditing a codebase for.

---

## 3. What actually costs, and none of it is construct shape

This is where the phase's measurements landed, and it is the part worth acting on first.

| Thing | Cost | Where |
|---|---|---|
| **Reaching JIT steady state** | **60–95 CPU-seconds, once per process** | §1.23 |
| A co-located database | a third of a four-core machine | §1.13 |
| An explicit transaction wrapper around one query | **~64 µs per request**, flat in row count | §1.11 |
| Exposed's per-row `ThreadLocal` lookup for the current transaction | **+12.5 µs per request** at 50 rows | §1.22 |
| `Dispatchers.IO` sized 64 instead of 16 *on this four-core box* | 27 % more CPU per request, and **not monotonic** in the parameter | §1.13 |

**The compiler's warm-up is the largest JIT-related number in the whole study, and it is not a rate.**
Reaching quiet costs 60–95 CPU-seconds and 7336 to 7921 compilations; after that the compiler
drips at **0.02 cores**, about one per cent of the service's CPU — comparable to the collector, not
several times it. So:

> **Significance follows pod lifetime and deploy frequency, not request rate.** Sixty CPU-seconds
> against a pod that lives an hour is under two per cent of its lifetime. Against a pod that lives ten
> minutes it is ten per cent. Against a one-core container at 50 rps it is the 61 % of self CPU that
> §1.1 measured — the same fixed capital against a much smaller denominator.

The recipe that follows is not about Kotlin at all: **if JIT cost shows up in your bill, the lever is
fewer restarts, longer-lived pods, or a class-data/AOT archive — not rewriting constructs.** That is
the phase this repository's plugin exists for.

**`Exposed`'s transaction wrapper is worth knowing about**: wrapping a single query in an explicit
transaction costs about as much as everything else Exposed adds on a one-row read. That is a design
choice with a price, not a JIT problem — and the same is true of the per-row `ThreadLocal` lookup,
which is the single largest component of Exposed's gap over hand-written JDBC.

---

## 4. One condition that flips a result

**`JobCancellationException` is thrown once per request** — 1.031 per request, measured on an
unthrottled counter, and construction and throw agree to 59 events in 1.28 million (§1.21, B-54).

It costs **0.06–0.15 % of request CPU** only because `fillInStackTrace` is overridden to do nothing.
That override is **conditional**:

```
fillInStackTrace():
  if (DebugKt.getDEBUG()) return super.fillInStackTrace()   // the full stack walk
  setStackTrace(new StackTraceElement[0]); return this      // stackless
```

`getDEBUG()` follows `CoroutineId.class.desiredAssertionStatus()` and the `kotlinx.coroutines.debug`
system property. **So `-ea` turns one free exception per request into one stack walk per request.**
That is fine in a test JVM and worth knowing before you enable assertions in something that serves
traffic.

---

## 5. What "free" is scoped to

Every row in section 1 was measured in JMH, where values do not escape the benchmark method and call
sites are monomorphic. That is the case C2 optimises best and it is not automatically the case inside
a request.

What extends those claims past the microbenchmark is the allocation census (§1.20): a real request on
this stack allocates 23–75 KB, and the products of the section-1 constructs are **absent** from it.
That is a bound from the other direction rather than a repetition of the same measurement. Where a
claim has only the microbenchmark behind it, "free" means "free where nothing escapes".

**And all of it is one stand.** Four cores, one JDK, one version of each library. Shares travel
between stands; absolute figures do not.

---

## 6. If you want to check any of this yourself

The harnesses are committed and take one command each: `microbench/` for the JMH pairs,
`bench/profile/` for the macro runs. Five habits did more to keep this study honest than any of them,
and all five come from being wrong first — nineteen published claims were withdrawn, and
[§2.2](research-jit-constructs.md) says what killed each.

1. **Measure a pair, never a number.** Every construct goes against the hand-written equivalent it is
   supposed to compile to. A number alone cannot tell "fast" from "the benchmark folded it away".
2. **Control the unit before believing it.** "16 B/op" was read as a continuation for a week; a
   continuation is 40 bytes by field layout, and a benchmark that allocates exactly one escaping
   `Integer` reads 16.000. Without that control the unit was inferred, not measured.
3. **Check the instrument reports.** JFR's `jdk.Compilation` ships with a 100 ms threshold and
   reported **zero** events on a run with 7268 compilations; `jdk.JavaExceptionThrow` is throttled at
   300/s and sat on that ceiling, understating throws by a factor of 6 to 17. A default that silently
   caps looks exactly like an answer.
4. **Give a share a time axis before calling it a cost.** The compiler's "4.9–6.1 % of CPU" was a
   decaying curve averaged over a window that started inside warm-up. Per ten seconds it is 26 % then
   near zero.
5. **Send it to someone who did not run it.** Two review rounds by the person who commissioned the
   study produced **ten of the nineteen retractions** — more than the study's own discipline caught
   unaided. Three of those were wrong operationalisations: measuring interpreted frames for inlining,
   the collector alone for scalar replacement, a flat profile for a time-varying share. A wrong
   operationalisation is invisible from inside, because it produces a plausible number that answers a
   question nobody asked.
