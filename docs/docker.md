# Docker & Deployment

## Quick Start

The full StormStack platform runs as multiple Docker services:

```bash
# Clone and configure
git clone https://github.com/ireland-samantha/lightning-engine.git
cd lightning-engine

# Copy environment template and configure secrets
cp .env.example .env
# Edit .env to set AUTH_JWT_SECRET and ADMIN_INITIAL_PASSWORD

# Start the platform
docker compose up -d
```

This starts:

| Service | Port | Image | Purpose |
|---------|------|-------|---------|
| **mongodb** | 27017 | `mongo:6` | Shared database |
| **redis** | 6379 | `redis:7` | Control plane state |
| **auth** | 8082 | `samanthacireland/thunder-auth` | Authentication service |
| **control-plane** | 8081 | `samanthacireland/thunder-control-plane` | Cluster orchestration |
| **backend** | 8080 | `samanthacireland/thunder-engine` | Game server |

## Multi-Node Cluster

For a multi-node cluster with multiple engine instances:

```bash
docker compose --profile cluster up -d
```

This adds additional engine nodes (node-2, node-3) that register with the control plane.

## Build from Source

### Using build.sh (Recommended)

```bash
# Build all modules
./build.sh build

# Build Docker images
./build.sh docker

# Full pipeline (build + test + Docker)
./build.sh all
```

### Manual Maven Build

```bash
# Build all modules
mvn clean install -DskipTests

# Build Docker images with jib
mvn package -Pdocker -DskipTests
```

## Docker Images

| Image | Base | Size | Purpose |
|-------|------|------|---------|
| `samanthacireland/thunder-engine` | Eclipse Temurin 25 | ~300MB | Game server |
| `samanthacireland/thunder-auth` | Eclipse Temurin 25 | ~200MB | Auth service |
| `samanthacireland/thunder-control-plane` | Eclipse Temurin 25 | ~200MB | Control plane |

## Environment Variables

### Required (Production)

| Variable | Description |
|----------|-------------|
| `AUTH_JWT_SECRET` | JWT signing secret (32+ characters). **Never deploy without this.** |
| `ADMIN_INITIAL_PASSWORD` | Initial admin password |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins (e.g., `https://yourdomain.com`) |

### Thunder Auth Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_ISSUER` | `https://stormstack.io` | JWT issuer claim |
| `SESSION_EXPIRY_HOURS` | `24` | Session token lifetime |
| `BCRYPT_COST` | `12` | Password hashing cost factor |
| `OAUTH2_SERVICE_TOKEN_LIFETIME` | `900` | Service token lifetime (seconds) |
| `OAUTH2_REFRESH_TOKEN_LIFETIME` | `604800` | Refresh token lifetime (seconds) |
| `RATE_LIMIT_ENABLED` | `true` | Enable login rate limiting |
| `RATE_LIMIT_MAX_ATTEMPTS` | `10` | Max login attempts per window |
| `OAUTH2_CONTROL_PLANE_SECRET` | - | Control plane OAuth2 client secret |
| `OAUTH2_GAME_SERVER_SECRET` | - | Game server OAuth2 client secret |

### Thunder Engine Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_ENABLED` | `true` | Enable authentication |
| `AUTH_SERVICE_URL` | `http://auth:8082` | Auth service URL |
| `SNAPSHOT_PERSISTENCE_ENABLED` | `false` | Enable MongoDB snapshot persistence |
| `SNAPSHOT_PERSISTENCE_DATABASE` | `stormstack` | Snapshot database name |
| `SNAPSHOT_PERSISTENCE_TICK_INTERVAL` | `60` | Ticks between persisted snapshots |
| `CONTROL_PLANE_NODE_ID` | `node-1` | Node identifier for control plane |
| `CONTROL_PLANE_ADVERTISE_ADDRESS` | - | URL for control plane registration |
| `MAX_CONTAINERS` | `100` | Max containers per node |
| `HEARTBEAT_INTERVAL_SECONDS` | `10` | Control plane heartbeat interval |

### Thunder Control Plane Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOSTS` | `redis://localhost:6379` | Redis connection string |
| `NODE_TTL_SECONDS` | `30` | Node registration TTL |
| `CONTROL_PLANE_TOKEN` | - | Node authentication token |
| `CONTROL_PLANE_REQUIRE_AUTH` | `true` | Require node authentication |
| `JWT_AUTH_ENABLED` | `true` | Enable JWT auth for admin endpoints |
| `AUTOSCALER_ENABLED` | `true` | Enable autoscaling |
| `AUTOSCALER_SCALE_UP_THRESHOLD` | `0.8` | Scale up at 80% saturation |
| `AUTOSCALER_SCALE_DOWN_THRESHOLD` | `0.3` | Scale down at 30% saturation |
| `AUTOSCALER_MIN_NODES` | `1` | Minimum node count |
| `AUTOSCALER_MAX_NODES` | `100` | Maximum node count |
| `AUTOSCALER_COOLDOWN_SECONDS` | `300` | Cooldown between scaling actions |

