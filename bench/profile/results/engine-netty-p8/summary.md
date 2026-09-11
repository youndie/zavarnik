# 2026-09-11T01:14:05Z label=engine-netty-p8 warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=82889 p50=0.71ms p99=1.82ms requests=100.0% ok
cost: cpu=782.0s over 120.0s = 6.52 cores busy, 79 us cpu/req, 0.64 ctx/req, threads=43, peak rss=855 MiB, requests=9946915
rps=79689 p50=0.74ms p99=1.89ms requests=100.0% ok
## /home/youndie/bench-results/engine-netty-p8/business.cpu.collapsed  (samples: 181984)
| category | self | owner |
|---|---|---|
| coroutines |   7.8% |   8.5% |
| serialization |   6.3% |  10.1% |
| netty |  14.3% |  44.7% |
| user |   2.4% |   5.7% |
| ktor |   8.7% |  12.7% |
| kotlinx |   1.3% |   1.6% |
| kotlin |   8.7% |  16.3% |
| jdk |  17.4% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  33.2% |   0.2% |
| user code anywhere on the stack |  39.9% | |

cpu self:   7.3% writev
cpu self:   6.2% __write
cpu self:   5.9% epoll_wait
cpu self:   3.7% read
cpu self:   2.5% itable stub
cpu self:   2.1% java/util/HashMap.getNode
cpu self:   1.2% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   1.2% kotlin/jvm/internal/TypeIntrinsics.getFunctionArity
cpu self:   1.1% bench/Pricing.quote
cpu self:   0.9% kotlin/coroutines/CombinedContext.get
cpu self:   0.9% jbyte_disjoint_arraycopy
cpu self:   0.8% kotlinx/serialization/json/internal/StringJsonLexer.consumeKeyString
cpu self:   0.8% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.8% io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom
cpu self:   0.8% io/ktor/util/pipeline/SuspendFunctionGun.loop
cpu self:   0.7% java/lang/String.equals
cpu self:   0.6% kotlinx/serialization/json/internal/JsonToStringWriter.ensureTotalCapacity
cpu self:   0.6% jdk/internal/util/DecimalDigits.uncheckedGetCharsLatin1
cpu self:   0.6% io/netty/channel/nio/NioIoHandler.wakeup
cpu self:   0.6% __vdso_clock_gettime
## GC and JIT from the service log
lines=8
