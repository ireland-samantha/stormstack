=============
API Reference
=============

StormStack exposes comprehensive REST and WebSocket APIs for managing containers,
matches, modules, and real-time game state.

This reference documents all available endpoints, authentication requirements,
and data formats. The API is defined in OpenAPI 3.0 format and is available
at ``/openapi.yaml`` in the repository root.

API Overview
============

StormStack provides two API servers:

**Thunder Engine** (port 8080)
   Game execution, ECS management, WebSocket streaming

**Thunder Control Plane** (port 8081)
   Cluster orchestration, node management, match routing

Authentication
==============

All API endpoints (except health checks) require JWT authentication.

.. code-block:: bash

   # Get a token
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "admin", "password": "your-password"}'

   # Use the token
   curl http://localhost:8080/api/containers \
     -H "Authorization: Bearer <your-jwt-token>"

For WebSocket connections, pass the token as a query parameter:

.. code-block:: text

   ws://localhost:8080/ws/containers/{containerId}/matches/{matchId}/snapshot?token=<jwt>

API Sections
============

.. toctree::
   :maxdepth: 2

   rest-api
   websocket-api

Quick Reference
===============

Engine Endpoints (port 8080)
----------------------------

.. list-table::
   :header-rows: 1
   :widths: 30 50 20

   * - Endpoint
     - Description
     - Auth Required
   * - ``GET /api/containers``
     - List all containers
     - Yes
   * - ``POST /api/containers``
     - Create a new container
     - Yes (admin)
   * - ``GET /api/containers/{id}/matches``
     - List matches in container
     - Yes
   * - ``POST /api/containers/{id}/matches``
     - Create a new match
     - Yes
   * - ``POST /api/containers/{id}/commands``
     - Queue commands for execution
     - Yes
   * - ``POST /api/containers/{id}/ticks``
     - Advance simulation tick
     - Yes
   * - ``GET /api/containers/{id}/snapshots/{matchId}``
     - Get current ECS snapshot
     - Yes
   * - ``POST /api/containers/{id}/modules``
     - Install a module
     - Yes (admin)

Control Plane Endpoints (port 8081)
-----------------------------------

.. list-table::
   :header-rows: 1
   :widths: 30 50 20

   * - Endpoint
     - Description
     - Auth Required
   * - ``GET /api/cluster/status``
     - Get cluster health overview
     - Yes
   * - ``GET /api/nodes``
     - List registered nodes
     - Yes
   * - ``POST /api/nodes/register``
     - Register a new node
     - Yes (node)
   * - ``GET /api/matches``
     - List all matches across cluster
     - Yes
   * - ``POST /api/matches/route``
     - Route a new match to optimal node
     - Yes
   * - ``GET /api/modules``
     - List available modules
     - Yes

WebSocket Endpoints
-------------------

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Endpoint
     - Description
   * - ``ws://.../ws/containers/{id}/matches/{matchId}/snapshot``
     - Full snapshot streaming
   * - ``ws://.../ws/containers/{id}/matches/{matchId}/delta``
     - Delta-compressed snapshot streaming
   * - ``ws://.../ws/containers/{id}/commands``
     - Binary command WebSocket (Protobuf)

Response Formats
================

All REST endpoints return JSON. Standard response structure:

**Success Response:**

.. code-block:: json

   {
     "data": { ... },
     "meta": {
       "timestamp": "2026-02-03T10:30:00Z",
       "requestId": "uuid"
     }
   }

**Error Response:**

.. code-block:: json

   {
     "error": {
       "code": "CONTAINER_NOT_FOUND",
       "message": "Container with ID 123 not found",
       "details": { ... }
     }
   }

Rate Limiting
=============

The API implements rate limiting per authenticated user:

* **Standard endpoints**: 1000 requests per minute
* **Command endpoints**: 100 commands per second per container
* **Snapshot endpoints**: No limit (WebSocket preferred for real-time)

Rate limit headers are included in responses:

.. code-block:: text

   X-RateLimit-Limit: 1000
   X-RateLimit-Remaining: 950
   X-RateLimit-Reset: 1706952600
