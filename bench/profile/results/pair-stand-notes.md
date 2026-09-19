# the pair, 2026-09-19 — what the stand is and what its ruler is

## machines
subject bench-a, generator bench-b, private link 10.0.0.2 <-> 10.0.0.3
cores: 4   mem: 7 GiB
jdk: openjdk version "25.0.4" 2026-07-21
postgres: PostgreSQL 17.11 on x86_64-pc-linux-musl, com
steal since boot: 0
cpufreq entries: 0  (0 = the governor cannot be fixed here either)
private link rtt:  0.438/0.795/2.069/0.637 ms

## what the database does on its own, without the JVM
pgbench -S, 4 clients, 10 s: tps 15630.9, latency average 0.256 ms

## the ruler: real mode, pool 32, 64 connections, ONE process, five repeats
 repeat        rps     p50ms   cores   us/req
      1       3389     17.24    2.07      609
      2       4138     14.41    2.02      489
      3       4292     13.67    1.98      462
      4       3952     14.61    1.96      496
      5       3109     18.07    1.91      613
rps: min 3109  median 3952  max 4292  -> spread 30 % of the median
DONE

## pool sweep, dbitem, real mode - SINGLE run each, so inside the ruler above
  pool        rps     p50ms     p99ms   cores
     8       3223     69.89    206.61    2.25
    16       3099     76.53    212.10    2.51
    32       4675     48.90    159.41    2.21
    64       4410     51.05    178.53    2.31
DONE

## offered concurrency against pool size - SINGLE run each, same caveat
 pool  conns        rps     p50ms     p99ms   cores
   32     32       3523      8.01     26.76    2.23
   32     64       3036     17.57     75.08    2.43
   32    256       2641     88.22    260.22    2.46
   64     64       3266     16.83     63.23    2.43
DONE

## wall-clock profile of the real-mode request path (25 s under load)
of samples whose stack contains bench/ code:
  69.8 % blocking syscall, 19.4 % sched_yield, 3.3 % pthread_cond_signal
the sched_yield caller, 100 % of it:
  HikariPool.recycle < ConcurrentBag.requite < Thread.yield
the Net.poll caller, 100 % of it:
  org/postgresql/core/VisibleBufferedInputStream.readMore   (the database round trip)
Hikari connection-acquisition wait: 0.16 % of all wall samples
