# RQ4's deciding clause — 2026-09-19, bench-a/bench-b pair, JDK 25.0.4
# Two arms behind one binary at a fixed 2000 rps, pool 32, io.parallelism 16, /db/items?limit=50

jdbc     rps=2000  762 us cpu/req  p50=2.26ms
exposed  rps=2000  894 us cpu/req  p50=3.36ms
DONE

CPU per request: jdbc 762 us, exposed 894 us, gap 132 us (1.17x)

Signals that would make the gap a JIT failure, as a share of each arm's CPU:
   dispatch stub      jdbc  2.19%  exposed  3.04%   ->  +10.5 us/req of the 132 us gap (+8%)
   interpreted        jdbc  0.00%  exposed  0.00%   ->   -0.0 us/req of the 132 us gap (-0%)
   exposed on stack   jdbc  0.00%  exposed 40.82%   -> +365.0 us/req of the 132 us gap (+276%)

Allocation: jdbc 57388 B/req, exposed 73942 B/req, extra 16554 B/req

Where the extra CPU actually goes - the ten frames that grew most, us/req:
     +13.7 us  java/lang/ThreadLocal$ThreadLocalMap.getEntryAfterMiss
     +11.7 us  java/util/HashMap.getNode
     +10.0 us  java/util/ArrayList.grow
      +8.1 us  finish_task_switch.isra.0_[k]
      +8.0 us  itable stub
      +6.9 us  kotlin/jvm/internal/Intrinsics.areEqual
      +5.3 us  org/jetbrains/exposed/v1/core/ResultRow$ResultRowCache.<init>
      +4.3 us  org/jetbrains/exposed/v1/core/ResultRow$Companion.create
      +4.0 us  kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
      +3.2 us  org/postgresql/jdbc/PgResultSet.getObject

## the largest component of the gap, traced
org/jetbrains/exposed/v1/jdbc/Query$ResultIterator.createResultRow
org/jetbrains/exposed/v1/core/ResultRow$Companion.create
org/jetbrains/exposed/v1/core/ResultRow.<init>
org/jetbrains/exposed/v1/core/ResultRow.<init>
org/jetbrains/exposed/v1/core/transactions/TransactionsKt.currentTransactionOrNull
org/jetbrains/exposed/v1/core/transactions/ThreadLocalTransactionsStack.getTransactionOrNull
java/lang/ThreadLocal.get
java/lang/ThreadLocal.get
java/lang/ThreadLocal$ThreadLocalMap.getEntry
java/lang/ThreadLocal$ThreadLocalMap.getEntryAfterMiss 158
