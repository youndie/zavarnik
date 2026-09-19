---
id: B-51
title: "Explain the real-mode ceiling, or make the stand able to ask the question"
status: open
priority: P1
size: M
stage: stage-8-jit-constructs
blocked_by: [B-41]
---

# B-51 — A ceiling nobody has explained, measured with a ruler too coarse to explain it

In real mode the stand saturates near 3–4.5k rps while PostgreSQL alone does **15 631 tps** on the
same box at 0.256 ms, and the JVM sits at 2.2–2.5 of its 4 cores
([research-jit-constructs](../research/research-jit-constructs.md) §1.10). Something between the
two is the limit and it is not named.

The wall-clock profile names a candidate: **19.4 %** of the request path's time is
`HikariPool.recycle → ConcurrentBag.requite → Thread.yield`, the spin HikariCP runs while anyone
waits for a connection. Acquisition wait is 0.16 %, so it is the hand-off and not the capacity.

What stops that from being the answer is the ruler. Five repeats of one configuration in one
process span **30 %**, and the same pool/concurrency pair measured 4675 rps once and 2641 twenty
minutes later. Every sweep run so far is a single run, so every difference between them is inside
the noise.

- **Measure before explaining.** Three repeats minimum per point, median reported with the spread
  beside it, and the first window after warm-up discarded — it was the slowest of five even after
  40 seconds of load.
- **Then the arms worth trying**, in the order their mechanism is testable: concurrency matched to
  the pool; `-XX:ActiveProcessorCount` against the `Dispatchers.IO` default of 64 threads feeding a
  much smaller pool; the round-trip count per request from the driver's own view.
- **A negative answer is a result here.** If the ceiling is the hand-off, that is a finding about
  every Ktor-plus-Hikari service under over-subscription and belongs upstream rather than in a
  verdict row.
- Does **not** cover: RQ4's construct verdicts. Those need [B-45](B-45-rq4-exposed-read-path-in-three-arms.md),
  and until this item closes, every real-mode number carries the 30 % ruler with it.

- AC: the ceiling is attributed to a named mechanism with repeats behind it, or recorded as
  unexplained with the arms that were tried and what each cost.
- AC: the stand's real-mode ruler is restated after the repeats, so later items know what they can
  claim.
- Anchors: `bench/profile/jit-pair.sh`, `bench/profile/results/pair-stand-notes.md`,
  `bench/src/main/kotlin/bench/Data.kt`.
