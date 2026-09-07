# jit-warmup

Does the AOT cache warm the JIT, or only the start? Two measurements on `bench/` (the Ktor CIO
benchmark service), cold (`-XX:AOTMode=off`) against cached, three repetitions each:

- `citime.sh` — twenty seconds of load on `/business` after readiness, then `-XX:+CITime` at
  exit: how many methods C1 and C2 compiled and how long they took. Counts do not depend on the
  CPU frequency; times do.
- `warmup-curve.sh` — rps in ten consecutive two-second windows after readiness. On this box the
  curve is dominated by frequency noise (research-architecture §1.9); it is kept because the
  first window says something the counts cannot.

Run on the box that hosts the stand, from anywhere: `bash citime.sh`, `bash warmup-curve.sh`.
The logs in `results/` are the record the research cites.
