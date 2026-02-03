===================
WebSocket Streaming
===================

Thunder Engine provides real-time game state streaming via WebSocket connections.
This page covers the streaming protocols, message formats, and client integration.

.. contents:: Contents
   :local:
   :depth: 2

Overview
========

WebSocket streaming enables clients to receive real-time updates of game state
without polling. Thunder Engine offers:

- **Full Snapshots** - Complete ECS state on connect
- **Delta Snapshots** - Only changed entities (bandwidth efficient)
- **Command WebSocket** - Send commands via WebSocket
- **Player-Filtered Streams** - Per-player visibility

WebSocket Endpoints
===================

.. list-table::
   :widths: 40 60
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``/ws/snapshots/{matchId}``
     - Full snapshot stream
   * - ``/ws/delta/{matchId}``
     - Delta-compressed stream
   * - ``/ws/player/{matchId}/{playerId}``
     - Player-filtered snapshot stream
   * - ``/ws/player/{matchId}/{playerId}/delta``
     - Player-filtered delta stream
   * - ``/ws/commands/{matchId}``
     - Bidirectional command channel
   * - ``/ws/simulation``
     - Tick-synchronized stream

Connecting
==========

Basic Connection
----------------

Connect with authentication token::

    const ws = new WebSocket(
      'ws://localhost:8080/ws/snapshots/1',
      ['Authorization', token]  // Subprotocol for auth
    );

    // Or via query parameter (less secure)
    const ws = new WebSocket(
      `ws://localhost:8080/ws/snapshots/1?token=${token}`
    );

Connection Events
-----------------

.. code-block:: javascript

    ws.onopen = () => {
      console.log('Connected to snapshot stream');
    };

    ws.onmessage = (event) => {
      const snapshot = JSON.parse(event.data);
      updateGameState(snapshot);
    };

    ws.onclose = (event) => {
      if (event.code !== 1000) {
        console.error('Connection lost:', event.reason);
        // Implement reconnection logic
      }
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

Full Snapshots
==============

The ``/ws/snapshots/{matchId}`` endpoint streams complete game state each tick.

Message Format
--------------

::

    {
      "type": "snapshot",
      "matchId": 1,
      "tick": 42,
      "timestamp": "2024-02-03T12:00:00.500Z",
      "modules": {
        "chess-core": {
          "entities": [
            {
              "id": 1,
              "components": {
                "Position": { "x": 10.0, "y": 20.0 },
                "Piece": { "type": "KING", "color": "WHITE" }
              }
            },
            {
              "id": 2,
              "components": {
                "Position": { "x": 15.0, "y": 25.0 },
                "Piece": { "type": "QUEEN", "color": "BLACK" }
              }
            }
          ]
        }
      }
    }

**Use Cases:**

- Simple games with small state
- Debugging and development
- Initial state on reconnection

Delta Snapshots
===============

The ``/ws/delta/{matchId}`` endpoint streams only changes since the last tick.
This dramatically reduces bandwidth for games with large state.

Message Format
--------------

::

    {
      "type": "delta",
      "matchId": 1,
      "tick": 43,
      "baseTick": 42,
      "timestamp": "2024-02-03T12:00:00.550Z",
      "changes": {
        "chess-core": {
          "added": [
            {
              "id": 100,
              "components": {
                "Position": { "x": 5.0, "y": 5.0 },
                "Particle": { "type": "EXPLOSION" }
              }
            }
          ],
          "modified": [
            {
              "id": 1,
              "components": {
                "Position": { "x": 11.0, "y": 20.0 }
              }
            }
          ],
          "removed": [2]
        }
      }
    }

Delta Fields
------------

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Field
     - Description
   * - ``added``
     - Entities created this tick (full component data)
   * - ``modified``
     - Entities with changed components (only changed values)
   * - ``removed``
     - Entity IDs deleted this tick

Client-Side Reconstruction
--------------------------

.. code-block:: javascript

    let gameState = {};  // Entity ID -> component data

    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);

      if (msg.type === 'snapshot') {
        // Full state - replace everything
        gameState = {};
        for (const [module, data] of Object.entries(msg.modules)) {
          for (const entity of data.entities) {
            gameState[entity.id] = entity.components;
          }
        }
      } else if (msg.type === 'delta') {
        // Apply changes
        for (const [module, changes] of Object.entries(msg.changes)) {
          // Add new entities
          for (const entity of changes.added || []) {
            gameState[entity.id] = entity.components;
          }

          // Update modified entities
          for (const entity of changes.modified || []) {
            Object.assign(gameState[entity.id], entity.components);
          }

          // Remove deleted entities
          for (const id of changes.removed || []) {
            delete gameState[id];
          }
        }
      }

      render(gameState);
    };

Handling Missed Deltas
----------------------

If the client misses ticks, request a full snapshot resync:

.. code-block:: javascript

    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);

      if (msg.type === 'delta') {
        if (msg.baseTick !== lastTick) {
          // Missed ticks - request resync
          ws.send(JSON.stringify({ action: 'resync' }));
          return;
        }
        lastTick = msg.tick;
      }
    };

The server will respond with a full snapshot.

Player-Filtered Streams
=======================

For games with fog-of-war or per-player visibility, use player-filtered streams.

