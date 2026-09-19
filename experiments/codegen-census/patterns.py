#!/usr/bin/env python3
"""How often each Kotlin codegen pattern occurs on a classpath, and in whose artifact.

    python3 patterns.py <jar-or-dir> [...]
    python3 patterns.py --json out.json <jar> [...]

WHY THIS EXISTS. `microbench/src/jmh/kotlin/micro/Codegen.kt` prices each pattern against the
hand-written equivalent. A price is only half of RQ5: a pattern that costs 3 ns and occurs twice in
a process is not a finding. D5 rescoped the question to every owner on the path, not just
application code, so the census has to run over the whole classpath.

WHY INSTRUCTIONS AND NOT CONSTANT-POOL ENTRIES. A class that calls `foo$default` ten times holds
ONE `Methodref` for it. Counting pool entries would have answered "how many distinct targets" while
reporting it as "how often", understating every pattern by however much it is reused - most for the
patterns used hardest, which is the wrong direction for a census to be wrong in. So this walks the
Code attribute, which costs an opcode-length table and nothing else.

WHAT THIS STILL DOES NOT SAY. Static counts, like the sizes scan next door. An occurrence is not an
execution: a pattern in a class nothing loads costs zero, and one inside a loop costs more than its
count. Only the profile says which. Read this table as where to look, never as a cost.
"""
import argparse
import json
import os
import struct
import zipfile

FIXED = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
         15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
WIDE = (5, 6)
REFS = (9, 10, 11)                                   # Fieldref, Methodref, InterfaceMethodref

# Operand bytes per opcode. Everything not named here takes none; the four that are not a constant
# length are handled in the walk.
_OPS = [0] * 256
for _op, _n in {16: 1, 17: 2, 18: 1, 19: 2, 20: 2, 132: 2, 169: 1, 188: 1, 197: 3,
                185: 4, 186: 4, 200: 4, 201: 4}.items():
    _OPS[_op] = _n
for _op in list(range(21, 26)) + list(range(54, 59)):
    _OPS[_op] = 1                                    # iload..aload, istore..astore
for _op in list(range(153, 169)) + [178, 179, 180, 181, 182, 183, 184, 187, 189,
                                    192, 193, 198, 199]:
    _OPS[_op] = 2
INVOKES = (182, 183, 184, 185, 186)
TABLESWITCH, LOOKUPSWITCH, WIDE_OP = 170, 171, 196


class ClassFile:
    """Enough of the format to answer: what does this class implement, hold, and call."""

    def __init__(self, data):
        if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
            raise ValueError("not a class file")
        self.d = data
        self.p = 8
        self.pool = {}
        self._pool()
        self.p += 2
        this_class = self._u2()
        self.p += 2
        # Not `self.p += 2 * self._u2()`: the augmented assignment loads self.p before evaluating
        # the right-hand side and silently loses the two bytes. Same trap as in scan.py.
        n_ifaces = self._u2()
        self.interfaces = [self._class_name(self._u2()) for _ in range(n_ifaces)]
        self.fields = self._members(code=False)
        self.name = self._class_name(this_class)
        self.methods = self._members(code=True)

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
                elif tag in REFS:
                    self.pool[i] = ("ref", struct.unpack_from(">HH", self.d, self.p))
                elif tag == 12:
                    self.pool[i] = ("nat", struct.unpack_from(">HH", self.d, self.p))
                self.p += FIXED[tag]
            else:
                raise ValueError("unknown constant pool tag %d" % tag)
            i += 2 if tag in WIDE else 1

    def _class_name(self, idx):
        e = self.pool.get(idx)
        return self.pool.get(e[1], "?") if isinstance(e, tuple) and e[0] == "class" else "?"

    def target(self, idx):
        """(owner, name) of a Fieldref/Methodref/InterfaceMethodref, or None for anything else.

        invokedynamic points at an InvokeDynamic entry, not a ref; a lambda call site therefore
        resolves to None here and is counted by its class shape instead, below."""
        e = self.pool.get(idx)
        if not (isinstance(e, tuple) and e[0] == "ref"):
            return None
        cls_i, nat_i = e[1]
        nat = self.pool.get(nat_i)
        if not (isinstance(nat, tuple) and nat[0] == "nat"):
            return None
        return self._class_name(cls_i), self.pool.get(nat[1][0], "?")

    def _members(self, code):
        out = []
        for _ in range(self._u2()):
            flags = self._u2()
            name = self.pool.get(self._u2(), "?")
            self.p += 2                              # descriptor
            body = None
            for _ in range(self._u2()):
                attr = self.pool.get(self._u2(), "?")
                length = self._u4()
                end = self.p + length
                if code and attr == "Code":
                    self.p += 4                      # max_stack, max_locals
                    n = self._u4()
                    body = self.d[self.p:self.p + n]
                self.p = end
            out.append((name, flags, body))
        return out


def invocations(cf, body):
    """Yield the (owner, name) of every invoke instruction in a method body."""
    i, n = 0, len(body)
    while i < n:
        op = body[i]
        if op in INVOKES:
            t = cf.target(struct.unpack_from(">H", body, i + 1)[0])
            if t:
                yield t
            i += 1 + _OPS[op]
        elif op == TABLESWITCH:
            j = i + 1 + ((4 - (i + 1) % 4) % 4)       # pad to the next 4-byte boundary
            low, high = struct.unpack_from(">ii", body, j + 4)
            i = j + 12 + 4 * (high - low + 1)
        elif op == LOOKUPSWITCH:
            j = i + 1 + ((4 - (i + 1) % 4) % 4)
            npairs = struct.unpack_from(">i", body, j + 4)[0]
            i = j + 8 + 8 * npairs
        elif op == WIDE_OP:
            i += 6 if body[i + 1] == 132 else 4       # wide iinc is longer
        else:
            i += 1 + _OPS[op]


