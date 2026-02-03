=====================
Thunder Engine
=====================

Thunder Engine is the core game execution service, providing ECS-based game state
management, tick-based simulation, and real-time state streaming.

.. toctree::
   :maxdepth: 1
   :hidden:

   commands
   websocket

.. contents:: Contents
   :local:
   :depth: 2

Overview
========

Thunder Engine provides:

- **Entity-Component-System (ECS)** - Efficient game state management
- **Execution Containers** - Isolated runtime environments for matches
- **Tick-Based Simulation** - Deterministic game loops
- **Command Queue** - Server-side action processing
- **Snapshot Streaming** - Real-time state updates via WebSocket

Architecture
------------

.. code-block:: text

    +------------------------------------------+
    |            Thunder Engine                |
    |  +------------------------------------+  |
    |  |       Execution Container          |  |
    |  |  +-----------+  +-----------+     |  |
    |  |  |  Match 1  |  |  Match 2  |     |  |
    |  |  |  (ECS)    |  |  (ECS)    |     |  |
    |  |  +-----------+  +-----------+     |  |
    |  |                                    |  |
    |  |  Modules: [chess-core, physics]   |  |
    |  +------------------------------------+  |
    |                                          |
    |  REST API      WebSocket                |
    |  /api/...      /ws/...                  |
    +------------------------------------------+

Each **Execution Container** provides complete isolation:

- Separate ClassLoader for module code
- Independent ECS store per match
- Own tick rate and game loop
- Isolated command queue

Quick Start
===========

Creating a Container
--------------------

::

    POST /api/containers
    Authorization: Bearer <token>
    Content-Type: application/json

    {
      "name": "chess-server-1"
    }

Response::

    {
      "id": "ctr_abc123",
      "name": "chess-server-1",
      "status": "RUNNING",
      "tickRate": 20,
      "matchCount": 0,
      "createdAt": "2024-02-03T12:00:00Z"
    }

Installing a Module
-------------------

::

    POST /api/containers/{containerId}/modules
    Content-Type: application/octet-stream

    <JAR file binary>

Creating a Match
----------------

::

    POST /api/containers/{containerId}/matches
    Content-Type: application/json

    {
      "maxPlayers": 2
    }

Response::

    {
      "matchId": 1,
      "containerId": "ctr_abc123",
      "status": "ACTIVE",
      "currentPlayers": 0,
      "maxPlayers": 2,
      "tick": 0
    }

Streaming State
---------------

Connect to WebSocket for real-time updates::

    ws://localhost:8080/ws/snapshots/{matchId}

See :doc:`websocket` for streaming documentation.

Documentation
=============

.. toctree::
   :maxdepth: 2

   websocket

For ECS concepts and architecture, see:

- :doc:`/concepts/ecs-basics` - ECS for game developers
- :doc:`/concepts/tick-cycle` - Tick lifecycle
- :doc:`/concepts/containers` - Container isolation
- :doc:`/architecture/ecs-internals` - ECS implementation details

REST API Reference
==================

Container Operations
--------------------

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``GET /api/containers``
     - List all containers
   * - ``POST /api/containers``
     - Create a container
   * - ``GET /api/containers/{id}``
     - Get container details
   * - ``DELETE /api/containers/{id}``
     - Delete a container
   * - ``POST /api/containers/{id}/modules``
     - Install a module
   * - ``GET /api/containers/{id}/modules``
     - List installed modules

Match Operations
----------------

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``GET /api/containers/{id}/matches``
     - List matches in container
   * - ``POST /api/containers/{id}/matches``
     - Create a match
   * - ``GET /api/containers/{id}/matches/{matchId}``
     - Get match details
   * - ``DELETE /api/containers/{id}/matches/{matchId}``
     - Finish/delete a match
   * - ``POST /api/containers/{id}/matches/{matchId}/join``
     - Join a match as player

Simulation Control
------------------

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``POST /api/containers/{id}/ticks/advance``
     - Advance one tick (manual mode)
   * - ``POST /api/containers/{id}/ticks/start``
     - Start auto-advance
   * - ``POST /api/containers/{id}/ticks/stop``
     - Stop auto-advance
   * - ``GET /api/containers/{id}/ticks``
     - Get tick status

Command Operations
------------------

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``POST /api/containers/{id}/commands``
     - Send a command
   * - ``GET /api/containers/{id}/commands``
     - List available commands
   * - ``GET /api/containers/{id}/commands/{name}/schema``
     - Get command schema

Snapshot Operations
-------------------

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``GET /api/containers/{id}/matches/{matchId}/snapshot``
     - Get current snapshot
   * - ``GET /api/containers/{id}/matches/{matchId}/history``
     - Get snapshot history

Security
========

Engine endpoints require authentication. Common scopes:

.. list-table::
   :widths: 35 65
   :header-rows: 1

   * - Scope
     - Operations
   * - ``engine.container.create``
     - Create containers
   * - ``engine.container.read``
     - View containers and matches
   * - ``engine.container.delete``
     - Delete containers
   * - ``engine.match.*``
     - Match operations
   * - ``engine.command.send``
     - Send game commands
   * - ``engine.snapshot.read``
     - Read ECS snapshots

See :doc:`/thunder/auth/authorization` for the complete scope matrix.

Fluent API (Java SDK)
=====================

The engine provides a fluent Java API for integration:

.. code-block:: java

    // Create container
    ExecutionContainer container = ExecutionContainer.create()
        .withTickRate(20)
        .build();

    // Install module
    container.modules()
        .install(ModuleLoader.load("chess-core.jar"));

    // Create match
    int matchId = container.matches().create();

    // Send command
    container.commands()
        .named("SpawnEntity")
        .forMatch(matchId)
        .withParam("x", 10.0f)
        .withParam("y", 20.0f)
        .execute();

    // Advance simulation
    container.ticks().advance();

    // Get snapshot
    Snapshot snapshot = container.snapshots()
        .forMatch(matchId)
        .get();

See :doc:`/concepts/containers` for detailed API documentation.

Configuration
=============

Engine configuration in ``application.yaml``::

    stormstack:
      engine:
        default-tick-rate: 20
        max-containers: 100
        max-matches-per-container: 50
        snapshot-retention: 100

      control-plane:
        enabled: true
        url: http://control-plane:8081

Docker Configuration
--------------------

::

    docker run -d \
      -p 8080:8080 \
      -e MONGODB_URI=mongodb://mongo:27017/stormstack \
      -e CONTROL_PLANE_URL=http://control-plane:8081 \
      samanthacireland/thunder-engine

Next Steps
==========

- :doc:`websocket` - Real-time state streaming
- :doc:`/concepts/ecs-basics` - ECS fundamentals
- :doc:`/concepts/tick-cycle` - Understanding ticks
- :doc:`/concepts/modules` - Module development
- :doc:`/api/rest-api` - Full API reference
