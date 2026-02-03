============
REST API
============

This page documents the REST API endpoints for StormStack Thunder Engine and Control Plane.

The complete OpenAPI specification is available at ``/openapi.yaml`` in the repository.

.. contents:: Table of Contents
   :local:
   :depth: 2

Authentication Endpoints
========================

Login
-----

.. http:post:: /api/auth/login

   Authenticate with username and password to receive a JWT token.

   **Request:**

   .. code-block:: json

      {
        "username": "admin",
        "password": "secret123"
      }

   **Response (200 OK):**

   .. code-block:: json

      {
        "token": "eyJhbGciOiJIUzI1NiIs...",
        "expiresIn": 3600,
        "tokenType": "Bearer"
      }

   :statuscode 200: Authentication successful
   :statuscode 401: Invalid credentials

Token Refresh
-------------

.. http:post:: /api/auth/refresh

   Refresh an expiring JWT token.

   **Headers:**

   * ``Authorization: Bearer <current-token>``

   **Response (200 OK):**

   .. code-block:: json

      {
        "token": "eyJhbGciOiJIUzI1NiIs...",
        "expiresIn": 3600
      }

Container Endpoints
===================

List Containers
---------------

.. http:get:: /api/containers

   List all execution containers.

   **Response (200 OK):**

   .. code-block:: json

      {
        "containers": [
          {
            "id": 1,
            "name": "production",
            "status": "RUNNING",
            "matchCount": 5,
            "tickRate": 60,
            "currentTick": 15420
          }
        ]
      }

Create Container
----------------

.. http:post:: /api/containers

   Create a new execution container.

   **Request:**

   .. code-block:: json

      {
        "name": "staging",
        "tickRate": 30,
        "maxEntities": 100000
      }

   **Response (201 Created):**

   .. code-block:: json

      {
        "id": 2,
        "name": "staging",
        "status": "CREATED"
      }

   :statuscode 201: Container created
   :statuscode 400: Invalid configuration
   :statuscode 403: Insufficient permissions

Get Container
-------------

.. http:get:: /api/containers/(int:containerId)

   Get details for a specific container.

   :param containerId: Container ID
   :type containerId: integer

   **Response (200 OK):**

   .. code-block:: json

      {
        "id": 1,
        "name": "production",
        "status": "RUNNING",
        "tickRate": 60,
        "currentTick": 15420,
        "modules": ["EntityModule", "RigidBodyModule"],
        "matches": [
          {"id": 1, "playerCount": 4, "entityCount": 150}
        ]
      }

Delete Container
----------------

.. http:delete:: /api/containers/(int:containerId)

   Delete a container. Container must be stopped first.

   :statuscode 204: Container deleted
   :statuscode 400: Container not stopped
   :statuscode 404: Container not found

Match Endpoints
===============

List Matches
------------

.. http:get:: /api/containers/(int:containerId)/matches

   List all matches in a container.

   **Response (200 OK):**

   .. code-block:: json

      {
        "matches": [
          {
            "id": 1,
            "containerId": 1,
            "playerCount": 4,
            "entityCount": 150,
            "status": "RUNNING",
            "createdAt": "2026-02-03T10:00:00Z"
          }
        ]
      }

Create Match
------------

.. http:post:: /api/containers/(int:containerId)/matches

   Create a new match in the container.

   **Request:**

   .. code-block:: json

      {
        "metadata": {
          "gameMode": "deathmatch",
          "maxPlayers": 8
        }
      }

   **Response (201 Created):**

   .. code-block:: json

      {
        "id": 2,
        "containerId": 1,
        "status": "CREATED"
      }

Command Endpoints
=================

Queue Commands
--------------

.. http:post:: /api/containers/(int:containerId)/commands

   Queue commands for execution in the next tick.

   **Request:**

   .. code-block:: json

      {
        "matchId": 1,
        "commands": [
          {
            "name": "SpawnEntity",
            "params": {
              "x": 100.0,
              "y": 50.0,
              "type": "player"
            }
          }
        ]
      }

   **Response (202 Accepted):**

   .. code-block:: json

      {
        "queued": 1,
        "nextTick": 15421
      }

   :statuscode 202: Commands queued
   :statuscode 400: Invalid command format
   :statuscode 404: Match not found

Get Command Schema
------------------

.. http:get:: /api/containers/(int:containerId)/commands/schema

   Get the schema for all available commands in the container.

   **Response (200 OK):**

   .. code-block:: json

      {
        "commands": [
          {
            "name": "SpawnEntity",
            "module": "EntityModule",
            "parameters": [
              {"name": "x", "type": "float", "required": true},
              {"name": "y", "type": "float", "required": true},
              {"name": "type", "type": "string", "required": true}
            ]
          }
        ]
      }

