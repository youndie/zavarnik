# 2026-09-10T22:25:00Z label=engine-cio warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## echo
clean: rps=55185 p50=0.71ms p99=11.29ms requests=100.0% ok
cost: cpu=826.4s over 120.0s = 6.89 cores busy, 125 us cpu/req, 3.12 ctx/req, threads=103, peak rss=774 MiB, requests=6622435
rps=54945 p50=0.73ms p99=11.29ms requests=100.0% ok
## /home/youndie/bench-results/engine-cio/echo.cpu.collapsed  (samples: 190035)
| category | self | owner |
|---|---|---|
| coroutines |  49.5% |  66.7% |
| user |   0.0% |   0.0% |
| ktor |   9.4% |  22.1% |
| kotlinx |   4.9% |   4.9% |
| kotlin |   4.0% |   6.1% |
| jdk |   4.2% |   0.0% |
| jvm |  28.1% |   0.1% |
| user code anywhere on the stack |   7.5% | |

## /home/youndie/bench-results/engine-cio/echo.alloc.collapsed  (samples: 105448271449)
| category | self | owner |
|---|---|---|
| coroutines |  11.1% |  11.5% |
| user |   0.3% |   0.3% |
| ktor |  44.0% |  69.9% |
| kotlinx |   0.8% |   3.1% |
| kotlin |   7.6% |  15.3% |
| jdk |  26.1% |   0.0% |
| jvm |  10.2% |   0.0% |
| user code anywhere on the stack |  13.7% | |

cpu self:  24.6% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   9.8% kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker
cpu self:   8.4% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   4.6% __write
cpu self:   3.7% pthread_cond_signal
cpu self:   3.5% kotlinx/coroutines/scheduling/WorkQueue.pollBuffer
cpu self:   2.2% kotlinx/io/SegmentPool.take
cpu self:   1.9% read
cpu self:   1.8% itable stub
cpu self:   1.8% epoll_ctl
cpu self:   1.6% kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull
cpu self:   1.3% kotlinx/coroutines/internal/LimitedDispatcher.dispatch
cpu self:   1.0% kotlinx/io/SegmentPool.recycle
cpu self:   0.6% kotlinx/coroutines/scheduling/CoroutineScheduler.parkedWorkersStackPop
cpu self:   0.6% ObjectMonitor::try_spin
cpu self:   0.5% kotlinx/coroutines/scheduling/CoroutineScheduler$Worker.runWorker
cpu self:   0.5% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.5% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   0.5% kotlin/coroutines/CombinedContext.get
cpu self:   0.5% io/ktor/utils/io/pool/DefaultPool.popTop
## items
clean: rps=49772 p50=0.87ms p99=9.54ms requests=100.0% ok
cost: cpu=846.6s over 120.0s = 7.06 cores busy, 142 us cpu/req, 3.21 ctx/req, threads=103, peak rss=825 MiB, requests=5972798
rps=49657 p50=0.87ms p99=9.57ms requests=100.0% ok
## /home/youndie/bench-results/engine-cio/items.cpu.collapsed  (samples: 195348)
| category | self | owner |
|---|---|---|
| coroutines |  34.9% |  52.8% |
| serialization |   3.1% |   5.8% |
| user |   1.1% |   1.2% |
| ktor |  11.1% |  26.0% |
| kotlinx |   4.4% |   4.4% |
| kotlin |   5.1% |   9.7% |
| jdk |   9.2% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  31.1% |   0.1% |
| user code anywhere on the stack |  20.0% | |

## /home/youndie/bench-results/engine-cio/items.alloc.collapsed  (samples: 144678570511)
| category | self | owner |
|---|---|---|
| coroutines |   7.6% |   8.0% |
| serialization |   0.8% |  16.1% |
| user |   0.3% |   0.3% |
| ktor |  30.3% |  48.2% |
| kotlinx |   0.5% |   1.9% |
| kotlin |   6.2% |  25.6% |
| jdk |  28.5% |   0.0% |
| jvm |  25.8% |   0.0% |
| user code anywhere on the stack |  46.1% | |