# Each pattern is a predicate over (owner, name) of a call site. `Sequence` and the eager chain are
# both counted so that the pair can be read as a ratio: which one this code actually reaches for.
# The "eager collection op" column needs reading with care, and the positive control is what
# showed it. `List.map` and `List.filter` are INLINE in the stdlib: they leave no call site at all,
# and what lands in CollectionsKt is their residue (`collectionSizeOrDefault`) plus the non-inline
# ends of the chain (`toList`, `sumOfInt`, `asSequence`). `Sequence.map`/`filter` are not inline and
# leave one call per stage. So a chain written eagerly is SMALLER here than the same chain written
# lazily, which is the reverse of how the two are usually ranked - see research §1 on RQ5.
CALL_PATTERNS = {
    "value class boxed": lambda o, m: m in ("box-impl", "unbox-impl"),
    "$default synthetic": lambda o, m: m.endswith("$default"),
    "eager collection op": lambda o, m: o.startswith("kotlin/collections/CollectionsKt"),
    "sequence op": lambda o, m: o.startswith("kotlin/sequences/SequencesKt"),
    "delegate getValue/setValue": lambda o, m: m in ("getValue", "setValue") and (
        o.startswith("kotlin/properties/") or o == "kotlin/Lazy"),
    "null-check intrinsic": lambda o, m: o == "kotlin/jvm/internal/Intrinsics" and m.startswith(
        ("checkNotNull", "checkParameterIsNotNull", "checkNotNullParameter",
         "checkNotNullExpressionValue")),
}
FUNCTION_IFACES = ("kotlin/jvm/functions/Function",)


def scan_entry(name, data, artifact, acc, broken):
    try:
        cf = ClassFile(data)
    except Exception as exc:
        broken.append("%s!%s: %s" % (artifact, name, exc))
        return
    a = acc.setdefault(artifact, {k: 0 for k in CALL_PATTERNS})
    a.setdefault("classes", 0)
    a.setdefault("capturing lambda class", 0)
    a.setdefault("singleton lambda class", 0)
    a.setdefault("delegate field", 0)
    a["classes"] += 1

    # A lambda's shape, not its call site: Kotlin compiles a NON-capturing lambda to a singleton
    # with an INSTANCE field and a capturing one to a class whose captures are instance fields. The
    # split matters because only the second allocates - which is the half of RQ3 that survived.
    if any(i.startswith(FUNCTION_IFACES) for i in cf.interfaces):
        instance_fields = sum(1 for (_, flags, _) in cf.fields if not flags & 0x0008)
        a["capturing lambda class" if instance_fields else "singleton lambda class"] += 1
    a["delegate field"] += sum(1 for (fn, _, _) in cf.fields if fn.endswith("$delegate"))

    for (_, _, body) in cf.methods:
        if not body:
            continue
        for owner, m in invocations(cf, body):
            for label, pred in CALL_PATTERNS.items():
                if pred(owner, m):
                    a[label] += 1


def scan(paths):
    acc, broken = {}, []
    for path in paths:
        artifact = os.path.basename(path)
        if path.endswith(".jar"):
            with zipfile.ZipFile(path) as z:
                for info in z.infolist():
                    if info.filename.endswith(".class") and not info.filename.startswith("META-INF/"):
                        scan_entry(info.filename, z.read(info), artifact, acc, broken)
        else:
            for root, _, files in os.walk(path):
                for f in files:
                    if f.endswith(".class"):
                        with open(os.path.join(root, f), "rb") as fh:
                            scan_entry(f, fh.read(), artifact, acc, broken)
    return acc, broken


COLUMNS = ["value class boxed", "$default synthetic", "capturing lambda class",
           "singleton lambda class", "eager collection op", "sequence op",
           "delegate field", "delegate getValue/setValue", "null-check intrinsic"]
SHORT = ["boxed vc", "$default", "capt λ", "single λ", "eager", "seq", "deleg f", "deleg gv", "null-chk"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--json")
    args = ap.parse_args()
    acc, broken = scan(args.paths)

    total = {c: sum(a.get(c, 0) for a in acc.values()) for c in COLUMNS}
    classes = sum(a.get("classes", 0) for a in acc.values())
    print("classes read: %d in %d artifacts" % (classes, len(acc)))
    if broken:
        print("unreadable entries: %d (first: %s)" % (len(broken), broken[0]))
    print()
    head = "%-46s %7s" % ("artifact", "classes") + "".join("%9s" % s for s in SHORT)
    print(head)
    order = sorted(acc.items(), key=lambda kv: -sum(kv[1].get(c, 0) for c in COLUMNS))
    for name, a in order:
        print("%-46s %7d" % (name[:46], a.get("classes", 0))
              + "".join("%9d" % a.get(c, 0) for c in COLUMNS))
    print("%-46s %7d" % ("TOTAL", classes) + "".join("%9d" % total[c] for c in COLUMNS))
    if args.json:
        with open(args.json, "w") as fh:
            json.dump({"by_artifact": acc, "total": total, "broken": broken}, fh, indent=1)


if __name__ == "__main__":
    main()
