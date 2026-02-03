# Useful Commands

Common commands for developing and testing StormStack (Thunder Engine + Lightning CLI).

## Lightning CLI

The `lightning` CLI is the primary way to interact with the Control Plane and Engine nodes.

### Installation

```bash
# Build the CLI (requires Go 1.24+)
cd lightning/cli
go build -o lightning ./cmd/lightning
```

### Configuration

```bash
# Set Control Plane URL
lightning config set control_plane_url http://localhost:8081

# Authenticate
lightning auth login --username admin --password admin

# Check status
lightning auth status
```

### Cluster Management

```bash
# Cluster health
lightning cluster status

# List nodes
lightning node list

# Node details
lightning cluster node node-1
```

### Deploying Games

```bash
# Deploy a match with modules
lightning deploy --modules EntityModule,RigidBodyModule,RenderingModule

# Deploy to a specific node
lightning deploy --modules EntityModule --node node-1

# List matches
lightning match list

# Get match details
lightning match get node-1-1-1

# Join a match
lightning match join node-1-1-1 --player-name "Alice" --player-id "player-001"

# Undeploy
lightning undeploy node-1-1-1
```

### Simulation Control

```bash
# Set context first
lightning node context set node-1 --container-id 1 --match-id node-1-1-1

# Or set from match ID
lightning node context match node-1-1-1

# Advance tick
lightning node tick advance
lightning node tick advance -n 10

# Auto-play at 60 FPS
lightning node simulation play --interval-ms 16

# Stop auto-play
lightning node simulation stop

# Get current tick
lightning node tick get
```

### Game Commands

```bash
# List available commands
lightning command list

# Send a command
lightning command send spawn '{"matchId":1,"playerId":1,"entityType":100}'
lightning command send setPosition '{"entityId":1,"x":10,"y":20}'

# Send from file
lightning command send-bulk commands.json
```

### Module Management

```bash
# List modules in registry
lightning module list

# Upload a module
lightning module upload MyModule 1.0.0 ./target/my-module.jar

# Distribute to all nodes
lightning module distribute MyModule 1.0.0

# List modules on a node
lightning node module list

# Hot-reload modules
lightning node module reload
```

### Snapshots

```bash
# Get current game state
lightning snapshot get
lightning snapshot get -o json
```

### WebSocket

```bash
# Connect to snapshot stream
lightning ws connect snapshot

# Receive messages
lightning ws receive --count 5
lightning ws receive -c 0  # Continuous

# Send messages
lightning ws send '{"command":"move","x":10}'

# Disconnect
lightning ws disconnect
```

---


## Building

```bash
# Build all modules
./mvnw clean install

# Build without tests
./mvnw clean install -DskipTests

# Build specific module
./mvnw compile -pl lightning/rendering/core

# Package webservice for Docker
./mvnw package -pl thunder/engine/provider -DskipTests
```

## Testing

```bash
# Run all tests
./mvnw test

# Run tests for specific module
./mvnw test -pl lightning/rendering/core

# Run specific test class
./mvnw test -pl lightning/rendering/core -Dtest=MatchPanelTest

# Run specific test method
./mvnw test -pl lightning/rendering/core -Dtest=MatchPanelTest#testMethodName
```

## GUI Acceptance Tests (macOS)

GUI tests require special JVM flags on macOS for GLFW/OpenGL support.

```bash
# Run all GUI acceptance tests
DOCKER_HOST=unix:///var/run/docker.sock ./mvnw test \
    -pl lightning/rendering/core-acceptance-test \
    -DskipTests=false

# Run specific acceptance test
DOCKER_HOST=unix:///var/run/docker.sock ./mvnw test \
    -pl lightning/rendering/core-acceptance-test \
    -Dtest=RenderingResourceGuiIT \
    -DskipTests=false

# Run single test method
DOCKER_HOST=unix:///var/run/docker.sock ./mvnw test \
    -pl lightning/rendering/core-acceptance-test \
    -Dtest=RenderingResourceGuiIT#completeWorkflow_uploadSpawnAttachVerify \
    -DskipTests=false
```

## Docker

