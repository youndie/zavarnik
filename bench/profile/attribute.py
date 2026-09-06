#!/usr/bin/env python3
"""Attribute async-profiler collapsed stacks to where the code came from.

Reads a `collapsed` file (one stack per line, frames separated by `;`, a count at the end) and
prints, for two views, how the samples split between user code (`bench.*`), Ktor, kotlinx,
the Kotlin stdlib, the JDK, and the JVM itself:

  self   — the leaf frame: who is executing / allocating right now;
  owner  — the first frame from the leaf that is neither JDK nor JVM: whose code asked for it,
           which is the view that bounds what a plugin over that code could change.

The categories are prefixes, listed in CATEGORIES; a frame that matches none is `other`.
Usage: attribute.py [--categories name=prefix[,prefix]...;name=prefix...] <collapsed file> ...
       The default categories are the bench's; another service passes its own, first match wins,
       e.g. --categories "user=io.konekt.;kompot=io.github.youndie.kompot.;exposed=org.jetbrains.exposed."
       followed by the defaults for ktor, kotlinx, kotlin and jdk.
"""
import sys
from collections import Counter

DEFAULT_CATEGORIES = [
    ("user", ("bench.",)),
    ("ktor", ("io.ktor.",)),
    ("kotlinx", ("kotlinx.",)),
    ("kotlin", ("kotlin.",)),
    ("jdk", ("java.", "jdk.", "sun.", "javax.", "com.sun.")),
    ("slf4j", ("org.slf4j.",)),
]
CATEGORIES = list(DEFAULT_CATEGORIES)


def category(frame: str) -> str:
    # async-profiler writes Java frames as `io/ktor/Foo.bar` and allocation leaves as `java.lang.Integer`;
    # the suffixes are `_[j]`, `_[i]`, `_[k]`, `_[0]`.
    name = frame.split("_[")[0].replace("/", ".")
    for cat, prefixes in CATEGORIES:
        if name.startswith(prefixes):
            return cat
    if "." in name and name[0].isalpha() and name[0].islower() and " " not in name and "::" not in name:
        return "other"
    return "jvm"  # native, kernel, JIT stubs, [unknown]


def attribute(path: str):
    self_c, owner_c, user_anywhere = Counter(), Counter(), 0
    total = 0
    with open(path) as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            stack, _, count = line.rpartition(" ")
            count = int(count)
            frames = stack.split(";")
            total += count
            cats = [category(fr) for fr in reversed(frames)]  # leaf first
            self_c[cats[0]] += count
            owner = next((c for c in cats if c not in ("jdk", "jvm")), cats[0])
            owner_c[owner] += count
            if "user" in cats:
                user_anywhere += count
    return total, self_c, owner_c, user_anywhere


def main():
    args = sys.argv[1:]
    if args and args[0] == "--categories":
        extra = [(part.split("=", 1)[0], tuple(part.split("=", 1)[1].split(","))) for part in args[1].split(";") if part]
        names = {name for name, _ in extra}
        CATEGORIES[:] = extra + [c for c in DEFAULT_CATEGORIES if c[0] not in names]
        args = args[2:]
    order = [name for name, _ in CATEGORIES] + ["other", "jvm"]
    for path in args:
        total, self_c, owner_c, anywhere = attribute(path)
        print(f"## {path}  (samples: {total})")
        print(f"| category | self | owner |")
        print(f"|---|---|---|")
        for cat in order:
            s, o = self_c.get(cat, 0), owner_c.get(cat, 0)
            if s or o:
                print(f"| {cat} | {100 * s / total:5.1f}% | {100 * o / total:5.1f}% |")
        print(f"| user code anywhere on the stack | {100 * anywhere / total:5.1f}% | |")
        print()


if __name__ == "__main__":
    main()
