#!/usr/bin/env python3
"""Is the compiler's CPU share a steady cost or the tail of warm-up?

    c2-share-over-time.py <recording.jfr> [...]

§1.20 read 4.9-6.1 % of request CPU in C2's threads off a flat profile and called it a standing
cost, on the strength of the phrase "compilation should long since have settled". A flat profile
cannot say that: it has no time axis, and the windows it came from began 40-45 s into a warm-up
whose quiet point §1.22's runner later put at 90 s. A declining share and a constant one look
identical once averaged.

`jdk.ThreadCPULoad` has the axis. It is emitted per thread per period with `user` and `system` as
fractions of one CPU, so summing the compiler threads per bucket and dividing by the sum over all
threads gives the share as a function of time. If it falls, §1.20's number is warm-up.

The unit matters as much as the shape, which is the other half of the review's point. A share is a
ratio to a denominator that itself moves with offered rate: the same compilation work is 61 % of a
tiny denominator at 50 rps and 5 % of a large one at saturation. Compiling the hot set is closer to
a fixed capital cost in CPU-seconds, so the seconds are printed too, and those are what a pod
lifetime and a deploy frequency can be multiplied by.
"""
import collections, re, subprocess, sys

JFR = "/usr/lib/jvm/java-25-openjdk-amd64/bin/jfr"
COMPILER = re.compile(r"C[12] Compiler(Thread)?|CompilerThread")

for path in sys.argv[1:]:
    raw = subprocess.run([JFR, "print", "--events", "jdk.ThreadCPULoad", path],
                         capture_output=True, text=True).stdout
    tot = collections.Counter(); c2 = collections.Counter()
    t0 = None
    for b in raw.split("jdk.ThreadCPULoad {")[1:]:
        t = re.search(r"startTime = (\d+):(\d+):(\d+)\.(\d+)", b)
        th = re.search(r"eventThread = \"([^\"]+)\"", b)
        u = re.search(r"user = ([\d.]+)", b)
        sy = re.search(r"system = ([\d.]+)", b)
        if not (t and th and u): continue
        sec = int(t.group(1))*3600 + int(t.group(2))*60 + int(t.group(3)) + int(t.group(4))/1000
        if t0 is None or sec < t0: t0 = sec
        load = float(u.group(1)) + (float(sy.group(1)) if sy else 0.0)
        bucket = int((sec - t0) // 10)
        tot[bucket] += load
        if COMPILER.search(th.group(1)): c2[bucket] += load
    if not tot:
        print("== %s: no jdk.ThreadCPULoad events" % path); continue
    n = max(tot) + 1
    print("== %s" % path.split("/")[-1])
    print("   compiler share per 10 s: " + " ".join(
        "%.1f%%" % (100 * c2.get(i, 0) / tot[i]) if tot.get(i) else "  -" for i in range(n)))
    first, last = [i for i in range(n) if tot.get(i)][:3], [i for i in range(n) if tot.get(i)][-3:]
    f = sum(c2.get(i, 0) for i in first) / max(sum(tot.get(i, 0) for i in first), 1e-9)
    l = sum(c2.get(i, 0) for i in last) / max(sum(tot.get(i, 0) for i in last), 1e-9)
    print("   first three buckets %.1f%%, last three %.1f%%  -> %s"
          % (100*f, 100*l, "declining: warm-up tail" if l < f * 0.7 else "flat: a standing cost"))
    # CPU-seconds: each sample is a fraction of one CPU over the event period (default 10 s here)
    print("   compiler CPU-seconds in the window: %.1f of %.1f total (%.1f %%)"
          % (sum(c2.values()) * 10, sum(tot.values()) * 10,
             100 * sum(c2.values()) / max(sum(tot.values()), 1e-9)))
