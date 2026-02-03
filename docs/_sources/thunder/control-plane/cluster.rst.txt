===================
Cluster Management
===================

This page covers detailed cluster management operations including node lifecycle,
capacity planning, and monitoring.

.. contents:: Contents
   :local:
   :depth: 2

Node Lifecycle
==============

Registration
------------

Nodes register with the control plane on startup:

.. code-block:: text

    Engine Node                    Control Plane
         |                              |
         |--- POST /api/nodes/register -->|
         |                              |
         |<-- 201 Created + TTL --------|
         |                              |
         |--- PUT /heartbeat (every N) -->|
         |                              |

Registration is **idempotent** - calling register with the same nodeId refreshes
the TTL without creating a duplicate.

**Registration Request:**

::

    POST /api/nodes/register
    Authorization: Bearer <token-with-control-plane.node.register>

    {
      "nodeId": "engine-node-1",
      "advertiseAddress": "http://10.0.1.5:8080",
      "capacity": {
        "maxContainers": 100,
        "maxMatches": 1000,
        "maxMemoryMb": 16384
      }
    }

**Response:**

::

    {
      "nodeId": "engine-node-1",
      "status": "HEALTHY",
      "advertiseAddress": "http://10.0.1.5:8080",
      "capacity": {
        "maxContainers": 100,
        "maxMatches": 1000,
        "maxMemoryMb": 16384
      },
      "metrics": {
        "activeContainers": 0,
        "activeMatches": 0,
        "usedMemoryMb": 0
      },
      "registeredAt": "2024-02-03T12:00:00Z",
      "lastHeartbeat": "2024-02-03T12:00:00Z"
    }

Heartbeats
----------

Nodes send periodic heartbeats to indicate health and report metrics:

::

    PUT /api/nodes/{nodeId}/heartbeat
    Authorization: Bearer <token>

    {
      "metrics": {
        "activeContainers": 25,
        "activeMatches": 150,
        "usedMemoryMb": 4096,
        "cpuPercent": 45.5
      }
    }

**Heartbeat Interval:**

- Default: 15 seconds
- Configurable via ``heartbeat-interval-seconds``
- Nodes that miss 4 heartbeats (1 minute) become UNHEALTHY

Draining
--------

When you need to remove a node gracefully:

1. **Drain the node** - Stops accepting new matches::

    POST /api/nodes/{nodeId}/drain
    Authorization: Bearer <token-with-control-plane.node.manage>

2. **Wait for matches to complete** - Existing matches continue running

3. **Deregister when empty**::

    DELETE /api/nodes/{nodeId}
    Authorization: Bearer <token>

During draining:

- New match requests are routed to other nodes
- Existing matches continue until completion
- Node status shows ``DRAINING``
- Heartbeats continue to report remaining matches

Capacity Planning
=================

Understanding Saturation
------------------------

**Cluster Saturation** measures how full your cluster is::

    saturation = totalActiveContainers / totalMaxContainers

Example with 3 nodes:

.. code-block:: text

    Node 1: 80 containers (max 100) = 80% saturated
    Node 2: 60 containers (max 100) = 60% saturated
    Node 3: 40 containers (max 100) = 40% saturated

    Cluster: 180 / 300 = 60% saturated

Capacity Configuration
----------------------

Set node capacity based on your hardware:

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Parameter
     - Guidance
   * - ``maxContainers``
     - Number of CPU cores x 4-8 (depends on game complexity)
   * - ``maxMatches``
     - Typically 10x maxContainers
   * - ``maxMemoryMb``
     - 80% of available RAM

**Example for 8-core, 32GB server:**

::

    {
      "capacity": {
        "maxContainers": 40,
        "maxMatches": 400,
        "maxMemoryMb": 25600
      }
    }

Autoscaling Thresholds
----------------------

Configure when to scale:

::

    stormstack:
      autoscaler:
        scale-up-threshold: 0.7    # 70% - add nodes
        scale-down-threshold: 0.3  # 30% - remove nodes
        cooldown-seconds: 300      # Wait 5 min between actions

**Recommendations:**

- Leave 30% headroom for burst traffic
- Scale up before hitting 100% to avoid match creation failures
- Scale down slowly to avoid thrashing

Monitoring
==========

Cluster Status
--------------

Get overall cluster health::

    GET /api/cluster/status
    Authorization: Bearer <token>

Response::

    {
      "healthy": true,
      "nodeCount": 3,
      "healthyNodes": 3,
      "unhealthyNodes": 0,
      "totalCapacity": {
        "maxContainers": 300,
        "maxMatches": 3000
      },
      "currentLoad": {
        "activeContainers": 180,
        "activeMatches": 900
      },
      "saturation": 0.60
    }

Node Metrics
------------

