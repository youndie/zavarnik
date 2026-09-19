---
id: B-43
title: "Intersect the classpath-wide size shortlist with the profile, so a suspect becomes a cost"
status: open
priority: P1
size: S
stage: stage-8-jit-constructs
blocked_by: [B-41]
---

# B-43 — The scan is done; what is missing is whether anything calls these methods

The classpath-wide scan is committed and answered half of what this item was for
([research-jit-constructs](../research/research-jit-constructs.md) §1.8): 691 of 56 471 methods on
the pinned request path exceed `FreqInlineSize`, three exceed 8000 bytes and all three are cold,
and 52 `invokeSuspend` bodies are over the threshold — four of them on the request path by
construction, in Ktor and the Netty engine rather than in application code.

What it cannot say is whether any of them runs. Size is static; a method over the threshold is a
suspect, and `BufferedChannel.toStringDebug` at 941 bytes will never appear in a request.

- **The remaining work is the intersection**: the shortlist against the CPU profile of B-41, so
  that every row says both how big the method is and what share of samples it owns. A row with no
  samples is struck out, in the output, rather than quietly dropped.
- **Then against the inlining log**, because a large method that C2 was never asked to inline costs
  nothing either. The three questions are separate and only the last one is a finding.
- **The threshold is read from the JVM at report time**, not written into the scan: `FreqInlineSize`
  is platform-dependent by declaration and equal on two platforms only in fact.
- **It stays a report, not a gate.** sborka owns the gate-shaped version (`kapkanMethodSizes`), and
  a number with two owners has none.
- Does **not** cover: pricing any of them. That is B-48.

- AC: one table, committed, with a row per method over the threshold that also appears in the
  profile — size, owner, share of CPU samples, and whether the inlining log names it.
- AC: the count of over-threshold methods that never appear in the profile, stated, because that
  number is the reason the scan alone decides nothing.
- Anchors: `experiments/method-sizes/scan.py`, `experiments/method-sizes/run.sh`,
  `bench/profile/attribute.py`.
