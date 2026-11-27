# Image of the payments service (ADR 0011). Builds the jar in one stage and runs it on a plain JRE in
# another, as a user without privileges. docker compose builds it as it is, in development and for a
# deployment (the release workflow publishes it as ghcr.io/<owner>/ayni-payments:<tag>).
#
#   docker build payments-service
#
# It shares a machine of 1 GB with the backend, so the JVM is sized by hand below
# (deploy/README.md, "Memory"); docker compose passes the same options with the heap as a variable.

# ---- Build ------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# The Maven wrapper and the dependencies first, in a layer of their own: changing the sources does
# not download them again.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/ayni-payments-*.jar extract --layers --launcher \
        --destination target/extracted

# ---- Run --------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

RUN groupadd --system ayni \
    && useradd --system --gid ayni --no-create-home --shell /usr/sbin/nologin ayni

WORKDIR /app

COPY --from=build /build/target/extracted/dependencies/ ./
COPY --from=build /build/target/extracted/spring-boot-loader/ ./
COPY --from=build /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build /build/target/extracted/application/ ./

# Serial GC (one small heap, no GC threads), a fixed heap instead of a percentage of the container,
# capped metaspace and code cache, half the default thread stack, and C1 only: the server compiler
# would spend memory on code that a service this small never makes hot.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xms32m -Xmx96m -XX:MaxMetaspaceSize=80m -Xss512k -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=16m"

USER ayni

EXPOSE 8081

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
