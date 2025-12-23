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
COPY lightning-engine/webservice/web-api-adapter/pom.xml ./lightning-engine/webservice/web-api-adapter/
COPY lightning-engine/webservice/quarkus-web-api/pom.xml ./lightning-engine/webservice/quarkus-web-api/
COPY lightning-engine/gui/pom.xml ./lightning-engine/gui/
COPY lightning-engine-extensions/modules/pom.xml ./lightning-engine-extensions/modules/
COPY lightning-engine-extensions/game-masters/pom.xml ./lightning-engine-extensions/game-masters/
COPY lightning-engine/api-acceptance-test/pom.xml ./lightning-engine/api-acceptance-test/
COPY lightning-engine/gui-acceptance-test/pom.xml ./lightning-engine/gui-acceptance-test/
COPY engine-api/pom.xml ./engine-api/
COPY game-renderer/pom.xml ./game-renderer/
COPY test-game-module/pom.xml ./test-game-module/
COPY game-app/pom.xml ./game-app/
COPY examples/pom.xml ./examples/
COPY examples/checkers-engine-module/pom.xml ./examples/checkers-engine-module/
COPY examples/checkers-gamemaster/pom.xml ./examples/checkers-gamemaster/
COPY examples/checkers/pom.xml ./examples/checkers/

# Create empty directories for modules not needed at runtime (to satisfy parent pom)
RUN mkdir -p engine-api/src/main/java game-renderer/src/main/java test-game-module/src/main/java \
    game-app/src/main/java lightning-engine-extensions/game-masters/src/main/java \
    examples/checkers-engine-module/src/main/java examples/checkers-gamemaster/src/main/java \
    examples/checkers/src/main/java

# Download dependencies (this layer will be cached if poms don't change)
RUN mvn dependency:go-offline -B -pl lightning-engine/webservice/quarkus-web-api,lightning-engine-extensions/modules,lightning-engine/gui -am || true

# Copy source code
COPY utils/src ./utils/src
COPY lightning-engine/engine-core/src ./lightning-engine/engine-core/src
COPY lightning-engine/engine-internal/src ./lightning-engine/engine-internal/src
COPY lightning-engine/rendering-core/src ./lightning-engine/rendering-core/src
COPY lightning-engine/rendering-test/src ./lightning-engine/rendering-test/src
COPY lightning-engine/webservice/web-api-adapter/src ./lightning-engine/webservice/web-api-adapter/src
COPY lightning-engine/webservice/quarkus-web-api/src ./lightning-engine/webservice/quarkus-web-api/src
COPY lightning-engine/gui/src ./lightning-engine/gui/src
COPY lightning-engine-extensions/modules/src ./lightning-engine-extensions/modules/src
COPY lightning-engine-extensions/game-masters/src ./lightning-engine-extensions/game-masters/src
COPY engine-api/src ./engine-api/src
COPY examples/checkers-engine-module/src ./examples/checkers-engine-module/src
COPY examples/checkers-gamemaster/src ./examples/checkers-gamemaster/src

# Build the quarkus-web-api module, engine-ext-modules, GUI, and checkers modules
RUN mvn package -B -DskipTests -pl lightning-engine/webservice/quarkus-web-api,lightning-engine-extensions/modules,lightning-engine/gui,examples/checkers-engine-module,examples/checkers-gamemaster -am

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

# Create modules and gamemasters directories, copy built JARs
RUN mkdir -p /app/modules /app/gamemasters /app/gui
COPY --from=build /app/lightning-engine-extensions/modules/target/engine-ext-modules-*.jar ./modules/
COPY --from=build /app/examples/checkers-engine-module/target/checkers-engine-module-*.jar ./modules/
COPY --from=build /app/examples/checkers-gamemaster/target/checkers-gamemaster-*.jar ./gamemasters/

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
