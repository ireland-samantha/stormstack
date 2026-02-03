Lightning Web Panel
===================

The Lightning Web Panel is a React-based admin dashboard for managing
StormStack clusters, containers, matches, and authentication.

.. toctree::
   :maxdepth: 2

   dashboard

Overview
--------

The Web Panel provides a visual interface for all StormStack operations,
organized into three main sections:

1. **Control Plane** - Cluster-wide management
2. **Engine (Node)** - Container and match operations
3. **Authentication** - User and role administration

Quick Start
-----------

**Prerequisites:**

- Node.js 18 or later
- npm

**Development mode:**

.. code-block:: bash

   cd lightning/webpanel
   npm install
   npm run dev

   # Open http://localhost:5173

**Production build:**

.. code-block:: bash

   npm run build
   # Outputs to dist/

**Default credentials:**

- Username: ``admin``
- Password: ``admin`` (change in production!)

Access via Docker
-----------------

When running the full stack with Docker Compose, the Web Panel is served
from the backend:

.. code-block:: bash

   docker compose up -d

   # Open http://localhost:8080/admin/dashboard

The Admin Dashboard section
---------------------------

Control Plane Section
~~~~~~~~~~~~~~~~~~~~~

Cluster-wide operations:

**Overview**
   Dashboard showing cluster health, node status, and recent matches.

**Nodes**
   List all registered nodes with health metrics (CPU, memory, container count).

**Matches**
   View all matches across the cluster with status filters.

**Modules**
   Manage modules available for deployment across the cluster.

**Deployments**
   Track and manage game deployments.

**Autoscaler**
   Configure automatic scaling rules based on load.

Engine (Node) Section
~~~~~~~~~~~~~~~~~~~~~

Operations on a selected node/container:

**Container Dashboard**
   Visual cards showing all containers with start/stop controls.

**Matches**
   CRUD operations for matches within the selected container.

**Players**
   Manage player registrations.

**Sessions**
   Track player sessions (connected, disconnected, abandoned).

**Commands**
   Send commands to matches with parameter forms.

**Live Snapshot**
   Real-time ECS state visualization via WebSocket.

**History**
   Browse historical snapshots for debugging.

**Logs**
   View container logs.

**Metrics**
   Performance metrics (tick timing, system execution times).

**Modules**
   Manage modules loaded in the container.

**AI**
   Configure AI/game master backends.

**Resources**
   Upload and manage game resources.

Authentication Section
~~~~~~~~~~~~~~~~~~~~~~

User and access management:

**Users**
   Create, edit, and disable user accounts.

**Roles**
   Define roles with scopes for RBAC.

**API Tokens**
   Generate API tokens for service authentication.

Technology Stack
----------------

The Web Panel is built with:

- **React 18** - UI framework
- **TypeScript** - Type safety
- **Material-UI (MUI)** - Component library
- **Redux Toolkit** - State management
- **RTK Query** - Data fetching with caching and polling
- **Vite** - Build tool

Key Features
------------

**Real-time Updates**
   RTK Query polling keeps data fresh without manual refresh.

**WebSocket Integration**
   Live snapshot viewer streams ECS state updates.

**Responsive Design**
   Works on desktop and mobile with collapsible sidebar.

**Type-Safe API Client**
   82+ API endpoints with full TypeScript definitions.

API Client
----------

The Web Panel includes a comprehensive API client at
``src/services/api.ts`` with methods for all Thunder endpoints:

.. code-block:: typescript

   // Example API usage
   import { apiClient } from './services/api';

   // Authentication
   await apiClient.login('admin', 'password');

   // Container operations
   const containers = await apiClient.getContainers();
   await apiClient.createContainer({ name: 'game-server-1' });
   await apiClient.startContainer(containerId);

   // Match operations
   const matches = await apiClient.getContainerMatches(containerId);
   await apiClient.createMatchInContainer(containerId, matchId, ['EntityModule']);

   // Snapshot operations
   const snapshot = await apiClient.getSnapshot(matchId);

Configuration
-------------

The Web Panel reads configuration from environment variables:

.. list-table::
   :header-rows: 1
   :widths: 30 50 20

   * - Variable
     - Description
     - Default
   * - ``VITE_API_URL``
     - Backend API URL
     - ``/api`` (relative)
   * - ``VITE_WS_URL``
     - WebSocket URL
     - (derived from location)

For development with a different backend:

.. code-block:: bash

   VITE_API_URL=http://localhost:8080/api npm run dev

Customization
-------------

**Theme**
   Edit ``src/theme.ts`` to customize colors and typography.

**Components**
   Add new panels in ``src/components/`` and register in ``App.tsx``.

**State Management**
   Add slices to ``src/store/slices/`` for new state.

See :doc:`dashboard` for detailed feature documentation.
