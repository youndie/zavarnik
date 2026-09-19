#!/usr/bin/env bash
# What a real request actually allocates, and who allocates it.
#
#   GEN=10.0.0.3 TARGET=10.0.0.2 ./alloc-census.sh
#
# Three verdicts in the fifth phase are closed by arithmetic rather than measurement - RQ3's
# continuation share, RQ5's two off-list patterns, and the boxing half of RQ3. The arithmetic runs
# "the count a request would need is orders of magnitude above the count it makes", which is sound
# and is not a number. This produces the number.
#
# It deliberately reuses the pair runs' configuration - real mode, 64 connections, no rate cap - so
# that the allocation profile pairs with the CPU profile already committed for the same endpoints,
# rather than describing a different operating point.
#
# Same guards as jit-pair.sh, for the same reasons: refuse a taken port (a service left from an
# interrupted run answers normally and the sweep then measures a process it did not start), reset
# the database before every run (POST inserts, and a stand that keeps rows drifts monotonically),
# and warm for 40 s (five seconds gave a profile that was 71 % JVM - C2 still compiling).
set -uo pipefail
cd "$(dirname "$0")/.."
. "$(dirname "$0")/warmup.env"
GEN=${GEN:?set GEN}; TARGET=${TARGET:?set TARGET}
PORT=18100; CONNS=${CONNS:-64}; WARM=${WARM:-$BENCH_WARMUP}; MEASURE=${MEASURE:-60}
ASPROF=${ASPROF:-$HOME/tools/async-profiler-4.5-linux-x64/bin/asprof}
OUT=${OUT:-$HOME/bench-results}/alloc-census; mkdir -p "$OUT"
export JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}
GEN_BODY=${GEN_BODY:-'~/new-item.json'}
url() { case $1 in
  dbitem) echo "http://$TARGET:$PORT/db/items/42" ;;
  dblist) echo "http://$TARGET:$PORT/db/items?limit=50" ;;
  dbpost) echo "http://$TARGET:$PORT/db/items" ;; esac; }
post_args() { [ "$1" = dbpost ] && echo "-m POST -T application/json -D $GEN_BODY" || echo ""; }
gen() { ssh -o BatchMode=yes -o StrictHostKeyChecking=no "$GEN" "~/tools/oha $*"; }

# NOT `for ep in ${ENDPOINTS:-"a b c"}`: the quotes survive into the word and the loop
# then runs once on the literal "dbitem dblist dbpost", whose url() is empty - so oha
# dials nothing, the profile is 181 bytes, and the script still prints DONE.
ENDPOINTS=${ENDPOINTS:-"dbitem dblist dbpost"}
for ep in $ENDPOINTS; do
  if ss -ltn | grep -q ":$PORT"; then echo "FATAL: $PORT busy"; exit 1; fi
  bash profile/reset-db.sh >/dev/null 2>&1
  JAVA_OPTS="-Dbench.engine=netty -Dbench.data=real -Dbench.host=$TARGET -Xms1g -Xmx1g -XX:+UseG1GC" \
    build/install/bench/bin/bench > "$OUT/$ep.service.log" 2>&1 &
  pid=$!
  for i in $(seq 150); do curl -s -o /dev/null "http://$TARGET:$PORT/health" 2>/dev/null && break; sleep 0.2; done
  kill -0 "$pid" 2>/dev/null || { echo "FATAL: $ep service died"; tail -5 "$OUT/$ep.service.log"; exit 1; }
  gen -z "${WARM}s" -c "$CONNS" --no-tui $(post_args "$ep") "$(url "$ep")" >/dev/null 2>&1
  "$ASPROF" -d "$MEASURE" -e alloc --total -o collapsed -f "$OUT/$ep.alloc.collapsed" $pid >/dev/null 2>&1 &
  prof=$!
  gen -z "${MEASURE}s" -c "$CONNS" --no-tui --output-format json $(post_args "$ep") "$(url "$ep")" > "$OUT/$ep.oha.json" 2>/dev/null
  wait $prof
  kill -TERM "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  # A run that collected nothing must not look like a run that found nothing.
  sz=$(wc -c < "$OUT/$ep.alloc.collapsed")
  [ "$sz" -lt 10000 ] && { echo "FATAL: $ep profile is $sz bytes - nothing was sampled"; exit 1; }
  python3 - "$OUT/$ep.oha.json" "$ep" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); s=d["summary"]
print("%-8s rps=%.0f p50=%.2fms requests=%d" % (sys.argv[2], s["requestsPerSec"],
      d["latencyPercentiles"]["p50"]*1000, sum(int(v) for v in d["statusCodeDistribution"].values())))
PY
done
echo DONE
