=========================
Thunder Control Plane
=========================

Thunder Control Plane manages distributed StormStack deployments, handling node registration,
match scheduling, module distribution, and autoscaling.

.. contents:: Contents
   :local:
   :depth: 2

Overview
========

The Control Plane is the central coordinator for multi-node StormStack clusters.
It provides:

- **Node Registry** - Track engine nodes with health status and capacity
- **Scheduler** - Route match creation to optimal nodes
- **Module Registry** - Store and distribute game modules
- **Match Registry** - Track all matches across the cluster
- **Autoscaler** - Recommend scaling actions based on load

Architecture
------------

.. code-block:: text

    +-------------------+
    |   Control Plane   |
    |  (Single Master)  |
    +--------+----------+
             |
    +--------+--------+--------+
    |        |        |        |
    v        v        v        v
  +----+  +----+  +----+  +----+
  |Node|  |Node|  |Node|  |Node|
  | 1  |  | 2  |  | 3  |  | 4  |
  +----+  +----+  +----+  +----+

Each engine node registers with the control plane and sends periodic heartbeats.
The control plane tracks node health, capacity, and current load to make scheduling decisions.

Quick Start
===========

Starting Control Plane
----------------------

Via Docker::

    docker run -d \
      -p 8081:8081 \
      -e REDIS_HOST=redis \
      samanthacireland/thunder-control-plane

Via Docker Compose (recommended)::

    docker compose up -d control-plane

Registering a Node
------------------

Engine nodes register automatically on startup. Manual registration::

    curl -X POST http://localhost:8081/api/nodes/register \
      -H "Authorization: Bearer <token>" \
      -H "Content-Type: application/json" \
      -d '{
        "nodeId": "node-1",
        "advertiseAddress": "http://node-1:8080",
        "capacity": {
          "maxContainers": 100,
          "maxMatches": 1000
        }
      }'

Creating a Match via Control Plane
----------------------------------

The control plane automatically selects the best node::

    curl -X POST http://localhost:8081/api/v1/deploy \
      -H "Authorization: Bearer <token>" \
      -H "Content-Type: application/json" \
      -d '{
        "matchId": "match-123",
        "modules": ["chess-core:1.0.0"],
        "maxPlayers": 2
      }'

Response::

    {
      "matchId": "match-123",
      "nodeId": "node-2",
      "nodeAddress": "http://node-2:8080",
      "status": "ACTIVE"
    }

Documentation
=============

.. toctree::
   :maxdepth: 2

   cluster

Core Services
=============

Node Registry
-------------

Manages node registration and health monitoring.

**Key Operations:**

- ``register()`` - Register or re-register a node (idempotent)
- ``heartbeat()`` - Refresh TTL and update metrics
- ``drain()`` - Mark node as draining (no new matches)
- ``deregister()`` - Remove node from cluster

**Node States:**

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - State
     - Description
   * - HEALTHY
     - Node is responding to heartbeats and accepting work
   * - UNHEALTHY
     - Node missed heartbeats but still registered
   * - DRAINING
     - Node accepts no new work but existing matches continue
   * - OFFLINE
     - Node deregistered or TTL expired

Scheduler
---------

Selects nodes for new match creation using a least-loaded algorithm.

**Selection Criteria:**

1. Node must be HEALTHY
2. Node must have available capacity
3. Prefers nodes with lower saturation (active/max containers)
4. Optional: Prefer specified node if provided

**Saturation Calculation:**

::

    saturation = activeContainers / maxContainers

Lower saturation = preferred for new matches.

Match Registry
--------------

Tracks all matches across the cluster.

**Match Entry:**

::

    {
      "matchId": "match-123",
      "nodeId": "node-2",
      "nodeAddress": "http://node-2:8080",
      "status": "ACTIVE",
      "modules": ["chess-core:1.0.0"],
      "currentPlayers": 1,
      "maxPlayers": 2,
      "createdAt": "2024-02-03T12:00:00Z"
    }

**Match Statuses:**

- ``PENDING`` - Match creation in progress
- ``ACTIVE`` - Match running and accepting players
- ``FULL`` - Match at max capacity
- ``FINISHED`` - Match completed

