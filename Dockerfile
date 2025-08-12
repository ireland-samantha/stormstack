# Multi-stage Dockerfile for building the Lightning Engine Backend
# Usage: docker build -t lightning-backend .

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

# Copy the parent pom and all module poms first for better caching
COPY pom.xml ./
COPY utils/pom.xml ./utils/
COPY lightning-engine/engine-core/pom.xml ./lightning-engine/engine-core/
COPY lightning-engine/engine-internal/pom.xml ./lightning-engine/engine-internal/
COPY lightning-engine/rendering-core/pom.xml ./lightning-engine/rendering-core/
COPY lightning-engine/rendering-test/pom.xml ./lightning-engine/rendering-test/
COPY lightning-engine/webservice/pom.xml ./lightning-engine/webservice/
COPY lightning-engine/webservice/quarkus-web-api/pom.xml ./lightning-engine/webservice/quarkus-web-api/
COPY lightning-engine/gui/pom.xml ./lightning-engine/gui/
COPY lightning-engine/api-acceptance-test/pom.xml ./lightning-engine/api-acceptance-test/
COPY lightning-engine/e2e-live-rendering-and-backend-acceptance-test/pom.xml ./lightning-engine/e2e-live-rendering-and-backend-acceptance-test/

# Engine adapter modules (game-sdk, web-api-adapter)
COPY lightning-engine/engine-adapter/pom.xml ./lightning-engine/engine-adapter/
COPY lightning-engine/engine-adapter/game-sdk/pom.xml ./lightning-engine/engine-adapter/game-sdk/
COPY lightning-engine/engine-adapter/web-api-adapter/pom.xml ./lightning-engine/engine-adapter/web-api-adapter/

# Extensions parent poms
COPY lightning-engine-extensions/modules/pom.xml ./lightning-engine-extensions/modules/
COPY lightning-engine-extensions/game-masters/pom.xml ./lightning-engine-extensions/game-masters/

# Module submodules (8 separate modules)
COPY lightning-engine-extensions/modules/entity-module/pom.xml ./lightning-engine-extensions/modules/entity-module/
COPY lightning-engine-extensions/modules/health-module/pom.xml ./lightning-engine-extensions/modules/health-module/
COPY lightning-engine-extensions/modules/rendering-module/pom.xml ./lightning-engine-extensions/modules/rendering-module/
COPY lightning-engine-extensions/modules/rigid-body-module/pom.xml ./lightning-engine-extensions/modules/rigid-body-module/
COPY lightning-engine-extensions/modules/box-collider-module/pom.xml ./lightning-engine-extensions/modules/box-collider-module/
COPY lightning-engine-extensions/modules/projectile-module/pom.xml ./lightning-engine-extensions/modules/projectile-module/
COPY lightning-engine-extensions/modules/items-module/pom.xml ./lightning-engine-extensions/modules/items-module/
COPY lightning-engine-extensions/modules/move-module/pom.xml ./lightning-engine-extensions/modules/move-module/

# Download dependencies (this layer will be cached if poms don't change)
RUN mvn dependency:go-offline -B -pl lightning-engine/webservice/quarkus-web-api,lightning-engine-extensions/modules,lightning-engine/gui -am || true

# Copy source code
COPY utils/src ./utils/src
COPY lightning-engine/engine-core/src ./lightning-engine/engine-core/src
COPY lightning-engine/engine-internal/src ./lightning-engine/engine-internal/src
COPY lightning-engine/rendering-core/src ./lightning-engine/rendering-core/src
COPY lightning-engine/rendering-test/src ./lightning-engine/rendering-test/src
COPY lightning-engine/webservice/quarkus-web-api/src ./lightning-engine/webservice/quarkus-web-api/src
COPY lightning-engine/gui/src ./lightning-engine/gui/src

# Engine adapter sources
COPY lightning-engine/engine-adapter/game-sdk/src ./lightning-engine/engine-adapter/game-sdk/src
COPY lightning-engine/engine-adapter/web-api-adapter/src ./lightning-engine/engine-adapter/web-api-adapter/src

# Game masters source
COPY lightning-engine-extensions/game-masters/src ./lightning-engine-extensions/game-masters/src

# Module submodule sources (8 separate modules)
COPY lightning-engine-extensions/modules/entity-module/src ./lightning-engine-extensions/modules/entity-module/src
COPY lightning-engine-extensions/modules/health-module/src ./lightning-engine-extensions/modules/health-module/src
COPY lightning-engine-extensions/modules/rendering-module/src ./lightning-engine-extensions/modules/rendering-module/src
COPY lightning-engine-extensions/modules/rigid-body-module/src ./lightning-engine-extensions/modules/rigid-body-module/src
COPY lightning-engine-extensions/modules/box-collider-module/src ./lightning-engine-extensions/modules/box-collider-module/src
COPY lightning-engine-extensions/modules/projectile-module/src ./lightning-engine-extensions/modules/projectile-module/src
COPY lightning-engine-extensions/modules/items-module/src ./lightning-engine-extensions/modules/items-module/src
COPY lightning-engine-extensions/modules/move-module/src ./lightning-engine-extensions/modules/move-module/src

# Build the quarkus-web-api module, modules, and GUI
RUN mvn package -B -DskipTests -pl lightning-engine/webservice/quarkus-web-api,lightning-engine-extensions/modules,lightning-engine/gui -am

# Stage 2: Create the runtime image
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S quarkus && adduser -S quarkus -G quarkus

# Copy the built artifacts
COPY --from=build /app/lightning-engine/webservice/quarkus-web-api/target/quarkus-app/lib/ ./lib/
COPY --from=build /app/lightning-engine/webservice/quarkus-web-api/target/quarkus-app/*.jar ./
COPY --from=build /app/lightning-engine/webservice/quarkus-web-api/target/quarkus-app/app/ ./app/
COPY --from=build /app/lightning-engine/webservice/quarkus-web-api/target/quarkus-app/quarkus/ ./quarkus/

# Create modules and gamemasters directories
RUN mkdir -p /app/modules /app/gamemasters /app/gui

# Copy individual module JARs (8 separate modules)
COPY --from=build /app/lightning-engine-extensions/modules/entity-module/target/entity-module-*.jar ./modules/
COPY --from=build /app/lightning-engine-extensions/modules/health-module/target/health-module-*.jar ./modules/
COPY --from=build /app/lightning-engine-extensions/modules/rendering-module/target/rendering-module-*.jar ./modules/
COPY --from=build /app/lightning-engine-extensions/modules/rigid-body-module/target/rigid-body-module-*.jar ./modules/
COPY --from=build /app/lightning-engine-extensions/modules/box-collider-module/target/box-collider-module-*.jar ./modules/
COPY --from=build /app/lightning-engine-extensions/modules/projectile-module/target/projectile-module-*.jar ./modules/
COPY --from=build /app/lightning-engine-extensions/modules/items-module/target/items-module-*.jar ./modules/
COPY --from=build /app/lightning-engine-extensions/modules/move-module/target/move-module-*.jar ./modules/

# Copy GUI JAR for download endpoint
COPY --from=build /app/lightning-engine/gui/target/engine-gui-*.jar ./gui/lightning-gui.jar

# Set proper permissions
RUN chown -R quarkus:quarkus /app

USER quarkus

# Expose the default Quarkus port
EXPOSE 8080

# Set environment variables for Quarkus
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0"
ENV GUI_JAR_PATH="/app/gui/lightning-gui.jar"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/simulation/tick || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
