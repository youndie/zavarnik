# 2026-09-11T00:27:10Z label=engine-cio-c8 warmup=60s measure=120s conns=8
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=20110 p50=0.37ms p99=0.73ms requests=100.0% ok
cost: cpu=556.6s over 120.0s = 4.64 cores busy, 231 us cpu/req, 10.80 ctx/req, threads=60, peak rss=783 MiB, requests=2413259
rps=20452 p50=0.36ms p99=0.70ms requests=100.0% ok
## /home/youndie/bench-results/engine-cio-c8/business.cpu.collapsed  (samples: 119594)
| category | self | owner |
|---|---|---|
| coroutines |  22.8% |  54.0% |
| serialization |   2.4% |   4.1% |
| user |   1.2% |   2.9% |
| ktor |  11.4% |  24.5% |
| kotlinx |   1.7% |   1.8% |
| kotlin |   7.4% |  12.6% |
| jdk |  10.6% |   0.0% |
| slf4j |   0.0% |   0.0% |
| jvm |  42.6% |   0.1% |
| user code anywhere on the stack |  24.0% | |

cpu self:  14.6% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   8.1% pthread_cond_signal
cpu self:   5.5% kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull
cpu self:   4.0% __write
cpu self:   3.9% kotlinx/coroutines/scheduling/WorkQueue.pollBuffer
cpu self:   2.9% itable stub
cpu self:   1.6% epoll_ctl
cpu self:   1.5% read
cpu self:   1.3% kotlinx/coroutines/internal/LimitedDispatcher.dispatch
cpu self:   1.2% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   1.2% java/util/HashMap.getNode
cpu self:   1.0% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   1.0% epoll_wait
cpu self:   0.9% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.8% pthread_cond_timedwait
cpu self:   0.8% kotlinx/coroutines/scheduling/CoroutineScheduler$Worker.findAnyTask
cpu self:   0.8% __vdso_clock_gettime
cpu self:   0.8% Parker::park
cpu self:   0.7% kotlinx/coroutines/scheduling/CoroutineScheduler$Worker.runWorker
cpu self:   0.6% kotlinx/coroutines/scheduling/WorkQueue.add
## GC and JIT from the service log
lines=3
