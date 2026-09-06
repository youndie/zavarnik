#!/usr/bin/env bash
# Proves the image's cache is used by the image's JVM — not by the JVM that built it.
#
# Builds the image, runs it with the cache made mandatory (-XX:AOTMode=on: a rejected cache is a
# JVM that does not start) and class-load logging, waits for /health from outside, stops the
# container with SIGTERM, and counts in its output how many classes came from the cache.
# Exit code 1 when the container did not become ready or no `sample.` class came from the cache.
#
# Usage (from the repository root):  samples/ktor/docker-check.sh [image-tag]
set -u
cd "$(dirname "$0")/../.."
IMAGE=${1:-zavarnik-ktor-sample}
PORT=${PORT:-18090}
BUILD_LOG=$(mktemp)
if ! docker build -f samples/ktor/Dockerfile -t "$IMAGE" . > "$BUILD_LOG" 2>&1; then
  echo "image build failed; the last lines of the build:"; tail -40 "$BUILD_LOG"; rm -f "$BUILD_LOG"; exit 1
fi
rm -f "$BUILD_LOG"
# No --rm: a JVM that refuses the cache exits at once, and --rm would take its output with it.
CID=$(docker run -d -p "$PORT:18090" -e JAVA_OPTS="-XX:AOTMode=on -Xlog:class+load=info" "$IMAGE")
trap 'docker rm -f "$CID" >/dev/null 2>&1' EXIT
ready=1
for i in $(seq 200); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/health")" = "200" ]; then ready=0; break; fi
  sleep 0.1
done
docker stop "$CID" >/dev/null 2>&1
LOG=$(docker logs "$CID" 2>&1 || true)
echo "image JVM: $(docker run --rm "$IMAGE" java -version 2>&1 | head -1)"
shared=$(printf '%s\n' "$LOG" | grep -c 'source: shared objects file')
app=$(printf '%s\n' "$LOG" | grep -E '\] sample\.' | grep -c 'source: shared objects file')
app_total=$(printf '%s\n' "$LOG" | grep -E '\] sample\.' | grep -c 'source:')
echo "ready=$([ $ready = 0 ] && echo yes || echo no); classes from the cache: $shared overall, $app of $app_total in sample.*"
printf '%s\n' "$LOG" | grep -E '\[(error|warning)\]\[aot' | head -5
if [ $ready != 0 ] || [ "$app" -eq 0 ] || [ "$app" != "$app_total" ]; then
  echo "--- last lines of the container's output:"; printf '%s\n' "$LOG" | grep -v 'source: ' | tail -15; exit 1
fi
