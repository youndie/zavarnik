---
id: B-47
title: "RQ2 and RQ3: the one call site every suspend body shares, and the fast path that never suspends"
status: done
priority: P2
size: M
stage: stage-8-jit-constructs
---

# B-47 — The resume edge and the continuation that may or may not be allocated

> **Done 2026-09-19.** RQ2 priced, RQ3 grey.
> [research-jit-constructs](../research/research-jit-constructs.md) §1.14–1.15.
>
> * the megamorphic call costs **6.715 ns against 0.755 monomorphic — 8.9×** — with the break
>   between 2 and 8 receivers, where `TypeProfileWidth` says it belongs;
> * on the non-suspending path the continuation **survives**: 16 B/op, and the arm with the lambda
>   hoisted into a field proves those bytes are the state-machine copy rather than the lambda;
> * the boxed primitive **is** removed — a value outside the `Integer` cache would make the arm read
>   32 B/op and it reads 16.
>
> **Both halves stop at the macro share**: 6 ns and 16 bytes a call are arithmetic until something
> counts the calls per request, and that is a profile question this phase has not asked.
>
> Three earlier versions of the RQ3 benchmark were wrong and each produced a plausible table —
> constant folding, an allocation whose owner was unknown, and a boxing arm inside the `Integer`
> cache. §1.15 keeps all three, because none was visible in its own numbers.

`BaseContinuationImpl.resumeWith` is `final` and calls `abstract invokeSuspend(Object)`: one call
site whose receivers are every suspend body in the process
([research-jit-constructs](../research/research-jit-constructs.md) §1.6). Ktor's pipeline has the
matching shape — one site calling every interceptor. The brief asks what they cost, and separately
whether escape analysis removes the continuation and the boxing on the path where a suspend
function does not actually suspend.

- **One item for both**, because they are one machinery and one benchmark harness: suspend chains
  driven through `startCoroutineUninterceptedOrReturn` with a hand-written `Continuation`, never
  through `runBlocking`, which measures the event loop.
- **The intrinsic is inline-only** — it has no JVM member and lands inside the benchmark method's
  own bytecode, which counts against that method's size. Worth knowing before a benchmark method
  crosses a threshold this study is about.
- **The receiver-count arm is 1, 2 and 8**, as the brief specifies, and the service-side number is
  the receiver count the *service* shows, which is the point of the brief's step 5.
- **B/op is the proxy for scalar replacement**, with `-XX:-DoEscapeAnalysis` and
  `-XX:-EliminateAllocations` as global bounds and a scoped `CompileCommand` preferred where one
  exists.
- Does **not** cover: the thread hop under `withContext(Dispatchers.IO)`. It is measured to be
  subtracted, and it is not a JIT effect.

- AC: ns/op and B/op for the 1/2/8 receiver benchmark with non-overlapping intervals or an explicit
  statement that they overlap; the service-side receiver count at both sites.
- AC: B/op of a non-suspending suspend chain against the same chain as plain calls, and the share
  of request bytes that continuation and boxing allocations own in the profile.
- Anchors: `bench/src/main/kotlin/bench/DispatchBench.kt`, `bench/profile/attribute.py`.
