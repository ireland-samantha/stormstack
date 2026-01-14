# ======================
# Stage 1: Build
# ======================
FROM maven:3.9-eclipse-temurin-25 AS build

# Install Node.js for frontend build
RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .

# Clean first, then build frontend, then package
RUN mvn -B clean

# Build frontend (outputs to src/main/resources/META-INF/resources/admin/dashboard/)
WORKDIR /app/lightning-engine/webservice/quarkus-web-api/src/main/frontend
RUN npm ci && npm run build

# Build backend (includes frontend assets in JAR) - no clean!
WORKDIR /app
RUN mvn -B install -DskipTests

# ======================
# Stage 2: Runtime
# ======================
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Install wget for healthcheck
RUN apk add --no-cache wget

# Create non-root user
RUN addgroup -S quarkus && adduser -S quarkus -G quarkus

# Copy Quarkus fast-jar
COPY --from=build /app/lightning-engine/webservice/quarkus-web-api/target/quarkus-app/lib/ ./lib/
COPY --from=build /app/lightning-engine/webservice/quarkus-web-api/target/quarkus-app/*.jar ./
COPY --from=build /app/lightning-engine/webservice/quarkus-web-api/target/quarkus-app/app/ ./app/
COPY --from=build /app/lightning-engine/webservice/quarkus-web-api/target/quarkus-app/quarkus/ ./quarkus/

# Create directories for modules, resources, and AI
RUN mkdir -p modules resources ai && chown -R quarkus:quarkus /app
USER quarkus

EXPOSE 8080
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0"

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --quiet --output-document=/dev/null http://localhost:8080/admin/dashboard/ || exit 1

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
