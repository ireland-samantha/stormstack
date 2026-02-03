Architecture Overview
============

Overview
---------------

StormStack is a distributed, authoritative multiplayer game server platform.
It allows untrusted game logic to be hot-deployed at runtime, safely isolated
via ClassLoader boundaries and enforced through JWT-scoped ECS access.

The system consists of three services (auth, control plane, engine) and supports
multi-node orchestration, live module reloads, and WebSocket-based state
streaming.

::

                                    ┌─────────────────────────────────────┐
                                    │           Game Clients              │
                                    │  (Web Panel / Game Client / CLI)    │
                                    └──────────────┬──────────────────────┘
                                                   │
                              ┌────────────────────┼────────────────────┐
                              │                    │                    │
                              ▼                    ▼                    ▼
                    ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
                    │  Thunder Auth   │  │ Thunder Control │  │ Thunder Engine  │
                    │   (port 8082)   │  │     Plane       │  │   (port 8080)   │
                    │                 │  │   (port 8081)   │  │                 │
                    │  • OAuth2/OIDC  │  │                 │  │  • Containers   │
                    │  • JWT tokens   │  │  • Node registry│  │  • ECS store    │
                    │  • User/Role    │  │  • Match routing│  │  • Game loop    │
                    │    management   │  │  • Autoscaling  │  │  • WebSocket    │
                    │  • Rate limiting│  │  • Module dist  │  │  • Hot-reload   │
                    └────────┬────────┘  └────────┬────────┘  └────────┬────────┘
                             │                    │                    │
                             ▼                    ▼                    ▼
                    ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
                    │    MongoDB      │  │     Redis       │  │    MongoDB      │
                    │  (users, roles, │  │  (node registry,│  │   (snapshots,   │
                    │   tokens)       │  │   match state)  │  │    history)     │
                    └─────────────────┘  └─────────────────┘  └─────────────────┘


Service Boundaries
------------------

+------------------------+-------------------------------------------------------+------+----------------------+
| Service                | Purpose                                               | Port | Storage              |
+========================+=======================================================+======+======================+
| Thunder Auth           | OAuth2/OIDC authentication, user management, tokens   | 8082 | MongoDB              |
+------------------------+-------------------------------------------------------+------+----------------------+
| Thunder Control Plane  | Cluster orchestration, node registry, match routing   | 8081 | Redis                |
+------------------------+-------------------------------------------------------+------+----------------------+
| Thunder Engine         | Game execution, ECS, WebSocket streaming               | 8080 | MongoDB (optional)   |
+------------------------+-------------------------------------------------------+------+----------------------+

Execution Containers
--------------------

Each Thunder Engine node runs multiple isolated **Execution Containers**.
A container is a complete runtime environment with:

