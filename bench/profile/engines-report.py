#!/usr/bin/env python3
"""Tables of the engine phase from the raw artefacts of the runs.

Reads `<results>/engine-<name><suffix>/` directories written by `engines.sh` and prints the
markdown the research document quotes: what each endpoint cost (rps and CPU per request from the
clean, profiler-free window), who owns the profile (attribute.py's owner view), and how much of
the CPU goes into the machinery that hands a request to a handler.

That last bucket is a classification of mine, so it prints the frames it counted with their
shares: the total is only as good as the list under it.

    python3 engines-report.py [--results ~/bench-results] [--suffix ''] [--engines cio,netty,jetty]
                              [--endpoints echo,items,business]
"""
import argparse
import json
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import attribute  # noqa: E402  — the same classifier the runs used

CATEGORIES = [
    ("user", ("bench.",)),
    ("coroutines", ("kotlinx.coroutines.",)),
    ("serialization", ("kotlinx.serialization.",)),
    ("netty", ("io.netty.",)),
    ("jetty", ("org.eclipse.jetty.", "jakarta.servlet.")),
    ("ktor", ("io.ktor.",)),
    ("kotlinx", ("kotlinx.",)),
    ("kotlin", ("kotlin.",)),
    ("jdk", ("java.", "jdk.", "sun.", "javax.", "com.sun.")),
    ("slf4j", ("org.slf4j.",)),
]

# The machinery that moves a request from an I/O thread to the handler and back, per engine. Frame
# prefixes, matched on the leaf (`self`) frame, written with slashes the way async-profiler emits
# Java frames.
DISPATCH = {
    "coroutine queue": (
        "kotlinx/coroutines/internal/LimitedDispatcher",
        "kotlinx/coroutines/internal/LockFreeTaskQueue",
        "kotlinx/coroutines/scheduling/",
        "kotlinx/coroutines/DispatchedTask",
        "kotlinx/coroutines/EventLoop",
    ),
    "netty event loop": (
        "io/netty/channel/nio/NioEventLoop",
        "io/netty/channel/epoll/Epoll",
        "io/netty/util/concurrent/SingleThreadEventExecutor",
        "io/netty/util/internal/shaded/org/jctools/",
    ),
    "jetty pool": (
        "org/eclipse/jetty/util/thread/",
        "java/util/concurrent/SynchronousQueue",
        "java/util/concurrent/ThreadPoolExecutor",
    ),
    "park/unpark": (
        "java/util/concurrent/locks/LockSupport",
        "jdk/internal/misc/Unsafe.park",
        "jdk/internal/misc/Unsafe.unpark",
        "pthread_cond_signal",
        "pthread_cond_wait",
        "__pthread_cond",
        "futex",
    ),
}


def self_frames(path):
    """Leaf frame -> share of samples, plus the total."""
    counts, total = Counter(), 0
    with open(path) as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            stack, _, count = line.rpartition(" ")
            count = int(count)
            total += count
            leaf = stack.split(";")[-1]
            counts[leaf] += count
    return counts, total


def clean_window(d):
    """rps, latency and CPU per request of the profiler-free window."""
    s = d["summary"]
    reqs = sum(int(v) for v in d["statusCodeDistribution"].values())
    return {
        "rps": s["requestsPerSec"],
        "p50": d["latencyPercentiles"]["p50"] * 1000,
        "p99": d["latencyPercentiles"]["p99"] * 1000,
        "ok": s["successRate"] * 100,
        "bytes": s["sizePerRequest"],
        "requests": reqs,
    }


