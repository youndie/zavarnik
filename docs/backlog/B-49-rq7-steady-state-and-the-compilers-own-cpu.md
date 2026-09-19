---
id: B-49
title: "RQ7: is steady state stable — and what the compiler itself costs under a container limit"
status: open
priority: P2
size: M
stage: stage-8-jit-constructs
blocked_by: [B-42, B-44]
---

# B-49 — Deoptimisation, exceptions as control flow, and the question the brief does not ask

RQ7 asks whether the service reaches a steady state and stays there: recurring deoptimisations,
profile pollution in shared generic code, exceptions used as control flow.

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

- AC: deoptimisations per minute after warm-up, per data mode, with the sites named; a site that
  recurs is reported with its reason.
- AC: the share of request CPU in exception construction, split by exception class.
- AC: a committed number for the compiler threads' own CPU under a container limit, with the rate,
  the limit and the machine stated beside it.
- Anchors: `bench/profile/run.sh`, `bench/profile/konekt.sh`.
