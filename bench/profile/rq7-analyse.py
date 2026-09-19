#!/usr/bin/env python3
"""Read an RQ7 recording and answer the two halves of the brief's RQ7 separately.

    rq7-analyse.py <jfr-binary> <endpoint>.jfr <requests> <seconds>

WHY NOT JUST COUNT. The brief's green is a rate ("under 1 deoptimisation per minute after warmup")
and its red is a shape ("a deoptimisation recurring at the same site"). Those need different
readings, and two things corrupt both if taken raw:

  * **The instrument deoptimises itself into existence.** Starting the recording with `jcmd` runs
    `jdk.jfr.internal.settings.*` on the Attach Listener thread, and the first events in every
    recording here are C2 discarding code for JFR's own startup. They are not the service's steady
    state and are excluded by thread and by stack.
  * **A falling rate and a flat rate mean opposite things.** Deoptimisation followed by
    recompilation on a better profile is how a JIT converges; the pathology is the same site
    deoptimising for ever. So the window is split in thirds: still settling, or a standing cost.
"""
import collections, json, re, subprocess, sys

jfr, path, requests, secs = sys.argv[1], sys.argv[2], int(sys.argv[3]), float(sys.argv[4])
raw = subprocess.run([jfr, "print", "--events", "jdk.Deoptimization", path],
                     capture_output=True, text=True).stdout
blocks = raw.split("jdk.Deoptimization {")[1:]
kept, dropped = [], 0
for b in blocks:
    if "jdk.jfr.internal" in b or 'eventThread = "Attach Listener"' in b:
        dropped += 1
        continue
    t = re.search(r"startTime = (\d+):(\d+):(\d+)\.(\d+)", b)
    m = re.search(r"method = (.+)", b)
    bci = re.search(r"bci = (\d+)", b)
    act = re.search(r'action = "(.+)"', b)
    rsn = re.search(r'reason = "(.+)"', b)
    if not (t and m):
        continue
    sec = int(t.group(1)) * 3600 + int(t.group(2)) * 60 + int(t.group(3)) + int(t.group(4)) / 1000.0
    kept.append((sec, "%s@%s" % (m.group(1).strip(), bci.group(1) if bci else "?"),
                 act.group(1) if act else "?", rsn.group(1) if rsn else "?"))

print("== %s" % path.split("/")[-1])
print("   events: %d total, %d from JFR's own startup discarded, %d kept" % (len(blocks), dropped, len(kept)))
if not kept:
    print("   NOTHING LEFT - the recording captured only the instrument"); sys.exit(0)
print("   rate: %.2f per minute over %.0f s, %.1f per million requests"
      % (len(kept) / secs * 60, secs, len(kept) / requests * 1e6))

t0 = min(k[0] for k in kept); span = max(max(k[0] for k in kept) - t0, 1e-9)
thirds = [0, 0, 0]
for sec, *_ in kept:
    thirds[min(int((sec - t0) / span * 3), 2)] += 1
print("   over thirds of the window: %d / %d / %d  -> %s"
      % (*thirds, "still settling" if thirds[2] * 2 < thirds[0] else "a standing cost"))

acts = collections.Counter(k[2] for k in kept)
print("   actions: " + ", ".join("%s=%d" % kv for kv in acts.most_common()))
print("   reasons: " + ", ".join("%s=%d" % kv for kv in collections.Counter(k[3] for k in kept).most_common(6)))

sites = collections.Counter(k[1] for k in kept)
print("   distinct sites: %d; most repeated:" % len(sites))
for s, c in sites.most_common(5):
    times = sorted(x[0] for x in kept if x[1] == s)
    gap = (times[-1] - times[0]) / max(len(times) - 1, 1)
    print("      %3d  every %5.1f s   %s" % (c, gap, s))
