==============
WebSocket API
==============

StormStack provides WebSocket endpoints for real-time streaming of ECS snapshots
and high-performance command submission.

.. contents:: Table of Contents
   :local:
   :depth: 2

Overview
========

WebSocket connections provide:

* **Real-time snapshot streaming** - Receive ECS state updates as they happen
* **Delta compression** - Only receive changed entities for bandwidth efficiency
* **Binary command protocol** - High-throughput command submission via Protocol Buffers

All WebSocket connections require JWT authentication passed as a query parameter.

Snapshot Streaming
==================

Full Snapshots
--------------

**Endpoint:**

.. code-block:: text

   ws://host:8080/ws/containers/{containerId}/snapshots/{matchId}?token={jwt}

**Parameters:**

* ``containerId`` - The container ID
* ``matchId`` - The match ID to subscribe to
* ``token`` - JWT authentication token

**Connection Flow:**

1. Client connects with valid JWT
2. Server sends initial full snapshot
3. Server sends full snapshot after each tick

**Message Format (JSON):**

.. code-block:: json

   {
     "type": "snapshot",
     "tick": 15421,
     "matchId": 1,
     "modules": {
       "EntityModule": {
         "entities": [
           {
             "id": 1,
             "components": {
               "Position": {"x": 100.0, "y": 50.0},
               "Velocity": {"dx": 1.0, "dy": 0.0}
             }
           }
         ]
       }
     }
   }

Delta Snapshots
---------------

**Endpoint:**

.. code-block:: text

   ws://host:8080/ws/containers/{containerId}/snapshots/delta/{matchId}?token={jwt}

Delta snapshots only include entities that changed since the last snapshot,
significantly reducing bandwidth for large game states.

**Connection Flow:**

1. Client connects with valid JWT
2. Server sends initial full snapshot (baseline)
3. Server sends delta snapshots after each tick

**Delta Message Format:**

.. code-block:: json

   {
     "type": "delta",
     "tick": 15422,
     "baseTick": 15421,
     "matchId": 1,
     "added": [
       {
         "id": 5,
         "components": {
           "Position": {"x": 0.0, "y": 0.0}
         }
       }
     ],
     "modified": [
       {
         "id": 1,
         "components": {
           "Position": {"x": 101.0, "y": 50.0}
         }
       }
     ],
     "removed": [3, 4]
   }

**Delta Fields:**

* ``added`` - Entities created since baseTick
* ``modified`` - Entities with changed component values
* ``removed`` - Entity IDs that were deleted

Handling Deltas
^^^^^^^^^^^^^^^

Clients should maintain local state and apply deltas:

.. code-block:: javascript

   let gameState = {};

   socket.onmessage = (event) => {
     const msg = JSON.parse(event.data);

     if (msg.type === 'snapshot') {
       // Full reset
       gameState = msg.modules;
     } else if (msg.type === 'delta') {
       // Apply changes
       for (const entity of msg.added) {
         gameState.entities[entity.id] = entity;
       }
       for (const entity of msg.modified) {
         Object.assign(gameState.entities[entity.id], entity);
       }
       for (const id of msg.removed) {
         delete gameState.entities[id];
       }
     }
   };

Command WebSocket
=================

For high-throughput command submission, use the binary WebSocket endpoint
with Protocol Buffer messages.

**Endpoint:**

.. code-block:: text

   ws://host:8080/containers/{containerId}/commands?token={jwt}

Protocol Buffer Definitions
---------------------------

The Protocol Buffer schema is located at:
``thunder/engine/adapters/api-proto/src/main/proto/command.proto``

**CommandBatch Message:**

.. code-block:: protobuf

   message CommandBatch {
     int64 match_id = 1;
     repeated Command commands = 2;
   }

   message Command {
     string name = 1;
     map<string, Value> params = 2;
   }

   message Value {
     oneof value {
       double number_value = 1;
       string string_value = 2;
       bool bool_value = 3;
     }
   }

**CommandResponse Message:**

.. code-block:: protobuf

   message CommandResponse {
     int64 tick = 1;
     int32 queued_count = 2;
     repeated Error errors = 3;
   }

   message Error {
     string command_name = 1;
     string message = 2;
   }

Usage Example
-------------

**JavaScript (with protobufjs):**

.. code-block:: javascript

   const protobuf = require('protobufjs');

   // Load the proto file
   const root = await protobuf.load('command.proto');
   const CommandBatch = root.lookupType('CommandBatch');
   const CommandResponse = root.lookupType('CommandResponse');

   // Create command batch
   const batch = CommandBatch.create({
     matchId: 1,
     commands: [
       {
         name: 'SpawnEntity',
         params: {
           x: { numberValue: 100.0 },
           y: { numberValue: 50.0 },
           type: { stringValue: 'player' }
         }
       }
     ]
   });

   // Send binary message
   const buffer = CommandBatch.encode(batch).finish();
   socket.send(buffer);

   // Receive response
   socket.onmessage = (event) => {
     const response = CommandResponse.decode(new Uint8Array(event.data));
     console.log(`Queued ${response.queuedCount} commands for tick ${response.tick}`);
   };

**Java (with generated classes):**

.. code-block:: java

   CommandBatch batch = CommandBatch.newBuilder()
       .setMatchId(1L)
       .addCommands(Command.newBuilder()
           .setName("SpawnEntity")
           .putParams("x", Value.newBuilder().setNumberValue(100.0).build())
           .putParams("y", Value.newBuilder().setNumberValue(50.0).build())
           .build())
       .build();

   session.getBasicRemote().sendBinary(ByteBuffer.wrap(batch.toByteArray()));

Connection Management
=====================

Heartbeats
----------

The server sends ping frames every 30 seconds. Clients must respond with pong
frames to keep the connection alive.

If no pong is received within 60 seconds, the server closes the connection.

Reconnection
------------

On disconnect, clients should:

1. Wait with exponential backoff (1s, 2s, 4s, 8s, max 30s)
2. Reconnect to the same endpoint
3. For delta streaming, request a full snapshot to re-establish baseline

Error Messages
--------------

WebSocket error messages are sent as JSON:

.. code-block:: json

   {
     "type": "error",
     "code": "MATCH_NOT_FOUND",
     "message": "Match 999 does not exist in container 1"
   }

**Error Codes:**

* ``AUTHENTICATION_FAILED`` - Invalid or expired JWT token
* ``MATCH_NOT_FOUND`` - Match ID does not exist
* ``CONTAINER_NOT_RUNNING`` - Container is paused or stopped
* ``RATE_LIMITED`` - Too many commands per second

Connection States
-----------------

.. code-block:: text

   CONNECTING -> AUTHENTICATING -> ACTIVE -> CLOSED
                      |                |
                      v                v
                   REJECTED        ERRORED

Performance Considerations
==========================

Binary vs JSON
--------------

* **JSON snapshots**: Easier to debug, suitable for development
* **Binary Protocol Buffers**: 5-10x smaller, recommended for production command streams

Recommended patterns:

* Use JSON snapshot streaming for visualization/debugging
* Use delta streaming for game clients (reduces bandwidth 80-90%)
* Use binary command WebSocket for game clients sending frequent input

Concurrency
-----------

Each WebSocket connection:

* Receives snapshots on its own thread
* Commands are thread-safe and can be sent from any thread
* Multiple connections to the same match are supported

Bandwidth Optimization
----------------------

For bandwidth-constrained environments:

1. Use delta snapshots instead of full snapshots
2. Subscribe only to modules you need (future feature)
3. Use binary protocol for commands
4. Consider compression at the transport layer (WSS with TLS compression)
