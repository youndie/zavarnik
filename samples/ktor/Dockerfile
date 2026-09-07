# The recipe: train the cache with the JVM that will run it, inside the image that will run it.
#
# The JVM accepts a cache only from the same JDK build — it compares the build string and the
# size of $JAVA_HOME/lib/modules — so the `-jdk` and `-jre` packages of the very same Temurin
# build are two different JDKs to it. Training in the build stage therefore ties the runtime
# stage to the full JDK image. Training in the *runtime* stage does not: the distribution carries
# `lib/zavarnik-runner.jar`, which trains and verifies on a bare JRE, without Gradle and without
# curl. Build stage: `-jdk`, `installDist` only. Runtime stage: `-jre`, `train`, `verify`, ship.
#
# One ARG names the Temurin build for both stages, and it names it fully — `25` alone would let
# the two stages drift apart the day one of them is rebuilt.
#
# Build from the repository root, because the sample takes the plugin from two directories up:
#     docker build -f samples/ktor/Dockerfile -t zavarnik-ktor-sample .
ARG TEMURIN=25.0.4_7

FROM eclipse-temurin:${TEMURIN}-jdk AS build
WORKDIR /src
COPY . .
# --no-daemon: a daemon that outlives the RUN step keeps the layer's processes alive for nothing.
RUN ./gradlew -p samples/ktor installDist --no-daemon --console=plain

FROM eclipse-temurin:${TEMURIN}-jre
# COPY preserves the jar mtimes; the training pins them before the run, and the JVM checks them
# against the cache.
COPY --from=build /src/samples/ktor/build/install/ktor-sample /opt/app
# The training run starts the application, hits it, stops it and assembles the cache; `verify`
# then proves the cache is accepted by *this* JVM — under -XX:AOTMode=on, with the jars checked
# against the manifest — before the image is built. One layer: the logs go with it, and a failed
# verification leaves no image.
RUN java -cp /opt/app/lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main train /opt/app \
 && java -cp /opt/app/lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main verify /opt/app \
 && rm -f /opt/app/lib/zavarnik-*.log /opt/app/lib/zavarnik-train.workload.log
EXPOSE 18090
CMD ["/opt/app/bin/ktor-sample"]
