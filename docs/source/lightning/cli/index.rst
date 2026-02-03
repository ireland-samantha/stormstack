Lightning CLI
=============

The Lightning CLI is a command-line interface for managing StormStack clusters,
deploying matches, and interacting with the Thunder Engine.

.. toctree::
   :maxdepth: 2

   commands
   configuration

Installation
------------

**Prerequisites:**

- Go 1.24 or later

**Build from source:**

.. code-block:: bash

   cd lightning/cli
   go build -o lightning ./cmd/lightning

   # Optionally add to PATH
   sudo mv lightning /usr/local/bin/

**Verify installation:**

.. code-block:: bash

   lightning version

Quick Start
-----------

1. **Configure the CLI:**

   .. code-block:: bash

      # Set Control Plane URL (default: http://localhost:8081)
      lightning config set control_plane_url http://localhost:8081

2. **Authenticate:**

   .. code-block:: bash

      # Interactive login
      lightning auth login

      # Or use API token
      lightning auth token lat_abc123...

3. **Check cluster health:**

   .. code-block:: bash

      lightning cluster status

4. **Deploy a match:**

   .. code-block:: bash

      lightning deploy --modules EntityModule,HealthModule

5. **Interact with the match:**

   .. code-block:: bash

      # List matches
      lightning match list

      # Get snapshot
      lightning snapshot get

Output Formats
--------------

The CLI supports multiple output formats via the ``-o`` flag:

.. list-table::
   :header-rows: 1
   :widths: 15 25 60

   * - Format
     - Flag
     - Use Case
   * - Table
     - ``-o table`` (default)
     - Human-readable output
   * - JSON
     - ``-o json``
     - Scripting, parsing with ``jq``
   * - YAML
     - ``-o yaml``
     - Configuration files
   * - Quiet
     - ``-o quiet``
     - Minimal output (IDs only)

**Example - JSON output:**

.. code-block:: bash

   lightning cluster nodes -o json | jq '.[] | select(.status == "HEALTHY")'

Global Flags
------------

These flags work with all commands:

.. option:: -o, --output <format>

   Output format: ``table``, ``json``, ``yaml``, or ``quiet``

.. option:: --control-plane <url>

   Override the Control Plane URL for this command

**Example:**

.. code-block:: bash

   lightning cluster status --control-plane http://prod.example.com:8081 -o json

Environment Variables
---------------------

.. list-table::
   :header-rows: 1
   :widths: 30 50 20

   * - Variable
     - Description
     - Default
   * - ``LIGHTNING_CONTROL_PLANE_URL``
     - Control Plane URL
     - ``http://localhost:8081``
   * - ``LIGHTNING_AUTH_TOKEN``
     - Authentication token
     - (none)
   * - ``LIGHTNING_OUTPUT_FORMAT``
     - Default output format
     - ``table``

Next Steps
----------

- :doc:`commands` - Complete command reference
- :doc:`configuration` - Configuration file details
- :doc:`/getting-started/quickstart` - Full quickstart guide
