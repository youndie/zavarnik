# Allocation census, real mode — 2026-09-19, bench-a/bench-b pair, JDK 25.0.4
# 64 connections, 40 s warm-up discarded, 60 s sampled with async-profiler -e alloc --total

dbitem   rps=4709 p50=12.65ms requests=282499
dblist   rps=3186 p50=18.65ms requests=191079
dbpost   rps=2992 p50=20.73ms requests=179435
DONE

## per request, by bucket
== dbitem  23434 B/request
   everything else           8255 B/req   35.2%
   coroutine machinery       5894 B/req   25.2%
   buffers and strings       3415 B/req   14.6%
   collections               3055 B/req   13.0%
   Object[]                  2442 B/req   10.4%
   boxing                     373 B/req    1.6%

== dblist  74953 B/request
   buffers and strings      35993 B/req   48.0%
   everything else          15695 B/req   20.9%
   Object[]                 10627 B/req   14.2%
   coroutine machinery       5726 B/req    7.6%
   collections               4308 B/req    5.7%
   boxing                    2604 B/req    3.5%

== dbpost  30741 B/request
   everything else           9537 B/req   31.0%
   coroutine machinery       7042 B/req   22.9%
   collections               5651 B/req   18.4%
   buffers and strings       5087 B/req   16.5%
   Object[]                  3001 B/req    9.8%
   boxing                     424 B/req    1.4%

## GC and JIT share of CPU, from the committed pair profiles
dbitem   GC 1.23%  JIT 4.94%
dblist   GC 1.17%  JIT 5.87%
dbpost   GC 1.18%  JIT 6.07%
