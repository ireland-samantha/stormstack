# Frontend (Web Dashboard)

The Lightning Web Panel is a React TypeScript application built with Vite, Material UI, and RTK Query. It provides a comprehensive admin interface for managing Thunder Engine clusters.

## Location

```
lightning/webpanel/
```

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| React | 19.x | UI framework |
| TypeScript | 5.x | Type safety |
| Vite | 7.x | Build tool and dev server |
| Material UI | 7.x | Component library |
| RTK Query | 2.x | API data fetching and caching |
| Vitest | 4.x | Testing framework |
| MSW | 2.x | API mocking for tests |

## Development

### Prerequisites

- Node.js 22+ (LTS recommended)
- npm 10+

### Install Dependencies

```bash
cd lightning/webpanel
npm install
```

### Run Development Server

```bash
npm run dev
```

Opens at http://localhost:5173 with hot module replacement (HMR).

**Note:** The dev server proxies API requests to the backend services. Start the cluster first:

```bash
# From project root
docker compose up -d
```

Or run services individually with `mvn quarkus:dev`.

### Build for Production

```bash
npm run build
```

Output is written to `dist/` and automatically copied to Quarkus resources during Maven build.

### Run Tests

```bash
# Watch mode (interactive)
npm test

# Single run
npm run test:run

# With coverage
npm run test:coverage
```

## Project Structure

```
src/
├── main.tsx              # Application entry point
├── App.tsx               # Root component with routing and layout
├── theme.ts              # Material UI theme configuration
├── components/           # UI components (40+)
│   ├── Login.tsx         # JWT authentication form
│   ├── ContainerDashboard.tsx  # Container lifecycle controls
│   ├── SnapshotPanel.tsx       # Live WebSocket snapshot viewer
│   ├── CommandsPanel.tsx       # REST + WebSocket command submission
│   ├── MatchesPanel.tsx        # Match CRUD
│   ├── PlayersPanel.tsx        # Player management
│   ├── SessionsPanel.tsx       # Session lifecycle
│   ├── HistoryPanel.tsx        # Snapshot history browser
│   ├── ModulesPanel.tsx        # Hot-reload module management
│   ├── AIPanel.tsx             # AI backend management
│   ├── ResourcesPanel.tsx      # Game asset management
│   ├── UsersPanel.tsx          # User CRUD (admin)
│   ├── RolesPanel.tsx          # Role hierarchy management
│   ├── ApiTokensPanel.tsx      # API token generation
│   ├── ClusterOverviewPanel.tsx    # Cluster health
│   ├── ClusterNodesPanel.tsx       # Node management
│   ├── ClusterMatchesPanel.tsx     # Cluster-wide matches
│   ├── ClusterModulesPanel.tsx     # Module distribution
│   ├── DeploymentsPanel.tsx        # Match deployment
│   ├── AutoscalerPanel.tsx         # Autoscaling recommendations
│   ├── CommandForm.tsx             # Dynamic command form
│   ├── SimulationControls.tsx      # Tick/play/stop controls
│   └── NotificationProvider.tsx    # Global toast notifications
├── services/
│   ├── api.ts            # TypeScript API type definitions
│   ├── websocket.ts      # Snapshot WebSocket client
│   └── commandWebSocket.ts   # Command WebSocket client
├── hooks/
│   ├── useSnapshot.ts         # Snapshot WebSocket hook
│   ├── useCommandWebSocket.ts # Command WebSocket hook
│   └── useContainerErrors.ts  # Error stream hook
├── store/
│   ├── store.ts          # Redux store configuration
│   ├── hooks.ts          # Typed Redux hooks
│   ├── api/
│   │   └── apiSlice.ts   # RTK Query API (82+ endpoints)
│   └── slices/
│       ├── authSlice.ts      # Authentication state
│       ├── uiSlice.ts        # UI navigation state
│       └── notificationSlice.ts  # Notification queue
├── contexts/
│   └── ContainerContext.tsx  # Container-scoped state
└── test/
    ├── setup.ts          # Vitest + MSW setup
    ├── testUtils.tsx     # Render helpers
    └── mocks/
        ├── handlers.ts   # MSW request handlers (40+ routes)
        └── server.ts     # MSW server instance
```

## Key Components

### Engine Panels (Local Container Management)

