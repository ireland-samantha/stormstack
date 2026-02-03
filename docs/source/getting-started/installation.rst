============
Installation
============

This guide covers installing StormStack and its prerequisites.

Prerequisites
-------------

Required Software
~~~~~~~~~~~~~~~~~

* **Docker** 20.10+ and **Docker Compose** v2
* **Go** 1.24+ (for building the Lightning CLI)

Optional (for development from source):

* **Java** 25 (with preview features enabled)
* **Maven** 3.9+
* **Node.js** 18+ and npm (for frontend development)
* **MongoDB** 6.0+ (if not using Docker)

Verify Prerequisites
~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   # Check Docker
   docker --version
   docker compose version

   # Check Go
   go version

Quick Install (Docker Compose)
------------------------------

This is the recommended way to get started. Docker Compose will run all services for you.

1. Clone the Repository
~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   git clone https://github.com/ireland-samantha/lightning-engine.git
   cd lightning-engine

2. Configure Environment
~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   # Copy the example environment file
   cp .env.example .env

   # Edit .env and set these required values:
   # - ADMIN_INITIAL_PASSWORD: Password for the admin user
   # - AUTH_JWT_SECRET: A long random string (32+ characters)

3. Start Services
~~~~~~~~~~~~~~~~~

.. code-block:: bash

   # Start all services
   docker compose up -d

This starts:

* **mongodb** (port 27017) - Database for all services
* **redis** (port 6379) - Control plane node registry
* **auth** (port 8082) - Authentication service
* **control-plane** (port 8081) - Cluster management
* **backend** (port 8080) - Thunder Engine game server

4. Build the Lightning CLI
~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   cd lightning/cli
   go build -o lightning ./cmd/lightning
   cd ../..

   # Optionally add to PATH
   sudo mv lightning/cli/lightning /usr/local/bin/

5. Verify Installation
~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: bash

   # Check CLI
   lightning --help

   # Check services are running
   docker compose ps

   # Check cluster health (after configuring CLI - see Quickstart)
   lightning cluster status

Multi-Node Cluster
------------------

For a multi-node cluster setup:

.. code-block:: bash

   docker compose --profile cluster up -d

This adds additional engine nodes (node-2, node-3) for distributed game hosting.

Build from Source (Development)
-------------------------------

For contributors who want to build from source:

1. Install all prerequisites (Java 25, Maven, Node.js)
2. Build all modules:

.. code-block:: bash

   ./build.sh build    # Build all modules (skip tests)
   ./build.sh test     # Run unit tests
   ./build.sh all      # Full pipeline

3. Start MongoDB:

.. code-block:: bash

   docker run -d --name mongodb -p 27017:27017 mongo:6.0

4. Run the backend in dev mode:

.. code-block:: bash

   export ADMIN_INITIAL_PASSWORD=dev-password
   export QUARKUS_MONGODB_CONNECTION_STRING=mongodb://localhost:27017
   cd thunder/engine/provider
   mvn quarkus:dev

Environment Variables
---------------------

.. list-table::
   :header-rows: 1
   :widths: 30 10 60

   * - Variable
     - Required
     - Description
   * - ``ADMIN_INITIAL_PASSWORD``
     - Yes
     - Password for the admin user
   * - ``AUTH_JWT_SECRET``
     - Yes
     - Secret for JWT signing (use a long random string, 32+ chars)
   * - ``CONTROL_PLANE_TOKEN``
     - No
     - Token for control plane API access (default: ``dev-token``)
   * - ``CORS_ORIGINS``
     - Production
     - Allowed CORS origins (e.g., ``https://yourdomain.com``)

Troubleshooting
---------------

Services won't start
~~~~~~~~~~~~~~~~~~~~

1. Check Docker is running: ``docker info``
2. Check for port conflicts: ``docker compose logs``
3. Ensure ``.env`` file exists with required variables

CLI can't connect
~~~~~~~~~~~~~~~~~

1. Verify services are running: ``docker compose ps``
2. Check the control plane URL: ``lightning config get control_plane_url``
3. Verify authentication: ``lightning auth status``

Next Steps
----------

Continue to :doc:`quickstart` to deploy your first game match!
