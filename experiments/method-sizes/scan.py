#!/usr/bin/env python3
"""Bytecode size of every method on a classpath, grouped by the artifact it came from.

    python3 scan.py --threshold 325 --huge 8000 <jar-or-dir> [...]
    python3 scan.py --json out.json <jar> [...]

WHY THIS AND NOT ASM. The question is one number per method - the length of the Code attribute -
and a class file gives it up after a constant-pool walk. A dependency that has to be fetched, and
that pins its own version of the thing being measured, is a worse trade than sixty lines. The same
reasoning is why sborka's kapkanMethodSizes reads the constant pool directly.

WHY GROUPED BY ARTIFACT. The second phase scanned application code only, because its subject was a
plugin over application code. Here the subject is the request path, and the mass of a request path
belongs to the framework: a table that says "10 of 283 methods are over the threshold" answers a
question about a tenth of a percent of the profile unless it says whose methods they are.

The numbers are static and say nothing about what runs. A method over the threshold is a suspect,
not a cost: only the profile says whether anything ever calls it, and only the inlining log says
whether C2 was ever asked to inline it.
"""
import argparse
import json
import os
import struct
import sys
import zipfile

# Constant-pool tag -> bytes that follow it. Utf8 (1) is variable and handled apart; Long (5) and
# Double (6) occupy two pool slots, which is the classic off-by-one in a hand-written reader.
FIXED = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
         15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
WIDE = (5, 6)


class ClassFile:
    """Just enough of the format to answer: which class, which methods, how long is each body."""

    def __init__(self, data):
        if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
            raise ValueError("not a class file")
        self.d = data
        self.p = 8  # magic, minor, major
        self.pool = {}
        self._pool()
        self.p += 2                                  # access_flags
        this_class = self._u2()
        self.p += 2                                  # super_class
        # NOT `self.p += 2 * self._u2()`: an augmented assignment loads self.p before it
        # evaluates the right-hand side, so the two bytes _u2 consumes are thrown away and
        # every later offset is short by two. It parses on, reads a field attribute length
        # out of the middle of a name, and lands megabytes past the end of a 5 KB file.
        interfaces = self._u2()
        self.p += 2 * interfaces
        self._members()                              # fields, discarded
        self.name = self._class_name(this_class)
        self.methods = self._members()

    def _u1(self):
        v = self.d[self.p]; self.p += 1; return v

    def _u2(self):
        v = struct.unpack_from(">H", self.d, self.p)[0]; self.p += 2; return v

    def _u4(self):
        v = struct.unpack_from(">I", self.d, self.p)[0]; self.p += 4; return v

    def _pool(self):
        count = self._u2()
        i = 1
        while i < count:
            tag = self._u1()
            if tag == 1:
                n = self._u2()
                self.pool[i] = self.d[self.p:self.p + n].decode("utf-8", "replace")
                self.p += n
            elif tag in FIXED:
                if tag == 7:
                    self.pool[i] = ("class", struct.unpack_from(">H", self.d, self.p)[0])
                self.p += FIXED[tag]
            else:
                raise ValueError("unknown constant pool tag %d" % tag)
            i += 2 if tag in WIDE else 1

    def _class_name(self, idx):
        entry = self.pool.get(idx)
        if isinstance(entry, tuple) and entry[0] == "class":
            return self.pool.get(entry[1], "?")
        return "?"

    def _members(self):
        out = []
        for _ in range(self._u2()):
            self.p += 2                              # access_flags
            name = self.pool.get(self._u2(), "?")
            desc = self.pool.get(self._u2(), "?")
            code = None
            for _ in range(self._u2()):              # attributes
                attr = self.pool.get(self._u2(), "?")
                length = self._u4()
                end = self.p + length
                if attr == "Code":
                    self.p += 4                      # max_stack, max_locals
                    code = self._u4()
                self.p = end
            out.append((name, desc, code))
        return out


def scan_entry(name, data, artifact, rows, broken):
    try:
        cf = ClassFile(data)
    except Exception as exc:                          # a jar may hold module-info, or a newer format
        broken.append("%s!%s: %s" % (artifact, name, exc))
        return
    for m_name, m_desc, code in cf.methods:
        if code is not None:
            rows.append({"artifact": artifact, "cls": cf.name,
                         "method": m_name, "desc": m_desc, "size": code})


