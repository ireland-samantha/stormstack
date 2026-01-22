# Useful Commands

Common commands for developing and testing the Lightning Engine.

## Building

```bash
# Build all modules
./mvnw clean install

# Build without tests
./mvnw clean install -DskipTests

# Build specific module
./mvnw compile -pl lightning-engine/gui

# Package webservice for Docker
./mvnw package -pl lightning-engine/webservice/quarkus-web-api -DskipTests
```

## Testing

```bash
# Run all tests
./mvnw test

# Run tests for specific module
./mvnw test -pl lightning-engine/gui

# Run specific test class
./mvnw test -pl lightning-engine/gui -Dtest=MatchPanelTest

# Run specific test method
./mvnw test -pl lightning-engine/gui -Dtest=MatchPanelTest#testMethodName
```

## GUI Acceptance Tests (macOS)

GUI tests require special JVM flags on macOS for GLFW/OpenGL support.

```bash
# Run all GUI acceptance tests
DOCKER_HOST=unix:///var/run/docker.sock ./mvnw test \
    -pl lightning-engine/gui-acceptance-test \
    -DskipTests=false

# Run specific acceptance test
DOCKER_HOST=unix:///var/run/docker.sock ./mvnw test \
    -pl lightning-engine/gui-acceptance-test \
    -Dtest=RenderingResourceGuiIT \
    -DskipTests=false

# Run single test method
DOCKER_HOST=unix:///var/run/docker.sock ./mvnw test \
    -pl lightning-engine/gui-acceptance-test \
    -Dtest=RenderingResourceGuiIT#completeWorkflow_uploadSpawnAttachVerify \
    -DskipTests=false
```

## Docker

```bash
# Pull from Docker Hub
docker pull samanthacireland/lightning-engine:0.0.1

# Build Docker image (full build)
docker build -t samanthacireland/lightning-engine:0.0.1 -f Dockerfile .

# Build Docker image (prebuilt jar)
docker build -t samanthacireland/lightning-engine:0.0.1 \
    -f lightning-engine/webservice/quarkus-web-api/Dockerfile.prebuilt \
    lightning-engine/webservice/quarkus-web-api

# Run Docker container
docker run -p 8080:8080 -e ADMIN_INITIAL_PASSWORD=admin samanthacireland/lightning-engine:0.0.1

# View container logs
docker logs <container-id>

# Stop and remove container
docker stop <container-id> && docker rm <container-id>
```

## Running the GUI Application

```bash
# Run GUI with Maven (requires display)
./mvnw exec:java -pl lightning-engine/gui \
    -Dexec.mainClass=com.lightningfirefly.engine.gui.EngineGuiApplication \
    -Dexec.args="-s http://localhost:8080 -m 1"

# macOS requires additional JVM flag
JAVA_TOOL_OPTIONS="-XstartOnFirstThread" ./mvnw exec:java -pl lightning-engine/gui \
    -Dexec.mainClass=com.lightningfirefly.engine.gui.EngineGuiApplication \
    -Dexec.args="-s http://localhost:8080"
```

## Quarkus Dev Mode

```bash
# Run Quarkus in dev mode (hot reload)
./mvnw quarkus:dev -pl lightning-engine/webservice/quarkus-web-api
```

## REST API Testing

```bash
# Get simulation tick
curl http://localhost:8080/api/simulation/tick

# Tick the simulation
curl -X POST http://localhost:8080/api/simulation/tick

# List matches
curl http://localhost:8080/api/matches

# Create match with modules
curl -X POST http://localhost:8080/api/matches \
    -H "Content-Type: application/json" \
    -d '{"enabledModuleNames":["SpawnModule","RenderModule"]}'

# Get match details
curl http://localhost:8080/api/matches/1

# Delete match
curl -X DELETE http://localhost:8080/api/matches/1

# List available modules
curl http://localhost:8080/api/modules

# List available commands
curl http://localhost:8080/api/commands

# Send spawn command
curl -X POST http://localhost:8080/api/commands \
    -H "Content-Type: application/json" \
    -d '{"commandName":"spawn","payload":{"matchId":1,"playerId":1,"entityType":100}}'

# Get all snapshots
curl http://localhost:8080/api/snapshots

# Get snapshot for specific match
curl http://localhost:8080/api/snapshots/match/1

# List resources
curl http://localhost:8080/api/resources

# Upload resource
curl -X POST http://localhost:8080/api/resources \
    -F "file=@texture.png" \
    -F "type=TEXTURE"

# Download resource
curl http://localhost:8080/api/resources/1/download -o resource.png
```

## Debugging

```bash
# View Maven dependency tree
./mvnw dependency:tree -pl lightning-engine/gui

# Check effective POM
./mvnw help:effective-pom -pl lightning-engine/gui

# Run with debug output
./mvnw test -X -pl lightning-engine/gui -Dtest=SomeTest
```

## Git

```bash
# View status
git status

# Stage all changes
git add .

# Commit with message
git commit -m "message"

# View recent commits
git log --oneline -10

# View diff
git diff

# View staged diff
git diff --staged
```
