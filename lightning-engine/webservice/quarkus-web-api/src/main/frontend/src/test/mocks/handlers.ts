/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { delay, http, HttpResponse } from "msw";

// Mock data
const mockContainers = [
  {
    id: 1,
    name: "default",
    status: "RUNNING" as const,
    currentTick: 100,
    autoAdvancing: false,
    autoAdvanceIntervalMs: 16,
  },
  {
    id: 2,
    name: "test",
    status: "STOPPED" as const,
    currentTick: 0,
    autoAdvancing: false,
    autoAdvanceIntervalMs: 16,
  },
];

const mockMatches = [
  {
    id: 1,
    name: "Match 1",
    status: "active",
    tick: 50,
    modules: ["EntityModule", "RigidBodyModule"],
  },
  {
    id: 2,
    name: "Match 2",
    status: "active",
    tick: 25,
    modules: ["EntityModule"],
  },
];

const mockUsers = [
  { id: 1, username: "admin", roles: ["admin"], enabled: true },
  { id: 2, username: "viewer", roles: ["view_only"], enabled: true },
];

const mockRoles = [
  {
    id: 1,
    name: "admin",
    description: "Full access",
    includedRoles: ["view_only", "command_manager"],
    scopes: ["*"],
  },
  {
    id: 2,
    name: "view_only",
    description: "Read-only access",
    includedRoles: [],
    scopes: ["engine.snapshot.view", "controlplane.cluster.read"],
  },
  {
    id: 3,
    name: "command_manager",
    description: "Can execute commands",
    includedRoles: ["view_only"],
    scopes: ["engine.command.submit", "engine.match.read"],
  },
];

const mockPlayers = [
  { id: 1, name: "Player 1", externalId: "ext-1" },
  { id: 2, name: "Player 2", externalId: "ext-2" },
];

const mockSessions = [
  { id: 1, playerId: 1, matchId: 1, status: "CONNECTED" as const },
  { id: 2, playerId: 2, matchId: 1, status: "DISCONNECTED" as const },
];

const mockCommands = [
  {
    name: "spawn",
    description: "Spawn an entity",
    module: "EntityModule",
    parameters: [
      {
        name: "type",
        type: "number",
        required: true,
        description: "Entity type",
      },
      { name: "x", type: "number", required: false, description: "X position" },
      { name: "y", type: "number", required: false, description: "Y position" },
    ],
  },
  {
    name: "move",
    description: "Move an entity",
    module: "RigidBodyModule",
    parameters: [
      { name: "entityId", type: "number", required: true },
      { name: "dx", type: "number", required: true },
      { name: "dy", type: "number", required: true },
    ],
  },
];

const mockModules = [
  {
    name: "EntityModule",
    version: "1.0.0",
    description: "Core entity management",
    components: ["ENTITY_ID", "OWNER"],
    source: "builtin" as const,
  },
  {
    name: "RigidBodyModule",
    version: "1.0.0",
    description: "Physics simulation",
    components: ["POSITION_X", "POSITION_Y", "VELOCITY_X", "VELOCITY_Y"],
    source: "builtin" as const,
  },
];

const mockAIs = [
  {
    name: "TickCounter",
    version: "1.0.0",
    description: "Counts ticks",
    requiredModules: [],
    source: "builtin" as const,
  },
];

const mockResources = [
  {
    id: "res-1",
    name: "sprite.png",
    contentType: "image/png",
    mimeType: "image/png",
    size: 1024,
    createdAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "res-2",
    name: "sound.wav",
    contentType: "audio/wav",
    mimeType: "audio/wav",
    size: 2048,
    createdAt: "2026-01-02T00:00:00Z",
  },
];

const mockSnapshot = {
  matchId: 1,
  tick: 50,
  modules: [
    {
      name: "EntityModule",
      version: "1.0",
      components: [
        { name: "ENTITY_ID", values: [1, 2, 3] },
        { name: "OWNER", values: [1, 1, 2] },
      ],
    },
    {
      name: "RigidBodyModule",
      version: "1.0",
      components: [
        { name: "POSITION_X", values: [100, 200, 300] },
        { name: "POSITION_Y", values: [50, 60, 70] },
      ],
    },
  ],
};

