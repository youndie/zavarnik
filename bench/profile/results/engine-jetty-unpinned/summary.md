# 2026-09-11T00:21:59Z label=engine-jetty-unpinned warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=52960 p50=1.11ms p99=3.02ms requests=100.0% ok
cost: cpu=1757.2s over 120.0s = 14.64 cores busy, 276 us cpu/req, 8.87 ctx/req, threads=227, peak rss=910 MiB, requests=6355411
rps=53407 p50=1.10ms p99=2.95ms requests=100.0% ok
## /home/youndie/bench-results/engine-jetty-unpinned/business.cpu.collapsed  (samples: 407561)
| category | self | owner |
|---|---|---|
| coroutines |   4.9% |  30.7% |
| serialization |   2.4% |   4.1% |
| jetty |   4.8% |  22.8% |
| user |   1.3% |   2.8% |
| ktor |   4.9% |   6.8% |
| kotlinx |   0.5% |   0.7% |
| kotlin |   4.9% |   8.0% |
| jdk |  14.9% |   4.1% |
| slf4j |   0.0% |   0.0% |
| jvm |  61.4% |  19.9% |
| user code anywhere on the stack |  29.6% | |

cpu self:  24.4% pthread_cond_signal
cpu self:  18.0% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   3.7% writev
cpu self:   1.7% java/util/concurrent/SynchronousQueue$Transferer.xferLifo
cpu self:   1.7% itable stub
cpu self:   1.6% java/util/concurrent/locks/LockSupport.unpark
cpu self:   1.4% read
cpu self:   1.4% epoll_wait
cpu self:   1.3% epoll_ctl
cpu self:   1.2% org/eclipse/jetty/util/ArrayTrie.getBest
cpu self:   1.1% java/util/concurrent/SynchronousQueue.xfer
cpu self:   1.0% java/util/concurrent/LinkedTransferQueue$DualNode.await
cpu self:   0.9% java/util/HashMap.getNode
cpu self:   0.9% __write
cpu self:   0.7% pthread_cond_timedwait
cpu self:   0.7% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.7% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   0.6% pthread_mutex_lock
cpu self:   0.6% Parker::park
cpu self:   0.5% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
## GC and JIT from the service log
lines=6
