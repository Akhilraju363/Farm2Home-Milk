# Farm2Home auth-service — standalone Render-deployable image (kept separate from backend/app,
# see docs/FREE_DEPLOYMENT_ARCHITECTURE.md). Build: mvn -pl auth-service -am clean package -DskipTests
# Run:   docker run -e PORT=8081 -e SPRING_DATASOURCE_URL=... -e JWT_SECRET=... -p 8081:8081 farm2home-auth
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar

# Render sets $PORT; server.port=${PORT:8081} in application.yml. Do not hardcode a service port.
ENV PORT=8081
EXPOSE 8081

# Container-aware memory, measured against a 512 MB container (see docs/FREE_DEPLOYMENT_ARCHITECTURE.md
# §12 for the run). auth-service is a single, comparatively light JPA+Security+web service (5
# entities, no Netty/WebFlux, no dashboard-style fan-out) — NOT the 12-service aggregate, so
# backend/app's tuning (MaxRAMPercentage=55, Xss512k, Netty arena caps) is deliberately NOT
# copied here; those flags exist to compensate for problems (Netty loopback arenas, thread-count
# spikes from an 8-way async fan-out) this service doesn't have.
#
# -XX:MaxRAMPercentage=60 : leaves headroom under 512 MB for metaspace/code-cache/threads/BCrypt
#   scratch while giving the JVM more heap than the JDK 21 container-aware default (25%, ~128 MB)
#   would. SerialGC is used for the same reason as backend/app: G1's remembered-set/marking-bitmap
#   overhead is a poor trade on a sub-1GB container.
# SerialGC is deliberate, not measured to be strictly required here (auth's live heap is small
#   and low-churn) but consistent with the same reasoning validated for backend/app; do not switch
#   to G1GC without measuring.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
