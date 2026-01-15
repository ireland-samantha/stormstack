# Multi-stage Dockerfile for building the Quarkus API application
# IMPORTANT: This Dockerfile must be built from the PROJECT ROOT directory
# Usage: docker build -f quarkus-api/Dockerfile -t lightningfirefly-api .

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Copy the parent pom and all module poms first for better caching
COPY pom.xml ./
COPY utils/pom.xml ./utils/
COPY auth/pom.xml ./auth/
COPY ecs/pom.xml ./ecs/
COPY simulation/pom.xml ./simulation/
COPY rts/pom.xml ./rts/
COPY rts/rts-domain/pom.xml ./rts/rts-domain/
COPY rts/rts-simulation/pom.xml ./rts/rts-simulation/
COPY spring-api/pom.xml ./spring-api/
COPY application/pom.xml ./application/
COPY integration-test/pom.xml ./integration-test/
COPY simulation-lean/pom.xml ./simulation-lean/
COPY simulation-lean-modules/pom.xml ./simulation-lean-modules/
COPY quarkus-api/pom.xml ./quarkus-api/

# Download dependencies (this layer will be cached if poms don't change)
RUN mvn dependency:go-offline -B -pl quarkus-api,simulation-lean-modules -am || true

# Copy source code
COPY utils/src ./utils/src
COPY auth/src ./auth/src
COPY ecs/src ./ecs/src
COPY simulation/src ./simulation/src
COPY rts/rts-domain/src ./rts/rts-domain/src
COPY rts/rts-simulation/src ./rts/rts-simulation/src
COPY simulation-lean/src ./simulation-lean/src
COPY simulation-lean-modules/src ./simulation-lean-modules/src
COPY quarkus-api/src ./quarkus-api/src

# Build the quarkus-api module and simulation-lean-modules
RUN mvn package -B -DskipTests -pl quarkus-api,simulation-lean-modules -am

# Stage 2: Create the runtime image
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S quarkus && adduser -S quarkus -G quarkus

# Copy the built artifacts
COPY --from=build /app/quarkus-api/target/quarkus-app/lib/ ./lib/
COPY --from=build /app/quarkus-api/target/quarkus-app/*.jar ./
COPY --from=build /app/quarkus-api/target/quarkus-app/app/ ./app/
COPY --from=build /app/quarkus-api/target/quarkus-app/quarkus/ ./quarkus/

# Create modules directory and copy built module JARs
RUN mkdir -p /app/modules
COPY --from=build /app/simulation-lean-modules/target/simulation-lean-modules-*.jar ./modules/

# Set proper permissions
RUN chown -R quarkus:quarkus /app

USER quarkus

# Expose the default Quarkus port
EXPOSE 8080

# Set environment variables for Java 25 and Quarkus
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 --enable-preview"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/containers || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
