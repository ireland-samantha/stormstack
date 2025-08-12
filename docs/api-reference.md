# REST API Reference

Full API documentation: [openapi.yaml](../openapi.yaml)

Postman collection: [postman-collection.json](../postman-collection.json)

## Endpoints Summary

| Method | Path | Description |
|--------|------|-------------|
| **Simulation** |||
| GET | `/api/simulation/tick` | Get current tick |
| POST | `/api/simulation/tick` | Advance one tick |
| POST | `/api/simulation/play?intervalMs=16` | Start auto-advance |
| POST | `/api/simulation/stop` | Stop auto-advance |
| GET | `/api/simulation/status` | Get playback status |
| **Commands** |||
| GET | `/api/commands` | List available commands with schemas |
| POST | `/api/commands` | Queue command for next tick |
| **Matches** |||
| GET | `/api/matches` | List all matches |
| POST | `/api/matches` | Create match with enabled modules |
| GET | `/api/matches/{id}` | Get match details |
| DELETE | `/api/matches/{id}` | Delete match and entities |
| **Snapshots** |||
| GET | `/api/snapshots` | Get all match snapshots |
| GET | `/api/snapshots/match/{id}` | Get specific match snapshot |
| WS | `/snapshots/{matchId}` | Stream snapshots via WebSocket |
| **Modules** |||
| GET | `/api/modules` | List installed modules |
| GET | `/api/modules/{name}` | Get module details |
| POST | `/api/modules/upload` | Upload module JAR |
| POST | `/api/modules/reload` | Reload all modules |
| DELETE | `/api/modules/{name}` | Uninstall module |
| **Resources** |||
| GET | `/api/resources` | List resources |
| POST | `/api/resources` | Upload texture |
| GET | `/api/resources/{id}` | Get resource metadata |
| GET | `/api/resources/{id}/data` | Download resource |
| DELETE | `/api/resources/{id}` | Delete resource |
| **GUI** |||
| GET | `/api/gui/info` | Get GUI availability and download info |
| GET | `/api/gui/download` | Download GUI as ZIP with auto-config |
| GET | `/api/gui/download/jar` | Download GUI JAR only |
| **Game Masters** |||
| GET | `/api/gamemasters` | List installed game masters |
| POST | `/api/gamemasters/upload` | Upload game master JAR |
| POST | `/api/gamemasters/reload` | Reload all game masters |

## Simulation

### Get Current Tick

```bash
curl http://localhost:8080/api/simulation/tick
# Response: {"tick": 42}
```

### Advance Tick

```bash
curl -X POST http://localhost:8080/api/simulation/tick
# Response: {"tick": 43}
```

### Auto-Play

```bash
# Start at 60 FPS (16ms interval)
curl -X POST "http://localhost:8080/api/simulation/play?intervalMs=16"

# Stop
curl -X POST http://localhost:8080/api/simulation/stop

# Check status
curl http://localhost:8080/api/simulation/status
# Response: {"playing": true, "intervalMs": 16}
```

## Commands

### List Available Commands

```bash
curl http://localhost:8080/api/commands
# Response: [
#   {"name": "spawn", "schema": {"entityType": "Long", "playerId": "Long"}},
#   {"name": "damage", "schema": {"entityId": "Long", "amount": "Float"}}
# ]
```

### Queue Command

```bash
curl -X POST http://localhost:8080/api/commands \
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

## Matches

### Create Match

```bash
curl -X POST http://localhost:8080/api/matches \
  -H "Content-Type: application/json" \
  -d '{
    "enabledModuleNames": ["EntityModule", "RigidBodyModule", "RenderingModule"],
    "enabledGameMasters": ["AIGameMaster"]
  }'
# Response: {"id": 1, "enabledModuleNames": [...], ...}
```

### List Matches

```bash
curl http://localhost:8080/api/matches
# Response: [{"id": 1, ...}, {"id": 2, ...}]
```

### Delete Match

```bash
curl -X DELETE http://localhost:8080/api/matches/1
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
const ws = new WebSocket('ws://localhost:8080/snapshots/1');
ws.onmessage = (event) => {
  const snapshot = JSON.parse(event.data);
  console.log(`Tick ${snapshot.tick}:`, snapshot.data);
};
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
