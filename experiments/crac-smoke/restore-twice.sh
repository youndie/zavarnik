#!/usr/bin/env bash
# Restore the same checkpoint image twice and compare what java.util.Random, ThreadLocalRandom and
# UUID.randomUUID() (SecureRandom) print: identical sequences from two restores of one image
# would mean every replica restored from it draws the same numbers.
set -uo pipefail
IMG=${IMG:-azul/zulu-openjdk:25-jdk-crac}
DIR=$(mktemp -d /tmp/crac-twice.XXXX); chmod 777 "$DIR"; mkdir -p "$DIR/cr"; chmod 777 "$DIR/cr"
cp "$(dirname "$0")/Hello.java" "$DIR/"
docker run --rm -v "$DIR:/work" -w /work "$IMG" bash -c 'javac Hello.java && java -XX:+PrintFlagsFinal -version 2>/dev/null | grep -i crac'
name=crac-twice-$$
docker run -d --name "$name" -v "$DIR:/work" -w /work "$IMG" java -XX:CRaCCheckpointTo=/work/cr Hello >/dev/null
sleep 2; docker exec "$name" jcmd Hello JDK.checkpoint >/dev/null 2>&1; sleep 2
echo "== before checkpoint"; docker logs "$name" 2>/dev/null | grep -E "^(start|tick)" ; docker rm -f "$name" >/dev/null 2>&1
for n in 1 2; do
  echo "== restore $n"
  docker run --rm -v "$DIR:/work" -w /work "$IMG" timeout -s TERM 3 java -XX:CRaCRestoreFrom=/work/cr 2>/dev/null | grep -E "^tick"
done