cpu self:  14.6% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   8.7% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   5.9% kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker
cpu self:   5.6% __write
cpu self:   4.0% pthread_cond_signal
cpu self:   3.6% kotlinx/coroutines/scheduling/WorkQueue.pollBuffer
cpu self:   2.4% itable stub
cpu self:   2.0% read
cpu self:   2.0% kotlinx/io/SegmentPool.take
cpu self:   1.9% epoll_ctl
cpu self:   1.2% kotlinx/coroutines/internal/LimitedDispatcher.dispatch
cpu self:   1.0% kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull
cpu self:   0.9% bench/ItemStore$list$$inlined$sortedByDescending$1.compare
cpu self:   0.8% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.8% kotlinx/io/SegmentPool.recycle
cpu self:   0.6% kotlinx/coroutines/scheduling/CoroutineScheduler.parkedWorkersStackPop
cpu self:   0.6% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   0.5% kotlinx/coroutines/scheduling/CoroutineScheduler$Worker.runWorker
cpu self:   0.5% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.5% java/util/concurrent/ConcurrentHashMap$Traverser.advance
## business
clean: rps=43472 p50=0.83ms p99=13.50ms requests=100.0% ok
cost: cpu=863.8s over 120.0s = 7.20 cores busy, 166 us cpu/req, 2.53 ctx/req, threads=103, peak rss=879 MiB, requests=5216660
rps=42676 p50=0.86ms p99=13.36ms requests=100.0% ok
## /home/youndie/bench-results/engine-cio/business.cpu.collapsed  (samples: 200384)
| category | self | owner |
|---|---|---|
| coroutines |  42.3% |  56.9% |
| serialization |   2.2% |   4.4% |
| user |   1.6% |   3.1% |
| ktor |   9.8% |  21.0% |
| kotlinx |   3.0% |   3.1% |
| kotlin |   5.6% |  11.3% |
| jdk |  11.6% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  24.0% |   0.2% |
| user code anywhere on the stack |  27.1% | |

## /home/youndie/bench-results/engine-cio/business.alloc.collapsed  (samples: 188990259177)
| category | self | owner |
|---|---|---|
| coroutines |   8.4% |   8.9% |
| serialization |   2.4% |  14.7% |
| user |   3.6% |   9.9% |
| ktor |  25.6% |  36.8% |
| kotlinx |   0.7% |   3.3% |
| kotlin |   8.1% |  26.4% |
| jdk |  28.0% |   0.0% |
| jvm |  23.2% |   0.0% |
| user code anywhere on the stack |  65.7% | |

cpu self:  17.3% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   6.9% kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker
cpu self:   5.3% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   4.3% kotlinx/coroutines/internal/LimitedDispatcher.dispatch
cpu self:   3.8% __write
cpu self:   2.7% itable stub
cpu self:   2.4% pthread_cond_signal
cpu self:   2.3% kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull
cpu self:   2.2% kotlinx/coroutines/scheduling/WorkQueue.pollBuffer
cpu self:   1.6% read
cpu self:   1.4% epoll_ctl
cpu self:   1.1% kotlinx/coroutines/internal/LockFreeTaskQueueCore.addLast
cpu self:   0.9% kotlinx/io/SegmentPool.take
cpu self:   0.8% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   0.8% bench/Pricing.quote
cpu self:   0.8% ObjectMonitor::try_spin
cpu self:   0.7% kotlinx/coroutines/scheduling/CoroutineScheduler.parkedWorkersStackPop
cpu self:   0.6% kotlinx/io/SegmentPool.recycle
cpu self:   0.6% kotlinx/coroutines/internal/ThreadContextKt.threadContextElements
cpu self:   0.6% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
## GC and JIT from the service log
lines=3
