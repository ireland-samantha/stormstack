# WebSocket Protocol Specification

Defines the WebSocket protocol for StormStack client-server communication.

**Goal:** Maintain compatibility with Java version where possible.

---

## Connection

### Endpoint

```
wss://{host}/ws/snapshots/{match_id}
```

### Authentication

JWT token must be provided in one of:
1. `Authorization` header: `Bearer <token>`
2. Query parameter: `?token=<token>`

### Connection Flow

```
Client                                  Server
  |                                       |
  |-------- WebSocket Upgrade ----------->|
  |         (with JWT token)              |
  |                                       |
  |<------- Connection Accepted ----------|
  |         (or 401/403 if auth fails)    |
  |                                       |
  |-------- Subscribe { match_id } ------>|
  |                                       |
  |<------- Snapshot (initial state) -----|
  |                                       |
  |<------- Delta (ongoing updates) ------|
  |<------- Delta ----------------------->|
  |                                       |
  |-------- Command { ... } ------------->|
  |<------- CommandResult { ... } --------|
  |                                       |
  |-------- Ping { timestamp } ---------->|
  |<------- Pong { timestamp, ... } ------|
  |                                       |
  |-------- Unsubscribe { match_id } ---->|
  |                                       |
  |-------- Close ----------------------->|
```

---

## Message Format

All messages are JSON-encoded with a `type` field for discrimination.

### Client → Server

#### Subscribe

```json
{
  "type": "Subscribe",
  "match_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Unsubscribe

```json
{
  "type": "Unsubscribe",
  "match_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Command

```json
{
  "type": "Command",
  "match_id": "550e8400-e29b-41d4-a716-446655440000",
  "command": {
    "name": "move",
    "entity_id": 12345,
    "payload": {
      "x": 10.5,
      "y": 20.3
    }
  }
}
```

#### Ping

```json
{
  "type": "Ping",
  "timestamp": 1707000000000
}
```

---

### Server → Client

#### Snapshot

Full world state, sent on subscribe and periodically.

```json
{
  "type": "Snapshot",
  "match_id": "550e8400-e29b-41d4-a716-446655440000",
  "snapshot": {
    "tick": 1000,
    "timestamp": 1707000000000,
    "entities": [
      {
        "id": 12345,
        "components": {
          "Position": { "x": 10.5, "y": 20.3 },
          "Velocity": { "x": 1.0, "y": 0.0 },
          "Health": { "current": 80, "max": 100 }
        }
      }
    ]
  }
}
```

#### Delta

Incremental update since last snapshot/delta.

```json
{
  "type": "Delta",
  "match_id": "550e8400-e29b-41d4-a716-446655440000",
  "delta": {
    "from_tick": 1000,
    "to_tick": 1001,
    "spawned": [
      {
        "id": 12346,
        "components": {
          "Position": { "x": 0.0, "y": 0.0 }
        }
      }
    ],
    "despawned": [12344],
    "updated": [
      {
        "entity_id": 12345,
        "component": "Position",
        "data": { "x": 11.5, "y": 20.3 }
      }
    ]
  }
}
```

#### CommandResult

```json
{
  "type": "CommandResult",
  "match_id": "550e8400-e29b-41d4-a716-446655440000",
  "result": {
    "success": true,
    "command_id": "cmd-123",
    "executed_tick": 1001
  }
}
```

Or on error:

```json
{
  "type": "CommandResult",
  "match_id": "550e8400-e29b-41d4-a716-446655440000",
  "result": {
    "success": false,
    "command_id": "cmd-123",
    "error": "Entity not found"
  }
}
```

#### Error

```json
{
  "type": "Error",
  "code": "MATCH_NOT_FOUND",
  "message": "Match 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

#### Pong

```json
{
  "type": "Pong",
  "timestamp": 1707000000000,
  "server_time": 1707000000005
}
```

---

## Error Codes

| Code | Description |
|------|-------------|
| `AUTH_FAILED` | Authentication failed |
| `ACCESS_DENIED` | Not authorized for this match |
| `MATCH_NOT_FOUND` | Match does not exist |
| `ALREADY_SUBSCRIBED` | Already subscribed to this match |
| `NOT_SUBSCRIBED` | Not subscribed to this match |
| `INVALID_COMMAND` | Command format invalid |
| `COMMAND_FAILED` | Command execution failed |
| `RATE_LIMITED` | Too many requests |
| `INTERNAL_ERROR` | Server internal error |

---

## Binary Protocol (Future)

For high-performance scenarios, a binary protocol variant may be added:

```
Header (8 bytes):
  - Magic: 0x5354 (2 bytes, "ST")
  - Version: u8 (1 byte)
  - Flags: u8 (1 byte)
    - 0x01: Compressed
    - 0x02: Delta mode
  - Type: u16 (2 bytes)
  - Length: u16 (2 bytes)

Body:
  - MessagePack or bincode encoded payload
```

---

## Flow Control

### Rate Limits

| Action | Limit |
|--------|-------|
| Subscribe | 10 per minute |
| Command | 60 per second |
| Ping | 1 per second |

### Backpressure

If client falls behind on delta consumption:
1. Server accumulates deltas up to 100
2. Beyond 100, server sends full snapshot instead
3. If still behind, connection may be dropped

---

## Keepalive

- Client should send Ping every 30 seconds
- Server sends Pong with round-trip info
- Connection closed after 90 seconds of no activity

---

## Compression

For large snapshots:
- `Content-Encoding: gzip` in WebSocket header (if supported)
- Or negotiate compression during handshake
- Fallback to uncompressed JSON

---

## Versioning

Protocol version in header:
- Current: `1.0`
- Negotiated during WebSocket upgrade
- Server may support multiple versions

---

## Java Compatibility Notes

### Differences from Java Implementation

| Aspect | Java | Rust |
|--------|------|------|
| Serialization | Jackson | serde_json |
| Compression | Java GZip | flate2 |
| Timestamps | Instant.toEpochMilli() | chrono::Utc.timestamp_millis() |
| UUIDs | java.util.UUID | uuid::Uuid |

### Migration Path

1. Rust server accepts both old and new format
2. Detect client version from User-Agent or negotiate
3. Translate as needed
4. Deprecate old format after migration period

---

## Change Log

| Date | Version | Change |
|------|---------|--------|
| 2026-02-02 | 1.0 | Initial specification (Rust) |

