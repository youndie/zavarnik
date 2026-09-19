---
id: B-51
title: "The real-mode ceiling is the machine, and pinning the database does not lift it"
status: done
priority: P1
size: M
stage: stage-8-jit-constructs
---

# B-51 — Answered: the box is full, and three JVM hypotheses died on the way

> **Done 2026-09-19.** Under load the whole machine reads **3.93 of 4 cores** — JVM 1.89,
> PostgreSQL 1.24, kernel and network 0.80. The service stops at two cores because two cores is what
> is left. Numbers and the failed hypotheses:
> [research-jit-constructs](../research/research-jit-constructs.md) §1.12 and §1.13.

What died along the way, in order, each with repeats behind it:

- **the pool** — 16/32/64 give 5286/5069/4595 rps, monotonically *worse*. The "step at 16→32" that
  the single-run sweep showed was noise;
- **Exposed** — hand-written JDBC through the same pool and dispatcher runs 1.67× more requests at
  1.61× less CPU each, and hits the same wall;
- **the IO dispatcher** — `io.parallelism` of 4/8/16/64/128 moves the price of a request from 245 to
  166 µs but leaves cores at 1.77–2.02 throughout;
- and earlier, **the `ConcurrentBag` spin**, which is real but lives in the pool the ceiling ignores.

**The brief's own mitigation does not work here**, and that is worth more than the answer. Its
threats section prescribes pinning the same-host database to its own cores. Pinned, throughput
*fell* 11 398 → 9 471 rps: it throttled Postgres from 1.24 to 0.96 cores rather than giving the JVM a
third. Pinning separates competitors when there is spare capacity; on four cores there is none to
partition.

- **The rule this leaves**: construct verdicts come from fixed-rate runs below saturation, where the
  arms differ only in their own cost. Saturation runs describe the stand. RQ4 ([B-45](B-45-rq4-exposed-read-path-in-three-arms.md))
  was taken that way and is unaffected.
- **A side result worth carrying out of the phase**: the default `Dispatchers.IO` size of 64 costs
  **27 % more CPU per request** than 16 on this four-core box, with 16 the cheapest of five sizes
  tried. Not a JIT finding — library cost, recorded and left alone, but it is a tuning fact about
  every Ktor service that hops to `Dispatchers.IO`.
- **What would actually fix the stand**: the database on a third machine. This pair has two, so the
  write-up states the ceiling as a property of the stand instead.

- Anchors: `bench/profile/results/pair-ceiling.md`,
  `bench/profile/results/pair-ceiling-mechanism.md`, `bench/profile/jit-pair.sh`.