export const handlers = [
  // Auth
  http.post("/api/auth/login", async ({ request }) => {
    await delay(100);
    const body = (await request.json()) as {
      username: string;
      password: string;
    };
    if (body.username === "admin" && body.password === "admin") {
      return HttpResponse.json({
        token: "mock-jwt-token",
        refreshToken: "mock-refresh-token",
      });
    }
    return HttpResponse.json({ error: "Invalid credentials" }, { status: 401 });
  }),

  http.get("/api/auth/me", async () => {
    await delay(50);
    return HttpResponse.json({
      id: 1,
      username: "admin",
      roles: ["admin"],
      enabled: true,
    });
  }),

  // Containers
  http.get("/api/containers", async () => {
    await delay(100);
    return HttpResponse.json(mockContainers);
  }),

  http.get("/api/containers/:id", async ({ params }) => {
    await delay(50);
    const container = mockContainers.find((c) => c.id === Number(params.id));
    if (!container) {
      return HttpResponse.json({ error: "Not found" }, { status: 404 });
    }
    return HttpResponse.json(container);
  }),

  http.post("/api/containers", async ({ request }) => {
    await delay(100);
    const body = (await request.json()) as { name: string };
    const newContainer = {
      id: mockContainers.length + 1,
      name: body.name,
      status: "CREATED" as const,
      currentTick: 0,
      autoAdvancing: false,
      autoAdvanceIntervalMs: 16,
    };
    return HttpResponse.json(newContainer, { status: 201 });
  }),

  http.delete("/api/containers/:id", async () => {
    await delay(50);
    return new HttpResponse(null, { status: 204 });
  }),

  http.post("/api/containers/:id/start", async ({ params }) => {
    await delay(100);
    const container = mockContainers.find((c) => c.id === Number(params.id));
    if (!container) {
      return HttpResponse.json({ error: "Not found" }, { status: 404 });
    }
    return HttpResponse.json({ ...container, status: "RUNNING" });
  }),

  http.post("/api/containers/:id/stop", async ({ params }) => {
    await delay(100);
    const container = mockContainers.find((c) => c.id === Number(params.id));
    if (!container) {
      return HttpResponse.json({ error: "Not found" }, { status: 404 });
    }
    return HttpResponse.json({ ...container, status: "STOPPED" });
  }),

  http.post("/api/containers/:id/tick", async ({ params }) => {
    await delay(50);
    const container = mockContainers.find((c) => c.id === Number(params.id));
    if (!container) {
      return HttpResponse.json({ error: "Not found" }, { status: 404 });
    }
    return HttpResponse.json({ tick: container.currentTick + 1 });
  }),

  http.post("/api/containers/:id/play", async ({ params }) => {
    await delay(50);
    const container = mockContainers.find((c) => c.id === Number(params.id));
    if (!container) {
      return HttpResponse.json({ error: "Not found" }, { status: 404 });
    }
    return HttpResponse.json({ ...container, autoAdvancing: true });
  }),

  http.post("/api/containers/:id/stop-auto", async ({ params }) => {
    await delay(50);
    const container = mockContainers.find((c) => c.id === Number(params.id));
    if (!container) {
      return HttpResponse.json({ error: "Not found" }, { status: 404 });
    }
    return HttpResponse.json({ ...container, autoAdvancing: false });
  }),

  // Container Matches
  http.get("/api/containers/:id/matches", async () => {
    await delay(100);
    return HttpResponse.json(mockMatches);
  }),

  http.post("/api/containers/:containerId/matches", async ({ request }) => {
    await delay(100);
    const body = (await request.json()) as {
      id?: number;
      enabledModuleNames?: string[];
    };
    const newMatch = {
      id: body.id || mockMatches.length + 1,
      name: `Match ${body.id || mockMatches.length + 1}`,
      status: "active",
      tick: 0,
      modules: body.enabledModuleNames || [],
    };
    return HttpResponse.json(newMatch, { status: 201 });
  }),

  http.delete("/api/containers/:containerId/matches/:matchId", async () => {
    await delay(50);
    return new HttpResponse(null, { status: 204 });
  }),

  // Users
  http.get("/api/auth/users", async () => {
    await delay(100);
    return HttpResponse.json(mockUsers);
  }),

  http.post("/api/auth/users", async ({ request }) => {
    await delay(100);
    const body = (await request.json()) as {
      username: string;
      password: string;
      roles?: string[];
    };
    const newUser = {
      id: mockUsers.length + 1,
      username: body.username,
      roles: body.roles || [],
      enabled: true,
    };
    return HttpResponse.json(newUser, { status: 201 });
  }),

  http.delete("/api/auth/users/:id", async () => {
    await delay(50);
    return new HttpResponse(null, { status: 204 });
  }),

  // Roles
  http.get("/api/auth/roles", async () => {
    await delay(100);
    return HttpResponse.json(mockRoles);
  }),

  http.post("/api/auth/roles", async ({ request }) => {
    await delay(100);
    const body = (await request.json()) as {
      name: string;
      description?: string;
      includes?: string[];
    };
    const newRole = {
      id: mockRoles.length + 1,
      name: body.name,
      description: body.description || "",
      includes: body.includes || [],
    };
    return HttpResponse.json(newRole, { status: 201 });
  }),

  http.delete("/api/auth/roles/:id", async () => {
    await delay(50);
    return new HttpResponse(null, { status: 204 });
  }),

  // Players
  http.get("/api/containers/:containerId/players", async () => {
    await delay(100);
    return HttpResponse.json(mockPlayers);
  }),

  http.post("/api/containers/:containerId/players", async ({ request }) => {
    await delay(100);
    const body = (await request.json()) as { id?: number };
    const newPlayer = {
      id: body.id || mockPlayers.length + 1,
      name: `Player ${body.id || mockPlayers.length + 1}`,
    };
    return HttpResponse.json(newPlayer, { status: 201 });
  }),

  http.delete("/api/containers/:containerId/players/:playerId", async () => {
    await delay(50);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(
    "/api/containers/:containerId/matches/:matchId/players",
    async ({ params }) => {
      await delay(100);
      // Return player-match data for match 1
      if (Number(params.matchId) === 1) {
        return HttpResponse.json([
          { playerId: 1, matchId: 1 },
          { playerId: 2, matchId: 1 },
        ]);
      }
      return HttpResponse.json([]);
    },
  ),

  // Sessions
  http.get("/api/containers/:containerId/sessions", async () => {
    await delay(100);
    return HttpResponse.json(mockSessions);
  }),

  http.post(
    "/api/containers/:containerId/matches/:matchId/sessions",
    async ({ request }) => {
      await delay(100);
      const body = (await request.json()) as { playerId: number };
      const newSession = {
        id: mockSessions.length + 1,
        playerId: body.playerId,
        matchId: 1,
        status: "CONNECTED" as const,
      };
      return HttpResponse.json(newSession, { status: 201 });
    },
  ),

  http.post(
    "/api/containers/:containerId/matches/:matchId/sessions/:playerId/disconnect",
    async () => {
      await delay(50);
      return new HttpResponse(null, { status: 204 });
    },
  ),

  http.post(
    "/api/containers/:containerId/matches/:matchId/sessions/:playerId/reconnect",
    async ({ params }) => {
      await delay(50);
      return HttpResponse.json({
        id: 1,
        playerId: Number(params.playerId),
        matchId: Number(params.matchId),
        status: "CONNECTED" as const,
      });
    },
  ),

  http.post(
    "/api/containers/:containerId/matches/:matchId/sessions/:playerId/abandon",
    async () => {
      await delay(50);
      return new HttpResponse(null, { status: 204 });
    },
  ),

  // Commands
  http.get("/api/containers/:containerId/commands", async () => {
    await delay(100);
    return HttpResponse.json(mockCommands);
  }),

  http.post("/api/containers/:containerId/commands", async () => {
    await delay(50);
    return new HttpResponse(null, { status: 204 });
  }),

  // Modules
  http.get("/api/modules", async () => {
    await delay(100);
    return HttpResponse.json(mockModules);
  }),

  http.get("/api/containers/:containerId/modules", async () => {
    await delay(100);
    return HttpResponse.json(["EntityModule", "RigidBodyModule"]);
  }),

  http.post("/api/containers/:containerId/modules/reload", async () => {
    await delay(200);
    return HttpResponse.json([
      "EntityModule",
      "RigidBodyModule",
      "RenderModule",
    ]);
  }),

  http.delete("/api/modules/:name", async () => {
    await delay(50);
    return new HttpResponse(null, { status: 204 });
  }),

  // AI
  http.get("/api/ai", async () => {
    await delay(100);
    return HttpResponse.json(mockAIs);
  }),

  http.delete("/api/ai/:name", async () => {
    await delay(50);
    return new HttpResponse(null, { status: 204 });
  }),

  // Resources (global)
  http.get("/api/resources", async () => {
    await delay(100);
    return HttpResponse.json(mockResources);
  }),

  http.delete("/api/resources/:id", async () => {
    await delay(50);
    return new HttpResponse(null, { status: 204 });
  }),

  // Container Resources
  http.get("/api/containers/:containerId/resources", async () => {
    await delay(100);
    return HttpResponse.json(mockResources);
  }),

  http.delete(
    "/api/containers/:containerId/resources/:resourceId",
    async () => {
      await delay(50);
      return new HttpResponse(null, { status: 204 });
    },
  ),

  // Snapshots
  http.get("/api/snapshots/match/:matchId", async () => {
    await delay(100);
    return HttpResponse.json(mockSnapshot);
  }),

  // History (container-scoped)
  http.get("/api/containers/:containerId/history", async () => {
    await delay(100);
    return HttpResponse.json({
      totalSnapshots: 100,
      matchCount: 2,
      matchIds: [1, 2],
      oldestTick: 1,
      newestTick: 50,
    });
  }),

  http.get(
    "/api/containers/:containerId/matches/:matchId/history",
    async ({ params }) => {
      await delay(100);
      return HttpResponse.json({
        matchId: Number(params.matchId),
        snapshotCount: 50,
        firstTick: 1,
        lastTick: 50,
      });
    },
  ),

  http.get(
    "/api/containers/:containerId/matches/:matchId/snapshots",
    async () => {
      await delay(100);
      return HttpResponse.json([mockSnapshot]);
    },
  ),

  // Legacy History (for backward compatibility)
  http.get("/api/history", async () => {
    await delay(100);
    return HttpResponse.json({
      totalSnapshots: 100,
      matchCount: 2,
      matchIds: [1, 2],
      oldestTick: 1,
      newestTick: 50,
    });
  }),

  http.get("/api/history/:matchId", async ({ params }) => {
    await delay(100);
    return HttpResponse.json({
      matchId: Number(params.matchId),
      snapshotCount: 50,
      firstTick: 1,
      lastTick: 50,
    });
  }),

  http.get("/api/history/:matchId/snapshots/latest", async () => {
    await delay(100);
    return HttpResponse.json([mockSnapshot]);
  }),

  // Restore
  http.post("/api/restore/match/:matchId", async () => {
    await delay(200);
    return new HttpResponse(null, { status: 204 });
  }),

  // =========================================================================
  // CONTROL PLANE ENDPOINTS
  // =========================================================================

  // Cluster Status
  http.get("/api/control-plane/cluster/status", async () => {
    await delay(100);
    return HttpResponse.json({
      clusterName: "lightning-cluster",
      healthyNodes: 3,
      totalNodes: 3,
      activeMatches: 5,
      totalCapacity: 100,
      usedCapacity: 45,
    });
  }),

  // Cluster Nodes
  http.get("/api/control-plane/cluster/nodes", async () => {
    await delay(100);
    return HttpResponse.json([
      {
        nodeId: "node-1",
        status: "HEALTHY",
        advertiseAddress: "192.168.1.10:8080",
        matchCount: 2,
        lastHeartbeat: new Date().toISOString(),
      },
      {
        nodeId: "node-2",
        status: "HEALTHY",
        advertiseAddress: "192.168.1.11:8080",
        matchCount: 2,
        lastHeartbeat: new Date().toISOString(),
      },
      {
        nodeId: "node-3",
        status: "HEALTHY",
        advertiseAddress: "192.168.1.12:8080",
        matchCount: 1,
        lastHeartbeat: new Date().toISOString(),
      },
    ]);
  }),

  // Cluster Matches (for DeploymentsPanel - uses /matches endpoint)
  http.get("/api/control-plane/matches", async () => {
    await delay(100);
    return HttpResponse.json([
      {
        matchId: "match-001",
        nodeId: "node-1",
        status: "RUNNING",
        moduleNames: ["EntityModule", "RigidBodyModule"],
        playerCount: 4,
        createdAt: new Date(Date.now() - 3600000).toISOString(),
      },
      {
        matchId: "match-002",
        nodeId: "node-1",
        status: "RUNNING",
        moduleNames: ["EntityModule"],
        playerCount: 2,
        createdAt: new Date(Date.now() - 7200000).toISOString(),
      },
      {
        matchId: "match-003",
        nodeId: "node-2",
        status: "CREATING",
        moduleNames: ["EntityModule", "HealthModule"],
        playerCount: 0,
        createdAt: new Date().toISOString(),
      },
    ]);
  }),

  // Cluster Modules (uses /modules endpoint, not /cluster/modules)
  http.get("/api/control-plane/modules", async () => {
    await delay(100);
    return HttpResponse.json([
      {
        name: "EntityModule",
        version: "1.0.0",
        nodeCount: 3,
        nodes: ["node-1", "node-2", "node-3"],
      },
      {
        name: "RigidBodyModule",
        version: "1.0.0",
        nodeCount: 2,
        nodes: ["node-1", "node-2"],
      },
      {
        name: "HealthModule",
        version: "1.2.0",
        nodeCount: 3,
        nodes: ["node-1", "node-2", "node-3"],
      },
    ]);
  }),

  // Deploy
  http.post("/api/control-plane/deploy", async ({ request }) => {
    await delay(200);
    const body = (await request.json()) as {
      modules: string[];
      preferredNodeId?: string;
    };
    return HttpResponse.json({
      matchId: "match-new-" + Date.now(),
      nodeId: body.preferredNodeId || "node-1",
      status: "CREATING",
    });
  }),

  // Undeploy
  http.delete("/api/control-plane/deploy/:matchId", async () => {
    await delay(100);
    return new HttpResponse(null, { status: 204 });
  }),

  // Get Deployment Details
  http.get("/api/control-plane/deploy/:matchId", async ({ params }) => {
    await delay(100);
    return HttpResponse.json({
      matchId: params.matchId,
      nodeId: "node-1",
      status: "RUNNING",
      modules: ["EntityModule", "RigidBodyModule"],
      endpoints: {
        http: "http://192.168.1.10:8080/api/matches/" + params.matchId,
        websocket: "ws://192.168.1.10:8080/ws/snapshots/" + params.matchId,
        commands: "http://192.168.1.10:8080/api/matches/" + params.matchId + "/commands",
      },
    });
  }),

  // Autoscaler Status
  http.get("/api/control-plane/autoscaler/status", async () => {
    await delay(100);
    return HttpResponse.json({
      inCooldown: false,
      lastRecommendation: {
        action: "SCALE_UP",
        reason: "High load detected",
        currentNodes: 3,
        recommendedNodes: 4,
        timestamp: new Date(Date.now() - 1800000).toISOString(),
      },
    });
  }),

  // Autoscaler Recommendation
  http.get("/api/control-plane/autoscaler/recommendation", async () => {
    await delay(100);
    return HttpResponse.json({
      action: "NO_ACTION",
      reason: "Cluster load is within acceptable range",
      currentNodes: 3,
      recommendedNodes: 3,
      timestamp: new Date().toISOString(),
    });
  }),

  // Acknowledge Scaling Action
  http.post("/api/control-plane/autoscaler/acknowledge", async () => {
    await delay(100);
    return new HttpResponse(null, { status: 204 });
  }),
];
