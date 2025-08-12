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

**Exposed Ports:**
- `8080` - REST API and WebSocket

**Health Check:** `GET /api/simulation/tick` (30s interval)

**Environment Variables:**

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Dquarkus.http.host=0.0.0.0` | JVM arguments |
| `QUARKUS_LOG_LEVEL` | `INFO` | Log verbosity |
| `GUI_JAR_PATH` | `/app/gui/lightning-gui.jar` | Path to GUI JAR for download endpoint |

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