Get detailed metrics for a specific node::

    GET /api/cluster/nodes/{nodeId}

Response::

    {
      "nodeId": "engine-node-1",
      "status": "HEALTHY",
      "advertiseAddress": "http://10.0.1.5:8080",
      "capacity": { ... },
      "metrics": {
        "activeContainers": 25,
        "activeMatches": 150,
        "usedMemoryMb": 4096,
        "cpuPercent": 45.5,
        "tickRate": 20,
        "avgTickDurationMs": 12.5
      },
      "lastHeartbeat": "2024-02-03T12:05:30Z",
      "uptime": "PT4H30M"
    }

Dashboard API
-------------

The dashboard endpoint provides aggregated data for monitoring::

    GET /api/dashboard/overview

Response::

    {
      "cluster": {
        "nodeCount": 3,
        "saturation": 0.60
      },
      "matches": {
        "active": 150,
        "pending": 5,
        "finished": 1234
      },
      "players": {
        "connected": 280
      }
    }

Using Lightning CLI
-------------------

Monitor cluster with the CLI::

    # Cluster overview
    lightning cluster status

    # List all nodes
    lightning cluster nodes

    # Node details
    lightning cluster nodes node-1

    # Watch mode (refreshes every 2s)
    lightning cluster status --watch

Scheduling
==========

Scheduler Algorithm
-------------------

The scheduler uses a **least-loaded** algorithm:

1. Filter: Only HEALTHY nodes with available capacity
2. Score: Calculate saturation (lower is better)
3. Select: Choose lowest saturation node
4. Optional: Honor preferred node if specified and eligible

**Example Selection:**

::

    Available nodes:
      node-1: 80% saturated
      node-2: 60% saturated  <- Selected
      node-3: DRAINING (excluded)

Preferred Node Hints
--------------------

Clients can suggest a preferred node::

    POST /api/matches/create

    {
      "modules": ["chess-core:1.0.0"],
      "preferredNodeId": "node-1"
    }

The scheduler will use the preferred node **only if**:

1. Node is HEALTHY
2. Node has available capacity
3. No other constraint prevents it

Otherwise, it falls back to normal selection.

Affinity and Anti-Affinity
--------------------------

For advanced scheduling (future feature):

- **Affinity**: Keep related matches on same node
- **Anti-affinity**: Spread matches across nodes for resilience

High Availability
=================

Current Architecture
--------------------

Thunder Control Plane runs as a **single master**. High availability is achieved through:

1. **Stateless design** - State stored in Redis
2. **Quick restart** - Node TTLs allow re-registration
3. **Proxy pass-through** - Clients can talk directly to nodes

.. warning::

    Control Plane is a single point of failure for match *creation*.
    Existing matches continue running on engine nodes even if Control Plane is down.

Production Recommendations
--------------------------

1. **Deploy behind load balancer** for health checks
2. **Configure automatic restarts** via Kubernetes/systemd
3. **Monitor heartbeat lag** to detect issues early
4. **Enable Redis persistence** for state durability

Failure Scenarios
-----------------

**Control Plane Down:**

- Existing matches: Continue running
- New matches: Cannot be created
- Node heartbeats: Fail (nodes mark as unhealthy when CP returns)
- Recovery: Nodes re-register, CP rebuilds state from Redis

**Node Down:**

- Scheduler excludes after missed heartbeats
- Matches on that node are lost
- New matches routed to healthy nodes
- No automatic match migration (stateful)

**Redis Down:**

- Control Plane loses state
- Nodes continue running
- On recovery: Full re-registration required

Troubleshooting
===============

Node Not Appearing
------------------

1. Check node can reach Control Plane network
2. Verify auth token has ``control-plane.node.register`` scope
3. Check Control Plane logs for registration errors
4. Verify advertiseAddress is reachable from Control Plane

Node Showing UNHEALTHY
----------------------

1. Check node is still running
2. Verify network connectivity for heartbeats
3. Check for GC pauses or high load causing heartbeat delays
4. Look for clock skew issues

Match Creation Failing
----------------------

If ``POST /api/matches/create`` returns errors:

1. **No available nodes**: Check cluster has HEALTHY nodes
2. **Capacity exceeded**: Check saturation isn't 100%
3. **Module not found**: Verify module is in registry
4. **Auth error**: Verify scope ``control-plane.match.create``

Autoscaler Not Scaling
----------------------

1. Verify autoscaler is enabled
2. Check cooldown period hasn't been exceeded
3. Verify min/max node constraints
4. Check recommendations are being acknowledged

Next Steps
==========

- :doc:`/thunder/auth/authorization` - Control plane security scopes
- :doc:`/api/rest-api` - Full API reference
- :doc:`/lightning/cli/commands` - CLI cluster commands
