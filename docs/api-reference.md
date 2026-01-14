# REST API Reference

Full API documentation: [openapi.yaml](../openapi.yaml)

Postman collection: [postman-collection.json](../postman-collection.json)

## Authentication

All endpoints (except `/api/auth/login`) require JWT authentication. Include the token in the `Authorization` header:

```bash
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/api/...
```

## Endpoints Summary

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| **Authentication** ||||
| POST | `/api/auth/login` | Login and get JWT token | None |
| POST | `/api/auth/refresh` | Refresh token | Any |
| GET | `/api/auth/me` | Get current user info | Any |
| **User Management** ||||
| GET | `/api/auth/users` | List all users | admin |
| POST | `/api/auth/users` | Create user | admin |
| GET | `/api/auth/users/{id}` | Get user by ID | admin |
| PUT | `/api/auth/users/{id}/password` | Update password | admin |
| PUT | `/api/auth/users/{id}/roles` | Update user roles | admin |
| DELETE | `/api/auth/users/{id}` | Delete user | admin |
| **Role Management** ||||
| GET | `/api/auth/roles` | List all roles | Any |
| POST | `/api/auth/roles` | Create role | admin |
| GET | `/api/auth/roles/{id}` | Get role by ID | Any |
| PUT | `/api/auth/roles/{id}/includes` | Update role hierarchy | admin |
| DELETE | `/api/auth/roles/{id}` | Delete role | admin |
| **Containers** ||||
| GET | `/api/containers` | List all containers | Any |
| POST | `/api/containers` | Create container | admin |
| GET | `/api/containers/{id}` | Get container details | Any |
| DELETE | `/api/containers/{id}` | Delete container | admin |
| POST | `/api/containers/{id}/start` | Start container | admin, command_manager |
| POST | `/api/containers/{id}/stop` | Stop container | admin, command_manager |
| POST | `/api/containers/{id}/pause` | Pause container | admin, command_manager |
| POST | `/api/containers/{id}/resume` | Resume container | admin, command_manager |
| GET | `/api/containers/{id}/tick` | Get container tick | Any |
| POST | `/api/containers/{id}/tick` | Advance one tick | admin, command_manager |
| POST | `/api/containers/{id}/play?intervalMs=16` | Start auto-advance | admin, command_manager |
| POST | `/api/containers/{id}/stop-auto` | Stop auto-advance | admin, command_manager |
| **Container Commands** ||||
| GET | `/api/containers/{id}/commands` | List available commands | Any |
| POST | `/api/containers/{id}/commands` | Queue command for next tick | admin, command_manager |
| **Container Matches** ||||
| GET | `/api/containers/{id}/matches` | List matches in container | Any |
| POST | `/api/containers/{id}/matches` | Create match in container | admin, command_manager |
| GET | `/api/containers/{id}/matches/{matchId}` | Get match details | Any |
| DELETE | `/api/containers/{id}/matches/{matchId}` | Delete match | admin |
| GET | `/api/containers/{id}/matches/{matchId}/snapshot` | Get match snapshot | Any |
| **Container Players** ||||
| GET | `/api/containers/{id}/players` | List players in container | Any |
| POST | `/api/containers/{id}/players` | Create player | admin, command_manager |
| GET | `/api/containers/{id}/players/{playerId}` | Get player | Any |
| DELETE | `/api/containers/{id}/players/{playerId}` | Delete player | admin |
| **Container Sessions** ||||
| GET | `/api/containers/{id}/sessions` | List active sessions | Any |
| POST | `/api/containers/{id}/sessions/connect` | Connect player to session | admin, command_manager |
| POST | `/api/containers/{id}/sessions/disconnect` | Disconnect player | admin, command_manager |
| **Container Snapshots** ||||
| GET | `/api/containers/{id}/matches/{matchId}/snapshot` | Get full snapshot | Any |
| GET | `/api/containers/{id}/matches/{matchId}/delta` | Get delta snapshot | Any |
| WS | `/ws/containers/{id}/snapshots/{matchId}` | Stream snapshots | Any |
| WS | `/ws/containers/{id}/snapshots/delta/{matchId}` | Stream delta snapshots | Any |
| **Container History (MongoDB)** ||||
| GET | `/api/containers/{id}/matches/{matchId}/history` | Get match history summary | Any |
| GET | `/api/containers/{id}/matches/{matchId}/history/snapshots` | Get snapshots in range | Any |
| GET | `/api/containers/{id}/matches/{matchId}/history/snapshots/latest` | Get latest snapshots | Any |
| DELETE | `/api/containers/{id}/matches/{matchId}/history` | Delete match history | admin |
| **Container Restore** ||||
| GET | `/api/containers/{id}/matches/{matchId}/restore/available` | Check if restore available | Any |
| POST | `/api/containers/{id}/matches/{matchId}/restore` | Restore match to tick | admin |
| POST | `/api/containers/{id}/restore/all` | Restore all matches | admin |
| GET | `/api/containers/{id}/restore/config` | Get restore configuration | Any |
| **Container Modules** ||||
| GET | `/api/containers/{id}/modules` | List modules in container | Any |
| POST | `/api/containers/{id}/modules/reload` | Reload modules | admin |
| **Modules (Global)** ||||
| GET | `/api/modules` | List installed modules | Any |
| GET | `/api/modules/{name}` | Get module details | Any |
| POST | `/api/modules/upload` | Upload module JAR | admin |
| POST | `/api/modules/reload` | Reload all modules | admin |
| DELETE | `/api/modules/{name}` | Uninstall module | admin |
| **Resources (Global)** ||||
| GET | `/api/resources` | List resources | Any |
| POST | `/api/resources` | Upload texture | admin, command_manager |
| GET | `/api/resources/{id}` | Get resource metadata | Any |
| GET | `/api/resources/{id}/data` | Download resource | Any |
| DELETE | `/api/resources/{id}` | Delete resource | admin |
| **GUI** ||||
| GET | `/api/gui/info` | Get GUI availability and download info | Any |
| GET | `/api/gui/download` | Download GUI as ZIP with auto-config | Any |
| GET | `/api/gui/download/jar` | Download GUI JAR only | Any |
| **AI** ||||
| GET | `/api/ai` | List installed AIs | Any |
| POST | `/api/ai/upload` | Upload AI JAR | admin |
| POST | `/api/ai/reload` | Reload all AIs | admin |

