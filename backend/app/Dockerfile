# Farm2Home single-JVM aggregation — Render deployable image.
# Build:  (from backend/)  mvn -pl app -am clean package -DskipTests
#         then             docker build -t farm2home-app backend/app
# Run:    docker run -e PORT=8080 -e SPRING_DATASOURCE_URL=... -e JWT_SECRET=... -p 8080:8080 farm2home-app
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# app/target/app.jar is the executable Boot jar produced by this module (finalName=app).
COPY target/app.jar app.jar

# Render sets $PORT; the app binds server.port=${PORT:8080}. Do not hardcode a service port.
ENV PORT=8080
EXPOSE 8080

# Container-aware memory. Overridable via JAVA_OPTS. Tuned for the 12-service aggregate on a
# 512 MB Free-tier box; every value below is measured (see GROUP5_MEMORY_REVIEW.md / GROUP6_REPORT.md).
#
# -XX:MaxRAMPercentage=55 : with 12 services the non-heap floor is ~110 MB metaspace + ~57 MB code
#   + ~40 MB symbols + threads + CDS ≈ 250 MB, so a 70% (358 MB) heap ceiling + that floor
#   over-commits 512 MB. Under a realistic reports large-export + concurrent-dashboard workload
#   the 70% config climbed to ~510 MiB (99.8%) and OOM-killed the JDK/NMT variant; 55% (~281 MB
#   heap ceiling, still ~3x the ~90 MB live heap) held at ~494-505 MiB with no OOM across the
#   same workload incl. 6 concurrent large Excel exports and 4 concurrent large PDF exports.
# -Xss512k : halves the default 1 MB thread stack. Reports' export endpoints run on MVC async
#   threads and dashboard fans out 8-wide, so thread count spikes; measured safe (no
#   StackOverflowError under load), ~3-5 MiB back at ~80 threads.
# -Dio.netty.allocator.num*Arenas=2 : the aggregate's only outbound HTTP is the in-process lb://
#   loopback. Netty's PooledByteBufAllocator otherwise sizes arenas from host CPU count (2*cores,
#   16 MB each) — ~25 MiB of off-heap "Other" for a workload that never needs it. See GROUP5_MEMORY_REVIEW.md.
# SerialGC is deliberate: G1 on a 512 MB container costs ~65 MB native (remembered sets / marking
#   bitmaps) that cancels its lower heap ceiling (measured). Do NOT switch to G1.
#
# NOTE: even so, the 12-service aggregate peaks at ~94-99% of 512 MB under heavy export load.
# A 13th service (Group 7 / auth) or higher export concurrency needs a >=1 GB instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -Xss512k -Dio.netty.allocator.numDirectArenas=2 -Dio.netty.allocator.numHeapArenas=2"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
