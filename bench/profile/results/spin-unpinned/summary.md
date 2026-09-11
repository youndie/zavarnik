# 2026-09-06T22:13:37Z label=spin-unpinned warmup=30s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=34356 p50=1.16ms p99=10.13ms requests=100.0% ok
rps=34595 p50=1.13ms p99=10.23ms requests=100.0% ok
## /home/youndie/bench-results/spin-unpinned/business.cpu.collapsed  (samples: 168499)
| category | self | owner |
|---|---|---|
| user |   0.8% |   1.8% |
| ktor |   6.6% |  13.9% |
| kotlinx |  57.2% |  76.7% |
| kotlin |   4.4% |   7.5% |
| slf4j |   0.0% |   0.0% |
| jdk |   5.7% |   0.0% |
| jvm |  25.3% |   0.1% |
| user code anywhere on the stack |  17.6% | |

cpu self:  33.2% kotlinx/coroutines/internal/LimitedDispatcher.obtainTaskOrDeallocateWorker
cpu self:   8.6% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:   8.3% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   3.5% pthread_cond_signal
cpu self:   2.6% kotlinx/coroutines/scheduling/WorkQueue.pollBuffer
## GC and JIT from the service log
lines=3
