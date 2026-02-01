# Performance Benchmarks

This document contains performance benchmarks from the Lightning Engine test suite.

## Physics Performance Test (PhysicsPerformanceIT)

Benchmark environment: Docker container with default resource limits on a 2025 MacBook Air M4

### Entity Spawning via WebSocket

| Metric | Value |
|--------|-------|
| Entities spawned | 10,000 |
| WebSocket send time | 83ms |
| Total spawn time | 2.7 seconds |
| Spawn rate | **3,672 entities/sec** |

### Component Attachment

| Metric | Value |
|--------|-------|
| Rigid bodies attached | 10,000 |
| Sprites attached | 10,000 |
| Total attachments | 20,000 |
| Attachment time | ~20 seconds |
| Attachment rate | **~500 components/sec** |

### Tick Performance

| Metric | Value |
|--------|-------|
| Entities with rigid bodies | 10,000 |
| Ticks executed | 100 |
| Total tick time | 134ms |
| Tick rate | **746 ticks/sec** |
| Time per tick | **1.34ms** |

### Summary

With 10,000 entities (each with RigidBody, Sprite, and GridMap components):

- **Spawn phase**: WebSocket fire-and-forget enables rapid command submission (120k commands/sec send rate)
- **Tick performance**: Physics simulation maintains 746 ticks/sec, well above the 60 tick/sec target
- **Position updates**: 99% of entities (9,900/10,000) have non-zero positions after 100 ticks

## Test Configuration

```java
// WebSocket rate limit increased for performance testing
.withEnv("WEBSOCKET_RATELIMIT_MAX_COMMANDS_PER_SECOND", "50000")
```

## Running the Benchmark

```bash
# Build Docker image
mvn clean install -Pdocker -DskipTests
docker build -t lightning-backend .

# Run performance test
mvn test -pl thunder/engine/tests/api-acceptance \
    -Dtest=PhysicsPerformanceIT \
    -DskipTests=false
```