## Authentication

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}'

# Response:
# {
#   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "userId": 1,
#   "username": "admin",
#   "roles": ["admin"],
#   "expiresAt": "2024-01-11T00:00:00Z"
# }
```

### Refresh Token

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Authorization: Bearer $TOKEN"
```

### Get Current User

```bash
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

### Create User (Admin Only)

```bash
curl -X POST http://localhost:8080/api/auth/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username": "newuser", "password": "password123", "roles": ["view_only"]}'
```

### Create Role with Hierarchy (Admin Only)

```bash
curl -X POST http://localhost:8080/api/auth/roles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "game_operator", "description": "Can manage games", "includedRoles": ["command_manager"]}'
```

## Offline Token CLI (issue-api-token)

For automated services, CI/CD pipelines, or long-lived integrations, generate offline tokens using the `issue-api-token` module:

### Build

```bash
./mvnw package -pl issue-api-token -DskipTests
```

### Generate Token

```bash
# Admin token with 24h expiry (default)
java -jar issue-api-token/target/issue-api-token.jar \
  --roles=admin \
  --secret=your-jwt-secret

# Custom user and expiry
java -jar issue-api-token/target/issue-api-token.jar \
  --roles=command_manager,view_only \
  --user=ci-pipeline \
  --expiry=168  # Hours (7 days)

# Output: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### CLI Options

| Option | Default | Description |
|--------|---------|-------------|
| `--roles` | (required) | Comma-separated role names |
| `--secret` | (required) | JWT signing secret (must match backend) |
| `--user` | `api-token-user` | Username claim in token |
| `--expiry` | `24` | Token validity in hours |

### Usage in Scripts

```bash
# Generate and store token
TOKEN=$(java -jar issue-api-token.jar --roles=admin --secret=$JWT_SECRET)

# Use in curl
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/matches
```

## Containers

Execution Containers provide isolated runtime environments with ClassLoader isolation, independent game loops, and container-scoped matches.

