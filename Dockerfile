# ======================
# Stage 1: Build
# ======================
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app
COPY . .

RUN mvn -B clean install

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

RUN chown -R quarkus:quarkus /app
USER quarkus

EXPOSE 8080
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0"

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/simulation/tick || exit 1

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
