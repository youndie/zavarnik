# The compiler's own cost over time — 2026-09-20, from the RQ7 recordings
# Recording starts after 90 s of load and runs 180 s.

## share of process CPU in compiler threads, per 10 s (jdk.ThreadCPULoad)
== dbitem.jfr
   compiler share per 10 s: 26.3% 0.7% 5.7% 0.6% 0.0% 0.5% 0.2% 0.3% 0.0% 0.0%   - 0.0% 0.0% 0.5% 1.0% 0.0% 0.0% 0.0%
   first three buckets 12.3%, last three 0.0%  -> declining: warm-up tail
   compiler CPU-seconds in the window: 233.1 of 9012.7 total (2.6 %)
== dblist.jfr
   compiler share per 10 s: 27.6%   - 0.3% 1.2% 0.8% 0.0% 0.3% 0.0% 0.1% 0.0% 0.2% 0.1% 0.7% 0.0% 0.0% 0.0% 0.0% 0.0% 100.0%
   first three buckets 11.6%, last three 3.6%  -> declining: warm-up tail
   compiler CPU-seconds in the window: 257.1 of 9157.3 total (2.8 %)
== dbpost.jfr
   compiler share per 10 s: 26.3% 0.4% 1.7% 0.0% 0.1% 0.0% 0.0% 0.1% 1.7% 0.2% 0.1% 13.9% 1.7% 1.0% 1.2% 0.4% 0.1%
   first three buckets 11.5%, last three 0.6%  -> declining: warm-up tail
   compiler CPU-seconds in the window: 248.0 of 7047.9 total (3.5 %)

## compiler CPU-seconds (jdk.CompilerStatistics, cumulative totalTimeSpent)
== dbitem
   at the recording's start, after 90 s of load: 62 compiler CPU-seconds over 7338 compilations
   over the 180 s window: +12 CPU-seconds, +528 compilations
   compiler CPU-seconds per 10 s: 8.0 1.0 1.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 0.0
   excluding the first and last buckets: 4.0 CPU-s over 170 s = 0.024 cores
== dblist
   at the recording's start, after 90 s of load: 69 compiler CPU-seconds over 7336 compilations
   over the 180 s window: +14 CPU-seconds, +435 compilations
   compiler CPU-seconds per 10 s: 11.0 0.0 1.0 1.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 0.0
   excluding the first and last buckets: 3.0 CPU-s over 160 s = 0.019 cores
== dbpost
   at the recording's start, after 90 s of load: 94 compiler CPU-seconds over 7921 compilations
   over the 180 s window: +14 CPU-seconds, +487 compilations
   compiler CPU-seconds per 10 s: 10.0 1.0 0.0 0.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0
   excluding the first and last buckets: 4.0 CPU-s over 170 s = 0.024 cores
