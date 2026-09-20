# B-54: two predictions from the RQ7 review — 2026-09-20
# dbpost, 90 s warm-up then a 400 s window, +jdk.JavaExceptionThrow#throttle=off

deoptimisations: 86 in 400 s = 12.9/min at 3107 rps
tryPark@40 events in this window: 0   (it fired 4 times in the earlier 180 s window)

JavaExceptionThrow, unthrottled: 1 281 796
ExceptionStatistics (created):   1 281 737
requests:                        1 242 812
  thrown per request  1.031
  created per request 1.031
