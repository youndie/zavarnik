---
id: B-53
title: "Compute the RQ0 gate instead of arguing it away"
status: done
priority: P2
size: XS
stage: stage-8-jit-constructs
---

# B-53 — D1 answers RQ0 with a different quantity than RQ0 asks about

The brief's RQ0 is a gate: **CPU per request against p50 latency**, with the study proceeding only
if the ratio clears its bar. [D1](../research/research-jit-constructs.md#d1) rejected the gate as
uninformative — and did so by arguing about **CPU shares by owner**, which is a different quantity.
The brief's author raised this in review and it holds: the objection to RQ0 may well be right, but
it was not supported by the thing RQ0 measures.

- **The division is one line over data already taken.** §1.11's fixed-rate runs at 2000 rps carry
  both numbers per arm: µs of CPU per request and p50. Nothing new needs to be measured.
- **And the gate is rate-dependent, which the brief did not fix.** At 2000 rps the exposed arm is
  646 µs against a 43.0 ms p50 — about 1.5 %. At saturation §1.12 gives 375 µs against 43.0 ms,
  under 1 %. The author's own note concedes the brief never pinned the rate the gate is evaluated
  at, so the gate's answer moves with a parameter it does not name.
- Does **not** cover: re-opening D1's conclusion. The decision to replace the gate can stand; what
  this item fixes is that the decision must be supported by the gate's own quantity, computed, with
  the rate it was computed at stated beside it.

- AC: the ratio computed from the committed §1.11 and §1.12 runs, at both rates, written into D1.
- AC: D1 says explicitly whether the gate would have passed, rather than leaving it inferred.
- Anchors: `docs/research/research-jit-constructs.md` §1.11, §1.12, D1;
  `bench/profile/results/`.

## Result — 2026-09-19: the gate has three answers, and the brief picks none of them

Computed over `bench-results/pair-real-db{item,list,post}`, which carry both quantities: 995/1181/1108
µs of CPU per request against p50 of 6.22/9.56/4.55 ms.

| attribution rule | dbitem | dblist | dbpost | verdict |
|---|---|---|---|---|
| narrow — self in `bench.`/`io.ktor.`/`kotlinx.`/`kotlin.` | 2.5 % | 2.2 % | 4.4 % | **red on all three** |
| middle — plus `java.*`/`jdk.*`, Netty, pgjdbc, Hikari | 7.5 % | 6.5 % | 12.0 % | **neither green nor red** |
| broad — any named owner on the stack; only native, kernel and JIT stubs out | 15.1 % | 11.6 % | 22.8 % | **green on all three** |

Three findings, in order of what they change:

1. **The gate's verdict is chosen by the reader.** "CPU time in JVM code of the application, Ktor,
   Exposed, serialisation and the JDBC driver" does not say where a `HashMap.get` sample reached from
   Exposed belongs. The three defensible readings span red, undecidable and green.
2. **Green and red do not cover the space.** Green is "at least 10 % on two or more", red is "below
   10 % on all three". A run clearing the bar on exactly one endpoint — the middle row above — is
   neither, and the brief gives no instruction for it.
3. **The rate is unstated**, as the author conceded in review, and the ratio moves with it.

Written into [D1](../research/research-jit-constructs.md#d1). D1's conclusion stands; its reasoning
was replaced, because "the bucket is nearly the whole process" was an argument about a different
quantity, which is what the objection said.