| Component | Purpose |
|-----------|---------|
| **ContainerDashboard** | Container lifecycle (create, start, stop, pause, resume), stats, tick control |
| **SnapshotPanel** | Live WebSocket snapshot streaming, module/component tree view |
| **CommandsPanel** | Dual-mode command submission (REST + WebSocket), dynamic forms |
| **MatchesPanel** | Match CRUD within a container |
| **PlayersPanel** | Player creation and management |
| **SessionsPanel** | Session lifecycle (connect, disconnect, reconnect, abandon) |
| **HistoryPanel** | Snapshot history browser, tick-range queries |
| **ModulesPanel** | Module listing, hot-reload trigger |

### Control Plane Panels (Cluster Management)

| Component | Purpose |
|-----------|---------|
| **ClusterOverviewPanel** | Cluster health, node count, capacity |
| **ClusterNodesPanel** | Node list, drain, deregister |
| **ClusterMatchesPanel** | Cluster-wide match lifecycle |
| **ClusterModulesPanel** | Module registry, version tracking, distribution |
| **DeploymentsPanel** | Deploy matches to cluster |
| **AutoscalerPanel** | Scaling recommendations, acknowledgment |

### Auth Panels (Identity Management)

| Component | Purpose |
|-----------|---------|
| **Login** | JWT authentication form |
| **UsersPanel** | User CRUD, role assignment, password reset |
| **RolesPanel** | Role creation, scope assignment, hierarchy |
| **ApiTokensPanel** | API token generation, revocation |

## API Integration

All API calls use RTK Query. Endpoints are defined in `store/apiSlice.ts`:

```typescript
// Example: fetch containers
const { data: containers, isLoading, error } = useGetContainersQuery();

// Example: create match
const [createMatch, { isLoading }] = useCreateMatchMutation();
await createMatch({ containerId, enabledModuleNames: ['EntityModule'] });
```

### Container-Scoped Endpoints

Most operations are container-scoped:

```typescript
// List matches in container
useGetMatchesQuery(containerId);

// Get snapshot for match in container
useGetSnapshotQuery({ containerId, matchId });

// Send command to container
useSendCommandMutation({ containerId, commandName, parameters });
```

## Testing

Tests use Vitest with React Testing Library and MSW for API mocking.

### Running Specific Tests

```bash
# Run tests matching pattern
npm test -- AIPanel

# Run single file
npm test -- src/components/AIPanel.test.tsx
```

### Test Utilities

```typescript
import { renderWithProviders } from '../test/testUtils';

test('renders panel', () => {
  renderWithProviders(<MyPanel />);
  expect(screen.getByText('Expected Text')).toBeInTheDocument();
});
```

## Maven Integration

The frontend is built as part of the Thunder Engine provider module:

```bash
# Build everything including frontend
./build.sh build

# Build just the webpanel
cd lightning/webpanel && npm run build
```

The build process:
1. `npm install` - Install dependencies
2. `npm run build` - Build production bundle
3. Copy output to `../resources/META-INF/resources/admin/dashboard/` (served by Quarkus)

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_URL` | `/api` | API base URL (dev server proxies to backend) |

## Styling

Uses Material UI's `sx` prop and styled components:

```typescript
<Box sx={{ display: 'flex', gap: 2, p: 2 }}>
  <Button variant="contained" color="primary">
    Action
  </Button>
</Box>
```

Theme customization is in `App.tsx`.

## Common Tasks

### Add a New Panel

1. Create component in `src/components/MyPanel.tsx`
2. Add RTK Query endpoints in `store/apiSlice.ts`
3. Add route in `App.tsx`
4. Create tests in `src/components/MyPanel.test.tsx`

### Add API Endpoint

```typescript
// In store/apiSlice.ts
getMyData: builder.query<MyDataType, number>({
  query: (containerId) => `/containers/${containerId}/mydata`,
  providesTags: (_, __, id) => [{ type: 'MyData', id }],
}),
```

### Mock API in Tests

```typescript
// In test file
import { server } from '../test/testUtils';
import { http, HttpResponse } from 'msw';

beforeEach(() => {
  server.use(
    http.get('/api/containers/:id/mydata', () => {
      return HttpResponse.json({ data: 'mocked' });
    })
  );
});
```
