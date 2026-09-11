#!/usr/bin/env bash
# Engine phase (docs/research/research-engines.md): the same service, the same jars and the same
# process on CIO, Netty and Jetty — one `-Dbench.engine` apart — under the protocol of run.sh.
# The categories are the phase's own: the engine's packages are named, and kotlinx is split into
# coroutines and serialization, because the finding under test lives in the first of the two.
#
# Usage:  JAVA_HOME=… [ENGINES="cio netty jetty"] [CONNS=64] [SUFFIX=] [ENDPOINTS=…] [PROFILES=…] ./engines.sh
#   Results land in $RESULTS/engine-<engine><suffix>/ (default $HOME/bench-results).
#   The connection sweep is the same script:  CONNS=16 SUFFIX=-c16 ENDPOINTS=business PROFILES=cpu ./engines.sh
set -u
cd "$(dirname "$0")"
ENGINES=${ENGINES:-"cio netty jetty"}
# First match wins in attribute.py, so kotlinx.coroutines has to be named before the default
# kotlinx catch-all; jetty carries the servlet API Ktor's engine talks through.
CATS=${CATEGORIES:-'coroutines=kotlinx.coroutines.;serialization=kotlinx.serialization.;netty=io.netty.;jetty=org.eclipse.jetty.,jakarta.servlet.'}
for engine in $ENGINES; do
  echo "### engine=$engine conns=${CONNS:-64} $(date -u +%FT%TZ)"
  JAVA_OPTS="${JAVA_OPTS:-} -Dbench.engine=$engine" \
  CATEGORIES="$CATS" LABEL="engine-$engine${SUFFIX:-}" SELF_FRAMES=${SELF_FRAMES:-15} \
    ./run.sh
done
