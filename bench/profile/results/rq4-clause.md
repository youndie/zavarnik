# RQ4's deciding clause, second run — 2026-09-20, clean windows plus the inlining lever
# Four arms behind one binary at a fixed 2000 rps, pool 32, io.parallelism 16, /db/items?limit=50
# 90 s warm-up (warmup.env), then a CLEAN window for us/req; profiles in their own windows after.

jdbc-325 rps=2000  710 us cpu/req  p50=1.94ms
exposed-325 rps=2000  819 us cpu/req  p50=3.44ms
jdbc-2000 rps=2000  684 us cpu/req  p50=1.89ms
exposed-2000 rps=2000  814 us cpu/req  p50=3.45ms
DONE

## the lever: FreqInlineSize 325 -> 2000 on both arms
jdbc     710 -> 684   (-26 us)
exposed  819 -> 814   (-5 us)
gap      109 -> 130   (+21 us: the lever helps JDBC more, so it WIDENS the gap)

CPU per request (clean windows): jdbc 710 us, exposed 819 us, gap 109 us (1.15x)

Signals, as a share of the gap. Each bounds its mechanism from ABOVE.
   dispatch stub        jdbc  1.97%  exposed  2.97%  ->  +10.4 us/req = +10% of the gap
   allocation frames    jdbc  4.36%  exposed  5.08%  ->  +10.7 us/req = +10% of the gap
   allocation bytes     jdbc 55950 B/req, exposed 80557 B/req, extra 24607 -> collector's share +3.0 us = +3%

Coverage: frames that grew total +235 us, frames that shrank -126 us, net +109 us against a 109 us gap.
          the ten largest cover 64 us, which is 59% of the gap - the rest is a long tail.

The twelve frames that grew most, us/req:
     +12.5 us  java/lang/ThreadLocal$ThreadLocalMap.getEntryAfterMiss
      +9.9 us  java/util/HashMap.getNode
      +8.3 us  itable stub
      +7.1 us  kotlin/jvm/internal/Intrinsics.areEqual
      +6.6 us  java/util/ArrayList.grow
      +6.1 us  org/jetbrains/exposed/v1/core/ResultRow$ResultRowCache.<init>
      +4.2 us  org/jetbrains/exposed/v1/core/ResultRow$Companion.create
      +3.5 us  org/jetbrains/exposed/v1/core/ResultRow$ResultRowCache.cached
      +2.9 us  org/postgresql/jdbc/PgResultSet.getObject
      +2.8 us  kotlinx/coroutines/DispatchedTask.run
      +2.6 us  org/jetbrains/exposed/v1/core/statements/api/IdentifierManagerApi.inProperCase
      +2.5 us  I2C/C2I adapters
