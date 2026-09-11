#!/usr/bin/env bash
# Does a snapshot survive being laid over the image as a layer, at a path it was not taken at?
#
# The whole packaging plan depends on it. A checkpoint is taken inside a container of the image
# with a host directory mounted (`--out /out`), so the snapshot is written to `/out/crac`; the
# image that ships it carries it somewhere else — `/opt/app/zavarnik/crac` — as one more layer over
# the very image it was taken in. If warp refuses that, the snapshot has to be taken where it will
# live, and the Jib path has to mount its own final location.
#
#     ./image-layer.sh        # from the repository root, after -p samples/ktor installDist
set -euo pipefail
BASE_JDK=${BASE_JDK:-azul/zulu-openjdk:25-jre-crac}
APP=${APP:-$PWD/samples/ktor/build/install/ktor-sample}
BASE=zavarnik-crac-base:local
FINAL=zavarnik-crac-final:local
CTX=$(mktemp -d); OUT=$(mktemp -d); chmod 777 "$OUT"
trap 'rm -rf "$CTX"; docker rm -f zavarnik-crac-restore >/dev/null 2>&1 || true' EXIT

echo "== the base image: the distribution on $BASE_JDK"
cp -r "$APP/." "$CTX/"
rm -f "$CTX/lib/app.aot" "$CTX/lib/app.aot.jars"
cat > "$CTX/Dockerfile" <<DOCKER
FROM $BASE_JDK
WORKDIR /opt/app
COPY . ./
EXPOSE 18090
ENTRYPOINT ["/opt/app/bin/ktor-sample"]
DOCKER
docker build -q -t "$BASE" "$CTX" >/dev/null

echo "== the checkpoint, inside a container of it, written to a mounted /out"
# --entrypoint java, because the image has one and without this the runner's command line would
# be handed to the start script as arguments: the server starts, nothing takes a checkpoint, and
# the container runs until something kills it.
docker run --rm --user "$(id -u):$(id -g)" -v "$OUT:/out" --entrypoint java "$BASE" \
  -cp /opt/app/lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main checkpoint /opt/app --out /out \
  2>&1 | grep -E "^zavarnik:|Exception|error" || true
test -n "$(ls -A "$OUT/crac" 2>/dev/null)" || { echo "no snapshot came out"; exit 1; }

echo "== the final image: the base plus the snapshot, at a path it was not taken at"
printf 'FROM %s\nCOPY crac /opt/app/zavarnik/crac\n' "$BASE" > "$OUT/Dockerfile"
docker build -q -t "$FINAL" "$OUT" >/dev/null
docker image ls --format '  {{.Repository}}:{{.Tag}} {{.Size}}' "$BASE" "$FINAL" 2>/dev/null ||
  for i in "$BASE" "$FINAL"; do docker image ls --format "  {{.Repository}}:{{.Tag}} {{.Size}}" "$i"; done

echo "== restoring from inside the image, with nothing mounted"
docker run -d --name zavarnik-crac-restore -p 18090:18090 --entrypoint java "$FINAL" \
  -XX:CRaCRestoreFrom=/opt/app/zavarnik/crac >/dev/null
t0=$(date +%s%3N); ready=no
for _ in $(seq 1 400); do
  if curl -sf -o /dev/null http://127.0.0.1:18090/health; then ready=yes; break; fi
  sleep 0.02
done
if [ "$ready" = yes ]; then
  echo "  ready in $(( $(date +%s%3N) - t0 )) ms; /api/warm answers $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18090/api/warm)"
else
  # Saying a number here would be saying the restore took that long, and it did not: it never
  # answered. The first reading of this experiment reported 13 s of "readiness" for a JVM that
  # had already thrown.
  echo "  NOT READY — the restored process never answered /health"
fi
docker logs zavarnik-crac-restore 2>&1 | grep -iE "warp:|error|Exception" | head -5
