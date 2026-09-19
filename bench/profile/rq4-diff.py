#!/usr/bin/env python3
"""Sort the Exposed-over-JDBC gap into work and JIT failure — RQ4's deciding clause.

    rq4-diff.py <dir> <jdbc us/req> <exposed us/req> <jdbc requests> <exposed requests>

The brief's red for RQ4 has two halves and only the first was ever tested: above 1.5x, AND at least
a third of the gap traceable to failed inlining, megamorphic dispatch or failed scalar replacement.
This is the second.

Method: a difference of two profiles taken at the same fixed rate behind one binary, so every frame
the arms share cancels. What is left is the layer, and the layer is then split by the brief's own
distinction - work the code asked for against work C2 failed to remove.

The three signals, and what each can and cannot show:
  dispatch stubs     `vtable stub` / `itable stub` frames are megamorphic dispatch, directly.
  interpreted frames a hot frame still running interpreted is a compilation that did not happen.
  allocation         bytes Exposed allocates over JDBC bound failed scalar replacement from above -
                     an object that escapes was never a candidate, so this OVERSTATES the signal,
                     which is the safe direction for a threshold test.
"""
import collections, sys

d, us_j, us_e, n_j, n_e = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])

def load(path):
    total = 0; own = collections.Counter(); marks = collections.Counter()
    for line in open(path):
        line = line.rstrip("\n")
        if not line: continue
        stack, _, c = line.rpartition(" ")
        try: c = int(c)
        except ValueError: continue
        total += c
        leaf = stack.split(";")[-1]
        own[leaf] += c
        low = stack.lower()
        if "vtable stub" in low or "itable stub" in low: marks["dispatch stub"] += c
        if "_[0]" in leaf or "Interpreter" in leaf: marks["interpreted"] += c
        if "org/jetbrains/exposed" in stack or "org.jetbrains.exposed" in stack: marks["exposed on stack"] += c
    return total, own, marks

tj, oj, mj = load("%s/jdbc.cpu.collapsed" % d)
te, oe, me = load("%s/exposed.cpu.collapsed" % d)
gap = us_e - us_j
print("CPU per request: jdbc %.0f us, exposed %.0f us, gap %.0f us (%.2fx)" % (us_j, us_e, gap, us_e/us_j))
print()
print("Signals that would make the gap a JIT failure, as a share of each arm's CPU:")
for k in ("dispatch stub", "interpreted", "exposed on stack"):
    sj, se = 100.0*mj[k]/tj, 100.0*me[k]/te
    # each arm's share converted to us/req, then differenced: this is the signal's contribution
    contrib = se/100.0*us_e - sj/100.0*us_j
    print("   %-18s jdbc %5.2f%%  exposed %5.2f%%   -> %+6.1f us/req of the %.0f us gap (%+.0f%%)"
          % (k, sj, se, contrib, gap, 100*contrib/gap))
print()
try:
    aj, _, _ = load("%s/jdbc.alloc.collapsed" % d)
    ae, _, _ = load("%s/exposed.alloc.collapsed" % d)
    print("Allocation: jdbc %.0f B/req, exposed %.0f B/req, extra %.0f B/req"
          % (aj/n_j, ae/n_e, ae/n_e - aj/n_j))
except FileNotFoundError:
    print("Allocation: profiles not present")
print()
print("Where the extra CPU actually goes - the ten frames that grew most, us/req:")
delta = []
for leaf in set(oj) | set(oe):
    d_us = oe[leaf]/te*us_e - oj[leaf]/tj*us_j
    if abs(d_us) > 0.5: delta.append((d_us, leaf))
for d_us, leaf in sorted(delta, reverse=True)[:10]:
    print("   %+7.1f us  %s" % (d_us, leaf))
