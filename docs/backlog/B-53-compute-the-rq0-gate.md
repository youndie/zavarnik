---
id: B-53
title: "Compute the RQ0 gate instead of arguing it away"
status: open
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
