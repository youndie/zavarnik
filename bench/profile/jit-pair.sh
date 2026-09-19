#!/usr/bin/env bash
# The fifth phase's measurement: four request shapes, two data modes, on a dedicated pair.
#
#   GEN=10.0.0.3 TARGET=10.0.0.2 ./jit-pair.sh
#
# ONE RATE PER ENDPOINT, NOT PER RUN.  CPU per request is not a constant of the code: measured on
# this pair it falls from 210 us at 5k rps to 53 us at saturation, because the cost of a wake-up
# amortises over whatever arrived while the event loop was away. So two variants are comparable only
# at the SAME offered rate. Taking 60 % of each variant's own saturation would put `real` - which
# saturates lower, having a database behind it - at a different point on that curve than `stub`, and
# the difference between the two would then be partly the difference between two rates. Both modes
# are probed, and both are measured at 60 % of the LOWER saturation.
#
# The rest of the order exists to stop specific ways of being wrong:
#   reset   before EVERY run, not between groups: POST inserts, and a stand that keeps rows gets
#           slower every repetition, which lands in the spread and eats the resolution.
#   guard   refuse to start if the port is taken. A service left from an interrupted run answers
#           normally, and then the sweep measures a process it did not start.
#   warm    40 s before anything is recorded. Five seconds of warm-up gave a profile that was 71 %
#           JVM - the C2 threads still compiling.
#   verify  the achieved rate is printed beside the requested one. If a run did not keep up it was
#           above capacity and its number belongs to the harness, not to the code.
set -uo pipefail
cd "$(dirname "$0")/.."
GEN=${GEN:?set GEN to the generator host}
TARGET=${TARGET:?set TARGET to the address the generator dials}
PORT=18100
MODES=${MODES:-"stub real"}
ENDPOINTS=${ENDPOINTS:-"plaintext dbitem dblist dbpost"}
OUT=${OUT:-$HOME/bench-results}
# A non-interactive ssh has no JAVA_HOME, and run.sh refuses without one.
export JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}
# The body lives on the GENERATOR, because that is where oha reads it. Expanding $HOME here would
# give the subject's path, which is how the first attempt got an empty saturation probe.
GEN_BODY=${GEN_BODY:-'~/new-item.json'}

url() {
  case $1 in
    plaintext) echo "http://$TARGET:$PORT/plaintext" ;;
    dbitem)    echo "http://$TARGET:$PORT/db/items/42" ;;
    dblist)    echo "http://$TARGET:$PORT/db/items?limit=50" ;;
    dbpost)    echo "http://$TARGET:$PORT/db/items" ;;
  esac
}
post_args() { [ "$1" = dbpost ] && echo "-m POST -T application/json -D $GEN_BODY" || echo ""; }
gen() { ssh -o BatchMode=yes -o StrictHostKeyChecking=no "$GEN" "~/tools/oha $*"; }

guard() {
  if ss -ltn | grep -q ":$PORT"; then echo "FATAL: $PORT busy"; ss -ltnp | grep ":$PORT"; exit 1; fi
}
start_service() { # $1 mode, $2 log
  JAVA_OPTS="-Dbench.engine=netty -Dbench.data=$1 -Dbench.host=$TARGET -Xms1g -Xmx1g -XX:+UseG1GC" \
    build/install/bench/bin/bench > "$2" 2>&1 &
  SERVICE_PID=$!
  for i in $(seq 150); do curl -s -o /dev/null "http://$TARGET:$PORT/health" 2>/dev/null && break; sleep 0.2; done
  kill -0 "$SERVICE_PID" 2>/dev/null || { echo "FATAL: service died"; tail -5 "$2"; exit 1; }
}
stop_service() { kill -TERM "$SERVICE_PID" 2>/dev/null; wait "$SERVICE_PID" 2>/dev/null; SERVICE_PID=; }

for ep in $ENDPOINTS; do
  echo "=== $ep"
  # --- saturation of every mode, so the shared rate is below all of them
  rate=0
  for mode in $MODES; do
    guard; bash profile/reset-db.sh >/dev/null 2>&1
    start_service "$mode" "/tmp/pair-$mode-$ep-probe.log"
    gen -z 40s -c 128 $(post_args "$ep") --no-tui --output-format json "$(url "$ep")" >/dev/null 2>&1
    gen -z 12s -c 256 $(post_args "$ep") --no-tui --output-format json "$(url "$ep")" > "/tmp/pair-$mode-$ep-sat.json" 2>/dev/null
    stop_service
    sat=$(python3 -c "import json;print(int(json.load(open('/tmp/pair-$mode-$ep-sat.json'))['summary']['requestsPerSec']))" 2>/dev/null || echo 0)
    [ "$sat" -lt 100 ] && { echo "  FATAL: $mode saturation probe returned nothing"; exit 1; }
    echo "  saturation $mode: $sat rps"
    # Written out rather than as `a || b && c`: that parses as `(a || b) && c`, happens to be
    # right here, and is the kind of line that is wrong after the next edit.
    if [ "$rate" -eq 0 ] || [ "$sat" -lt "$rate" ]; then rate=$sat; fi
  done
  rate=$(( rate * 60 / 100 ))
  echo "  shared rate: $rate rps (60 % of the lower saturation)"

  # --- both modes at that one rate
  for mode in $MODES; do
    label="pair-$mode-$ep"
    guard; bash profile/reset-db.sh >/dev/null 2>&1
    JAVA_OPTS="-Dbench.engine=netty -Dbench.data=$mode -Dbench.host=$TARGET" \
    LABEL="$label" ENDPOINTS="$ep" PROFILES=cpu WARMUP=45 MEASURE=60 RATE="$rate" \
    GEN="$GEN" TARGET="$TARGET" RESULTS="$OUT" OHA_BODY="$GEN_BODY" \
      profile/run.sh >/dev/null 2>&1
    if [ -f "$OUT/$label/summary.md" ]; then
      echo "  --- $mode (asked $rate rps)"
      grep -E "^clean:|^cost:" "$OUT/$label/summary.md" | sed 's/^/    /'
      echo "sat_rate=$rate" >> "$OUT/$label/summary.md"
    else
      echo "  FATAL: run.sh produced nothing for $label"; exit 1
    fi
  done
done
echo DONE
