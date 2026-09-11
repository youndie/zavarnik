# 2026-09-06T22:08:10Z label=spin-taskset8 warmup=30s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=34267 p50=1.17ms p99=10.31ms requests=100.0% ok
rps=32093 p50=1.27ms p99=10.55ms requests=100.0% ok
## /home/youndie/bench-results/spin-taskset8/business.cpu.collapsed  (samples: 163758)
| category | self | owner |
|---|---|---|
| user |   0.8% |   1.7% |
| ktor |   6.8% |  14.7% |
| kotlinx |  52.3% |  75.6% |
| kotlin |   4.6% |   7.9% |
| slf4j |   0.0% |   0.0% |
| jdk |   6.2% |   0.0% |
| jvm |  29.4% |   0.1% |
| user code anywhere on the stack |  18.0% | |

cpu self:  21.8% kotlinx/coroutines/internal/LockFreeTaskQueue.removeFirstOrNull
cpu self:  10.6% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   7.9% kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker
cpu self:   6.0% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   4.9% kotlinx/coroutines/scheduling/WorkQueue.pollBuffer
## GC and JIT from the service log
lines=3
