# 2026-09-06T20:11:13Z label=smoke warmup=15s measure=30s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## echo
rps=58630 p50=0.81ms p99=6.55ms requests=100.0% ok
## /home/youndie/bench-results/smoke/echo.cpu.collapsed  (samples: 70585)
| category | self | owner |
|---|---|---|
| jvm | 100.0% | 100.0% |
| user code anywhere on the stack |   0.0% | |

## /home/youndie/bench-results/smoke/echo.alloc.collapsed  (samples: 23844048473)
| category | self | owner |
|---|---|---|
| user |   0.3% |   0.3% |
| ktor |  44.2% |  44.2% |
| kotlinx |  12.1% |  12.1% |
| kotlin |   7.9% |   7.9% |
| jdk |  25.3% |  25.3% |
| jvm |  10.3% |  10.3% |
| user code anywhere on the stack |   0.3% | |

## items
rps=43773 p50=1.14ms p99=7.01ms requests=100.0% ok
## /home/youndie/bench-results/smoke/items.cpu.collapsed  (samples: 71769)
| category | self | owner |
|---|---|---|
| jvm | 100.0% | 100.0% |
| user code anywhere on the stack |   0.0% | |

## /home/youndie/bench-results/smoke/items.alloc.collapsed  (samples: 31599826064)
| category | self | owner |
|---|---|---|
| user |   0.3% |   0.3% |
| ktor |  30.4% |  30.4% |
| kotlinx |   9.3% |   9.3% |
| kotlin |   6.1% |   6.1% |
| jdk |  28.5% |  28.5% |
| jvm |  25.4% |  25.4% |
| user code anywhere on the stack |   0.3% | |

## business
rps=30953 p50=1.29ms p99=11.46ms requests=100.0% ok
## /home/youndie/bench-results/smoke/business.cpu.collapsed  (samples: 82938)
| category | self | owner |
|---|---|---|
| jvm | 100.0% | 100.0% |
| user code anywhere on the stack |   0.0% | |

## /home/youndie/bench-results/smoke/business.alloc.collapsed  (samples: 31831560918)
| category | self | owner |
|---|---|---|
| user |   3.7% |   3.7% |
| ktor |  25.7% |  25.7% |
| kotlinx |  11.1% |  11.1% |
| kotlin |   8.0% |   8.0% |
| jdk |  28.6% |  28.6% |
| jvm |  23.0% |  23.0% |
| user code anywhere on the stack |   3.7% | |

## GC and JIT from the service log
lines=3
