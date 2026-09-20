import re, subprocess, collections
J = "/usr/lib/jvm/java-25-openjdk-amd64/bin/jfr"
def dur(s):
    tot = 0.0
    for v, u in re.findall(r"([\d.]+)\s*(ms|s|m|h)", s):
        tot += float(v) * {"ms": 0.001, "s": 1, "m": 60, "h": 3600}[u]
    return tot
for ep in ("dbitem", "dblist", "dbpost"):
    raw = subprocess.run([J, "print", "--events", "jdk.CompilerStatistics",
                          "/root/bench-results/rq7/%s.jfr" % ep], capture_output=True, text=True).stdout
    pts = []
    for b in raw.split("jdk.CompilerStatistics {")[1:]:
        t = re.search(r"startTime = (\d+):(\d+):(\d+)\.(\d+)", b)
        tt = re.search(r"totalTimeSpent = (.+)", b)
        cc = re.search(r"compileCount = (\d+)", b)
        if not (t and tt): continue
        sec = int(t.group(1))*3600 + int(t.group(2))*60 + int(t.group(3)) + int(t.group(4))/1000
        pts.append((sec, dur(tt.group(1)), int(cc.group(1)) if cc else 0))
    if len(pts) < 2: print("== %s: too few samples" % ep); continue
    pts.sort()
    t0, cpu0, n0 = pts[0]; t1, cpu1, n1 = pts[-1]
    print("== %s" % ep)
    print("   at the recording's start, after 90 s of load: %.0f compiler CPU-seconds over %d compilations"
          % (cpu0, n0))
    print("   over the %.0f s window: +%.0f CPU-seconds, +%d compilations" % (t1-t0, cpu1-cpu0, n1-n0))
    buckets = collections.defaultdict(float)
    for i in range(1, len(pts)):
        buckets[int((pts[i][0]-t0)//10)] += pts[i][1] - pts[i-1][1]
    n = max(buckets) + 1
    print("   compiler CPU-seconds per 10 s: " + " ".join("%.1f" % buckets.get(i, 0) for i in range(n)))
    mid = sum(v for k, v in buckets.items() if 0 < k < n-1)
    span = (n-2) * 10
    print("   excluding the first and last buckets: %.1f CPU-s over %d s = %.3f cores" % (mid, span, mid/span))
