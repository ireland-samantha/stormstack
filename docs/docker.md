# Docker

## Quick Start (Docker Hub)

The easiest way to run Lightning Engine is using the published Docker image:

```bash
export ADMIN_INITIAL_PASSWORD=your-secure-password
docker compose up -d
```

This pulls `samanthacireland/lightning-engine:0.0.1` from Docker Hub.

## Build from Source

### Option 1: Maven Profile (Recommended)

Build the Docker image as part of the Maven build:

```bash
# Full build with Docker image
./mvnw clean install -Pdocker

# Or just build the Docker image (after a regular build)
./mvnw install -Pdocker -pl lightning-engine/webservice/quarkus-web-api
```

### Option 2: Manual Docker Build

```bash
# Full build (compiles from source inside container)
docker build -t samanthacireland/lightning-engine:0.0.1 .

# Or use pre-built JARs (faster, requires local mvn package first)
./mvnw package -DskipTests
docker build -f Dockerfile.prebuilt -t samanthacireland/lightning-engine:0.0.1 .
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
| `samanthacireland/lightning-engine:0.0.1` | ~300MB | Quarkus app, modules JAR |
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

**Security Environment Variables:**

| Variable | Default | Description |
|----------|---------|-------------|
| `ADMIN_INITIAL_PASSWORD` | (random) | Initial admin user password. **Required in production.** If not set, a secure random password is generated and logged. |
| `CORS_ORIGINS` | (none) | Allowed CORS origins. **Required in production.** Example: `https://yourdomain.com` |
| `AUTH_JWT_SECRET` | (random) | JWT signing secret. If not set, a secure random secret is generated. |

> ⚠️ **Security Warning:** In production, always set `ADMIN_INITIAL_PASSWORD` and `CORS_ORIGINS`. Never use wildcard (`*`) for CORS in production.

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

## Production Deployment

For production deployments, ensure you configure the required security environment variables:

```bash
# Required security configuration
export ADMIN_INITIAL_PASSWORD="your-secure-admin-password"
export CORS_ORIGINS="https://yourdomain.com"

# Optional (generated automatically if not set)
export AUTH_JWT_SECRET="your-jwt-secret-key"

# Start with production profile
QUARKUS_PROFILE=prod docker compose up -d
```

### Security Checklist

- [ ] Set `ADMIN_INITIAL_PASSWORD` to a strong, unique password
- [ ] Set `CORS_ORIGINS` to your specific domain(s) - never use `*`
- [ ] Change the admin password after first login
- [ ] Use HTTPS in production (configure a reverse proxy)
- [ ] Review MongoDB access controls if exposed externally
