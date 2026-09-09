#!/usr/bin/env bash
# What the AOT cache costs in resident memory. Starts the installed Ktor sample without the cache
# (-XX:AOTMode=off) and with it, in alternation, N times each; reads VmRSS, RssAnon and RssFile
# from /proc/<pid>/status once the app answers /health and again after 200 requests over the two
# sample routes. The start script execs java, so the script's pid is the JVM's.
#
#     ./run.sh 5            # from the repository root, on a box with the sample installed
set -euo pipefail
APP=${APP:-samples/ktor/build/install/ktor-sample}
RUNS=${1:-5}
OUT=${OUT:-/tmp/zavarnik-memory}
mkdir -p "$OUT"
BODY='{"items":[{"id":1,"name":"x","tags":["a"],"price":2.5}],"note":"n"}'
test -f "$APP/lib/app.aot" || { echo "no $APP/lib/app.aot — train first"; exit 1; }
rss() { awk '/^(VmRSS|RssAnon|RssFile)/{sub(":", "", $1); printf "%s=%s ", $1, $2}' "/proc/$1/status"; }
measure() {
  local variant=$1 opts=$2 pid
  JAVA_OPTS="$opts" "$APP/bin/ktor-sample" >"$OUT/$variant.log" 2>&1 &
  pid=$!
  for _ in $(seq 1 600); do curl -sf -o /dev/null http://127.0.0.1:18090/health && break; sleep 0.05; done
  local ready; ready=$(rss "$pid")
  for _ in $(seq 1 100); do
    curl -s -o /dev/null http://127.0.0.1:18090/api/warm
    curl -s -o /dev/null -X POST -H 'content-type: application/json' -d "$BODY" http://127.0.0.1:18090/api/order
  done
  local after; after=$(rss "$pid")
  kill -TERM "$pid"; wait "$pid" || true
  echo "$variant ready: $ready| after 200 requests: $after"
}
{
  echo "$(java -version 2>&1 | head -1); $(nproc) cores; cache $(stat -c %s "$APP/lib/app.aot") bytes; $RUNS runs per variant, kB"
  for _ in $(seq 1 "$RUNS"); do
    measure cold "-XX:AOTMode=off"
    measure cached ""
  done
} | tee "$OUT/run.log"
