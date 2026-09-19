import glob, os, collections
PAT = ("Throwable.<init>", "fillInStackTrace", "JobCancellationException", "Exception.<init>",
       "getStackTrace", "CancellationException")
for f in sorted(glob.glob("/root/bench-results/pair-real-db*/*.cpu.collapsed")):
    ep = os.path.basename(f).split(".")[0]
    total = 0; hit = collections.Counter()
    for line in open(f):
        line = line.rstrip("\n")
        if not line: continue
        stack, _, c = line.rpartition(" ")
        try: c = int(c)
        except ValueError: continue
        total += c
        leaf = stack.split(";")[-1]
        for p in PAT:
            if p in leaf: hit["leaf:" + p] += c
            if p in stack: hit["any:" + p] += c
    anyexc = sum(v for k, v in hit.items() if k.startswith("any:"))
    leafexc = sum(v for k, v in hit.items() if k.startswith("leaf:"))
    print("%-8s samples=%6d  exception frames: leaf %.3f%%  anywhere-on-stack(overcounts) %.2f%%"
          % (ep, total, 100*leafexc/total, 100*anyexc/total))
    for k, v in sorted(hit.items()):
        if v: print("      %-40s %6d  %.3f%%" % (k, v, 100*v/total))
