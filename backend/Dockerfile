# ==============================================================================
# FinPulse Backend (Spring Boot 3 / Java 21) - Production Multi-Stage Dockerfile
# ==============================================================================

# Stage 1: Build executable JAR
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# Cache Maven dependencies first for faster incremental builds
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source and package JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal hardened runtime image
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl for container healthcheck
RUN apk add --no-cache curl \
    && addgroup -S finpulse \
    && adduser -S finpulse -G finpulse

WORKDIR /app

# Copy compiled Spring Boot fat JAR with non-root ownership
COPY --from=build --chown=finpulse:finpulse /workspace/target/*.jar /app/app.jar

USER finpulse

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD sh -c 'curl -fsS "http://localhost:${SERVER_PORT:-8080}/actuator/health" > /dev/null || exit 1'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
