---
id: B-42
title: "A warm-up gate on an instrument that reports: jdk.Compilation switched on, inlining evidence taken elsewhere"
status: open
priority: P1
size: S
stage: stage-8-jit-constructs
---

# B-42 — The steady-state gate, wired to something that can say no

The brief starts measurement "after JFR shows no C2 compilations for 60 seconds" and calls JFR the
instrument that needs no diagnostic flags. Measured
([research-jit-constructs](../research/research-jit-constructs.md) §1.3), a `settings=profile`
recording of a run with **7268** compile tasks holds **zero** `jdk.Compilation`
events: the shipped threshold is 100 ms and `jdk.CompilerInlining` ships disabled. The gate as
written cannot fail.

Switched on explicitly, the same event is a census — 6022 against 6052 compile tasks in its id
range in one sweep, 6015 against 6024 in another — so the gate is buildable. What is not buildable
on JFR is the inlining evidence.

- **The gate is `jdk.Compilation` with `+jdk.Compilation#enabled=true` and `#threshold=0ms`**, and
  the runs it gates carry the same setting, since it is the same census either way and costs +5.3 % and +5.7 % across two sweeps
  on a microbenchmark that does nothing but compile — less, on a service that waits for I/O.
- **The duration is per data mode and per engine**, not one number: real mode compiles driver and
  pool code that stub mode never loads.
- **Inlining refusals come from `-XX:+PrintInlining` / `LogCompilation`, in their own runs**, because
  `jdk.CompilerInlining` covers the first 8 to 96 compile ids of a recording and then stops, in
  eight recordings out of eight. That splits a verdict needing both a reason and a time across two
  runs, and the item's job is to make that split cheap rather than to wish it away.
- **The first version of this item had it backwards**, and the reason is kept in §1.3: the control
  was a one-method loop that stopped compiling before the recording was live, so a truncated subject
  read as a truncated instrument.
- Does **not** cover: choosing the load level. That is B-41's `cost:` protocol.

- AC: a committed log showing, for each mode, the time after which `jdk.Compilation` reports no
  level-4 compilation of application, Ktor, Exposed or serialiser code for 60 s, cross-checked once
  against `-XX:+PrintCompilation` on the same workload.
- AC: the warm-up duration is a value the harness reads, not a constant re-typed per script.
- Anchors: `bench/profile/run.sh`, `experiments/jit-warmup/warmup-curve.sh`,
  `experiments/jfr-compiler-events/long-run.sh`.
