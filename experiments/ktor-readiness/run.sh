#!/usr/bin/env bash
# B-01: time-to-first-200 on /health for the Ktor stand, with and without the AOT cache, on
# SerialGC and G1. Twenty runs per variant, measured from the outside: the clock starts before
# the start script is launched and stops on the first HTTP 200, which is what an orchestrator
# and a user see. Asserts nothing; the sorted rows and the class-source count are the result.
#
# Usage:  JAVA_HOME=/path/to/jdk-25 ./run.sh [runs]
# Needs curl, network for the first Gradle resolution, and a free port (STAND_PORT, default 8080).
set -u
cd "$(dirname "$0")"
RUNS=${1:-20}
PORT=${STAND_PORT:-8080}
# Scratch logs live outside the tree: a one-way replica (mutagen) deletes anything the run writes
# into the synced directory, mid-run, and the symptom is "No such file" for a file just written.
TMP=$(mktemp -d)
JAVA=${JAVA_HOME:?set JAVA_HOME}/bin/java
echo "# $(date -u +%Y-%m-%dT%H:%M:%SZ) $(uname -sm) $(hostname) runs=$RUNS port=$PORT"
"$JAVA" -version 2>&1 | sed 's/^/# /'
grep -m1 "model name" /proc/cpuinfo 2>/dev/null | sed 's/^/# /' || sysctl -n machdep.cpu.brand_string 2>/dev/null | sed 's/^/# cpu: /'

(cd app && ./gradlew -q installDist 2>&1 | tail -5)
APP=$(cd app/build/install/stand && pwd); S=$APP/bin/stand
rm -f "$APP/lib/app.aot"
# mtime normalisation as the plugin will do it (research D3): the constant Jib uses by default.
# TZ pinned: `touch -t` reads local time, and the constant is 1970-01-01T00:00:01Z (Jib's default).
TZ=UTC find "$APP/lib" -name '*.jar' -exec touch -t 197001010000.01 {} +
echo "# jars=$(ls "$APP"/lib/*.jar | wc -l | tr -d ' ')"

now_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }
wait_ready() { # $1 = deadline ms; prints 0 on 200
  while :; do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health")" = "200" ]; then return 0; fi
    [ "$(now_ms)" -gt "$1" ] && return 1
  done
}
stop_app() { kill -TERM "$1" 2>/dev/null; wait "$1" 2>/dev/null; }
workload() {
  for i in $(seq 20); do
    curl -s -o /dev/null "http://127.0.0.1:$PORT/api/warm"
    curl -s -o /dev/null -X POST -H 'Content-Type: application/json' \
      -d '{"items":[{"id":1,"name":"x","tags":["a"],"price":2.5},{"id":2,"name":"y","tags":[],"price":1.0}],"note":"n"}' \
      "http://127.0.0.1:$PORT/api/order"
  done
}

echo; echo "=== T0 training run through the real start script (SIGTERM after the workload)"
t0=$(now_ms); JAVA_OPTS="-XX:AOTCacheOutput=$APP/lib/app.aot -Dstand.port=$PORT" "$S" > "$TMP/train.log" 2>&1 & PID=$!
wait_ready $((t0 + 60000)) || { echo "stand did not become ready"; stop_app $PID; exit 1; }
echo "ready after $(( $(now_ms) - t0 )) ms"; workload; stop_app $PID
grep -E "creation is complete|Only one|Error" "$TMP/train.log" | sed "s#$APP#<app>#"
echo "cache bytes=$(wc -c < "$APP/lib/app.aot" 2>/dev/null || echo none)"

echo; echo "=== T1 class sources with the cache (one run, -Xlog:class+load)"
JAVA_OPTS="-Xlog:class+load=info -Dstand.port=$PORT" "$S" > "$TMP/classes.log" 2>&1 & PID=$!
wait_ready $(( $(now_ms) + 60000 )); workload; stop_app $PID
total=$(grep -c "source:" "$TMP/classes.log"); shared=$(grep -c "source: shared objects file" "$TMP/classes.log")
app_total=$(grep -E "^\[.*\] (stand\.|io\.ktor\.|kotlinx\.|kotlin\.)" "$TMP/classes.log" | grep -c "source:")
app_shared=$(grep -E "^\[.*\] (stand\.|io\.ktor\.|kotlinx\.|kotlin\.)" "$TMP/classes.log" | grep -c "source: shared objects file")
echo "all classes: $shared of $total from the cache; app+ktor+kotlin: $app_shared of $app_total"
echo "not from the cache, app+ktor+kotlin, top packages:"; grep -E "^\[.*\] (stand\.|io\.ktor\.|kotlinx\.|kotlin\.)" "$TMP/classes.log" | grep -v "shared objects file" | sed -E 's/^\[[^]]*\]\[[^]]*\]\[[^]]*\] //; s/ source:.*//' | awk -F. '{print $1"."$2"."$3}' | sort | uniq -c | sort -rn | head -8

measure() { # $1 label, rest = JAVA_OPTS
  local label=$1; shift; local rows=()
  for i in $(seq "$RUNS"); do
    local t0; t0=$(now_ms)
    JAVA_OPTS="$* -Dstand.port=$PORT" "$S" > /dev/null 2>&1 & local pid=$!
    if wait_ready $((t0 + 60000)); then rows+=($(( $(now_ms) - t0 ))); else rows+=(timeout); fi
    stop_app $pid
  done
  echo "$label: $(printf '%s\n' "${rows[@]}" | sort -n | tr '\n' ' ')"
}
echo; echo "=== M1 ms to first 200 on /health, $RUNS runs each, sorted"
measure "no-cache  SerialGC" -XX:AOTMode=off -XX:+UseSerialGC
measure "cache     SerialGC" -XX:+UseSerialGC
measure "no-cache  G1      " -XX:AOTMode=off -XX:+UseG1GC
measure "cache     G1      " -XX:+UseG1GC
echo; echo "=== M2 sanity: the cache variant really used the cache (-XX:AOTMode=on exit code)"
JAVA_OPTS="-XX:AOTMode=on -XX:+UseSerialGC -Dstand.port=$PORT" "$S" > "$TMP/strict.log" 2>&1 & PID=$!
if wait_ready $(( $(now_ms) + 60000 )); then echo "ready under -XX:AOTMode=on: yes"; else echo "ready under -XX:AOTMode=on: NO"; grep -E "aot|AOT" "$TMP/strict.log" | head -5; fi; stop_app $PID
rm -rf "$TMP"