Module Registry
---------------

Stores and distributes game modules to nodes.

**Operations:**

- ``upload()`` - Upload a module JAR
- ``download()`` - Download module for installation
- ``distribute()`` - Push module to all nodes
- ``delete()`` - Remove module from registry

**Module Metadata:**

::

    {
      "name": "chess-core",
      "version": "1.0.0",
      "size": 1048576,
      "checksum": "sha256:abc123...",
      "uploadedAt": "2024-02-03T12:00:00Z",
      "uploadedBy": "admin"
    }

Autoscaler
----------

Analyzes cluster load and recommends scaling actions.

**Recommendations:**

::

    {
      "action": "SCALE_UP",
      "currentNodes": 3,
      "recommendedNodes": 5,
      "reason": "Cluster saturation at 85%, above threshold of 70%",
      "metrics": {
        "totalCapacity": 300,
        "activeContainers": 255,
        "saturation": 0.85
      }
    }

**Scaling Actions:**

- ``SCALE_UP`` - Add more nodes
- ``SCALE_DOWN`` - Remove nodes (after draining)
- ``NONE`` - Cluster is balanced

Configuration
=============

Application Configuration
-------------------------

::

    stormstack:
      control-plane:
        node-ttl-seconds: 60
        heartbeat-interval-seconds: 15

      autoscaler:
        enabled: true
        scale-up-threshold: 0.7
        scale-down-threshold: 0.3
        min-nodes: 2
        max-nodes: 20
        cooldown-seconds: 300

      proxy:
        enabled: true
        timeout-ms: 30000

      module-storage:
        type: filesystem
        path: /var/lib/stormstack/modules

Redis Configuration
-------------------

Control Plane uses Redis for node and match state::

    quarkus:
      redis:
        hosts: redis://localhost:6379

API Endpoints
=============

Node Management
---------------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``POST /api/nodes/register``
     - Register a node
   * - ``PUT /api/nodes/{nodeId}/heartbeat``
     - Send heartbeat
   * - ``POST /api/nodes/{nodeId}/drain``
     - Mark node as draining
   * - ``DELETE /api/nodes/{nodeId}``
     - Deregister node

Cluster Status
--------------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``GET /api/cluster/nodes``
     - List all nodes
   * - ``GET /api/cluster/nodes/{nodeId}``
     - Get node details
   * - ``GET /api/cluster/status``
     - Get cluster overview

Match Management
----------------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``POST /api/matches/create``
     - Create a match
   * - ``GET /api/matches``
     - List matches
   * - ``GET /api/matches/{matchId}``
     - Get match details
   * - ``DELETE /api/matches/{matchId}``
     - Delete match

Deployment
----------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``POST /api/v1/deploy``
     - Deploy a match
   * - ``GET /api/v1/deploy/{matchId}``
     - Get deployment status
   * - ``DELETE /api/v1/deploy/{matchId}``
     - Undeploy a match

Module Management
-----------------

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Endpoint
     - Description
   * - ``POST /api/modules``
     - Upload module
   * - ``GET /api/modules``
     - List modules
   * - ``GET /api/modules/{name}/{version}/download``
     - Download module
   * - ``POST /api/modules/{name}/{version}/distribute``
     - Distribute to nodes
   * - ``DELETE /api/modules/{name}/{version}``
     - Delete module

Security
========

All Control Plane endpoints require authentication. Required scopes:

- ``control-plane.cluster.read`` - View cluster status
- ``control-plane.node.register`` - Register nodes
- ``control-plane.node.manage`` - Drain/deregister nodes
- ``control-plane.match.*`` - Match operations
- ``control-plane.module.*`` - Module operations
- ``control-plane.deploy.*`` - Deployment operations

See :doc:`/thunder/auth/authorization` for the complete scope matrix.

Next Steps
==========

- :doc:`cluster` - Detailed cluster management
- :doc:`/thunder/auth/index` - Authentication setup
- :doc:`/api/rest-api` - Full API reference
- :doc:`/lightning/cli/commands` - CLI for cluster management
