Web Panel Dashboard Features
============================

Detailed documentation of the Lightning Web Panel dashboard features.

Control Plane Features
----------------------

Cluster Overview
~~~~~~~~~~~~~~~~

The Cluster Overview panel displays:

- **Total Nodes** - Number of registered nodes
- **Healthy Nodes** - Nodes with HEALTHY status
- **Draining Nodes** - Nodes being drained for maintenance
- **Capacity** - Total vs. used container capacity
- **Saturation** - Average cluster load percentage
- **Recent Matches** - Last 10 created matches

Nodes Panel
~~~~~~~~~~~

View and manage cluster nodes:

**Node Information:**

- Node ID
- Status (HEALTHY, DRAINING, OFFLINE)
- Advertise address
- Container count
- Match count
- CPU usage percentage
- Memory usage (used/max)
- Registration time
- Last heartbeat

**Actions:**

- Drain node (prevents new deployments)
- Remove node from cluster

Cluster Matches Panel
~~~~~~~~~~~~~~~~~~~~~

View all matches across the cluster:

**Filters:**

- Status: CREATING, RUNNING, FINISHED, ERROR
- Node ID
- Module names

**Match Information:**

- Match ID (format: ``node-container-match``)
- Node location
- Container ID
- Status
- Module list
- Player count
- WebSocket URL
- Created timestamp

Deployments Panel
~~~~~~~~~~~~~~~~~

Manage game deployments:

**Create Deployment:**

1. Select modules from available list
2. Optionally specify preferred node
3. Choose auto-start behavior

**Deployment Status:**

- Match ID
- Node assignment
- Container ID
- Status
- HTTP and WebSocket endpoints

Autoscaler Panel
~~~~~~~~~~~~~~~~

Configure automatic scaling:

**Settings:**

- Enable/disable autoscaler
- Minimum nodes
- Maximum nodes
- Scale-up threshold (saturation %)
- Scale-down threshold
- Cooldown period

**Recommendations:**

- Current action recommendation (SCALE_UP, SCALE_DOWN, NO_ACTION)
- Reason for recommendation
- Cooldown status

Engine (Node) Features
----------------------

Container Dashboard
~~~~~~~~~~~~~~~~~~~

Visual management of execution containers:

**Container Cards:**

Each container displays as a card showing:

- Container name and ID
- Status indicator (RUNNING = green, PAUSED = yellow, STOPPED = red)
- Current tick number
- Match count
- Module count
- Start/Stop/Delete buttons

**Card Actions:**

- Click card to select container
- Start - Initialize and start the container
- Stop - Stop the container
- Delete - Remove container (only when stopped)

**Control Panel (for selected container):**

- **Step** - Advance one tick manually
- **Play (60 FPS)** - Start auto-advance at 60 ticks/second
- **Play (30 FPS)** - Start auto-advance at 30 ticks/second
- **Pause** - Stop auto-advance

**Statistics:**

- Entity count vs. maximum
- ECS memory usage
- JVM heap usage
- Loaded module count

**Create Container Dialog:**

- Container name (required)
- Max memory (MB, optional)
- Module selection (multi-select)
- AI selection (multi-select)

Matches Panel
~~~~~~~~~~~~~

Manage matches within the selected container:

**Create Match:**

1. Specify match ID (auto-generated if empty)
2. Select modules to enable
3. Select AI backends to enable

**Match List:**

- Match ID
- Status
- Tick number
- Player count
- Module list
- Delete action

Players Panel
~~~~~~~~~~~~~

Manage player registrations:

**Create Player:**

- Player ID (auto-assigned if empty)
- Player name
- External ID (for linking to external systems)

**Player List:**

- Player ID
- Name
- External ID
- Created timestamp
- Delete action

Sessions Panel
~~~~~~~~~~~~~~

Track player sessions:

**Session Information:**

- Session ID
- Player ID
- Match ID
- Status (CONNECTED, DISCONNECTED, ABANDONED)
- Connected timestamp
- Disconnected timestamp

**Actions:**

- Reconnect - Reconnect a disconnected session
- Disconnect - Mark session as disconnected
- Abandon - Mark session as permanently abandoned

Commands Panel
~~~~~~~~~~~~~~