def cost_line(summary_path, endpoint):
    """The `cost:` line run.sh wrote for this endpoint, parsed back into numbers."""
    if not os.path.exists(summary_path):
        return {}
    section, out = None, {}
    for line in open(summary_path):
        if line.startswith("## "):
            section = line[3:].strip()
        if section == endpoint and line.startswith("cost:"):
            for part in line[5:].split(","):
                part = part.strip()
                if part.endswith("us cpu/req"):
                    out["us"] = float(part.split()[0])
                elif part.endswith("cores busy"):
                    # `cpu=863.8s over 120.0s = 7.20 cores busy` — the number is third from the end.
                    out["cores"] = float(part.split()[-3])
                elif part.startswith("threads="):
                    out["threads"] = int(part.split("=")[1])
                elif part.startswith("peak rss="):
                    out["rss"] = float(part.split("=")[1].split()[0])
                elif part.endswith("ctx/req"):
                    out["ctx"] = float(part.split()[0])
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", default=os.path.expanduser("~/bench-results"))
    ap.add_argument("--suffix", default="")
    ap.add_argument("--engines", default="cio,netty,jetty")
    ap.add_argument("--endpoints", default="echo,items,business")
    ap.add_argument("--prefix", default="engine-")
    args = ap.parse_args()
    engines = args.engines.split(",")
    endpoints = args.endpoints.split(",")
    attribute.CATEGORIES[:] = CATEGORIES

    print(f"## Цена запроса — чистое окно без профайлера (`{args.prefix}*{args.suffix}`)\n")
    print("| эндпоинт | движок | rps | p50 | p99 | мкс CPU/запрос | ядер занято | потоков | ctx/запрос | peak RSS | байт/ответ |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for ep in endpoints:
        for e in engines:
            d = os.path.join(args.results, f"{args.prefix}{e}{args.suffix}")
            j = os.path.join(d, f"{ep}.oha0.json")
            if not os.path.exists(j):
                continue
            w = clean_window(json.load(open(j)))
            c = cost_line(os.path.join(d, "summary.md"), ep)
            print(f"| `/{ep}` | {e} | {w['rps']:,.0f} | {w['p50']:.2f} мс | {w['p99']:.2f} мс | "
                  f"{c.get('us', float('nan')):.0f} | {c.get('cores', float('nan')):.2f} | {c.get('threads', 0)} | "
                  f"{c.get('ctx', float('nan')):.2f} | {c.get('rss', float('nan')):.0f} МиБ | {w['bytes']:.0f} |".replace(",", " "))

    for view, title in (("owner", "владельцу"), ("self", "листу")):
        print(f"\n## CPU по {title}\n")
        names = [n for n, _ in CATEGORIES] + ["other", "jvm"]
        print("| эндпоинт | движок | " + " | ".join(names) + " |")
        print("|---|---|" + "---|" * len(names))
        for ep in endpoints:
            for e in engines:
                p = os.path.join(args.results, f"{args.prefix}{e}{args.suffix}", f"{ep}.cpu.collapsed")
                if not os.path.exists(p):
                    continue
                total, self_c, owner_c, _ = attribute.attribute(p)
                c = owner_c if view == "owner" else self_c
                cells = " | ".join(f"{100 * c.get(n, 0) / total:.1f}" for n in names)
                print(f"| `/{ep}` | {e} | {cells} |")

    print("\n## Машинерия передачи запроса обработчику — доля self CPU\n")
    print("| эндпоинт | движок | " + " | ".join(DISPATCH) + " | всего |")
    print("|---|---|" + "---|" * (len(DISPATCH) + 1))
    detail = {}
    for ep in endpoints:
        for e in engines:
            p = os.path.join(args.results, f"{args.prefix}{e}{args.suffix}", f"{ep}.cpu.collapsed")
            if not os.path.exists(p):
                continue
            counts, total = self_frames(p)
            row, seen = [], []
            for bucket, prefixes in DISPATCH.items():
                share = 0
                for frame, n in counts.items():
                    if frame.startswith(prefixes):
                        share += n
                        seen.append((100 * n / total, frame, bucket))
                row.append(100 * share / total)
            detail[(ep, e)] = sorted(seen, reverse=True)
            print(f"| `/{ep}` | {e} | " + " | ".join(f"{x:.1f}" for x in row) + f" | {sum(row):.1f} |")
    print("\nЧто сложено в эти доли (кадры выше 0,5 %):\n")
    for (ep, e), frames in detail.items():
        named = [f"`{f.split('_[')[0]}` {s:.1f} % ({b})" for s, f, b in frames if s >= 0.5]
        if named:
            print(f"- `/{ep}`, {e}: " + ", ".join(named))


if __name__ == "__main__":
    main()