### JVM Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xms128m -Xmx256m` | JVM memory settings (auth/control-plane) |
| `JAVA_OPTS` | `-Xms1g -Xmx1536m` | JVM memory settings (engine) |
| `QUARKUS_LOG_LEVEL` | `INFO` | Log verbosity |

## Docker Compose Configuration

### Single Node (Development)

```yaml
version: '3.8'
services:
  mongodb:
    image: mongo:6
    ports:
      - "27017:27017"
    volumes:
      - mongodb-data:/data/db

  redis:
    image: redis:7
    ports:
      - "6379:6379"

  auth:
    image: samanthacireland/thunder-auth:0.0.3-SNAPSHOT
    ports:
      - "8082:8082"
    environment:
      - MONGODB_URI=mongodb://mongodb:27017
      - JWT_SECRET=${AUTH_JWT_SECRET}
      - ADMIN_INITIAL_PASSWORD=${ADMIN_INITIAL_PASSWORD}
    depends_on:
      - mongodb

  control-plane:
    image: samanthacireland/thunder-control-plane:0.0.3-SNAPSHOT
    ports:
      - "8081:8081"
    environment:
      - REDIS_HOSTS=redis://redis:6379
      - AUTH_SERVICE_URL=http://auth:8082
      - CONTROL_PLANE_TOKEN=${CONTROL_PLANE_TOKEN:-dev-token}
    depends_on:
      - redis
      - auth

  backend:
    image: samanthacireland/thunder-engine:0.0.3-SNAPSHOT
    ports:
      - "8080:8080"
    environment:
      - MONGODB_URI=mongodb://mongodb:27017
      - AUTH_SERVICE_URL=http://auth:8082
      - CONTROL_PLANE_URL=http://control-plane:8081
      - CONTROL_PLANE_TOKEN=${CONTROL_PLANE_TOKEN:-dev-token}
      - CONTROL_PLANE_NODE_ID=node-1
      - CONTROL_PLANE_ADVERTISE_ADDRESS=http://backend:8080
    depends_on:
      - mongodb
      - auth
      - control-plane

volumes:
  mongodb-data:
```

### Multi-Node Cluster

Add the `cluster` profile to start multiple engine nodes:

```yaml
  node-2:
    profiles: [cluster]
    image: samanthacireland/thunder-engine:0.0.3-SNAPSHOT
    environment:
      - CONTROL_PLANE_NODE_ID=node-2
      - CONTROL_PLANE_ADVERTISE_ADDRESS=http://node-2:8080
      # ... other env vars

  node-3:
    profiles: [cluster]
    image: samanthacireland/thunder-engine:0.0.3-SNAPSHOT
    environment:
      - CONTROL_PLANE_NODE_ID=node-3
      - CONTROL_PLANE_ADVERTISE_ADDRESS=http://node-3:8080
      # ... other env vars
```

## Health Checks

Each service exposes health endpoints:

| Service | Endpoint | Response |
|---------|----------|----------|
| Thunder Engine | `GET /api/health` | `{"status":"UP"}` |
| Thunder Auth | `GET /q/health` | Quarkus health |
| Thunder Control Plane | `GET /q/health` | Quarkus health |

## Accessing the Admin Dashboard

After starting Docker Compose:

1. Open `http://localhost:8080/admin/dashboard`
2. Login with:
   - Username: `admin`
   - Password: Value of `ADMIN_INITIAL_PASSWORD`

## Production Deployment Checklist

- [ ] Set `AUTH_JWT_SECRET` to a long random string (32+ characters)
- [ ] Set `ADMIN_INITIAL_PASSWORD` to a strong password
- [ ] Set `CORS_ALLOWED_ORIGINS` to your specific domain(s) - never use `*`
- [ ] Set `OAUTH2_CONTROL_PLANE_SECRET` for service-to-service auth
- [ ] Configure external MongoDB with authentication
- [ ] Configure external Redis with authentication
- [ ] Set up HTTPS via reverse proxy (nginx, Traefik, etc.)
- [ ] Configure proper JVM memory limits for your workload
- [ ] Set `QUARKUS_PROFILE=prod` for production optimizations
- [ ] Review and configure autoscaler thresholds

## Troubleshooting

### Services not starting

Check logs for each service:

```bash
docker compose logs auth
docker compose logs control-plane
docker compose logs backend
```

### Node not registering with Control Plane

1. Verify `CONTROL_PLANE_URL` is reachable from the engine container
2. Verify `CONTROL_PLANE_TOKEN` matches on both sides
3. Check control-plane logs for registration attempts

### Authentication failures

1. Verify `AUTH_SERVICE_URL` is correct
2. Check auth service logs for errors
3. Verify JWT secrets match across services

### MongoDB connection issues

1. Verify MongoDB is healthy: `docker compose ps`
2. Check connection string: `MONGODB_URI`
3. Verify network connectivity between containers
