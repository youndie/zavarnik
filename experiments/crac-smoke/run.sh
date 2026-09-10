#!/usr/bin/env bash
# Can this box checkpoint and restore a JVM at all, and with which Docker privileges?
# A one-class program prints a tick a second with nanoTime, wall time, java.util.Random,
# ThreadLocalRandom and UUID; it is checkpointed with jcmd and restored in a fresh container.
# Tried in order: no extra privileges, CAP_CHECKPOINT_RESTORE+SYS_PTRACE, --privileged.
#
#     ./run.sh            # on a box with Docker; IMG overrides the image
set -uo pipefail
IMG=${IMG:-azul/zulu-openjdk:25-jdk-crac}
DIR=$(mktemp -d /tmp/crac-smoke.XXXX); chmod 777 "$DIR"
cp "$(dirname "$0")/Hello.java" "$DIR/"
echo "host: $(uname -r); ptrace_scope=$(cat /proc/sys/kernel/yama/ptrace_scope 2>/dev/null); docker $(docker version --format '{{.Server.Version}}')"
docker pull -q "$IMG" >/dev/null
docker run --rm -v "$DIR:/work" -w /work "$IMG" bash -c 'java -version 2>&1 | head -2; javac Hello.java; ls lib/criu 2>/dev/null; ls -l $JAVA_HOME/lib/criu; $JAVA_HOME/lib/criu --version 2>&1 | head -1'
attempt() {
  local label=$1; shift
  echo "== $label"
  rm -rf "$DIR/cr"; mkdir -p "$DIR/cr"; chmod 777 "$DIR/cr"
  local name=crac-smoke-$$
  docker rm -f "$name" >/dev/null 2>&1
  docker run -d --name "$name" "$@" -v "$DIR:/work" -w /work "$IMG" java -XX:CRaCCheckpointTo=/work/cr Hello >/dev/null
  sleep 3
  docker exec "$name" jcmd Hello JDK.checkpoint 2>&1 | tail -3
  sleep 3
  echo "-- checkpoint container:"; docker logs "$name" 2>&1 | tail -6; docker rm -f "$name" >/dev/null 2>&1
  echo "-- image files: $(ls "$DIR/cr" 2>/dev/null | wc -l), $(du -sh "$DIR/cr" 2>/dev/null | cut -f1)"
  [ -s "$DIR/cr/core-1.img" ] || [ -n "$(ls -A "$DIR/cr" 2>/dev/null)" ] || { echo "-- no image, next"; return 1; }
  echo "-- restore:"; sleep 2
  docker run --rm "$@" -v "$DIR:/work" -w /work "$IMG" timeout -s TERM 4 java -XX:CRaCRestoreFrom=/work/cr 2>&1 | tail -6
}
attempt "no extra privileges" || attempt "cap-add CHECKPOINT_RESTORE SYS_PTRACE" --cap-add CHECKPOINT_RESTORE --cap-add SYS_PTRACE || attempt "privileged" --privileged
echo "scratch: $DIR"
