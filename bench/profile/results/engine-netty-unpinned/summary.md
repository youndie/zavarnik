# 2026-09-11T00:16:47Z label=engine-netty-unpinned warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=69429 p50=0.85ms p99=2.15ms requests=100.0% ok
cost: cpu=1210.1s over 120.0s = 10.08 cores busy, 145 us cpu/req, 1.50 ctx/req, threads=73, peak rss=995 MiB, requests=8331633
rps=71322 p50=0.84ms p99=1.96ms requests=100.0% ok
## /home/youndie/bench-results/engine-netty-unpinned/business.cpu.collapsed  (samples: 276135)
| category | self | owner |
|---|---|---|
| coroutines |   6.4% |   7.2% |
| serialization |   4.0% |   7.1% |
| netty |  11.9% |  53.6% |
| user |   2.1% |   4.8% |
| ktor |   8.5% |  12.4% |
| kotlinx |   0.8% |   1.0% |
| kotlin |   7.7% |  13.5% |
| jdk |  15.2% |   0.0% |
| slf4j |   0.1% |   0.1% |
| jvm |  43.3% |   0.2% |
| user code anywhere on the stack |  35.3% | |

cpu self:  13.6% __write
cpu self:   9.7% epoll_wait
cpu self:   6.4% writev
cpu self:   3.9% read
cpu self:   2.5% itable stub
cpu self:   1.7% java/util/HashMap.getNode
cpu self:   1.1% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.8% bench/Pricing.quote
cpu self:   0.7% kotlinx/serialization/json/internal/StringJsonLexer.consumeKeyString
cpu self:   0.7% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.7% io/netty/util/concurrent/SingleThreadEventExecutor.pollTaskFrom
cpu self:   0.7% __vdso_clock_gettime
cpu self:   0.7% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   0.6% kotlinx/coroutines/internal/DispatchedContinuation.getContext
cpu self:   0.6% kotlin/jvm/internal/TypeIntrinsics.getFunctionArity
cpu self:   0.6% jbyte_disjoint_arraycopy
cpu self:   0.5% sun/nio/ch/SelectorImpl.processDeregisterQueue
cpu self:   0.5% pthread_cond_signal
cpu self:   0.5% kotlinx/serialization/json/internal/JsonToStringWriter.ensureTotalCapacity
cpu self:   0.5% java/util/ArrayList.grow
## GC and JIT from the service log
lines=8
