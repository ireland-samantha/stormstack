Lightning Tools
===============

Lightning is the client tooling suite for StormStack, providing multiple interfaces
for managing and interacting with Thunder backend services.

.. toctree::
   :maxdepth: 2
   :caption: Lightning Components

   cli/index
   webpanel/index
   rendering/index

Overview
--------

Lightning consists of three main components:

**Lightning CLI**
   A command-line interface built with Go and Cobra. Use it for:

   - Deploying matches to the cluster
   - Managing cluster nodes and health
   - Joining matches and sending commands
   - Streaming snapshots via WebSocket
   - Scripting and automation with JSON output

**Lightning Web Panel**
   A React-based admin dashboard with Material-UI. Use it for:

   - Visual cluster management and monitoring
   - Container and match lifecycle management
   - Real-time snapshot visualization
   - User and role administration
   - Resource and module management

**Lightning Rendering Engine**
   A Java-based GUI framework using LWJGL and NanoVG. Use it for:

   - Building game visualization clients
   - Custom debugging and monitoring tools
   - Headless testing of GUI components

Choosing Your Interface
-----------------------

.. list-table::
   :header-rows: 1
   :widths: 20 40 40

   * - Use Case
     - Recommended Tool
     - Why
   * - Quick deployment
     - CLI
     - One command: ``lightning deploy --modules X,Y``
   * - Monitoring cluster
     - Web Panel
     - Visual dashboard with real-time updates
   * - Automation/CI
     - CLI with ``-o json``
     - Machine-readable output for scripting
   * - Debugging matches
     - Web Panel
     - Live snapshot viewer, command panel
   * - Game visualization
     - Rendering Engine
     - Native OpenGL rendering for game clients

Quick Start
-----------

**CLI Quick Start:**

.. code-block:: bash

   # Install CLI (requires Go 1.24+)
   cd lightning/cli && go build -o lightning ./cmd/lightning

   # Configure and authenticate
   lightning config set control_plane_url http://localhost:8081
   lightning auth login

   # Deploy your first match
   lightning deploy --modules EntityModule,HealthModule

**Web Panel Quick Start:**

.. code-block:: bash

   # Build and start (requires Node.js)
   cd lightning/webpanel
   npm install && npm run dev

   # Open http://localhost:5173
   # Login with admin/admin (default credentials)

See the individual component documentation for detailed usage guides.
