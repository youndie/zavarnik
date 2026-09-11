# 2026-09-11T00:11:40Z label=engine-cio-unpinned warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=34495 p50=1.15ms p99=9.95ms requests=100.0% ok
cost: cpu=1502.7s over 120.0s = 12.52 cores busy, 363 us cpu/req, 5.71 ctx/req, threads=125, peak rss=852 MiB, requests=4139556
rps=34672 p50=1.15ms p99=9.93ms requests=100.0% ok
## /home/youndie/bench-results/engine-cio-unpinned/business.cpu.collapsed  (samples: 343284)
| category | self | owner |
|---|---|---|
| coroutines |  54.1% |  72.4% |
| serialization |   1.6% |   2.7% |
| user |   0.9% |   1.9% |
| ktor |   6.6% |  14.1% |
| kotlinx |   1.2% |   1.2% |
| kotlin |   4.5% |   7.5% |
| jdk |   6.0% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  25.1% |   0.2% |
| user code anywhere on the stack |  18.0% | |

cpu self:  23.3% kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull
cpu self:   9.4% kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker
cpu self:   7.9% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   7.5% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   3.8% pthread_cond_signal
cpu self:   3.5% kotlinx/coroutines/internal/LimitedDispatcher.dispatch
cpu self:   2.8% kotlinx/coroutines/scheduling/WorkQueue.tryStealLastScheduled
cpu self:   2.6% __write
cpu self:   1.8% itable stub
cpu self:   1.1% ObjectMonitor::try_spin
cpu self:   1.0% read
cpu self:   0.8% epoll_ctl
cpu self:   0.8% ObjectMonitor::try_lock
cpu self:   0.7% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   0.6% kotlinx/coroutines/internal/LockFreeTaskQueueCore.addLast
cpu self:   0.6% java/util/HashMap.getNode
cpu self:   0.5% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.4% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.4% kotlin/coroutines/CombinedContext.get
cpu self:   0.4% io/ktor/http/cio/internals/CharsKt.equalsLowerCase
## GC and JIT from the service log
lines=3
