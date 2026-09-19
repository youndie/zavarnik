---
id: B-42
title: "A warm-up gate on an instrument that reports, and the answer to why JFR's compiler events stop"
status: open
priority: P1
size: S
stage: stage-8-jit-constructs
---

# B-42 — The steady-state gate, rebuilt on something that can say no

The brief starts measurement "after JFR shows no C2 compilations for 60 seconds". Measured
([research-jit-constructs](../research/research-jit-constructs.md) §1.3), a `settings=profile`
recording of a run with 1258 compilations holds **zero** `jdk.Compilation` events, and so does the
repeat: the shipped threshold is 100 ms and `jdk.CompilerInlining` ships disabled. The gate as
written cannot fail.

- **The gate becomes a separate run** with `-XX:+PrintCompilation -XX:+PrintInlining`, whose output
  is a warm-up *duration* for this stand. The timing runs then use that duration and carry no
  compiler flags, because the flags change what they would be timing (D3).
- **The duration is per data mode and per engine**, not one number: real mode compiles driver and
  pool code that stub mode never loads.
- **Open question 1 is settled here or recorded as unsettled.** Even forced on, the recording held
  about 60 events of some 1340 compile tasks, inside a window of roughly twenty milliseconds — a
  count that repeats between runs while the window moves. Hypothesis: per-thread buffers that the
  compiler threads stop filling once compilation tails off, so nothing flushes them. Test: the stand
  under load, where compilation continues for minutes, with `jcmd JFR.dump` taken mid-run rather
  than at exit. If JFR turns out usable on a live service, the
  gate can move back to it and say so.
- Does **not** cover: choosing the load level. That is B-41's `cost:` protocol.

- AC: a committed log showing, for each mode, the time after which `-XX:+PrintCompilation` prints
  no level-4 compilation of application, Ktor, Exposed or serialiser code for 60 s.
- AC: a committed comparison of `jcmd JFR.dump` mid-run against the same run's exit dump, with the
  event counts of both, and a written answer — usable, or not, and why.
- Anchors: `bench/profile/run.sh`, `experiments/jit-warmup/warmup-curve.sh`,
  `experiments/jfr-compiler-events/run.sh`.
