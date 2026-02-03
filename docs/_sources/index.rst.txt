.. StormStack documentation master file

=====================================
StormStack Documentation
=====================================

**StormStack** is a multi-match game server framework consisting of:

* **Thunder** - Backend services (Engine, Auth, Control Plane)
* **Lightning** - Client tools (CLI, Rendering Engine, Web Panel)

Built on Java 25 and Quarkus, StormStack features ECS architecture, hot-reloadable modules, and real-time WebSocket streaming.

.. note::

   This documentation covers the StormStack Java version 0.1.1. A Rust rewrite is in progress.

Quick Links
-----------

* :doc:`getting-started/index` - New to StormStack? Start here!
* :doc:`tutorials/index` - Step-by-step tutorials
* :doc:`api/index` - REST and WebSocket API reference
* :doc:`lightning/cli/index` - CLI command reference
* :doc:`concepts/why` - Motivation behind the project
Key Features
------------

**Multi-Game Execution**
   Run multiple games on a single JVM via isolated containers with separate ClassLoaders.

**Entity-Component-System (ECS)**
   High-performance columnar storage with O(1) component access.

**Hot-Reloadable Modules**
   Update game logic at runtime without server restarts.

**WebSocket Streaming**
   Real-time ECS snapshots with delta compression for bandwidth efficiency.

**Role-Based Access Control**
   JWT authentication with fine-grained permissions.

Architecture Overview
---------------------

.. code-block:: text

   Game Clients (Web Panel / CLI / Game Client)
              |
              v
   +------------------+  +------------------+  +------------------+
   |  Thunder Auth    |  | Thunder Control  |  |  Thunder Engine  |
   |   (port 8082)    |  |     Plane        |  |   (port 8080)    |
   |                  |  |   (port 8081)    |  |                  |
   |  - OAuth2/OIDC   |  |                  |  |  - Containers    |
   |  - JWT tokens    |  |  - Node registry |  |  - ECS store     |
   |  - User/Role     |  |  - Match routing |  |  - Game loop     |
   |    management    |  |  - Autoscaling   |  |  - WebSocket     |
   +------------------+  +------------------+  +------------------+

Documentation Contents
----------------------

.. toctree::
   :maxdepth: 2
   :caption: Getting Started

   getting-started/index
   tutorials/index

.. toctree::
   :maxdepth: 2
   :caption: Core Concepts

   concepts/index

.. toctree::
   :maxdepth: 2
   :caption: Thunder (Backend)

   thunder/index

.. toctree::
   :maxdepth: 2
   :caption: Lightning (Tools)

   lightning/index

.. toctree::
   :maxdepth: 2
   :caption: API Reference

   api/index

.. toctree::
   :maxdepth: 2
   :caption: Architecture

   architecture/index

.. toctree::
   :maxdepth: 2
   :caption: Contributing

   contributing/index


Indices and tables
------------------

* :ref:`genindex`
* :ref:`search`
