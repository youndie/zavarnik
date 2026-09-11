#!/usr/bin/env bash
# Does a CRaC snapshot travel between two different CPUs, and does -XX:CPUFeatures make it travel?
#
# Two machines, one pinned image digest so the JDK is byte-identical on both (a snapshot verifies
# the build id of every file it had mapped), and a one-class program that prints a line before the
# checkpoint and another after the restore.
#
#     ./cross.sh checkpoint <dir> [flag]     # on the machine that takes it; flag e.g. -XX:CPUFeatures=generic
#     ./cross.sh restore    <dir>            # on the machine that restores it
#
# `<dir>` holds Probe.class and the snapshot under `cr/`; move the whole directory between the
# machines. The checkpoint runs as root inside the container and hands the files back to the host
# user afterwards, because `jcmd` cannot attach to a JVM started under an unmapped uid — the
# checkpoint then produces nothing and the empty directory is easy to mistake for a failed restore.
set -uo pipefail
IMG=${IMG:-azul/zulu-openjdk@sha256:89e61fe907730bb0c51187192c48e56ec5a008f2514e2e6d6a54b435349012f7}
MODE=${1:?usage: cross.sh checkpoint|restore <dir> [flag]}
DIR=$(cd "${2:?usage: cross.sh checkpoint|restore <dir> [flag]}" && pwd)
FLAG=${3:-}

echo "== $(hostname) — $(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2- | xargs)"
echo "   features: $(grep -o 'avx512f\|avx2\|avx_vnni' /proc/cpuinfo | sort -u | tr '\n' ' ')"

case $MODE in
  checkpoint)
    rm -rf "$DIR/cr" "$DIR/go"; mkdir -p "$DIR/cr"; chmod -R 777 "$DIR"
    docker rm -f crac-cpu >/dev/null 2>&1
    echo "   checkpoint flag: ${FLAG:-none}"
    # shellcheck disable=SC2086
    docker run -d --name crac-cpu -v "$DIR:/work" -w /work "$IMG" java $FLAG -XX:CRaCCheckpointTo=/work/cr Probe >/dev/null
    sleep 3
    docker exec crac-cpu jcmd Probe JDK.checkpoint 2>&1 | tail -2
    sleep 4
    docker logs crac-cpu 2>&1 | grep -E 'warp:|before checkpoint|error' | tail -3
    docker rm -f crac-cpu >/dev/null 2>&1
    docker run --rm -v "$DIR:/work" "$IMG" chown -R "$(id -u):$(id -g)" /work/cr >/dev/null 2>&1
    echo "   snapshot: $(du -sh "$DIR/cr" | cut -f1), $(ls "$DIR/cr" | tr '\n' ' ')"
    ;;
  restore)
    # An EMPTY snapshot directory also answers "incompatible or missing CPU features", so the
    # check has to come first or a transfer that dropped the image reads as a CPU verdict.
    [ -s "$DIR/cr/core.img" ] || { echo "   NO SNAPSHOT in $DIR/cr — nothing to conclude"; exit 2; }
    touch "$DIR/go"; chmod -R 777 "$DIR"
    docker run --rm -v "$DIR:/work" -w /work "$IMG" java -XX:CRaCRestoreFrom=/work/cr 2>&1 | tail -6
    code=${PIPESTATUS[0]}
    case $code in
      0)   echo "   RESTORED (exit 0)" ;;
      1)   echo "   refused (exit 1) — a message, which is the good failure" ;;
      139) echo "   CRASHED (exit 139, SIGSEGV) — no message at all" ;;
      *)   echo "   exit $code" ;;
    esac
    ;;
  *) echo "usage: cross.sh checkpoint|restore <dir> [flag]"; exit 2 ;;
esac
