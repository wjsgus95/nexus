# Multi-stage Dockerfile for Nexus CI Smoke, Regression & Kafka Diff CLI
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
COPY configs ./configs

RUN chmod +x ./gradlew \
    && ./gradlew test fatJar --no-daemon \
    && java -jar build/libs/nexus-0.1.0-all.jar doctor --offline -p /workspace/configs/catalog/kr/profile.yaml

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/build/libs/nexus-0.1.0-all.jar /app/nexus.jar
COPY --from=builder /workspace/configs /app/configs

ENV PROFILE_PATH=/app/configs/catalog/kr/profile.yaml \
    REPORT_OUTPUT_DIR=/app/reports \
    CONCURRENCY_LIMIT=10 \
    RETRY_COUNT=2

ENTRYPOINT ["java", "-jar", "/app/nexus.jar"]
