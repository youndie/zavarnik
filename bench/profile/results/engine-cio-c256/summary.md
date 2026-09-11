# 2026-09-11T00:42:25Z label=engine-cio-c256 warmup=60s measure=120s conns=256
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=66532 p50=2.67ms p99=37.30ms requests=100.0% ok
cost: cpu=876.0s over 120.0s = 7.30 cores busy, 110 us cpu/req, 1.19 ctx/req, threads=110, peak rss=822 MiB, requests=7984110
rps=66438 p50=2.67ms p99=36.03ms requests=100.0% ok
## /home/youndie/bench-results/engine-cio-c256/business.cpu.collapsed  (samples: 208449)
| category | self | owner |
|---|---|---|
| coroutines |  34.2% |  42.7% |
| serialization |   3.9% |   6.6% |
| user |   1.8% |   3.7% |
| ktor |  13.2% |  28.6% |
| kotlinx |   3.8% |   4.0% |
| kotlin |   8.3% |  14.0% |
| jdk |  10.8% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  24.1% |   0.4% |
| user code anywhere on the stack |  29.1% | |

cpu self:  12.9% kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker
cpu self:   6.1% kotlinx/coroutines/internal/LimitedDispatcher.dispatch
cpu self:   5.2% __write
cpu self:   3.3% itable stub
cpu self:   3.1% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   2.5% pthread_cond_signal
cpu self:   2.4% epoll_ctl
cpu self:   2.1% read
cpu self:   2.0% kotlinx/coroutines/internal/LockFreeTaskQueueCore.addLast
cpu self:   1.5% java/util/HashMap.getNode
cpu self:   1.2% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   1.1% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   1.1% kotlin/coroutines/CombinedContext.get
cpu self:   1.1% io/ktor/http/cio/internals/CharsKt.equalsLowerCase
cpu self:   0.9% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.9% kotlinx/io/SegmentPool.takeL2
cpu self:   0.9% kotlinx/coroutines/JobSupport.removeNode$kotlinx_coroutines_core
cpu self:   0.8% java/lang/AbstractStringBuilder.append
cpu self:   0.8% bench/Pricing.quote
cpu self:   0.7% kotlinx/serialization/json/internal/StringJsonLexer.consumeKeyString
## GC and JIT from the service log
lines=3
