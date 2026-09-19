import re, subprocess
J = "/usr/lib/jvm/java-25-openjdk-amd64/bin/jfr"
for ep, reqs, secs in (("dbitem", 861840, 180), ("dblist", 535320, 180), ("dbpost", 318240, 180)):
    raw = subprocess.run([J, "print", "--events", "jdk.ExceptionStatistics",
                          "/root/bench-results/rq7/%s.jfr" % ep], capture_output=True, text=True).stdout
    vals = [int(m) for m in re.findall(r"throwables = ([\d ]+)", raw.replace(" ", " ")).__iter__().__class__ and
            [x.replace(" ", "") for x in re.findall(r"throwables = ([\d ]+)", raw)]]
    if not vals:
        print("== %s  no ExceptionStatistics events" % ep); continue
    total = max(vals) - min(vals)
    print("== %s  cumulative throwables %d -> %d  = %d over %d s = %.0f/s, %.3f per request"
          % (ep, min(vals), max(vals), total, secs, total/secs, total/reqs))
