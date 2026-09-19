---
id: B-49
title: "RQ7: is steady state stable — and what the compiler itself costs under a container limit"
status: open
priority: P1
size: M
stage: stage-8-jit-constructs
blocked_by: []
---

# B-49 — Deoptimisation, exceptions as control flow, and the question the brief does not ask

RQ7 asks whether the service reaches a steady state and stays there: recurring deoptimisations,
profile pollution in shared generic code, exceptions used as control flow.

**Raised to P1 on 2026-09-19, and unblocked.** B-44 is done and B-42 is not a prerequisite for the
recording — the warm-up is handled inside `bench/profile/rq7-steady-state.sh` by starting the JFR
recording *after* 90 s of load rather than by gating on a compilation count. Two things moved this
item to the top of what is left:

* It is now **the only research question never measured at all.**
* §1.20 measured C2's own threads at **4.9–6.1 % of request CPU on a saturated four-core box**, four
  to five times the garbage collector and larger than every construct in the brief's list put
  together. Open question 3 found the same quantity at 61 % in a one-core container at 50 rps. The
  two ends of that range are the most valuable unmeasured number in the phase.

**The rate is not the whole test.** Green is "under 1 deoptimisation per minute after warmup", but
red is "a deoptimisation *recurring at the same site*" — which is red at any rate. So the events have
to be grouped by method and bci, not counted, and the runner does that.

**And the instrument has to be proved awake first.** §1.3 is the precedent: on shipped settings
`jdk.Compilation` carries a 100 ms threshold and reports nothing on a service that compiles in tens
of milliseconds, so a gate phrased against it cannot fail. The runner sets the thresholds explicitly
and refuses an empty recording, because a default that silently drops events looks exactly like a
stable steady state.

- **The exception arm names which exception.** `JobCancellationException` overrides
  `fillInStackTrace` and is already stackless; `TimeoutCancellationException` does not, so every
  `withTimeout` expiry walks the stack
  ([research-jit-constructs](../research/research-jit-constructs.md) §1.2). "Exceptions as control
  flow" is two prices, and the brief's toggle is already applied upstream to one of them.
- **Deoptimisation counting uses `jdk.Deoptimization`**, which is the one compiler event that did
  report on shipped settings in §1.3 — and the count is still checked against
  `-XX:+PrintCompilation`'s "made not entrant" lines before any rate is published.
- **Open question 3 lives here.** On a real service at 50 rps under a one-core limit, 61 % of self
  CPU was the JVM's own threads and the frames were C2's. That is not a construct verdict and does
  not fit the brief's output shape, but on the class of service this study is about it may be the
  largest JIT-related number in it. Measured on konekt's own stand, it decides nothing about the
  construct list and is reported separately.
- Does **not** cover: GC tuning, or any conclusion about collector behaviour.

- AC: the recording is shown non-empty and the deoptimisation events grouped by site before any
  verdict is written.
- AC: deoptimisations per minute after warm-up, per data mode, with the sites named; a site that
  recurs is reported with its reason.
- AC: the share of request CPU in exception construction, split by exception class.
- AC: a committed number for the compiler threads' own CPU under a container limit, with the rate,
  the limit and the machine stated beside it.
- Anchors: `bench/profile/run.sh`, `bench/profile/konekt.sh`.
