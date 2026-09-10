#!/usr/bin/env bash
# The Ktor sample (samples/ktor, installed) under a CRaC JDK: what a checkpoint says while the CIO
# server's socket is open, and — if an image comes out — time from `docker run` to the first 200 on
# /health for a plain start against a restore, N each, plus one functional request after restore.
#
#     ./run.sh 5      # from the repository root, on a box where samples/ktor is installed
set -uo pipefail
IMG=${IMG:-azul/zulu-openjdk:25-jdk-crac}
APP=${APP:-$PWD/samples/ktor/build/install/ktor-sample}
RUNS=${1:-5}
POLICY=${POLICY:-}            # path to a jdk.crac.resource-policies file; empty = none
SKIP_PLAIN=${SKIP_PLAIN:-}    # non-empty: skip the plain-start timing
POLICY_MOUNT=(); POLICY_OPT=""
if [ -n "$POLICY" ]; then POLICY_MOUNT=(-v "$(realpath "$POLICY"):/policies.yaml:ro"); POLICY_OPT=" -Djdk.crac.resource-policies=/policies.yaml"; fi
DIR=$(mktemp -d /tmp/crac-ktor.XXXX); chmod 777 "$DIR"; mkdir -p "$DIR/cr"; chmod 777 "$DIR/cr"
PORT=18090
BODY='{"items":[{"id":1,"name":"x","tags":["a"],"price":2.5}],"note":"n"}'
now_ms() { date +%s%3N; }
wait_ready() { # prints ms until the first 200
  local t0; t0=$(now_ms)
  for _ in $(seq 1 600); do
    if curl -sf -o /dev/null "http://127.0.0.1:$PORT/health"; then echo $(( $(now_ms) - t0 )); return 0; fi
    sleep 0.02
  done
  echo timeout; return 1
}
start_plain() { docker run -d --name "$1" -p $PORT:$PORT -v "$APP:/app:ro" -e JAVA_OPTS="-XX:AOTMode=off" "$IMG" /app/bin/ktor-sample >/dev/null; }
echo "image: $(docker run --rm "$IMG" java -version 2>&1 | sed -n 2p); app: $APP"
if [ -z "$SKIP_PLAIN" ]; then
  echo "== plain start, $RUNS runs: ms to first 200"
  for i in $(seq 1 "$RUNS"); do
    start_plain crac-ktor-plain; r=$(wait_ready); echo "plain $i: $r"; docker rm -f crac-ktor-plain >/dev/null 2>&1
  done
fi
echo "== checkpoint attempt with the server up${POLICY:+, policies: $POLICY}"
docker run -d --name crac-ktor-cp -p $PORT:$PORT -v "$APP:/app:ro" -v "$DIR/cr:/cr" "${POLICY_MOUNT[@]}" -e JAVA_OPTS="-XX:AOTMode=off -XX:CRaCCheckpointTo=/cr$POLICY_OPT" "$IMG" /app/bin/ktor-sample >/dev/null
wait_ready >/dev/null
for _ in $(seq 1 100); do
  curl -s -o /dev/null "http://127.0.0.1:$PORT/api/warm"
  curl -s -o /dev/null -X POST -H 'content-type: application/json' -d "$BODY" "http://127.0.0.1:$PORT/api/order"
done
docker exec crac-ktor-cp jcmd sample.MainKt JDK.checkpoint 2>&1 | grep -v "^\s*at " | tail -12
sleep 3
echo "-- container log tail:"; docker logs crac-ktor-cp 2>&1 | tail -12
docker rm -f crac-ktor-cp >/dev/null 2>&1
echo "-- image: $(ls "$DIR/cr" | wc -l) files, $(du -sh "$DIR/cr" | cut -f1)"
if [ -n "$(ls -A "$DIR/cr")" ]; then
  echo "== restore, $RUNS runs: ms to first 200"
  for i in $(seq 1 "$RUNS"); do
    docker run -d --name crac-ktor-rs -p $PORT:$PORT -v "$APP:/app:ro" -v "$DIR/cr:/cr" "${POLICY_MOUNT[@]}" "$IMG" java -XX:CRaCRestoreFrom=/cr >/dev/null
    r=$(wait_ready); echo "restore $i: $r"
    if [ "$i" = 1 ]; then
      echo "-- functional after restore: $(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'content-type: application/json' -d "$BODY" "http://127.0.0.1:$PORT/api/order") on /api/order, $(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/api/warm") on /api/warm"
      echo "-- log tail:"; docker logs crac-ktor-rs 2>&1 | tail -5
    fi
    docker rm -f crac-ktor-rs >/dev/null 2>&1
  done
fi
echo "scratch: $DIR"
