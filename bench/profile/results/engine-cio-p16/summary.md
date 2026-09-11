# 2026-09-11T01:08:55Z label=engine-cio-p16 warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=69211 p50=0.73ms p99=6.10ms requests=100.0% ok
cost: cpu=865.0s over 120.0s = 7.21 cores busy, 104 us cpu/req, 1.25 ctx/req, threads=66, peak rss=805 MiB, requests=8305451
rps=68636 p50=0.74ms p99=6.19ms requests=100.0% ok
## /home/youndie/bench-results/engine-cio-p16/business.cpu.collapsed  (samples: 205017)
| category | self | owner |
|---|---|---|
| coroutines |  29.1% |  39.2% |
| serialization |   4.2% |   6.8% |
| user |   1.9% |   4.1% |
| ktor |  14.0% |  30.3% |
| kotlinx |   3.5% |   3.7% |
| kotlin |   9.1% |  15.7% |
| jdk |  11.8% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  26.3% |   0.3% |
| user code anywhere on the stack |  30.0% | |

cpu self:  11.5% kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker
cpu self:   5.2% __write
cpu self:   4.0% kotlinx/coroutines/internal/LimitedDispatcher.dispatch
cpu self:   3.9% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   3.7% itable stub
cpu self:   2.9% pthread_cond_signal
cpu self:   2.8% epoll_ctl
cpu self:   2.3% read
cpu self:   1.3% java/util/HashMap.getNode
cpu self:   1.1% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   1.1% io/ktor/utils/io/pool/DefaultPool.popTop
cpu self:   1.0% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   1.0% kotlin/coroutines/CombinedContext.get
cpu self:   1.0% java/lang/AbstractStringBuilder.append
cpu self:   1.0% io/ktor/http/cio/internals/CharsKt.equalsLowerCase
cpu self:   0.9% kotlinx/coroutines/internal/LockFreeTaskQueueCore.addLast
cpu self:   0.8% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   0.8% kotlinx/coroutines/internal/LockFreeLinkedListNode.correctPrev
cpu self:   0.8% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.8% bench/Pricing.quote
## GC and JIT from the service log
lines=3
