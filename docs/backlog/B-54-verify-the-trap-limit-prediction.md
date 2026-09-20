---
id: B-54
title: "Two predictions from the RQ7 review, each falsifiable in one longer window"
status: open
priority: P2
size: S
stage: stage-8-jit-constructs
---

# B-54 — A prediction worth more than the measurement that suggested it

Review of §1.21 produced two claims that the recording it was based on cannot settle, and both fall
out of one longer run.

**1. `tryPark@40` should never deoptimise a fifth time.** It fired exactly four times in 160 s, and
`PerBytecodeTrapLimit` is **4** on this JVM. If that is the mechanism, C2 has stopped speculating at
that bytecode index for the life of the compiled method, and a window twice as long must show **no
fifth event at that bci**. If a fifth appears, the trap limit is not the explanation and the site is
recompiling — which is the shape the brief's red condition was actually reaching for.

**2. "One `Throwable` per request" is a construction count, not a throw count.** `jdk.ExceptionStatistics`
counts Throwables created — settled by a control that allocates 1 000 000 unthrown `RuntimeException`s
and moves the counter by 1 001 004. `JobCancellationException` is normally handed around as a
cancellation cause and need not pass through `athrow` at all. `jdk.JavaExceptionThrow` would answer
it, but its 300/s throttle was saturated, so the recording could only say "more than 300 per second".

- **Both need the same run**: a longer window with `+jdk.JavaExceptionThrow#throttle=off`, on one
  endpoint, after the 90 s warm-up `warmup.env` carries.
- **The throttle is the thing to watch.** With it off, the event's own cost rises with the true rate;
  if the recording perturbs the service enough to change the number it reports, that has to be seen
  and said rather than discovered later.
- Does **not** cover: re-opening RQ7's verdict. Both halves are green (§1.21); these sharpen the
  explanation under them.

- AC: a window of at least 360 s showing whether a fifth `tryPark@40` event occurs.
- AC: an unthrottled throw count for one endpoint, reported beside the construction count, with the
  ratio between them stated.
- Anchors: `bench/profile/rq7-steady-state.sh`, `bench/profile/rq7-analyse.py`,
  `bench/profile/results/rq7-steady-state.md`.