### Create Container

```bash
curl -X POST http://localhost:8080/api/containers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "my-game-server"}'

# Response:
# {
#   "id": 1,
#   "name": "my-game-server",
#   "status": "CREATED",
#   "tick": 0,
#   "matchCount": 0
# }
```

### List Containers

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers

# Response: [{"id": 1, "name": "my-game-server", "status": "CREATED"}, ...]
```

### Start/Stop Container

```bash
# Start container
curl -X POST http://localhost:8080/api/containers/1/start \
  -H "Authorization: Bearer $TOKEN"

# Stop container
curl -X POST http://localhost:8080/api/containers/1/stop \
  -H "Authorization: Bearer $TOKEN"

# Pause container (keeps state, stops ticking)
curl -X POST http://localhost:8080/api/containers/1/pause \
  -H "Authorization: Bearer $TOKEN"

# Resume container
curl -X POST http://localhost:8080/api/containers/1/resume \
  -H "Authorization: Bearer $TOKEN"
```

### Tick Control

```bash
# Get current tick
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers/1/tick
# Response: {"tick": 42, "status": "RUNNING"}

# Advance one tick
curl -X POST http://localhost:8080/api/containers/1/tick \
  -H "Authorization: Bearer $TOKEN"
# Response: {"tick": 43}

# Start auto-advance at 60 FPS
curl -X POST "http://localhost:8080/api/containers/1/play?intervalMs=16" \
  -H "Authorization: Bearer $TOKEN"

# Stop auto-advance
curl -X POST http://localhost:8080/api/containers/1/stop-auto \
  -H "Authorization: Bearer $TOKEN"
```

### Container Matches

```bash
# Create match in container
curl -X POST http://localhost:8080/api/containers/1/matches \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "enabledModuleNames": ["EntityModule", "RigidBodyModule", "RenderingModule"]
  }'
# Response: {"id": 1, "containerId": 1, "enabledModuleNames": [...]}

# List matches in container
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers/1/matches

# Delete match
curl -X DELETE http://localhost:8080/api/containers/1/matches/1 \
  -H "Authorization: Bearer $TOKEN"
```

### Container Commands

```bash
# List available commands
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers/1/commands
# Response: [{"name": "spawn", "schema": {...}}, ...]

# Queue command
curl -X POST http://localhost:8080/api/containers/1/commands \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "commandName": "spawn",
    "payload": {
      "matchId": 1,
      "entityType": 1,
      "playerId": 1
    }
  }'
```

### Container Players and Sessions

```bash
# Create player in container
curl -X POST http://localhost:8080/api/containers/1/players \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Player1"}'

# List players
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers/1/players

# Connect player to session
curl -X POST http://localhost:8080/api/containers/1/sessions/connect \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"playerId": 1, "matchId": 1}'

# List active sessions
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/containers/1/sessions
```

### Container Snapshots

```bash
# Get match snapshot
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/containers/1/matches/1/snapshot

# Get delta snapshot
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/containers/1/matches/1/delta?fromTick=100&toTick=105"
```

### WebSocket Streaming (Container-Scoped)

```javascript
// Full snapshots
const ws = new WebSocket('ws://localhost:8080/ws/containers/1/snapshots/1');
ws.onmessage = (event) => {
  const snapshot = JSON.parse(event.data);
  console.log(`Tick ${snapshot.tick}:`, snapshot.data);
};

// Delta snapshots
const wsDelta = new WebSocket('ws://localhost:8080/ws/containers/1/snapshots/delta/1');
wsDelta.onmessage = (event) => {
  const delta = JSON.parse(event.data);
  console.log(`Changes: ${delta.changeCount}`);
};
```

### Match Restore

```bash
# Check if restore is available
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/containers/1/matches/1/restore/available
# Response: {"matchId": 1, "canRestore": true}

# Restore match to specific tick
curl -X POST http://localhost:8080/api/containers/1/matches/1/restore?tick=100 \
  -H "Authorization: Bearer $TOKEN"

# Restore match to latest snapshot (tick=-1)
curl -X POST http://localhost:8080/api/containers/1/matches/1/restore \
  -H "Authorization: Bearer $TOKEN"

