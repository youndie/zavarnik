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
KOTLIN=${KOTLIN_VERSION:-2.4.20}
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

resolve_all() { # fills JARS and MISSING from COORDS
  mkdir -p lib
  JARS=(); MISSING=()
  for c in $COORDS; do
    if p=$(resolve "$c"); then JARS+=("$p"); else MISSING+=("$c"); fi
  done
}