```bash
# Pull from Docker Hub
docker pull samanthacireland/thunder-engine:0.0.2

# Build Docker image (full build)
docker build -t samanthacireland/thunder-engine:0.0.2 -f Dockerfile .

# Build Docker image (prebuilt jar)
docker build -t samanthacireland/thunder-engine:0.0.2 \
    -f thunder/engine/provider/Dockerfile.prebuilt \
    thunder/engine/provider

# Run Docker container
docker run -p 8080:8080 -e ADMIN_INITIAL_PASSWORD=admin samanthacireland/thunder-engine:0.0.2

# View container logs
docker logs <container-id>

# Stop and remove container
docker stop <container-id> && docker rm <container-id>
```

## Running the GUI Application

```bash
# Run GUI with Maven (requires display)
./mvnw exec:java -pl lightning/rendering/core \
    -Dexec.mainClass=com.lightningfirefly.engine.gui.EngineGuiApplication \
    -Dexec.args="-s http://localhost:8080 -m 1"

# macOS requires additional JVM flag
JAVA_TOOL_OPTIONS="-XstartOnFirstThread" ./mvnw exec:java -pl lightning/rendering/core \
    -Dexec.mainClass=com.lightningfirefly.engine.gui.EngineGuiApplication \
    -Dexec.args="-s http://localhost:8080"
```

## Quarkus Dev Mode

```bash
# Run Quarkus in dev mode (hot reload)
./mvnw quarkus:dev -pl thunder/engine/provider
```

## REST API Testing (Engine - port 8080)

All endpoints require authentication. Get a token first:

```bash
# Login (via Control Plane proxy)
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin"}' | jq -r '.token')

# Health check (no auth)
curl http://localhost:8080/api/health

# List containers
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers

# Create container
curl -X POST http://localhost:8080/api/containers \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"test","moduleNames":["EntityModule","RigidBodyModule"]}'

# Start container
curl -X POST http://localhost:8080/api/containers/1/start \
    -H "Authorization: Bearer $TOKEN"

# Get container tick
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers/1/tick

# Advance tick
curl -X POST http://localhost:8080/api/containers/1/tick \
    -H "Authorization: Bearer $TOKEN"

# Start auto-play (60 FPS)
curl -X POST "http://localhost:8080/api/containers/1/play?intervalMs=16" \
    -H "Authorization: Bearer $TOKEN"

# Stop auto-play
curl -X POST http://localhost:8080/api/containers/1/stop-auto \
    -H "Authorization: Bearer $TOKEN"

# Create match in container
curl -X POST http://localhost:8080/api/containers/1/matches \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"enabledModuleNames":["EntityModule","RigidBodyModule"]}'

# Get snapshot
curl -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/containers/1/matches/1/snapshot

# List available commands
curl -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/containers/1/commands

# Send command
curl -X POST http://localhost:8080/api/containers/1/commands \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"commandName":"spawn","parameters":{"matchId":1,"playerId":1,"entityType":100}}'

# List modules
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/modules

# Upload module JAR
curl -X POST http://localhost:8080/api/modules/upload \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@my-module.jar"

# Reload modules
curl -X POST http://localhost:8080/api/modules/reload \
    -H "Authorization: Bearer $TOKEN"

# Upload resource
curl -X POST http://localhost:8080/api/containers/1/resources \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@texture.png" \
    -F "resourceName=player" \
    -F "resourceType=TEXTURE"
```

## REST API Testing (Control Plane - port 8081)

```bash
# Cluster status
curl http://localhost:8081/api/cluster/status

# List nodes
curl http://localhost:8081/api/cluster/nodes

# Deploy match
curl -X POST http://localhost:8081/api/deploy \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"modules":["EntityModule","RigidBodyModule"]}'

# List matches
curl http://localhost:8081/api/matches

# Get autoscaler recommendation
curl http://localhost:8081/api/autoscaler/recommendation

# Upload module to registry
curl -X POST http://localhost:8081/api/modules \
    -F "name=MyModule" \
    -F "version=1.0.0" \
    -F "file=@my-module.jar"

# Distribute module to all nodes
curl -X POST http://localhost:8081/api/modules/MyModule/1.0.0/distribute
```

## Debugging

```bash
# View Maven dependency tree
./mvnw dependency:tree -pl lightning/rendering/core

# Check effective POM
./mvnw help:effective-pom -pl lightning/rendering/core

# Run with debug output
./mvnw test -X -pl lightning/rendering/core -Dtest=SomeTest
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
