# 2026-09-11T00:47:36Z label=engine-netty-c256 warmup=60s measure=120s conns=256
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=103757 p50=2.12ms p99=7.88ms requests=100.0% ok
cost: cpu=856.6s over 120.0s = 7.14 cores busy, 69 us cpu/req, 0.26 ctx/req, threads=42, peak rss=856 MiB, requests=12451133
rps=106832 p50=2.09ms p99=7.13ms requests=100.0% ok
## /home/youndie/bench-results/engine-netty-c256/business.cpu.collapsed  (samples: 210187)
| category | self | owner |
|---|---|---|
| coroutines |   8.9% |   9.6% |
| serialization |   6.9% |  11.1% |
| netty |  15.4% |  40.1% |
| user |   2.3% |   6.2% |
| ktor |   9.5% |  14.0% |
| kotlinx |   1.5% |   1.9% |
| kotlin |   9.0% |  16.5% |
| jdk |  18.1% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  28.5% |   0.4% |
| user code anywhere on the stack |  41.3% | |

cpu self:   8.5% writev
cpu self:   3.6% read
cpu self:   3.4% epoll_wait
cpu self:   3.2% __write
cpu self:   2.5% itable stub
cpu self:   2.1% java/util/HashMap.getNode
cpu self:   1.3% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   1.1% kotlin/jvm/internal/TypeIntrinsics.getFunctionArity
cpu self:   1.0% kotlinx/serialization/json/internal/StringJsonLexer.consumeKeyString
cpu self:   1.0% kotlinx/coroutines/channels/BufferedChannel.updateCellSend
cpu self:   1.0% io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom
cpu self:   1.0% bench/Pricing.quote
cpu self:   0.9% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.9% jbyte_disjoint_arraycopy
cpu self:   0.8% kotlin/coroutines/CombinedContext.get
cpu self:   0.7% kotlinx/serialization/json/internal/JsonToStringWriter.ensureTotalCapacity
cpu self:   0.7% java/lang/String.equals
cpu self:   0.6% kotlinx/coroutines/internal/LockFreeLinkedListNode.finishAdd
cpu self:   0.6% kotlinx/coroutines/CoroutineContextKt$$Lambda.0x000000005a10e328.invoke
cpu self:   0.6% kotlin/jvm/internal/Intrinsics.areEqual
## GC and JIT from the service log
lines=8
