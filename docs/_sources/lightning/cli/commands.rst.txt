CLI Command Reference
=====================

Complete reference for all Lightning CLI commands.

.. contents:: Command Groups
   :local:
   :depth: 2

auth - Authentication
---------------------

Manage CLI authentication and credentials.

.. program:: lightning auth login

lightning auth login
~~~~~~~~~~~~~~~~~~~~

Authenticate with username and password.

.. code-block:: bash

   lightning auth login

   # Non-interactive
   lightning auth login --username admin --password admin

.. option:: --username <user>

   Username for non-interactive login

.. option:: --password <pass>

   Password for non-interactive login

The CLI stores tokens in ``~/.lightning.yaml`` for future use.

.. program:: lightning auth token

lightning auth token
~~~~~~~~~~~~~~~~~~~~

Set an API token directly.

.. code-block:: bash

   lightning auth token lat_abc123def456...

Use this with API tokens generated from the Web Panel.

.. program:: lightning auth status

lightning auth status
~~~~~~~~~~~~~~~~~~~~~

Show current authentication status.

.. code-block:: bash

   lightning auth status
   # Output: Authenticated with JWT token

.. program:: lightning auth logout

lightning auth logout
~~~~~~~~~~~~~~~~~~~~~

Remove saved authentication.

.. code-block:: bash

   lightning auth logout

.. program:: lightning auth refresh

lightning auth refresh
~~~~~~~~~~~~~~~~~~~~~~

Refresh the authentication token using the stored refresh token.

.. code-block:: bash

   lightning auth refresh

This command uses the OAuth2 refresh_token grant type to obtain new access
and refresh tokens without requiring credentials. The refresh token must have
been obtained from a previous ``lightning auth login``.

Use this when your access token is expiring or has expired, to avoid having to
re-enter credentials.

deploy - Game Deployment
------------------------

Deploy and manage game matches on the cluster.

.. program:: lightning deploy

lightning deploy
~~~~~~~~~~~~~~~~

Deploy a new game match to the cluster.

.. code-block:: bash

   # Deploy with modules
   lightning deploy --modules EntityModule,HealthModule

   # Deploy to specific node
   lightning deploy --modules EntityModule --node node-1

   # JSON output for scripting
   lightning deploy --modules EntityModule -o json

.. option:: -m, --modules <list>

   Comma-separated list of modules to enable (required)

.. option:: -n, --node <node-id>

   Preferred node ID for deployment

.. option:: --auto-start

   Auto-start the container (default: true)

**Output includes:**

- Match ID
- Node ID
- Container ID
- HTTP endpoint
- WebSocket endpoint
- Commands endpoint

.. program:: lightning deploy undeploy

lightning deploy undeploy
~~~~~~~~~~~~~~~~~~~~~~~~~

Remove a deployed game from the cluster.

.. code-block:: bash

   lightning deploy undeploy node-1-42-7

.. program:: lightning deploy status

lightning deploy status
~~~~~~~~~~~~~~~~~~~~~~~

Get deployment status.

.. code-block:: bash

   lightning deploy status node-1-42-7 -o json

cluster - Cluster Management
----------------------------

Monitor and manage the Thunder Engine cluster.

.. program:: lightning cluster status

lightning cluster status
~~~~~~~~~~~~~~~~~~~~~~~~

Show cluster health overview.

.. code-block:: bash

   lightning cluster status

**Output includes:**

- Total nodes
- Healthy nodes
- Draining nodes
- Total capacity
- Used capacity
- Average saturation

.. program:: lightning cluster nodes

lightning cluster nodes
~~~~~~~~~~~~~~~~~~~~~~~

List all nodes in the cluster.

.. code-block:: bash

   lightning cluster nodes
   lightning cluster nodes -o json

**Output columns:**

- Node ID
- Status (HEALTHY, DRAINING, OFFLINE)
- Address
- Containers
- Matches
- CPU %
- Memory

.. program:: lightning cluster node

lightning cluster node
~~~~~~~~~~~~~~~~~~~~~~

Get details for a specific node.

.. code-block:: bash

   lightning cluster node node-1

match - Match Management
------------------------

Manage game matches in the cluster.

.. program:: lightning match list

lightning match list
~~~~~~~~~~~~~~~~~~~~

List all matches in the cluster.

.. code-block:: bash

   # List all matches
   lightning match list

   # Filter by status
   lightning match list --status RUNNING

.. option:: -s, --status <status>

   Filter by status: ``RUNNING``, ``FINISHED``, ``ERROR``

.. program:: lightning match get

lightning match get
~~~~~~~~~~~~~~~~~~~

Get details for a specific match.

.. code-block:: bash

   lightning match get node-1-42-7

.. program:: lightning match join

lightning match join
~~~~~~~~~~~~~~~~~~~~

Join a match and get a match token for WebSocket connections.

.. code-block:: bash

   lightning match join node-1-42-7 --player-name "Alice" --player-id "alice-001"

