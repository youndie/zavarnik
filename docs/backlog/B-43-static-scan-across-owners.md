---
id: B-43
title: "Re-run the scan and the join once the stand has a database"
status: open
priority: P2
size: XS
stage: stage-8-jit-constructs
blocked_by: [B-41]
---

# B-43 — Done for the half of the path that exists; the other half has no database behind it

Both halves of this item are built and run
([research-jit-constructs](../research/research-jit-constructs.md) §1.8 and §1.9). The scan covers
56 471 methods across the pinned stack and found 638 over `FreqInlineSize`; the join against a CPU
profile of the stand on Netty found that **49 of them run**, that they own **3.86 %** of self
samples between them, and that the largest single one owns 0.56 %.

What is left is not more tooling. It is the same two commands against a stand that has a data layer:
192 of the 638 suspects belong to Exposed, HikariCP and the driver, and on a stand with no database
they could not appear. The 89 % miss rate is honest about that; the number will change when there is
something for them to run in.

- **Re-run `run.sh` and `intersect.py` once B-41 lands**, on the four endpoints of the brief and in
  both data modes, and compare the two miss rates rather than replacing one with the other.
- **The join locates, it does not price.** A method's `self` share is its own body; the cost of a
  refused inline is call overhead plus the optimisation lost across the boundary. Pricing is B-48,
  and §1.9 cut its subject from 638 methods to 49.
- **It stays a report, not a gate** — sborka owns the gate-shaped version, and a number with two
  owners has none.
- Does **not** cover: the alloc profile. Size is a CPU question.

- AC: the intersection re-run with a database behind the stand, with the miss rate reported over the
  artifacts that could appear rather than over the whole shortlist.
- AC: the Exposed and driver rows of the shortlist either gain a share or are struck out in the
  output, so that the 192 stop being unknown.
- Anchors: `experiments/method-sizes/intersect.py`, `experiments/method-sizes/scan.py`,
  `bench/profile/results/netty-jit/`.
