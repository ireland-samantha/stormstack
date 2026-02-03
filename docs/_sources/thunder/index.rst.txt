========================
Thunder Backend Services
========================

Thunder is the backend component of StormStack, providing the server-side infrastructure
for running multi-match game servers. It consists of three main services:

.. contents:: Services
   :local:
   :depth: 1

Overview
========

Thunder is built on **Java 25** and **Quarkus 3.x**, following clean architecture principles
with framework-agnostic core modules. The backend handles:

- **Multi-match execution** on a single JVM via isolated containers
- **Real-time game state** streaming via WebSocket
- **Authentication & authorization** via OAuth2 and JWT
- **Cluster management** for distributed deployments

Architecture Pattern
--------------------

All Thunder services follow the **two-module pattern**:

- **Core Module** (``*/core/``): Framework-agnostic domain logic, services, and repositories
- **Provider Module** (``*/provider/``): Quarkus REST endpoints, persistence, and configuration

This separation ensures the business logic remains portable and testable without
framework dependencies. See :doc:`/architecture/two-module-pattern` for details.

Services
========

Thunder Engine
--------------

The game execution engine providing:

- Entity-Component-System (ECS) for game state management
- Execution containers with ClassLoader isolation
- Tick-based simulation with configurable rates
- Command queue for game actions
- Delta-compressed snapshot streaming

.. toctree::
   :maxdepth: 2

   engine/index

Thunder Auth
------------

Authentication and authorization service providing:

- OAuth2 token endpoint with multiple grant types
- JWT-based authentication with RSA256 or HMAC256
- Role-based access control (RBAC)
- Scope-based authorization for fine-grained permissions
- Rate limiting and brute force protection

.. toctree::
   :maxdepth: 2

   auth/index

Thunder Control Plane
---------------------

Cluster management service providing:

- Node registration and health monitoring
- Match scheduling with least-loaded selection
- Module registry and distribution
- Autoscaling recommendations
- Dashboard APIs

.. toctree::
   :maxdepth: 2

   control-plane/index

Docker Images
=============

Thunder services are distributed as Docker images:

- ``samanthacireland/thunder-engine`` - Main game server
- ``samanthacireland/thunder-auth`` - Authentication service
- ``samanthacireland/thunder-control-plane`` - Cluster management

Quick start with Docker Compose::

    docker compose up -d

See :doc:`/getting-started/installation` for detailed setup instructions.

Technology Stack
================

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Layer
     - Technology
   * - Language
     - Java 25 (with preview features, virtual threads)
   * - Framework
     - Quarkus 3.x (REST + WebSocket)
   * - Build
     - Maven 3.9+ (multi-module)
   * - Persistence
     - MongoDB 6.0+ (Auth, Engine), Redis (Control Plane)
   * - Authentication
     - JWT + BCrypt, OAuth2 (RFC 6749)
   * - Testing
     - JUnit 5, Mockito, Testcontainers

Next Steps
==========

- :doc:`auth/index` - Set up authentication
- :doc:`engine/index` - Learn about the game engine
- :doc:`control-plane/index` - Manage your cluster
- :doc:`/api/rest-api` - REST API reference
