# 2026-09-11T00:32:15Z label=engine-netty-c8 warmup=60s measure=120s conns=8
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=23613 p50=0.32ms p99=0.58ms requests=100.0% ok
cost: cpu=314.4s over 120.0s = 2.62 cores busy, 111 us cpu/req, 2.53 ctx/req, threads=42, peak rss=845 MiB, requests=2833660
rps=24621 p50=0.31ms p99=0.55ms requests=100.0% ok
## /home/youndie/bench-results/engine-netty-c8/business.cpu.collapsed  (samples: 70403)
| category | self | owner |
|---|---|---|
| coroutines |   6.4% |   7.0% |
| serialization |   4.0% |   7.3% |
| netty |  11.7% |  52.8% |
| user |   2.2% |   5.0% |
| ktor |   7.9% |  11.7% |
| kotlinx |   0.8% |   1.0% |
| kotlin |   8.0% |  14.9% |
| jdk |  17.3% |   0.0% |
| slf4j |   0.1% |   0.1% |
| jvm |  41.6% |   0.2% |
| user code anywhere on the stack |  36.1% | |

cpu self:  12.7% epoll_wait
cpu self:   9.9% __write
cpu self:   5.7% writev
cpu self:   3.6% read
cpu self:   2.7% itable stub
cpu self:   1.6% java/util/HashMap.getNode
cpu self:   1.3% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   1.0% kotlin/jvm/internal/TypeIntrinsics.getFunctionArity
cpu self:   1.0% __vdso_clock_gettime
cpu self:   0.8% sun/nio/ch/SelectorImpl.processDeregisterQueue
cpu self:   0.7% sun/nio/ch/EPoll.wait
cpu self:   0.7% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.7% jbyte_disjoint_arraycopy
cpu self:   0.7% io/netty/util/concurrent/AbstractEventExecutor.runTask
cpu self:   0.7% io/ktor/util/pipeline/SuspendFunctionGun.loop
cpu self:   0.6% kotlinx/serialization/json/internal/StringJsonLexer.consumeKeyString
cpu self:   0.6% java/lang/String.equals
cpu self:   0.6% io/netty/util/internal/PlatformDependent.hashCodeAscii
cpu self:   0.6% io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom
cpu self:   0.6% bench/Pricing.quote
## GC and JIT from the service log
lines=8
