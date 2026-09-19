# method-sizes

Bytecode size of every method on the request path, grouped by the artifact it came from.

Phase 1 of the fifth phase's brief: the static scan whose output is the shortlist for RQ1. The
second phase scanned application code only and found 10 of 283 methods over `FreqInlineSize`. That
was the right scope for a plugin over application code and the wrong one here, where application
code owns 0.9–4.0 % of a real service's CPU and the request path is mostly framework.

```bash
JAVA_HOME=/path/to/jdk25 ./run.sh
```

`run.sh` resolves the pinned stack — Ktor on Netty, kotlinx.serialization and coroutines, Exposed
over the PostgreSQL driver and HikariCP — from the Gradle cache, falling back to Maven Central into
`lib/` (not committed). The coordinates in the script *are* the pin; editing them is how the scan
follows a version bump. The threshold is read from the JVM at report time rather than written into
the script, because `FreqInlineSize` is platform-dependent by declaration and equal on two
platforms only in fact.

`scan.py` reads the class files directly — a constant-pool walk and the length of each `Code`
attribute. A dependency that would have to be fetched, and that pins its own version of the thing
being measured, is a worse trade than sixty lines; sborka's `kapkanMethodSizes` reads the constant
pool for the same reason.

Class initialisers are counted and then set aside. They run once, before anything is hot, and they
are reliably what the top of a size-ordered list is made of — lookup tables, translation bundles,
keyword sets — so leaving them in turns the shortlist into a list of large constants.

The result is in [research-jit-constructs](../../docs/research/research-jit-constructs.md) §1.8.
**These numbers are static and say nothing about what runs**, which is what `intersect.py` is for:

```bash
python3 intersect.py --scan results/<stamp>.json <path-to>/business.cpu.collapsed
```

It joins the shortlist to a collapsed profile from async-profiler and prints two columns per method
— `self`, samples whose leaf is this method, and `stack`, samples with it anywhere below. A method
that is large and hot in `self` is a candidate for the inlining question; one that is large and
appears only in `stack` is a frame, and its size is somebody else's problem.

The number that matters as much as the table is the miss rate, and it is printed twice: over the
whole shortlist, and over the part of it in artifacts that appear in the profile at all. A stand
without a database cannot execute Exposed, and counting its methods as "never seen" would turn an
absent subject into evidence. On the first run — the stand on Netty, no data layer — 49 of 638
methods appeared, 89 % of those that could have run did not, and the 49 owned 3.86 % of self
samples between them (§1.9).
