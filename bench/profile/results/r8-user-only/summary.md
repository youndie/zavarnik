# 2026-09-06T20:54:51Z label=r8-user-only warmup=60s measure=120s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## echo
clean: rps=54148 p50=0.88ms p99=6.61ms requests=100.0% ok
rps=53545 p50=0.88ms p99=6.79ms requests=100.0% ok
## /home/youndie/bench-results/r8-user-only/echo.cpu.collapsed  (samples: 278504)
| category | self | owner |
|---|---|---|
| user |   0.0% |   0.0% |
| ktor |   8.6% |  20.7% |
| kotlinx |  46.7% |  72.9% |
| kotlin |   4.0% |   6.3% |
| jdk |   4.4% |   0.0% |
| jvm |  36.3% |   0.1% |
| user code anywhere on the stack |   7.8% | |

## /home/youndie/bench-results/r8-user-only/echo.alloc.collapsed  (samples: 96536965310)
| category | self | owner |
|---|---|---|
| user |   0.2% |   0.2% |
| ktor |  44.4% |  69.8% |
| kotlinx |  11.9% |  14.6% |
| kotlin |   7.6% |  15.4% |
| jdk |  25.5% |   0.0% |
| jvm |  10.3% |   0.0% |
| user code anywhere on the stack |  13.8% | |

## items
clean: rps=35492 p50=1.29ms p99=9.30ms requests=100.0% ok
rps=32453 p50=1.44ms p99=9.20ms requests=100.0% ok
## /home/youndie/bench-results/r8-user-only/items.cpu.collapsed  (samples: 276306)
| category | self | owner |
|---|---|---|
| user |   0.8% |   0.8% |
| ktor |   9.8% |  23.0% |
| kotlinx |  31.5% |  66.5% |
| kotlin |   5.1% |   9.5% |
| slf4j |   0.0% |   0.0% |
| jdk |   8.3% |   0.0% |
| jvm |  44.5% |   0.2% |
| user code anywhere on the stack |  16.2% | |

## /home/youndie/bench-results/r8-user-only/items.alloc.collapsed  (samples: 91291998162)
| category | self | owner |
|---|---|---|
| user |   0.2% |   0.2% |
| ktor |  30.7% |  47.9% |
| kotlinx |   9.2% |  26.4% |
| kotlin |   6.2% |  25.5% |
| jdk |  28.3% |   0.0% |
| jvm |  25.4% |   0.0% |
| user code anywhere on the stack |  45.3% | |

## business
clean: rps=27447 p50=1.55ms p99=11.09ms requests=100.0% ok
rps=30806 p50=1.40ms p99=10.08ms requests=100.0% ok
## /home/youndie/bench-results/r8-user-only/business.cpu.collapsed  (samples: 301377)
| category | self | owner |
|---|---|---|
| user |   1.2% |   2.5% |
| ktor |   9.0% |  18.5% |
| kotlinx |  41.3% |  68.4% |
| kotlin |   5.3% |  10.5% |
| slf4j |   0.0% |   0.0% |
| jdk |   9.7% |   0.0% |
| jvm |  33.5% |   0.2% |
| user code anywhere on the stack |  22.9% | |

## /home/youndie/bench-results/r8-user-only/business.alloc.collapsed  (samples: 124681215757)
| category | self | owner |
|---|---|---|
| user |   3.6% |  10.1% |
| ktor |  25.8% |  36.3% |
| kotlinx |  11.5% |  26.8% |
| kotlin |   8.2% |  26.8% |
| jdk |  27.9% |   0.0% |
| jvm |  23.0% |   0.0% |
| user code anywhere on the stack |  65.7% | |

## GC and JIT from the service log
lines=3
