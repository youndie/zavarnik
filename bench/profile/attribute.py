#!/usr/bin/env python3
"""Attribute async-profiler collapsed stacks to where the code came from.

Reads a `collapsed` file (one stack per line, frames separated by `;`, a count at the end) and
prints, for two views, how the samples split between user code (`bench.*`), Ktor, kotlinx,
the Kotlin stdlib, the JDK, and the JVM itself:

  self   — the leaf frame: who is executing / allocating right now;
  owner  — the first frame from the leaf that is neither JDK nor JVM: whose code asked for it,
           which is the view that bounds what a plugin over that code could change.

The categories are prefixes, listed in CATEGORIES; a frame that matches none is `other`.
Usage: attribute.py <collapsed file> [<collapsed file> ...]
"""
import sys
from collections import Counter

CATEGORIES = [
    ("user", ("bench.",)),
    ("ktor", ("io.ktor.",)),
    ("kotlinx", ("kotlinx.",)),
    ("kotlin", ("kotlin.",)),
    ("jdk", ("java.", "jdk.", "sun.", "javax.", "com.sun.")),
    ("slf4j", ("org.slf4j.",)),
]


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
    for path in sys.argv[1:]:
        total, self_c, owner_c, anywhere = attribute(path)
        print(f"## {path}  (samples: {total})")
        print(f"| category | self | owner |")
        print(f"|---|---|---|")
        for cat in ["user", "ktor", "kotlinx", "kotlin", "slf4j", "other", "jdk", "jvm"]:
            s, o = self_c.get(cat, 0), owner_c.get(cat, 0)
            if s or o:
                print(f"| {cat} | {100 * s / total:5.1f}% | {100 * o / total:5.1f}% |")
        print(f"| user code anywhere on the stack | {100 * anywhere / total:5.1f}% | |")
        print()


if __name__ == "__main__":
    main()
