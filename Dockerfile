# syntax=docker/dockerfile:1

# ---- Stage 1: build the jar with Maven -------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Cache dependencies separately from source so `docker build` reuses them
# across rebuilds when only source files change.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Stage 2: runtime --------------------------------------------------------
# Ubuntu-based JRE image so `apt-get` is available for nginx + gettext-base (envsubst).
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends nginx gettext-base curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/commengine.jar /app/commengine.jar
COPY deploy/nginx.conf.template /etc/nginx/templates/nginx.conf.template
COPY deploy/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# The REST API and Socket.IO server always listen on these FIXED internal ports;
# Nginx is the only thing that needs to know about the platform's dynamic $PORT.
ENV SERVER_PORT=5000
ENV SOCKETIO_PORT=5001

# Informational only — the platform (Render, etc.) decides the externally
# reachable port via the $PORT env var, which entrypoint.sh renders into nginx.conf.
EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]