- Its own ClassLoader (module isolation)
- Its own EntityComponentStore (ECS data)
- Its own GameLoop (tick processing)
- Its own CommandQueue (command execution)
- Multiple Matches (game instances sharing the container's modules)

::

    Thunder Engine Node
        │
        ├── Container 1 (id: 1, name: "production")
        │       ├── ContainerClassLoader (isolated)
        │       │       └── Loaded JARs: EntityModule, RigidBodyModule, MyGameModule
        │       ├── EntityComponentStore (1M entity capacity)
        │       ├── GameLoop (tick thread, 60 FPS)
        │       ├── CommandQueue (per-tick execution)
        │       ├── SnapshotProvider (columnar format)
        │       └── Matches
        │               ├── Match 1 (players: 4, entities: 150)
        │               ├── Match 2 (players: 2, entities: 80)
        │               └── Match 3 (players: 6, entities: 200)
        │
        └── Container 2 (id: 2, name: "staging")
                ├── ContainerClassLoader (different module versions)
                ├── EntityComponentStore (separate)
                ├── GameLoop (30 FPS for testing)
                └── Matches
                        └── Match 4 (test match)

Container Lifecycle
-------------------

+-----------+--------------------------------------------------+
| Status    | Description                                      |
+===========+==================================================+
| CREATED   | Container initialized, modules loaded            |
+-----------+--------------------------------------------------+
| STARTING  | Container starting up                            |
+-----------+--------------------------------------------------+
| RUNNING   | Container actively processing ticks              |
+-----------+--------------------------------------------------+
| PAUSED    | Ticking stopped, state preserved                 |
+-----------+--------------------------------------------------+
| STOPPING  | Container shutting down                          |
+-----------+--------------------------------------------------+
| STOPPED   | Container stopped, resources released            |
+-----------+--------------------------------------------------+

Container Isolation
-------------------

Each container provides:

- ClassLoader isolation with hybrid delegation
- Independent game loop and tick rate
- Separate ECS store
- Container-scoped command execution
- Independent lifecycle management

See ``classloaders.md`` for details.

ECS Architecture
----------------

The Entity Component System uses columnar, array-based storage:

::

    ArrayEntityComponentStore
        ├── Entity Pool
        │       ├── Entity 0: [POSITION_X, POSITION_Y, HEALTH, ENTITY_TYPE, MATCH_ID]
        │       ├── Entity 1: [POSITION_X, POSITION_Y, VELOCITY_X, VELOCITY_Y, MATCH_ID]
        │       └── Entity 2: [POSITION_X, POSITION_Y, SPRITE_ID, MATCH_ID]
        └── Component Arrays
                ├── POSITION_X: [100.0, 200.0, 150.0, ...]
                ├── POSITION_Y: [50.0, 75.0, 100.0, ...]
                ├── HEALTH: [100.0, NaN, NaN, ...]
                └── MATCH_ID: [1.0, 1.0, 2.0, ...]

Store Decorator Pattern
-----------------------

::

    ModuleScopedStore
        └── LockingEntityComponentStore
                └── DirtyTrackingStore
                        └── CachedEntityComponentStore
                                └── ArrayEntityComponentStore

Core Components
---------------

+------------+-----------------------------------------------+
| Component  | Purpose                                       |
+============+===============================================+
| ENTITY_ID  | Unique entity identifier                      |
+------------+-----------------------------------------------+
| MATCH_ID   | Match isolation                               |
+------------+-----------------------------------------------+
| OWNER_ID   | Player ownership tracking                    |
+------------+-----------------------------------------------+

Tick-Based Simulation
---------------------

::

    1. Execute Commands
    2. Run Systems
    3. Notify Tick Listeners (async)
    4. Record Metrics

Tick Control
------------

::

    curl -X POST http://localhost:8080/api/containers/1/tick
    curl -X POST http://localhost:8080/api/containers/1/play?intervalMs=16
    curl -X POST http://localhost:8080/api/containers/1/stop-auto

WebSocket Streaming
-------------------

::

    Client ── connect ──▶ Server
           ◀─ full snapshot
           ◀─ delta updates

WebSocket Endpoints
-------------------

+---------------------------------------------------------------------+----------------------------+
| Endpoint                                                            | Purpose                    |
+=====================================================================+============================+
| /ws/containers/{id}/matches/{matchId}/snapshot                      | Full snapshot              |
+---------------------------------------------------------------------+----------------------------+
| /ws/containers/{id}/matches/{matchId}/delta                         | Delta snapshot             |
+---------------------------------------------------------------------+----------------------------+
| /ws/matches/{matchId}/players/{playerId}/errors                     | Player error stream        |
+---------------------------------------------------------------------+----------------------------+

Authentication & Authorization
------------------------------

OAuth2 Grant Types
~~~~~~~~~~~~~~~~~~

+--------------------+----------------------------------+
| Grant Type         | Use Case                         |
+====================+==================================+
| password           | User login                       |
+--------------------+----------------------------------+
| client_credentials | Service-to-service auth          |
+--------------------+----------------------------------+
| refresh_token      | Token rotation                   |
+--------------------+----------------------------------+
| token_exchange     | API token exchange               |
+--------------------+----------------------------------+

Scope-Based Authorization
~~~~~~~~~~~~~~~~~~~~~~~~~

Permissions follow ``service.resource.operation`` with wildcard support.

Control Plane Architecture
--------------------------

::

    Thunder Control Plane
        ├── Node Registry
        ├── Scheduler
        ├── Match Router
        ├── Autoscaler
        └── Module Registry

Project Structure
-----------------

::

    stormstack/
    ├── thunder/
    │   ├── engine/
    │   ├── auth/
    │   └── control-plane/
    ├── lightning/
    └── docs/

Key Design Decisions
-------------------

- Two-module (core/provider) architecture
- Float-based ECS with NaN sentinel
- Match isolation via component
- Async tick listeners
- JWT-based module authorization