Send commands to matches:

**Command Form:**

1. Select command from available list
2. Command description and parameters shown
3. Fill parameter values
4. Specify target match ID
5. Optionally specify player ID
6. Send command

**Available Commands:**

Displays all commands registered by loaded modules with:

- Command name
- Description
- Parameters (name, type, required)
- Source module

Live Snapshot Panel
~~~~~~~~~~~~~~~~~~~

Real-time ECS state visualization:

**Features:**

- WebSocket connection to selected match
- Auto-reconnect on disconnect
- Snapshot data organized by module
- Component values displayed per entity
- Tick number and timestamp

**Display Modes:**

- Table view (default)
- JSON view (raw data)
- Delta view (changes only)

**Controls:**

- Match selector
- Connect/Disconnect
- Pause/Resume updates

History Panel
~~~~~~~~~~~~~

Browse historical snapshots:

**Navigation:**

- Match selector
- Tick range slider
- Previous/Next tick buttons
- Jump to specific tick

**Snapshot Display:**

- Same format as Live Snapshot
- Comparison with previous tick

Logs Panel
~~~~~~~~~~

View container logs:

**Features:**

- Real-time log streaming
- Log level filters (DEBUG, INFO, WARN, ERROR)
- Search/filter text
- Auto-scroll toggle

Metrics Panel
~~~~~~~~~~~~~

Performance monitoring:

**Tick Metrics:**

- Last tick duration (ms and ns)
- Average tick duration
- Min/Max tick duration
- Total tick count

**System Metrics:**

- Per-system execution time
- Success/failure rate

**Command Metrics:**

- Per-command execution time
- Success/failure rate

**Snapshot Metrics:**

- Generation count
- Cache hit rate
- Incremental update rate
- Average generation time

Modules Panel
~~~~~~~~~~~~~

Manage modules in the container:

**Available Modules:**

- Module name
- Version
- Description
- Components provided
- Commands provided
- Source (builtin/jar)

**Actions:**

- Upload new module JAR
- Uninstall module
- Reload all modules

AI Panel
~~~~~~~~

Configure AI/game master backends:

**Available AIs:**

- AI name
- Version
- Description
- Required modules

**Actions:**

- Upload new AI JAR
- Uninstall AI
- Reload all AIs

Resources Panel
~~~~~~~~~~~~~~~

Manage game resources:

**Upload Resource:**

- File upload
- Name override
- Chunked upload for large files

**Resource List:**

- Resource ID
- Name
- MIME type
- Size
- Chunked status
- Created timestamp
- Download/Delete actions

Authentication Features
-----------------------

Users Panel
~~~~~~~~~~~

User account management:

**Create User:**

- Username
- Password
- Role assignment

**User List:**

- User ID
- Username
- Email
- Roles
- Enabled status
- Created timestamp
- Last login

**Actions:**

- Change password
- Modify roles
- Enable/Disable account
- Delete user

Roles Panel
~~~~~~~~~~~

Role-based access control:

**Create Role:**

- Role name
- Description
- Included roles (inheritance)
- Scopes (permissions)

**Role List:**

- Role ID
- Name
- Description
- Included roles
- Assigned scopes
- Resolved scopes (expanded)

API Tokens Panel
~~~~~~~~~~~~~~~~

Service authentication tokens:

**Create Token:**

- Token name
- Scopes
- Expiration (optional)

**Token List:**

- Token ID
- Name
- Scopes
- Created timestamp
- Expiration
- Last used
- Active status

**Actions:**

- Revoke token

.. note::

   The plaintext token is only shown once at creation time.
   Store it securely!

Keyboard Shortcuts
------------------

.. list-table::
   :header-rows: 1
   :widths: 20 80

   * - Shortcut
     - Action
   * - ``Esc``
     - Close open dialogs
   * - ``/``
     - Focus search (where available)

Troubleshooting
---------------

**WebSocket won't connect:**

- Check that the backend is running
- Verify the match exists and container is RUNNING
- Check browser console for connection errors

**Data not refreshing:**

- RTK Query polls every 2 seconds by default
- Click refresh button for immediate update
- Check network tab for failed requests

**Authentication errors:**

- Token may have expired - login again
- Check role permissions for the operation