Connecting as a Player
----------------------

::

    const ws = new WebSocket(
      `ws://localhost:8080/ws/player/1/player_abc123?token=${token}`
    );

Only entities visible to that player are included in the stream.

Visibility Rules
----------------

Visibility is determined by the game module's visibility system. Common patterns:

- **Proximity-based**: Only entities within range
- **Team-based**: Only friendly + detected enemy entities
- **Fog-of-war**: Only explored/visible areas

Command WebSocket
=================

The ``/ws/commands/{matchId}`` endpoint provides bidirectional communication
for sending commands and receiving confirmations.

Sending Commands
----------------

.. code-block:: javascript

    const ws = new WebSocket(
      `ws://localhost:8080/ws/commands/1?token=${token}`
    );

    // Send a command
    ws.send(JSON.stringify({
      action: 'command',
      name: 'MoveUnit',
      playerId: 'player_abc',
      params: {
        unitId: 5,
        targetX: 100.0,
        targetY: 200.0
      }
    }));

Command Responses
-----------------

::

    {
      "type": "command_result",
      "commandId": "cmd_123",
      "success": true,
      "tick": 44
    }

Or on error::

    {
      "type": "command_result",
      "commandId": "cmd_123",
      "success": false,
      "error": "INVALID_UNIT",
      "message": "Unit 5 does not exist"
    }

Binary Protocol
===============

For maximum efficiency, Thunder Engine supports Protocol Buffers over WebSocket.

Enabling Binary Mode
--------------------

Connect with binary subprotocol::

    const ws = new WebSocket(
      'ws://localhost:8080/ws/delta/1',
      ['binary', token]
    );

    ws.binaryType = 'arraybuffer';

    ws.onmessage = (event) => {
      const snapshot = DeltaSnapshot.decode(
        new Uint8Array(event.data)
      );
      // Process protobuf message
    };

Protobuf Definitions
--------------------

The proto files are in ``thunder/engine/adapters/api-proto/``::

    message DeltaSnapshot {
      int64 match_id = 1;
      int64 tick = 2;
      int64 base_tick = 3;
      repeated ModuleChanges changes = 4;
    }

    message ModuleChanges {
      string module_name = 1;
      repeated Entity added = 2;
      repeated EntityPatch modified = 3;
      repeated int64 removed = 4;
    }

Bandwidth Comparison
--------------------

.. list-table::
   :widths: 30 35 35
   :header-rows: 1

   * - Format
     - Typical Size
     - Compression
   * - JSON Full
     - 10 KB/tick
     - ~3 KB gzipped
   * - JSON Delta
     - 500 bytes/tick
     - ~200 bytes gzipped
   * - Binary Delta
     - 100 bytes/tick
     - ~80 bytes

Connection Management
=====================

Rate Limiting
-------------

WebSocket connections are rate limited:

- **Max connections per IP**: 100
- **Max messages per second**: 60
- **Max message size**: 64 KB

Exceeding limits results in connection termination.

Reconnection
------------

Implement exponential backoff for reconnection:

.. code-block:: javascript

    class WebSocketClient {
      constructor(url) {
        this.url = url;
        this.reconnectDelay = 1000;
        this.maxDelay = 30000;
        this.connect();
      }

      connect() {
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
          this.reconnectDelay = 1000;  // Reset on success
        };

        this.ws.onclose = (event) => {
          if (event.code !== 1000) {
            setTimeout(() => this.connect(), this.reconnectDelay);
            this.reconnectDelay = Math.min(
              this.reconnectDelay * 2,
              this.maxDelay
            );
          }
        };
      }
    }

Heartbeats
----------

Send periodic pings to detect dead connections:

.. code-block:: javascript

    setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ action: 'ping' }));
      }
    }, 30000);

Server responds with pong::

    { "type": "pong", "timestamp": "2024-02-03T12:00:00Z" }

Error Handling
==============

WebSocket Error Codes
---------------------

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Code
     - Description
   * - 1000
     - Normal closure
   * - 1002
     - Protocol error (invalid message format)
   * - 1008
     - Policy violation (auth failed, rate limited)
   * - 1011
     - Server error
   * - 4000
     - Match not found
   * - 4001
     - Container stopped
   * - 4002
     - Player not in match

Error Messages
--------------

Errors are sent as JSON messages before close::

    {
      "type": "error",
      "code": 4000,
      "message": "Match 99 not found"
    }

Security
========

Authentication
--------------

All WebSocket connections require a valid JWT token with appropriate scopes:

- ``engine.snapshot.read`` - Required for snapshot streams
- ``engine.command.send`` - Required for command WebSocket

Token can be provided via:

1. **Subprotocol** (recommended)::

    new WebSocket(url, ['Authorization', `Bearer ${token}`])

2. **Query parameter**::

    new WebSocket(`${url}?token=${token}`)

3. **Cookie** (if configured)

TLS
---

Always use ``wss://`` in production to encrypt the WebSocket connection.

Next Steps
==========

- :doc:`/api/websocket-api` - Full WebSocket API reference
- :doc:`/concepts/tick-cycle` - Understanding tick synchronization
- :doc:`/architecture/delta-compression` - Delta algorithm details
- :doc:`/lightning/cli/commands` - CLI WebSocket commands