# Restore all matches in container
curl -X POST http://localhost:8080/api/containers/1/restore/all \
  -H "Authorization: Bearer $TOKEN"
```

### Match History

```bash
# Get match history summary
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/containers/1/matches/1/history

# Get snapshots in tick range
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/containers/1/matches/1/history/snapshots?fromTick=0&toTick=100&limit=50"

# Get latest snapshots
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/containers/1/matches/1/history/snapshots/latest?limit=10"

# Delete match history
curl -X DELETE http://localhost:8080/api/containers/1/matches/1/history \
  -H "Authorization: Bearer $TOKEN"
```

## Snapshots

### Get Snapshot

```bash
curl http://localhost:8080/api/snapshots/match/1
# Response:
# {
#   "matchId": 1,
#   "tick": 42,
#   "data": {
#     "EntityModule": {
#       "POSITION_X": [100.0, 200.0],
#       "POSITION_Y": [50.0, 75.0]
#     }
#   }
# }
```

### WebSocket Streaming

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/snapshots/1');
ws.onmessage = (event) => {
  const snapshot = JSON.parse(event.data);
  console.log(`Tick ${snapshot.tick}:`, snapshot.data);
};
```

## Delta Snapshots

Delta snapshots provide bandwidth-efficient real-time updates by sending only changes between ticks.

### Get Delta Between Ticks

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/snapshots/delta/1?fromTick=100&toTick=105"

# Response:
# {
#   "matchId": 1,
#   "fromTick": 100,
#   "toTick": 105,
#   "changedComponents": {
#     "RigidBodyModule": {
#       "POSITION_X": {"42": 150.0, "43": 200.0}
#     }
#   },
#   "addedEntities": [44, 45],
#   "removedEntities": [41],
#   "changeCount": 5,
#   "compressionRatio": 0.15
# }
```

### Delta WebSocket Streaming

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/snapshots/delta/1');
ws.onmessage = (event) => {
  const delta = JSON.parse(event.data);
  console.log(`Changes from tick ${delta.fromTick} to ${delta.toTick}:`);
  console.log(`- Changed: ${delta.changeCount}, Added: ${delta.addedEntities.length}`);
};

// Reset to receive full snapshot on next tick
ws.send('reset');
```

## History (MongoDB)

When MongoDB persistence is enabled, snapshots are stored for historical replay.

### Get History Summary

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/history

# Response:
# {
#   "totalSnapshots": 1500,
#   "matchCount": 3,
#   "matchIds": [1, 2, 3],
#   "database": "lightningfirefly",
#   "collection": "snapshots"
# }
```

### Get Snapshots in Range

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/history/1/snapshots?fromTick=0&toTick=100&limit=50"
```

### Get Latest Snapshots

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/history/1/snapshots/latest?limit=10"
```

### Delete Match History (Admin Only)

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/history/1
```

## Modules

### Upload Module

```bash
curl -X POST http://localhost:8080/api/modules/upload \
  -F "file=@target/my-module.jar"
```

### Reload Modules

```bash
curl -X POST http://localhost:8080/api/modules/reload
```

### List Modules

```bash
curl http://localhost:8080/api/modules
# Response: [
#   {"name": "EntityModule", "components": 3, "systems": 0, "commands": 1},
#   ...
# ]
```

## Resources

### Upload Resource

```bash
curl -X POST http://localhost:8080/api/resources \
  -F "file=@my-sprite.png" \
  -F "name=player-sprite"
# Response: {"id": 1, "name": "player-sprite", "size": 1234, "contentType": "image/png"}
```

### Download Resource

```bash
curl http://localhost:8080/api/resources/1/data > sprite.png
```

### List Resources

```bash
curl http://localhost:8080/api/resources
# Response: [{"id": 1, "name": "player-sprite", ...}]
```

## GUI Download

### Get GUI Info

```bash
curl http://localhost:8080/api/gui/info
# Response: {"available": true, "version": "1.0.0", "size": 12345678}
```

### Download GUI Package

```bash
# ZIP with auto-config
curl -O http://localhost:8080/api/gui/download

# JAR only
curl -O http://localhost:8080/api/gui/download/jar
```
