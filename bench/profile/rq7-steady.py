import re, subprocess, collections, sys
J = "/usr/lib/jvm/java-25-openjdk-amd64/bin/jfr"
for ep, reqs in (("dbitem", 861840), ("dblist", 535320), ("dbpost", 318240)):
    raw = subprocess.run([J, "print", "--events", "jdk.Deoptimization",
                          "/root/bench-results/rq7/%s.jfr" % ep], capture_output=True, text=True).stdout
    ev = []
    for b in raw.split("jdk.Deoptimization {")[1:]:
        if "jdk.jfr.internal" in b or "Attach Listener" in b:
            continue
        t = re.search(r"startTime = (\d+):(\d+):(\d+)\.(\d+)", b)
        m = re.search(r"method = (.+)", b)
        bci = re.search(r"bci = (\d+)", b)
        a = re.search(r'action = "(.+)"', b)
        r = re.search(r'reason = "(.+)"', b)
        if t and m:
            sec = int(t.group(1))*3600 + int(t.group(2))*60 + int(t.group(3)) + int(t.group(4))/1000
            ev.append((sec, m.group(1).strip() + "@" + (bci.group(1) if bci else "?"),
                       a.group(1) if a else "?", r.group(1) if r else "?"))
    t0 = min(e[0] for e in ev); tN = max(e[0] for e in ev)
    mid = [e for e in ev if t0 + 10 <= e[0] <= tN - 10]
    span = (tN - 10) - (t0 + 10)
    print("== %s  steady window %.0f s: %d events = %.2f/min, %.1f per million requests"
          % (ep, span, len(mid), len(mid)/span*60, len(mid)/reqs*1e6))
    print("   actions: " + ", ".join("%s=%d" % kv for kv in collections.Counter(e[2] for e in mid).most_common()))
    print("   reasons: " + ", ".join("%s=%d" % kv for kv in collections.Counter(e[3] for e in mid).most_common(4)))
    for site, c in collections.Counter(e[1] for e in mid).most_common(4):
        times = sorted(e[0] for e in mid if e[1] == site)
        gaps = ("   intervals: " + ", ".join("%.0fs" % (times[i+1]-times[i]) for i in range(len(times)-1))) if c > 1 else ""
        print("      %2d x  %s%s" % (c, site, gaps))
    print()
