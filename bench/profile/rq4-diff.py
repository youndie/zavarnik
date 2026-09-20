#!/usr/bin/env python3
"""Sort the Exposed-over-JDBC gap into work and JIT failure — RQ4's deciding clause.

    rq4-diff.py <dir> <jdbc us/req> <exposed us/req> <requests>

The brief's red for RQ4 has two halves and only the first was ever tested: above 1.5x, AND at least
a third of the gap traceable to failed inlining, megamorphic dispatch or failed scalar replacement.
This is the second, and the first version of this script got two of the three signals wrong.

  * **Failed inlining is NOT interpreted frames.** A refused inline leaves a call to a *compiled*
    method and loses the optimisation across that boundary; it does not leave the callee running in
    the interpreter. Counting interpreted frames answers the huge-method question instead, and
    answers it "zero" whatever the inlining does. The causal test is the lever - FreqInlineSize on
    both arms - and it lives in the runner, not here.
  * **Failed scalar replacement is not bounded by GC alone.** An object C2 could not remove costs
    the collector, and before that it costs the mutator: the header write, the field stores, and
    every later read going to memory instead of a register. Charging it only the collector's share
    understates it several-fold. Both are reported, and the allocation-attributable FRAMES are the
    mutator half.

What no signal here can do is separate "escaped, so never a candidate" from "did not escape and was
missed". Everything below therefore bounds the failure component from ABOVE, which is the safe
direction for a threshold test and is stated rather than implied.
"""
import collections, sys

d, us_j, us_e, reqs = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), int(sys.argv[4])

ALLOC_FRAMES = ("<init>", ".create", "ArrayList.grow", "Arrays.copyOf", "ArrayList.<init>",
                "newInstance", "HashMap.resize", "StringBuilder")

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
        if "vtable stub" in leaf or "itable stub" in leaf: marks["dispatch stub"] += c
        n = leaf.replace("/", ".")
        if any(f in n for f in ALLOC_FRAMES): marks["allocation frames"] += c
    return total, own, marks

tj, oj, mj = load("%s/jdbc.cpu.collapsed" % d)
te, oe, me = load("%s/exposed.cpu.collapsed" % d)
gap = us_e - us_j
print("CPU per request (clean windows): jdbc %.0f us, exposed %.0f us, gap %.0f us (%.2fx)"
      % (us_j, us_e, gap, us_e / us_j))
print()

def us(counter, total, per_req, leaf):  # samples -> us/req
    return counter[leaf] / total * per_req

print("Signals, as a share of the gap. Each bounds its mechanism from ABOVE.")
for k in ("dispatch stub", "allocation frames"):
    contrib = me[k] / te * us_e - mj[k] / tj * us_j
    print("   %-20s jdbc %5.2f%%  exposed %5.2f%%  -> %+6.1f us/req = %+.0f%% of the gap"
          % (k, 100.0 * mj[k] / tj, 100.0 * me[k] / te, contrib, 100 * contrib / gap))
try:
    aj, _, _ = load("%s/jdbc.alloc.collapsed" % d)
    ae, _, _ = load("%s/exposed.alloc.collapsed" % d)
    extra = ae / reqs - aj / reqs
    gc_share = 0.012  # measured on this stand, 1.17-1.23 % of CPU (research 1.20)
    gc_us = extra / (ae / reqs) * gc_share * us_e
    print("   %-20s jdbc %.0f B/req, exposed %.0f B/req, extra %.0f -> collector's share %+.1f us = %+.0f%%"
          % ("allocation bytes", aj / reqs, ae / reqs, extra, gc_us, 100 * gc_us / gap))
except FileNotFoundError:
    print("   allocation profiles not present")
print()

delta = sorted(((oe[l] / te * us_e - oj[l] / tj * us_j, l) for l in set(oj) | set(oe)), reverse=True)
grew = sum(d_us for d_us, _ in delta if d_us > 0)
shrank = sum(d_us for d_us, _ in delta if d_us < 0)
top10 = sum(d_us for d_us, _ in delta[:10])
print("Coverage: frames that grew total %+.0f us, frames that shrank %+.0f us, net %+.0f us against a %.0f us gap."
      % (grew, shrank, grew + shrank, gap))
print("          the ten largest cover %.0f us, which is %.0f%% of the gap - the rest is a long tail."
      % (top10, 100 * top10 / gap))
print()
print("The twelve frames that grew most, us/req:")
for d_us, leaf in delta[:12]:
    print("   %+7.1f us  %s" % (d_us, leaf))
