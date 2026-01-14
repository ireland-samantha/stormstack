# Build stage
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom files
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

# Copy required modules
COPY auth auth
COPY utils utils
COPY issue-api-token issue-api-token

# Build the issue-api-token module (includes auth dependency)
RUN chmod +x mvnw && \
    ./mvnw clean package -pl issue-api-token -am -DskipTests -q

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Copy the shaded JAR
COPY --from=builder /build/issue-api-token/target/issue-api-token.jar /app/issue-api-token.jar

# Set entrypoint
ENTRYPOINT ["java", "-jar", "/app/issue-api-token.jar"]

# Default to showing help
CMD ["--help"]
