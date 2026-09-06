# The recipe: train the cache with the JDK that will run it.
#
# The JVM accepts a cache only from the same JDK build (it compares the build string and the
# size of lib/modules, not the version), so the training run cannot happen on the developer's
# machine or on a CI runner with a different JDK: it happens here, in the build stage, on the
# very image the runtime stage ships. One ARG names that image for both stages, and it names it
# fully — `25` alone would let the two stages drift apart the day one of them is rebuilt.
#
# Build from the repository root, because the sample takes the plugin from two directories up:
#     docker build -f samples/ktor/Dockerfile -t zavarnik-ktor-sample .
ARG TEMURIN=25.0.4_7

FROM eclipse-temurin:${TEMURIN}-jdk AS build
WORKDIR /src
COPY . .
# --no-daemon: a daemon that outlives the RUN step keeps the layer's processes alive for nothing.
# The training run starts the application, hits it, stops it and assembles the cache, all inside
# this step; aotVerify then proves the cache is accepted by *this* JVM before the image is built.
RUN ./gradlew -p samples/ktor installDist aotTrain aotVerify --no-daemon --console=plain

# The same image as the build stage — not the `-jre` variant of the same build. The cache records
# the size of $JAVA_HOME/lib/modules, and the JRE package of the very same Temurin build has a
# smaller one: the JVM then refuses the cache with "This file is not the one used while building
# the AOT cache: '/opt/java/openjdk/lib/modules', size has changed". Two stages, one image.
FROM eclipse-temurin:${TEMURIN}-jdk
# COPY preserves the mtimes aotTrain pinned, which the JVM checks against the cache.
COPY --from=build /src/samples/ktor/build/install/ktor-sample /opt/app
EXPOSE 18090
CMD ["/opt/app/bin/ktor-sample"]