def scan(paths):
    rows, broken = [], []
    for path in paths:
        artifact = os.path.basename(path)
        if path.endswith(".jar"):
            with zipfile.ZipFile(path) as z:
                for info in z.infolist():
                    # Multi-release jars hold the same class several times; the versioned copies
                    # would double-count, and the base copy is the one a JDK 25 run without
                    # --release uses for everything that is not explicitly versioned.
                    if info.filename.endswith(".class") and not info.filename.startswith("META-INF/"):
                        scan_entry(info.filename, z.read(info), artifact, rows, broken)
        else:
            for root, _, files in os.walk(path):
                for f in files:
                    if f.endswith(".class"):
                        full = os.path.join(root, f)
                        with open(full, "rb") as fh:
                            scan_entry(full, fh.read(), artifact, rows, broken)
    return rows, broken


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--threshold", type=int, default=325, help="FreqInlineSize, read from the JVM")
    ap.add_argument("--huge", type=int, default=8000, help="HugeMethodLimit, a develop flag")
    ap.add_argument("--top", type=int, default=20)
    ap.add_argument("--with-clinit", action="store_true",
                    help="keep <clinit> in the shortlist; by default it is counted and set aside")
    ap.add_argument("--json")
    args = ap.parse_args()

    rows, broken = scan(args.paths)
    over = [r for r in rows if r["size"] > args.threshold]
    huge = [r for r in rows if r["size"] > args.huge]

    # A class initialiser runs once, before anything is hot, and is never a candidate for inlining
    # into a request. It is also, reliably, what the top of a size-ordered list is made of: lookup
    # tables, translation bundles, keyword sets. Counted, then set aside, so that the shortlist this
    # produces is a list of suspects rather than a list of large constants.
    def hot_shaped(r):
        return args.with_clinit or r["method"] != "<clinit>"

    over_hot = [r for r in over if hot_shaped(r)]
    huge_hot = [r for r in huge if hot_shaped(r)]

    by_artifact = {}
    for r in rows:
        a = by_artifact.setdefault(r["artifact"], {"methods": 0, "over": 0, "huge": 0, "bytes": 0})
        a["methods"] += 1
        a["bytes"] += r["size"]
        if r["size"] > args.threshold and hot_shaped(r):
            a["over"] += 1
        if r["size"] > args.huge and hot_shaped(r):
            a["huge"] += 1

    print("methods with a body: %d in %d artifacts" % (len(rows), len(by_artifact)))
    print("over FreqInlineSize=%d: %d (%.2f %%), of which <clinit>: %d"
          % (args.threshold, len(over), 100.0 * len(over) / max(len(rows), 1), len(over) - len(over_hot)))
    print("over %d: %d, of which <clinit>: %d" % (args.huge, len(huge), len(huge) - len(huge_hot)))
    if broken:
        print("unreadable entries: %d (first: %s)" % (len(broken), broken[0]))
    print()
    print("%-52s %8s %8s %7s %6s" % ("artifact", "methods", ">%d" % args.threshold, "share", ">%d" % args.huge))
    for name, a in sorted(by_artifact.items(), key=lambda kv: -kv[1]["over"]):
        print("%-52s %8d %8d %6.1f%% %6d"
              % (name, a["methods"], a["over"], 100.0 * a["over"] / max(a["methods"], 1), a["huge"]))
    print()
    print("the %d largest bodies%s:" % (args.top, "" if args.with_clinit else ", class initialisers set aside"))
    for r in sorted([r for r in rows if hot_shaped(r)], key=lambda r: -r["size"])[:args.top]:
        print("  %7d  %s.%s%s  [%s]" % (r["size"], r["cls"], r["method"], r["desc"], r["artifact"]))

    # The suspend bodies get a section of their own because they are what the brief suspects, and
    # because a name like `Foo$bar$1.invokeSuspend` says nothing about whether it is on the request
    # path - the class it belongs to does. Printed here so that every number the research quotes
    # lives in the log beside the table it came from.
    susp = sorted([r for r in over if r["method"] == "invokeSuspend"], key=lambda r: -r["size"])
    print()
    print("invokeSuspend bodies over the threshold: %d; the %d largest:" % (len(susp), min(args.top, len(susp))))
    for r in susp[:args.top]:
        print("  %7d  %s  [%s]" % (r["size"], r["cls"], r["artifact"]))

    print()
    print("the largest bodies per artifact, class initialisers set aside:")
    for name in sorted(by_artifact, key=lambda n: -by_artifact[n]["over"]):
        sel = sorted([r for r in over_hot if r["artifact"] == name], key=lambda r: -r["size"])[:3]
        if sel:
            print("  %s" % name)
            for r in sel:
                print("    %7d  %s.%s" % (r["size"], r["cls"].rsplit("/", 1)[-1], r["method"]))

    if args.json:
        with open(args.json, "w") as fh:
            json.dump({"threshold": args.threshold, "huge": args.huge,
                       "by_artifact": by_artifact, "rows": rows}, fh)
    return 0


if __name__ == "__main__":
    sys.exit(main())
