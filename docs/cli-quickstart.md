# Lightning CLI Quickstart

The Lightning CLI (`lightning`) is the command-line tool for managing StormStack game server clusters. It communicates with the Thunder Control Plane to deploy games, manage matches, and interact with your game in real-time.

## Status

Production-ready for cluster management operations. The CLI supports all Control Plane and Engine operations including match deployment, module management, and real-time WebSocket connections.

## Installation

### Build from Source

```bash
# Requires Go 1.24+
cd lightning/cli
go build -o lightning ./cmd/lightning

# Move to PATH (optional)
sudo mv lightning /usr/local/bin/
```

### Verify Installation

```bash
lightning --help
```

## Configuration

The CLI stores configuration in `~/.lightning.yaml`.

### Set Control Plane URL

```bash
lightning config set control_plane_url http://localhost:8081
```

### Authentication

```bash
# Interactive login
lightning auth login

# Non-interactive
lightning auth login --username admin --password your-password

# Or set an API token directly
lightning auth token lat_your-api-token
```

Check auth status:
```bash
lightning auth status
```

## Quick Start: Deploy Your First Game

This walkthrough assumes you have StormStack running (see main README for Docker Compose setup).

### 1. Check Cluster Health

```bash
# View cluster overview
lightning cluster status

# Output:
# Cluster Status:
#   Total Nodes:     3
#   Healthy Nodes:   3
#   Draining Nodes:  0
#   Total Capacity:  300
#   Used Capacity:   5
#   Saturation:      1.7%
```

### 2. List Available Nodes

```bash
lightning node list

# Output:
# NODE ID      STATUS   ADDRESS                CONTAINERS  MATCHES  CPU    MEMORY
# node-1       HEALTHY  http://backend:8080    2           1        12.3%  512MB/2GB
# node-2       HEALTHY  http://node-2:8080     1           0        5.2%   256MB/1GB
# node-3       HEALTHY  http://node-3:8080     2           0        8.1%   384MB/1GB
```

### 3. Deploy a Game Match

```bash
# Deploy with modules - the Control Plane picks the best node
lightning deploy --modules EntityModule,RigidBodyModule,RenderingModule

# Output:
# Match deployed successfully!
#   Match ID:     node-1-42-1
#   Node:         node-1
#   Container:    42
#   Status:       RUNNING
#
# Endpoints:
#   HTTP:         http://backend:8080/api/containers/42
#   WebSocket:    ws://backend:8080/ws/containers/42/matches/1/snapshots
#   Commands:     ws://backend:8080/ws/containers/42/matches/1/commands
```

### 4. List Matches

```bash
lightning match list

# Output:
# MATCH ID       NODE     STATUS    PLAYERS  MODULES
# node-1-42-1    node-1   RUNNING   0        EntityModule, RigidBodyModule, RenderingModule
```

### 5. Join the Match

```bash
lightning match join node-1-42-1 --player-name "Alice" --player-id "player-001"

# Output:
# Joined match node-1-42-1 as Alice (player-001)
# Match token stored. Expires: 2026-01-31 23:30:00
# Command WebSocket: ws://backend:8080/ws/containers/42/matches/1/commands?token=...
# Snapshot WebSocket: ws://backend:8080/ws/containers/42/matches/1/snapshots?token=...
#
# Use 'lightning ws connect command' or 'lightning ws connect snapshot' to connect.
```

### 6. Set Node Context for Commands

```bash
# Set context to the node hosting your match
lightning node context set node-1 --container-id 42 --match-id node-1-42-1

# Or resolve from match ID directly
lightning node context match node-1-42-1
```

### 7. Control the Simulation

```bash
# Advance one tick
lightning node tick advance

# Advance 10 ticks
lightning node tick advance -n 10

# Start auto-play at 60 FPS (16ms interval)
lightning node simulation play --interval-ms 16

# Stop auto-play
lightning node simulation stop

# Get current tick
lightning node tick get
```

### 8. Send Game Commands

```bash
# List available commands
lightning command list

# Spawn an entity
lightning command send spawn '{"matchId":1,"playerId":1,"entityType":100}'

# Move an entity
lightning command send setPosition '{"entityId":1,"x":10.0,"y":20.0,"z":0}'
```

### 9. Get Game State

```bash
# Get current snapshot
lightning snapshot get

# Output as JSON for programmatic use
lightning snapshot get -o json
```

### 10. Cleanup

```bash
# Finish match (stops accepting players)
lightning match finish node-1-42-1

# Delete match completely
lightning match delete node-1-42-1

# Or undeploy (stops container and deletes match)
lightning undeploy node-1-42-1
```

## Module Management

The Control Plane has a centralized module registry. Upload modules once, distribute everywhere.

### List Registered Modules

```bash
lightning module list

# Output:
# NAME               VERSION   DESCRIPTION
# EntityModule       1.0.0     Core entity management
# RigidBodyModule    1.0.0     Physics simulation
# RenderingModule    1.0.0     Sprite rendering
```

### Upload a New Module

```bash
lightning module upload MyGameModule 1.0.0 ./target/my-game-module.jar \
    --description "Custom game logic"
```

### Distribute to All Nodes

```bash
lightning module distribute MyGameModule 1.0.0

# Output:
# Module MyGameModule@1.0.0 distributed to 3 nodes
```

### Hot-Reload on a Node

After uploading new JARs to a node, trigger a reload:

