#!/usr/bin/env bash
# The pinned stack of the request path, and how to get the jars for it.
#
# Sourced, not executed. Sets COORDS, defines resolve(), and fills JARS/MISSING:
#
#   . "$(dirname "$0")/../stack.sh"
#
# WHY THIS IS NOT COPIED INTO EACH EXPERIMENT. Two scans read the same classpath - method sizes and
# the codegen census - and a second copy of the coordinate list is a version bump that lands in one
# scan and not the other, with both tables still printing a version number and disagreeing about
# which one ran. Editing the list HERE is how every scan follows a bump at once.
CACHE=${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}
CENTRAL=${MAVEN_CENTRAL:-https://repo1.maven.org/maven2}

KTOR=${KTOR_VERSION:-3.5.2}
KOTLIN=${KOTLIN_VERSION:-2.4.10}
KOTLINX_SER=${KOTLINX_SER_VERSION:-1.11.0}
KOTLINX_COR=${KOTLINX_COR_VERSION:-1.11.0}
EXPOSED=${EXPOSED_VERSION:-1.4.0}
NETTY=${NETTY_VERSION:-4.2.16.Final}
HIKARI=${HIKARI_VERSION:-7.0.2}
PGJDBC=${PGJDBC_VERSION:-42.7.13}

COORDS="
io.ktor:ktor-server-core-jvm:$KTOR
io.ktor:ktor-server-netty-jvm:$KTOR
io.ktor:ktor-server-content-negotiation-jvm:$KTOR
io.ktor:ktor-serialization-jvm:$KTOR
io.ktor:ktor-serialization-kotlinx-jvm:$KTOR
io.ktor:ktor-serialization-kotlinx-json-jvm:$KTOR
io.ktor:ktor-http-jvm:$KTOR
io.ktor:ktor-utils-jvm:$KTOR
io.ktor:ktor-io-jvm:$KTOR
io.ktor:ktor-events-jvm:$KTOR
org.jetbrains.kotlin:kotlin-stdlib:$KOTLIN
org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$KOTLINX_COR
org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$KOTLINX_SER
org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$KOTLINX_SER
org.jetbrains.exposed:exposed-core:$EXPOSED
org.jetbrains.exposed:exposed-jdbc:$EXPOSED
com.zaxxer:HikariCP:$HIKARI
org.postgresql:postgresql:$PGJDBC
io.netty:netty-common:$NETTY
io.netty:netty-buffer:$NETTY
io.netty:netty-transport:$NETTY
io.netty:netty-codec:$NETTY
io.netty:netty-codec-base:$NETTY
io.netty:netty-codec-http:$NETTY
io.netty:netty-handler:$NETTY
io.netty:netty-resolver:$NETTY
"

resolve() { # group:artifact:version -> path on stdout
  local g=${1%%:*} rest=${1#*:} a v f
  a=${rest%%:*}; v=${rest#*:}
  f=$(find "$CACHE/$g/$a/$v" -name "$a-$v.jar" ! -name "*sources*" 2>/dev/null | head -1)
  if [ -z "$f" ]; then
    f="lib/$a-$v.jar"
    if [ ! -s "$f" ]; then
      # `${g//./\/}` looks right and yields io\/ktor - a literal backslash, and a 404 that
      # reads like a missing artifact rather than a broken URL.
      local path; path=$(printf %s "$g" | tr . /)
      curl -fsSL -o "$f" "$CENTRAL/$path/$a/$v/$a-$v.jar" || { rm -f "$f"; echo "could not fetch $1" >&2; return 1; }
    fi
  fi
  echo "$f"
}

# The pins above are a hand-written list beside a set that moves, which is the failure this guard
# exists for: the scans read kotlin-stdlib 2.4.20 for a stand that ships 2.4.10, and stdlib owns the
# largest column in the codegen census. A version that is merely written down names nothing.
#
# The manifest is produced FROM the stand's own installDist and committed, so the check has a
# subject that travels with the repository rather than one that only exists on the bench host.
MANIFEST=${MANIFEST:-$(dirname "${BASH_SOURCE[0]}")/../bench/profile/results/dist-manifest.txt}
verify_against_dist() {
  [ -r "$MANIFEST" ] || { echo "stack.sh: no dist manifest at $MANIFEST - cannot verify pins" >&2; return 0; }
  local bad=0 c g rest a v
  for c in $COORDS; do
    g=${c%%:*}; rest=${c#*:}; a=${rest%%:*}; v=${rest#*:}
    # Only artifacts the stand actually ships are checkable; the scan deliberately reads a few that
    # it does not (and those are listed, not silently skipped).
    if grep -q "^$a-" "$MANIFEST"; then
      grep -q "^$a-$v\.jar$" "$MANIFEST" || {
        echo "stack.sh: PIN MISMATCH $a pinned $v, stand ships $(grep "^$a-" "$MANIFEST" | head -1)" >&2
        bad=1
      }
    else
      echo "stack.sh: note - $a is scanned but not shipped by the stand" >&2
    fi
  done
  [ "$bad" = 0 ] || { echo "stack.sh: refusing to scan a stack the stand does not run" >&2; return 1; }
}

resolve_all() { # fills JARS and MISSING from COORDS
  verify_against_dist || exit 1
  mkdir -p lib
  JARS=(); MISSING=()
  for c in $COORDS; do
    if p=$(resolve "$c"); then JARS+=("$p"); else MISSING+=("$c"); fi
  done
}
