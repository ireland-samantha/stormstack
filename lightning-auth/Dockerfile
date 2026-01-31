FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Copy the Quarkus app structure
COPY target/quarkus-app/lib/ /app/lib/
COPY target/quarkus-app/*.jar /app/
COPY target/quarkus-app/app/ /app/app/
COPY target/quarkus-app/quarkus/ /app/quarkus/

# Expose the auth service port
EXPOSE 8082

# Run the application
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
