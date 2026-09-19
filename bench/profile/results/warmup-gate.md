# Warm-up: when does compilation stop? — 2026-09-20, from the RQ7 recordings
# jdk.Compilation enabled at threshold 0ms; recording starts after 90 s of load, runs 180 s

== dbitem  597 compilations, 260 at level 4, 16 of those in app/Ktor/Exposed/serialiser code
   all,  per 10 s: 381 39 39 12 3 3 8 8 1 1 2 2 7 10 3 13 4 1 60
   hot,  per 10 s: 15 0 1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   longest quiet stretch for hot level-4 code: 160 s
== dblist  564 compilations, 264 at level 4, 25 of those in app/Ktor/Exposed/serialiser code
   all,  per 10 s: 398 30 26 5 1 10 4 1 2 5 14 0 9 1 1 0 2 0 55
   hot,  per 10 s: 25 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   longest quiet stretch for hot level-4 code: 180 s
== dbpost  570 compilations, 259 at level 4, 33 of those in app/Ktor/Exposed/serialiser code
   all,  per 10 s: 374 30 27 9 3 3 4 1 6 2 19 14 19 7 13 3 1 0 35
   hot,  per 10 s: 24 0 0 0 0 0 0 0 0 0 0 1 2 2 1 0 0 0 3
   longest quiet stretch for hot level-4 code: 100 s

# The first bucket of every recording is JFR.start forcing recompilation (see research 1.21),
# and the last is JFR.stop. The gate must not be evaluated inside either.
