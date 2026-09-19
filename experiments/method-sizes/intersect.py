#!/usr/bin/env python3
"""Which of the oversized methods actually run, and how much of the profile they own.

    python3 intersect.py --scan results/<stamp>.json <collapsed> [...]

scan.py answers "how big is every method on the classpath". A collapsed stack file from
async-profiler answers "what ran". Neither decides anything alone: a 941-byte method that never
appears in a request costs nothing, and the size table is full of those. This is the join, and it
is the only output of the pair that names a cost rather than a suspect.

Two columns per method, and the difference between them is the point:

  self    samples whose LEAF is this method - time spent in its own body
  stack   samples with this method anywhere on the stack - time spent beneath it

A method that is large and hot in `self` is a candidate for the inlining question. A method that is
large and only ever appears in `stack` is a frame, and its size is somebody else's problem.

The count that matters as much as the table is the one printed last: how many oversized methods
never appear at all. That number is why a size scan is not a finding.
"""
import argparse
import collections
import json
import sys


def read_collapsed(paths):
    """folded stacks -> (samples per frame as leaf, samples per frame anywhere, total)"""
    self_n = collections.Counter()
    stack_n = collections.Counter()
    total = 0
    for path in paths:
        with open(path) as fh:
            for line in fh:
                line = line.rstrip("\n")
                if not line:
                    continue
                frames, _, count = line.rpartition(" ")
                try:
                    count = int(count)
                except ValueError:
                    continue
                total += count
                parts = frames.split(";")
                if not parts:
                    continue
                self_n[parts[-1]] += count
                # A frame can repeat on one stack (recursion); counting it once keeps "share of
                # samples that passed through here" a share rather than a sum that can exceed 100 %.
                for f in set(parts):
                    stack_n[f] += count
    return self_n, stack_n, total


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("collapsed", nargs="+")
    ap.add_argument("--scan", required=True, help="the JSON written by scan.py")
    ap.add_argument("--top", type=int, default=30)
    args = ap.parse_args()

    scan = json.load(open(args.scan))
    threshold = scan["threshold"]
    self_n, stack_n, total = read_collapsed(args.collapsed)
    if not total:
        sys.exit("no samples in %s" % ", ".join(args.collapsed))

    # async-profiler writes Java frames with slashes and a dot before the method, which is exactly
    # how scan.py stores them. Anything else in the profile - native frames, allocation leaves -
    # simply will not match, which is correct: they have no bytecode size.
    over = [r for r in scan["rows"] if r["size"] > threshold and r["method"] != "<clinit>"]
    rows = []
    for r in over:
        key = "%s.%s" % (r["cls"], r["method"])
        s, t = self_n.get(key, 0), stack_n.get(key, 0)
        if s or t:
            rows.append((r, s, t))

    rows.sort(key=lambda x: (-x[1], -x[2]))

    # An artifact that is not on this profile at all had no chance: a stand without a database
    # cannot execute Exposed, and counting its methods as "never seen" turns an absent subject into
    # evidence. The miss rate is therefore reported twice - over the whole shortlist and over the
    # part of it that could have run.
    cls_to_art = {}
    for r in scan["rows"]:
        cls_to_art.setdefault(r["cls"], r["artifact"])
    live = set()
    for f in stack_n:
        cls = f.rsplit(".", 1)[0]
        if cls in cls_to_art:
            live.add(cls_to_art[cls])
    could = [r for r in over if r["artifact"] in live]
    absent = sorted({r["artifact"] for r in over} - live)

    print("samples: %d; methods over %d bytes: %d; of those seen in the profile: %d"
          % (total, threshold, len(over), len(rows)))
    print("never seen: %d of the whole shortlist (%.1f %%), %d of the %d in artifacts that appear "
          "at all (%.1f %%)"
          % (len(over) - len(rows), 100.0 * (len(over) - len(rows)) / max(len(over), 1),
             len(could) - len(rows), len(could),
             100.0 * (len(could) - len(rows)) / max(len(could), 1)))
    if absent:
        print("artifacts absent from this profile, so their methods could not run: %s"
              % ", ".join(absent))
    print()
    print("%8s %7s %7s  %s" % ("bytes", "self%", "stack%", "method"))
    for r, s, t in rows[:args.top]:
        print("%8d %6.2f%% %6.2f%%  %s.%s  [%s]"
              % (r["size"], 100.0 * s / total, 100.0 * t / total,
                 r["cls"], r["method"], r["artifact"]))

    print()
    seen_self = sum(s for _, s, _ in rows)
    print("oversized methods own %.2f %% of self samples in total" % (100.0 * seen_self / total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