Simulation Endpoints
====================

Advance Tick
------------

.. http:post:: /api/containers/(int:containerId)/ticks

   Manually advance the simulation by one or more ticks.

   **Request:**

   .. code-block:: json

      {
        "count": 1
      }

   **Response (200 OK):**

   .. code-block:: json

      {
        "previousTick": 15420,
        "currentTick": 15421,
        "commandsExecuted": 3
      }

Start Auto-Advance
------------------

.. http:post:: /api/containers/(int:containerId)/ticks/start

   Start automatic tick advancement at the configured tick rate.

   **Response (200 OK):**

   .. code-block:: json

      {
        "status": "RUNNING",
        "tickRate": 60
      }

Stop Auto-Advance
-----------------

.. http:post:: /api/containers/(int:containerId)/ticks/stop

   Stop automatic tick advancement.

   **Response (200 OK):**

   .. code-block:: json

      {
        "status": "PAUSED",
        "currentTick": 15421
      }

Snapshot Endpoints
==================

Get Snapshot
------------

.. http:get:: /api/containers/(int:containerId)/snapshots/(int:matchId)

   Get the current ECS snapshot for a match.

   **Query Parameters:**

   * ``format`` - Response format: ``json`` (default) or ``binary``
   * ``modules`` - Comma-separated list of modules to include

   **Response (200 OK):**

   .. code-block:: json

      {
        "matchId": 1,
        "tick": 15421,
        "modules": {
          "EntityModule": {
            "components": ["Position", "Velocity"],
            "entities": [
              {
                "id": 1,
                "Position": {"x": 100.0, "y": 50.0},
                "Velocity": {"dx": 1.0, "dy": 0.0}
              }
            ]
          }
        }
      }

Module Endpoints
================

List Modules
------------

.. http:get:: /api/containers/(int:containerId)/modules

   List installed modules in a container.

   **Response (200 OK):**

   .. code-block:: json

      {
        "modules": [
          {
            "id": "EntityModule",
            "version": "1.0.0",
            "components": ["Position", "Velocity", "Entity"],
            "commands": ["SpawnEntity", "DestroyEntity"],
            "systems": ["MovementSystem"]
          }
        ]
      }

Install Module
--------------

.. http:post:: /api/containers/(int:containerId)/modules

   Install a module JAR file into the container.

   **Content-Type:** ``multipart/form-data``

   **Form Fields:**

   * ``file`` - The module JAR file

   **Response (201 Created):**

   .. code-block:: json

      {
        "id": "CustomGameModule",
        "version": "1.0.0",
        "status": "INSTALLED"
      }

Control Plane Endpoints
=======================

Cluster Status
--------------

.. http:get:: /api/cluster/status

   Get overall cluster health and status.

   **Response (200 OK):**

   .. code-block:: json

      {
        "totalNodes": 3,
        "healthyNodes": 3,
        "drainingNodes": 0,
        "totalCapacity": 300,
        "usedCapacity": 45,
        "saturation": 0.15
      }

List Nodes
----------

.. http:get:: /api/nodes

   List all registered nodes in the cluster.

   **Response (200 OK):**

   .. code-block:: json

      {
        "nodes": [
          {
            "id": "node-1",
            "url": "http://engine-1:8080",
            "status": "HEALTHY",
            "capacity": 100,
            "activeMatches": 15,
            "lastHeartbeat": "2026-02-03T10:30:00Z"
          }
        ]
      }

Route Match
-----------

.. http:post:: /api/matches/route

   Route a new match to the optimal node based on capacity.

   **Request:**

   .. code-block:: json

      {
        "gameType": "battle-royale",
        "expectedPlayers": 100,
        "region": "us-west"
      }

   **Response (200 OK):**

   .. code-block:: json

      {
        "nodeId": "node-2",
        "nodeUrl": "http://engine-2:8080",
        "containerId": 1,
        "matchId": 42
      }

Error Codes
===========

.. list-table::
   :header-rows: 1

   * - Code
     - Description
   * - ``AUTHENTICATION_REQUIRED``
     - No valid JWT token provided
   * - ``PERMISSION_DENIED``
     - User lacks required role/permission
   * - ``CONTAINER_NOT_FOUND``
     - Container ID does not exist
   * - ``MATCH_NOT_FOUND``
     - Match ID does not exist
   * - ``CONTAINER_NOT_RUNNING``
     - Container must be running for this operation
   * - ``INVALID_COMMAND``
     - Command name or parameters invalid
   * - ``MODULE_CONFLICT``
     - Module version conflict during install
   * - ``CAPACITY_EXCEEDED``
     - Cluster at capacity, cannot route match
