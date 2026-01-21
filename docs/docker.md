# Docker

## Build the Image

### Option 1: Maven Profile (Recommended)

Build the Docker image as part of the Maven build:

```bash
# Full build with Docker image
./mvnw clean install -Pdocker

# Or just build the Docker image (after a regular build)
./mvnw install -Pdocker -pl lightning-engine/webservice/quarkus-web-api
```

This creates `lightning-backend:latest` using `Dockerfile.prebuilt`.

### Option 2: Manual Docker Build

```bash
# Full build (compiles from source inside container)
docker build -t lightning-backend .

# Or use pre-built JARs (faster, requires local mvn package first)
./mvnw package -DskipTests
docker build -f Dockerfile.prebuilt -t lightning-backend .
```

### Running Playwright Tests

The Playwright E2E tests require the Docker image. Build it before running tests:

```bash
# Build with Docker
./mvnw clean install -Pdocker

# Run Playwright tests
./mvnw verify -pl lightning-engine/webservice/playwright-test -Pacceptance-tests
```

## Run with Docker Compose

```bash
# Start backend only
docker compose up -d

# Start with pre-built image
docker compose --profile prebuilt up -d backend-prebuilt

# View logs
docker compose logs -f backend

# Stop
docker compose down
```

## Container Details

| Image | Size | Contents |
|-------|------|----------|
| `lightning-backend` | ~300MB | Quarkus app, modules JAR |
| `eclipse-temurin:25-jre-alpine` | Base runtime |
| `mongo:7` | MongoDB for snapshot persistence |

**Exposed Ports:**
- `8080` - REST API and WebSocket
- `27017` - MongoDB (optional, for external access)

**Health Check:** `GET /api/health` (30s interval)

**Environment Variables:**

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Dquarkus.http.host=0.0.0.0` | JVM arguments |
| `QUARKUS_LOG_LEVEL` | `INFO` | Log verbosity |
| `QUARKUS_MONGODB_CONNECTION_STRING` | `mongodb://mongodb:27017` | MongoDB connection |
| `SNAPSHOT_PERSISTENCE_ENABLED` | `true` | Enable snapshot history |
| `SNAPSHOT_PERSISTENCE_DATABASE` | `lightningfirefly` | MongoDB database name |
| `SNAPSHOT_PERSISTENCE_COLLECTION` | `snapshots` | MongoDB collection name |
| `SNAPSHOT_PERSISTENCE_TICK_INTERVAL` | `1` | Persist every N ticks |

## MongoDB Persistence

The docker-compose setup includes MongoDB for snapshot history:

```yaml
services:
  mongodb:
    image: mongo:7
    container_name: lightning-mongodb
    ports:
      - "27017:27017"
    volumes:
      - mongodb-data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]

  backend:
    depends_on:
      mongodb:
        condition: service_healthy
    environment:
      - QUARKUS_MONGODB_CONNECTION_STRING=mongodb://mongodb:27017
      - SNAPSHOT_PERSISTENCE_ENABLED=true
```

To disable MongoDB persistence:

```bash
SNAPSHOT_PERSISTENCE_ENABLED=false docker compose up -d
```
