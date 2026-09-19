---
id: B-43
title: "Re-run the scan and the join once the stand has a database"
status: done
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

## Result — 2026-09-20: the 192 run, and the conclusion does not move

Joined against four real-mode profiles (`pair-real-db{item,list,post}` and `rq4-clause/exposed`)
instead of the stub-mode one:

| | stub | with a database |
|---|---|---|
| of 638 oversized methods, seen at all | 49 | **73** |
| never seen | 89 % | **88.6 %** |
| self samples they own together | 3.86 % | **3.48 %** |
| largest single | 0.56 % | **0.60 %** (`QueryExecutorImpl.processResults`, 2304 b) |

Both acceptance criteria are met. The Exposed and driver rows gained shares rather than being struck
out — `PgResultSet.getObject` 0.20 %, `QueryExecutorImpl.sendBind` 0.13 %, `BlockingExecutableKt.executeIn`
0.11 % — and the miss rate is reported beside the old one rather than replacing it. The one artifact
that still cannot appear is named in the output (`netty-resolver`), so the denominator is honest.

**What it changes: nothing, and that is the result.** A data layer adds 24 methods to the list of
oversized code that actually runs and lowers their combined share. Written into
[research-jit-constructs](../research/research-jit-constructs.md) §1.9.
