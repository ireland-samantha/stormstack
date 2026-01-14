# Docker

## Build the Image

```bash
# Full build (compiles from source inside container)
docker build -t lightning-backend .

# Or use pre-built JARs (faster, requires local mvn package first)
./mvnw package -DskipTests
docker build -f Dockerfile.prebuilt -t lightning-backend:prebuilt .
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
| `lightning-backend` | ~350MB | Quarkus app, modules JAR, GUI JAR |
| `eclipse-temurin:25-jre-alpine` | Base runtime |
| `mongo:7` | MongoDB for snapshot persistence |

**Exposed Ports:**
- `8080` - REST API and WebSocket
- `27017` - MongoDB (optional, for external access)

**Health Check:** `GET /api/simulation/tick` (30s interval)

**Environment Variables:**

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Dquarkus.http.host=0.0.0.0` | JVM arguments |
| `QUARKUS_LOG_LEVEL` | `INFO` | Log verbosity |
| `GUI_JAR_PATH` | `/app/gui/lightning-gui.jar` | Path to GUI JAR for download endpoint |
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

## GUI Download Endpoint

When running in Docker, the GUI JAR is bundled and available for download:

```bash
# Get info about GUI availability
curl http://localhost:8080/api/gui/info

# Download ZIP with auto-configuration
curl -O http://localhost:8080/api/gui/download

# Download JAR only (no config)
curl -O http://localhost:8080/api/gui/download/jar
```

The `/api/gui/download` endpoint returns a ZIP containing:
- `lightning-gui.jar` - The GUI application
- `server.properties` - Pre-configured with the server URL
- `README.txt` - Usage instructions