.. option:: -n, --player-name <name>

   Player display name (required)

.. option:: -p, --player-id <id>

   Player unique ID (required)

**Output includes:**

- Match token (stored for ``ws`` commands)
- Command WebSocket URL
- Snapshot WebSocket URL
- Token expiration time

After joining, use ``lightning ws connect`` to start streaming.

.. program:: lightning match finish

lightning match finish
~~~~~~~~~~~~~~~~~~~~~~

Mark a match as finished.

.. code-block:: bash

   lightning match finish node-1-42-7

.. program:: lightning match delete

lightning match delete
~~~~~~~~~~~~~~~~~~~~~~

Delete a match from the cluster.

.. code-block:: bash

   lightning match delete node-1-42-7

node - Node Operations
----------------------

Manage node context and direct node operations.

.. program:: lightning node context set

lightning node context set
~~~~~~~~~~~~~~~~~~~~~~~~~~

Set the current node context.

.. code-block:: bash

   lightning node context set node-1

After setting context, commands like ``snapshot`` and ``command`` operate on this node.

.. program:: lightning node context show

lightning node context show
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Show the current node context.

.. code-block:: bash

   lightning node context show

.. program:: lightning node context clear

lightning node context clear
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Clear the current node context.

.. code-block:: bash

   lightning node context clear

module - Module Management
--------------------------

Manage game modules.

.. program:: lightning module list

lightning module list
~~~~~~~~~~~~~~~~~~~~~

List available modules.

.. code-block:: bash

   lightning module list

.. program:: lightning module upload

lightning module upload
~~~~~~~~~~~~~~~~~~~~~~~

Upload a module JAR file.

.. code-block:: bash

   lightning module upload my-module.jar

command - Game Commands
-----------------------

Send commands to matches. Commands use the current match context set by
``lightning node context match``.

.. program:: lightning command send

lightning command send
~~~~~~~~~~~~~~~~~~~~~~

Send a command to a match using the current context.

.. code-block:: bash

   # First, set the match context
   lightning node context match node-1-42-7

   # Then send commands (context determines target match)
   lightning command send spawn '{"matchId":1,"playerId":1,"entityType":100}'
   lightning command send damage '{"entityId":1,"amount":25.0}'

The first argument is the command name, and the second argument (optional)
is a JSON string containing the command parameters.

snapshot - ECS Snapshots
------------------------

Get ECS state snapshots. Snapshots use the current match context set by
``lightning node context match``.

.. program:: lightning snapshot get

lightning snapshot get
~~~~~~~~~~~~~~~~~~~~~~

Get current snapshot for a match using the current context.

.. code-block:: bash

   # First, set the match context
   lightning node context match node-1-42-7

   # Then get snapshot (context determines target match)
   lightning snapshot get

   # With JSON output
   lightning snapshot get -o json

ws - WebSocket Connections
--------------------------

Manage real-time WebSocket connections.

.. program:: lightning ws connect snapshot

lightning ws connect snapshot
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Connect to snapshot WebSocket stream.

.. code-block:: bash

   # Uses context from 'match join'
   lightning ws connect snapshot

.. program:: lightning ws connect command

lightning ws connect command
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Connect to command WebSocket for sending commands.

.. code-block:: bash

   lightning ws connect command

config - CLI Configuration
--------------------------

Manage CLI configuration.

.. program:: lightning config set

lightning config set
~~~~~~~~~~~~~~~~~~~~

Set a configuration value.

.. code-block:: bash

   lightning config set control_plane_url http://prod.example.com:8081
   lightning config set output_format json

.. program:: lightning config get

lightning config get
~~~~~~~~~~~~~~~~~~~~

Get a configuration value.

.. code-block:: bash

   lightning config get control_plane_url

version - Version Information
-----------------------------

.. program:: lightning version

lightning version
~~~~~~~~~~~~~~~~~

Show CLI version information.

.. code-block:: bash

   lightning version

Common Workflows
----------------

**Deploy and Monitor:**

.. code-block:: bash

   # Deploy
   MATCH=$(lightning deploy --modules Entity,Health -o quiet)
   echo "Deployed: $MATCH"

   # Monitor
   lightning match get $MATCH -o json | jq '.status'

**Join and Interact:**

.. code-block:: bash

   # Join match
   lightning match join $MATCH -n "Player1" -p "p1"

   # Set context for the match
   lightning node context match $MATCH

   # Send commands (uses context)
   lightning command send spawn '{"matchId":1,"playerId":1,"entityType":100}'

   # Watch snapshots
   lightning ws connect snapshot

**Scripted Health Check:**

.. code-block:: bash

   # Check all nodes are healthy
   UNHEALTHY=$(lightning cluster nodes -o json | jq '[.[] | select(.status != "HEALTHY")] | length')
   if [ "$UNHEALTHY" -gt 0 ]; then
     echo "WARNING: $UNHEALTHY unhealthy nodes"
     exit 1
   fi