```bash
# Set node context first
lightning node context set node-1

# Reload modules from disk
lightning node module reload

# Output:
# Reloaded 5 modules
#   - EntityModule
#   - RigidBodyModule
#   - RenderingModule
#   - GridMapModule
#   - MyGameModule
```

### List Modules on a Node

```bash
lightning node module list

# Output:
# NAME              FLAG COMPONENT       ENABLED MATCHES
# EntityModule      ENTITY              3
# RigidBodyModule   RIGID_BODY          2
# RenderingModule   SPRITE              3
```

## WebSocket Streaming

Connect to game WebSocket endpoints for real-time interaction.

### Connect to Snapshot Stream

```bash
# First join a match to get credentials
lightning match join node-1-42-1 --player-name "Observer" --player-id "obs-001"

# Connect to snapshot WebSocket
lightning ws connect snapshot

# Receive messages
lightning ws receive --count 5

# Continuous streaming
lightning ws receive -c 0  # Ctrl+C to stop
```

### Connect to Command Stream

```bash
lightning ws connect command

# Send a command
lightning ws send '{"command":"move","params":{"entityId":1,"x":10,"y":20}}'

# Disconnect
lightning ws disconnect
```

## Proxy Mode

When nodes are on Docker-internal networks (not directly reachable from your machine), use proxy mode to route requests through the Control Plane.

```bash
# Enable proxy mode
lightning node proxy enable

# All node commands now route through Control Plane:
# CLI -> Control Plane -> Node

# Check proxy status
lightning node proxy status

# Disable proxy mode (direct connections)
lightning node proxy disable
```

## Output Formats

All commands support multiple output formats:

```bash
# Table format (default)
lightning node list

# JSON format
lightning node list -o json

# YAML format
lightning node list -o yaml

# Quiet mode (IDs only)
lightning node list -o quiet
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `LIGHTNING_CONTROL_PLANE_URL` | Control Plane URL (default: `http://localhost:8081`) |
| `LIGHTNING_AUTH_TOKEN` | Authentication token |
| `LIGHTNING_OUTPUT_FORMAT` | Default output format (`table`, `json`, `yaml`, `quiet`) |

## Command Reference

| Command | Description |
|---------|-------------|
| `lightning auth login` | Authenticate with username/password |
| `lightning auth token` | Set API token directly |
| `lightning auth status` | Show auth status |
| `lightning auth logout` | Remove saved auth |
| `lightning cluster status` | Cluster health overview |
| `lightning cluster nodes` | List all cluster nodes |
| `lightning node list` | List nodes |
| `lightning node context set` | Set node context |
| `lightning node context match` | Set match context |
| `lightning node context show` | Show current context |
| `lightning node tick advance` | Advance simulation ticks |
| `lightning node tick get` | Get current tick |
| `lightning node simulation play` | Start auto-advancing |
| `lightning node simulation stop` | Stop auto-advancing |
| `lightning node module list` | List modules on node |
| `lightning node module reload` | Hot-reload modules |
| `lightning node metrics get` | Get node metrics |
| `lightning node metrics container` | Get container metrics |
| `lightning node proxy enable` | Enable proxy mode |
| `lightning node proxy disable` | Disable proxy mode |
| `lightning deploy` | Deploy a match to the cluster |
| `lightning undeploy` | Remove a deployed match |
| `lightning deploy status` | Get deployment status |
| `lightning match list` | List all matches |
| `lightning match get` | Get match details |
| `lightning match join` | Join a match |
| `lightning match finish` | Mark match as finished |
| `lightning match delete` | Delete a match |
| `lightning module list` | List modules in registry |
| `lightning module versions` | List module versions |
| `lightning module upload` | Upload a module |
| `lightning module distribute` | Distribute to all nodes |
| `lightning command send` | Send a game command |
| `lightning command send-bulk` | Send commands from file |
| `lightning command list` | List available commands |
| `lightning snapshot get` | Get game state snapshot |
| `lightning ws connect` | Connect to WebSocket |
| `lightning ws send` | Send WebSocket message |
| `lightning ws receive` | Receive WebSocket messages |
| `lightning ws disconnect` | Disconnect WebSocket |
| `lightning ws status` | Show WebSocket status |
| `lightning config set` | Set configuration |
| `lightning version` | Show CLI version |

## Typical Development Workflow

```bash
# 1. Start the cluster
docker compose up -d

# 2. Login
lightning auth login --username admin --password admin

# 3. Check cluster status
lightning cluster status

# 4. Deploy your game
lightning deploy --modules EntityModule,RigidBodyModule,MyGameModule

# 5. Set context
lightning node context match node-1-1-1

# 6. Start simulation
lightning node simulation play --interval-ms 16

# 7. Develop and test...
lightning command send spawn '{"matchId":1,"playerId":1,"entityType":1}'
lightning snapshot get

# 8. Make module changes, rebuild JAR, then reload
lightning node module reload

# 9. Cleanup when done
lightning undeploy node-1-1-1
```

## Troubleshooting

### "No context set" errors

Set a node context before running node-specific commands:
```bash
lightning node context set <node-id>
```

### Connection refused

1. Verify Control Plane is running: `curl http://localhost:8081/q/health`
2. Check configured URL: `lightning config get control_plane_url`
3. Check auth status: `lightning auth status`

### Proxy mode issues

If nodes are on Docker networks, enable proxy mode:
```bash
lightning node proxy enable
```

### Authentication failures

Re-authenticate:
```bash
lightning auth logout
lightning auth login
```
