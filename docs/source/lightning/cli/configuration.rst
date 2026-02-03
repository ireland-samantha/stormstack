CLI Configuration
=================

The Lightning CLI stores configuration in ``~/.lightning.yaml``.

Configuration File
------------------

**Location:** ``~/.lightning.yaml``

.. warning::

   The configuration file contains sensitive authentication tokens. For security,
   ensure the file has restricted permissions:

   .. code-block:: bash

      chmod 600 ~/.lightning.yaml

   This prevents other users on the system from reading your tokens.

**Example configuration:**

.. code-block:: yaml

   control_plane_url: http://localhost:8081
   auth_url: http://localhost:8082
   auth_token: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
   refresh_token: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
   output_format: table

   # Node context (set by 'lightning node context set')
   current_node_id: node-1
   current_engine_url: http://localhost:8080

   # Match context
   current_match_id: node-1-1-1
   current_container_id: 1

   # Node proxy setting
   use_node_proxy: true

   # WebSocket context (set by 'lightning match join')
   current_match_token: eyJ...
   current_command_ws_url: ws://localhost:8080/ws/commands/node-1-1-1
   current_snapshot_ws_url: ws://localhost:8080/ws/snapshots/node-1-1-1

Configuration Options
---------------------

.. list-table::
   :header-rows: 1
   :widths: 25 15 40 20

   * - Option
     - Type
     - Description
     - Default
   * - ``control_plane_url``
     - string
     - Thunder Control Plane URL
     - ``http://localhost:8081``
   * - ``auth_url``
     - string
     - Thunder Auth service URL
     - ``http://localhost:8082``
   * - ``auth_token``
     - string
     - JWT or API authentication token
     - (none)
   * - ``refresh_token``
     - string
     - JWT refresh token
     - (none)
   * - ``output_format``
     - string
     - Default output format
     - ``table``
   * - ``use_node_proxy``
     - boolean
     - Route node requests through Control Plane
     - ``true``

Context Options
---------------

These are set automatically by commands like ``node context set`` and ``match join``:

.. list-table::
   :header-rows: 1
   :widths: 30 50 20

   * - Option
     - Description
     - Set By
   * - ``current_node_id``
     - Currently targeted node
     - ``node context set``
   * - ``current_engine_url``
     - Direct URL to the node's engine
     - ``node context set``
   * - ``current_match_id``
     - Currently targeted match
     - ``node context set``
   * - ``current_container_id``
     - Container ID for current match
     - ``node context set``
   * - ``current_match_token``
     - Match token for WebSocket auth
     - ``match join``
   * - ``current_command_ws_url``
     - WebSocket URL for commands
     - ``match join``
   * - ``current_snapshot_ws_url``
     - WebSocket URL for snapshots
     - ``match join``

Setting Values
--------------

**Via CLI:**

.. code-block:: bash

   lightning config set control_plane_url http://prod.example.com:8081
   lightning config set output_format json

**Via Environment:**

Environment variables override config file values:

.. code-block:: bash

   export LIGHTNING_CONTROL_PLANE_URL=http://prod.example.com:8081
   export LIGHTNING_AUTH_TOKEN=lat_abc123...
   export LIGHTNING_OUTPUT_FORMAT=json

**Via Flags:**

Command-line flags override both config and environment:

.. code-block:: bash

   lightning cluster status --control-plane http://prod.example.com:8081 -o json

Precedence Order
----------------

Configuration is resolved in this order (highest priority first):

1. Command-line flags (``--control-plane``, ``-o``)
2. Environment variables (``LIGHTNING_*``)
3. Config file (``~/.lightning.yaml``)
4. Built-in defaults

Node Proxy Setting
------------------

The ``use_node_proxy`` option controls how the CLI communicates with nodes.

**When ``true`` (default):**

- Requests to nodes are routed through the Control Plane
- URL pattern: ``/api/nodes/{nodeId}/proxy/{path}``
- Works when nodes are on Docker-internal networks
- Recommended for most setups

**When ``false``:**

- Requests go directly to node's advertised address
- Requires network access to the node
- Lower latency, useful for local development

.. code-block:: bash

   # Enable direct node access
   lightning config set use_node_proxy false

Multiple Environments
---------------------

For managing multiple environments (dev, staging, production), use environment
variables or separate config files:

**Option 1: Environment Variables**

.. code-block:: bash

   # Development
   alias lightning-dev='LIGHTNING_CONTROL_PLANE_URL=http://localhost:8081 lightning'

   # Production
   alias lightning-prod='LIGHTNING_CONTROL_PLANE_URL=http://prod.example.com:8081 lightning'

**Option 2: Config File Switching**

.. code-block:: bash

   # Create environment-specific configs
   cp ~/.lightning.yaml ~/.lightning-dev.yaml
   cp ~/.lightning.yaml ~/.lightning-prod.yaml

   # Switch configs
   ln -sf ~/.lightning-prod.yaml ~/.lightning.yaml

Troubleshooting
---------------

**Token expired:**

.. code-block:: bash

   lightning auth login
   # Or refresh if using JWT
   lightning auth refresh

**Wrong environment:**

.. code-block:: bash

   # Check current config
   lightning config get control_plane_url

**Context issues:**

.. code-block:: bash

   # Clear and reset
   lightning node context clear
   lightning node context set node-1
