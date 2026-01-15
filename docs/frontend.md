# Frontend (Web Dashboard)

The web dashboard is a React TypeScript application built with Vite, Material UI, and RTK Query.

## Location

```
lightning-engine/webservice/quarkus-web-api/src/main/frontend/
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
cd lightning-engine/webservice/quarkus-web-api/src/main/frontend
npm install
```

### Run Development Server

```bash
npm run dev
```

Opens at http://localhost:5173 with hot module replacement (HMR).

**Note:** The dev server proxies API requests to http://localhost:8080. Start the backend first:

```bash
# From project root
./mvnw quarkus:dev -pl lightning-engine/webservice/quarkus-web-api
```

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
├── App.tsx               # Root component with routing
├── components/           # UI components
│   ├── AIPanel.tsx       # AI management
│   ├── CommandForm.tsx   # Command queue interface
│   ├── ContainerDashboard.tsx  # Container lifecycle controls
│   ├── ContainersPanel.tsx     # Container list
│   ├── HistoryPanel.tsx  # MongoDB snapshot history
│   ├── Login.tsx         # Authentication form
│   ├── MatchesPanel.tsx  # Match management
│   ├── ModulesPanel.tsx  # Module listing
│   ├── ResourcesPanel.tsx    # Resource management
│   ├── RolesPanel.tsx    # Role management (admin)
│   ├── SessionsPanel.tsx # Player sessions
│   ├── SimulationControls.tsx  # Tick/play/stop controls
│   ├── SnapshotPanel.tsx # Live snapshot viewer
│   └── UsersPanel.tsx    # User management (admin)
├── contexts/
│   └── ContainerContext.tsx  # Selected container state
├── store/
│   ├── store.ts          # Redux store configuration
│   └── apiSlice.ts       # RTK Query API definitions
└── test/
    └── testUtils.tsx     # Test utilities and providers
```

## Key Components

### ContainerDashboard

Main dashboard showing container lifecycle controls:
- Create/delete containers
- Start/stop/pause/resume containers
- Tick controls (step, play, stop)

### SnapshotPanel

Real-time snapshot viewer:
- WebSocket connection to container/match
- Tree view of ECS data by module/component
- Auto-refresh on tick updates

### CommandForm

Send commands to the game engine:
- Dropdown of available commands
- Dynamic form based on command schema
- JSON preview before send

### Login

JWT authentication:
- Username/password login
- Token stored in localStorage
- Role-based UI visibility

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

The frontend is built as part of the Quarkus module:

```bash
# Build backend + frontend
./mvnw package -pl lightning-engine/webservice/quarkus-web-api

# Skip frontend build
./mvnw package -pl lightning-engine/webservice/quarkus-web-api -Dskip.npm
```

The build process:
1. `npm install` - Install dependencies
2. `npm run build` - Build production bundle
3. Copy `dist/` to `target/classes/META-INF/resources/`

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
