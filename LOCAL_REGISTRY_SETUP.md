# Local Docker Registry Setup (Tailscale)

## Overview

Your local Docker registry is configured to be accessible over Tailscale at `$TAILSCALE_IP:5001`.

## What Was Configured

### 1. Environment Variables

Added to `~/.zshrc`:
```bash
export TAILSCALE_IP=$(/Applications/Tailscale.app/Contents/MacOS/Tailscale ip -4 2>/dev/null)
export LOCAL_REGISTRY_PORT=5001
```

**For current terminal session:**
```bash
source ~/.zshrc
```

### 2. Docker Configuration

Updated `~/.docker/daemon.json` to allow insecure registry:
```json
{
  "insecure-registries": ["$TAILSCALE_IP:5001"]
}
```

**Docker Desktop has been restarted** to apply these changes.

### 3. Build Script Commands

Added to `./build.sh`:
- `registry-start` - Start local Docker registry
- `registry-stop` - Stop local Docker registry
- `registry-status` - Check registry status and list images
- `docker-local` - Build and push to local registry

### 4. Maven Profile

Added `-PlocalDockerRegistry` profile to build and push images.

## Usage

### Start the Registry

```bash
./build.sh registry-start
```

### Build and Push an Image

```bash
# Make sure TAILSCALE_IP is set
export TAILSCALE_IP=$(./Applications/Tailscale.app/Contents/MacOS/Tailscale ip -4)

# Build and push
./build.sh docker-local
```

Or with explicit environment variable:
```bash
TAILSCALE_IP=$TAILSCALE_IP ./build.sh docker-local
```

### Check Registry Status

```bash
./build.sh registry-status
```

### Stop the Registry

```bash
./build.sh registry-stop
```

## Access from AWS (via Tailscale)

### 1. Configure Docker on AWS Instance

Add to `/etc/docker/daemon.json`:
```json
{
  "insecure-registries": ["$TAILSCALE_IP:5001"]
}
```

Restart Docker:
```bash
sudo systemctl restart docker
```

### 2. Pull Image

```bash
docker pull $TAILSCALE_IP:5001/thunder-engine:0.0.3-SNAPSHOT
```

### 3. Run with Docker Compose (Recommended)

Clone the repository and use docker-compose with the local registry image:

```bash
cd stormstack

# Option 1: Set environment variable
export THUNDER_ENGINE_IMAGE=$TAILSCALE_IP:5001/thunder-engine:0.0.3-SNAPSHOT
docker compose up -d

# Option 2: Inline environment variable
THUNDER_ENGINE_IMAGE=$TAILSCALE_IP:5001/thunder-engine:0.0.3-SNAPSHOT docker compose up -d

# Option 3: Create .env file from example and edit
cp .env.example .env
# Edit .env and set: THUNDER_ENGINE_IMAGE=$TAILSCALE_IP:5001/thunder-engine:0.0.3-SNAPSHOT
docker compose up -d
```

### 4. Or Run Container Manually

```bash
docker run -d \
  --name thunder-engine \
  -p 8080:8080 \
  -e QUARKUS_MONGODB_CONNECTION_STRING=mongodb://localhost:27017 \
  $TAILSCALE_IP:5001/thunder-engine:0.0.3-SNAPSHOT
```

## Troubleshooting

### Verify Insecure Registry Configuration

```bash
docker info | grep -A 5 "Insecure Registries"
```

Should show:
```
Insecure Registries:
  $TAILSCALE_IP:5001
  ...
```

### Check if Registry is Running

```bash
docker ps | grep local-docker-registry
```

### List Images in Registry

```bash
curl http://$TAILSCALE_IP:5001/v2/_catalog
```

### Check Tailscale IP

```bash
/Applications/Tailscale.app/Contents/MacOS/Tailscale ip -4
```

## Files Modified

- `docker-compose.registry.yml` - Registry configuration
- `docker-compose.yml` - Added `THUNDER_ENGINE_IMAGE` environment variable support
- `.env.example` - Environment variable documentation
- `.gitignore` - Added `.env` to prevent committing secrets
- `build.sh` - Added registry commands
- `thunder/engine/provider/pom.xml` - Added `localDockerRegistry` profile with multi-platform support
- `~/.docker/daemon.json` - Added insecure registry
- `~/.zshrc` - Added environment variables
