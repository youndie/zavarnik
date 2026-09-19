---
id: B-42
title: "A warm-up gate on an instrument that reports: jdk.Compilation switched on, inlining evidence taken elsewhere"
status: done
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

## Result — 2026-09-20: the gate is met, for one of the two things it could mean

Answered from the RQ7 recordings, which already carry `jdk.Compilation` at `threshold=0ms` — the
setting this item established — over 180 s that begin after 90 s of load.

| | level-4 compilations of app/Ktor/Exposed/serialiser code | longest quiet stretch |
|---|---|---|
| dbitem | 16, of which 15 in the first 10 s | **160 s** |
| dblist | 25, all in the first 10 s | **180 s** |
| dbpost | 33, 24 in the first 10 s | **100 s** |

**The brief's "no C2 compilations for 60 seconds" is met on all three, with margin** — once the
first bucket is excluded, and it must be: §1.21 showed that starting the recording is itself what
compiles, and the last bucket is `JFR.stop` doing the same in reverse. So the answer to "how long is
warm-up" on this stand is **90 s of load**, which is what the runners already use and now read from
`bench/profile/warmup.env` rather than re-typing.

**But the gate only closes for that scope.** Counting every compilation at every level, C2 never
goes quiet: 1–19 events per 10 s across the whole window, on every endpoint. That is the same fact
§1.20 reports as 4.9–6.1 % of request CPU in compiler threads. The brief does not say whether its
gate means hot application-path code or all compilation, and the two readings disagree — the same
shape of hole as the RQ0 gate ([B-53](B-53-compute-the-rq0-gate.md)), and worth recording as a
second instance rather than a one-off.

**Not done, and named:** the cross-check against `-XX:+PrintCompilation` on the same workload. The
`jdk.Compilation` census was already cross-checked against compile-task counts in §1.3 (6022 of 6052
in one sweep, 6015 of 6024 in another), which is the same claim by a different route, so the second
cross-check would be confirmation rather than evidence.

**Anchors:** `bench/profile/results/warmup-gate.md`, `bench/profile/warmup.env`,
`bench/profile/rq7-steady-state.sh`.
