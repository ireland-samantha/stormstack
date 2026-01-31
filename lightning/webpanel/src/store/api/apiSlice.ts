/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react";
import type {
    AIData, ApiTokenData, CommandData, ComponentDataResponse, ContainerData, ContainerMetricsData, ContainerStatsData, CreateApiTokenRequest, CreateApiTokenResponse, CreateContainerRequest, CreateRoleRequest, CreateUserRequest, DeltaSnapshotData, HistorySummary, LoginResponse, MatchData, MatchHistorySummary, ModuleData, ModuleDataResponse, PlayerData,
    PlayerMatchData, ResourceData, RoleData, SessionData, SnapshotData, UserData,
    ClusterStatusData, ClusterNodeData, ClusterMatchData, DashboardOverviewData, PagedResponse, ClusterModuleData, DeployRequest, DeployResponse,
    AutoscalerStatus, ScalingRecommendation
} from "../../services/api";

// Re-export types for convenience
export type {
    ContainerData,
    CreateContainerRequest,
    ContainerStatsData,
    MatchData,
    UserData,
    CreateUserRequest,
    RoleData,
    CreateRoleRequest,
    ApiTokenData,
    CreateApiTokenRequest,
    CreateApiTokenResponse,
    PlayerData,
    PlayerMatchData,
    SessionData,
    CommandData,
    ModuleData,
    AIData,
    ResourceData,
    SnapshotData,
    ComponentDataResponse,
    ModuleDataResponse,
};

interface CreateMatchRequest {
  id?: number;
  enabledModuleNames?: string[];
  enabledAINames?: string[];
}

interface EnqueueCommandRequest {
  commandName: string;
  matchId?: number;
  parameters?: Record<string, unknown>;
}

// Use full URL for MSW compatibility in tests
const getBaseUrl = () => {
  // In browser, use relative path; in tests/SSR, use full URL
  if (
    typeof window !== "undefined" &&
    window.location &&
    window.location.origin !== "null"
  ) {
    return window.location.origin + "/api";
  }
  return "http://localhost/api";
};

export const apiSlice = createApi({
  reducerPath: "api",
  baseQuery: fetchBaseQuery({
    baseUrl: getBaseUrl(),
    prepareHeaders: (headers) => {
      const token = localStorage.getItem("authToken");
      if (token) {
        headers.set("Authorization", `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: [
    "Container",
    "Match",
    "User",
    "Role",
    "ApiToken",
    "Player",
    "Session",
    "Command",
    "Module",
    "AI",
    "Resource",
    "Snapshot",
    "History",
    "ClusterStatus",
    "ClusterNode",
    "ClusterMatch",
    "ClusterModule",
    "Autoscaler",
  ],
  endpoints: (builder) => ({
    // =========================================================================
    // AUTH ENDPOINTS
    // =========================================================================
    login: builder.mutation<
      LoginResponse,
      { username: string; password: string }
    >({
      query: (credentials) => ({
        url: "/auth/login",
        method: "POST",
        body: credentials,
      }),
    }),

    getCurrentUser: builder.query<UserData, void>({
      query: () => "/auth/me",
      providesTags: ["User"],
    }),

    // =========================================================================
    // CONTAINER ENDPOINTS
    // =========================================================================
    getContainers: builder.query<ContainerData[], void>({
      query: () => "/containers",
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ id }) => ({ type: "Container" as const, id })),
              { type: "Container", id: "LIST" },
            ]
          : [{ type: "Container", id: "LIST" }],
    }),

    getContainer: builder.query<ContainerData, number>({
      query: (id) => `/containers/${id}`,
      providesTags: (_, __, id) => [{ type: "Container", id }],
    }),

    createContainer: builder.mutation<ContainerData, CreateContainerRequest>({
      query: (body) => ({
        url: "/containers",
        method: "POST",
        body,
      }),
      invalidatesTags: [{ type: "Container", id: "LIST" }],
    }),

    deleteContainer: builder.mutation<void, number>({
      query: (id) => ({
        url: `/containers/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, id) => [
        { type: "Container", id },
        { type: "Container", id: "LIST" },
      ],
    }),

    startContainer: builder.mutation<ContainerData, number>({
      query: (id) => ({
        url: `/containers/${id}/start`,
        method: "POST",
      }),
      invalidatesTags: (_, __, id) => [{ type: "Container", id }],
    }),

    stopContainer: builder.mutation<ContainerData, number>({
      query: (id) => ({
        url: `/containers/${id}/stop`,
        method: "POST",
      }),
      invalidatesTags: (_, __, id) => [{ type: "Container", id }],
    }),

    pauseContainer: builder.mutation<ContainerData, number>({
      query: (id) => ({
        url: `/containers/${id}/pause`,
        method: "POST",
      }),
      invalidatesTags: (_, __, id) => [{ type: "Container", id }],
    }),

    resumeContainer: builder.mutation<ContainerData, number>({
      query: (id) => ({
        url: `/containers/${id}/resume`,
        method: "POST",
      }),
      invalidatesTags: (_, __, id) => [{ type: "Container", id }],
    }),

    advanceContainerTick: builder.mutation<{ tick: number }, number>({
      query: (id) => ({
        url: `/containers/${id}/tick`,
        method: "POST",
      }),
      invalidatesTags: (_, __, id) => [
        { type: "Container", id },
        { type: "Snapshot" },
      ],
    }),

    playContainer: builder.mutation<
      ContainerData,
      { id: number; intervalMs?: number }
    >({
      query: ({ id, intervalMs = 16 }) => ({
        url: `/containers/${id}/play?intervalMs=${intervalMs}`,
        method: "POST",
      }),
      invalidatesTags: (_, __, { id }) => [{ type: "Container", id }],
    }),

    stopContainerAutoAdvance: builder.mutation<ContainerData, number>({
      query: (id) => ({
        url: `/containers/${id}/stop-auto`,
        method: "POST",
      }),
      invalidatesTags: (_, __, id) => [{ type: "Container", id }],
    }),

    getContainerTick: builder.query<{ tick: number }, number>({
      query: (id) => `/containers/${id}/tick`,
      providesTags: (_, __, id) => [{ type: "Container", id }],
    }),

    getContainerStats: builder.query<ContainerStatsData, number>({
      query: (id) => `/containers/${id}/stats`,
      providesTags: (_, __, id) => [{ type: "Container", id: `STATS_${id}` }],
    }),

    getContainerMetrics: builder.query<ContainerMetricsData, number>({
      query: (id) => `/containers/${id}/metrics`,
      providesTags: (_, __, id) => [{ type: "Container", id: `METRICS_${id}` }],
    }),

    resetContainerMetrics: builder.mutation<void, number>({
      query: (id) => ({
        url: `/containers/${id}/metrics/reset`,
        method: "POST",
      }),
      invalidatesTags: (_, __, id) => [
        { type: "Container", id: `METRICS_${id}` },
      ],
    }),

    // =========================================================================
    // MATCH ENDPOINTS (Container-scoped)
    // =========================================================================
    getContainerMatches: builder.query<MatchData[], number>({
      query: (containerId) => `/containers/${containerId}/matches`,
      providesTags: (result, _, containerId) =>
        result
          ? [
              ...result.map(({ id }) => ({ type: "Match" as const, id })),
              { type: "Match", id: `CONTAINER_${containerId}` },
            ]
          : [{ type: "Match", id: `CONTAINER_${containerId}` }],
    }),

    createMatchInContainer: builder.mutation<
      MatchData,
      { containerId: number; body: CreateMatchRequest }
    >({
      query: ({ containerId, body }) => ({
        url: `/containers/${containerId}/matches`,
        method: "POST",
        body,
      }),
      invalidatesTags: (_, __, { containerId }) => [
        { type: "Match", id: `CONTAINER_${containerId}` },
        { type: "Container", id: containerId },
      ],
    }),

    deleteMatchFromContainer: builder.mutation<
      void,
      { containerId: number; matchId: number }
    >({
      query: ({ containerId, matchId }) => ({
        url: `/containers/${containerId}/matches/${matchId}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, { containerId, matchId }) => [
        { type: "Match", id: matchId },
        { type: "Match", id: `CONTAINER_${containerId}` },
        { type: "Container", id: containerId },
      ],
    }),

    // =========================================================================
    // USER ENDPOINTS
    // =========================================================================
    getUsers: builder.query<UserData[], void>({
      query: () => "/auth/users",
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ id }) => ({ type: "User" as const, id })),
              { type: "User", id: "LIST" },
            ]
          : [{ type: "User", id: "LIST" }],
    }),

    createUser: builder.mutation<UserData, CreateUserRequest>({
      query: (body) => ({
        url: "/auth/users",
        method: "POST",
        body,
      }),
      invalidatesTags: [{ type: "User", id: "LIST" }],
    }),

    updateUserRoles: builder.mutation<
      UserData,
      { userId: number; roles: string[] }
    >({
      query: ({ userId, roles }) => ({
        url: `/auth/users/${userId}/roles`,
        method: "PUT",
        body: { roles },
      }),
      invalidatesTags: (_, __, { userId }) => [{ type: "User", id: userId }],
    }),

    deleteUser: builder.mutation<void, number>({
      query: (id) => ({
        url: `/auth/users/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, id) => [
        { type: "User", id },
        { type: "User", id: "LIST" },
      ],
    }),

    setUserEnabled: builder.mutation<
      UserData,
      { userId: number; enabled: boolean }
    >({
      query: ({ userId, enabled }) => ({
        url: `/auth/users/${userId}/enabled`,
        method: "PUT",
        body: { enabled },
      }),
      invalidatesTags: (_, __, { userId }) => [{ type: "User", id: userId }],
    }),

    updateUserPassword: builder.mutation<
      void,
      { userId: number; password: string }
    >({
      query: ({ userId, password }) => ({
        url: `/auth/users/${userId}/password`,
        method: "PUT",
        body: { password },
      }),
    }),

    // =========================================================================
    // ROLE ENDPOINTS
    // =========================================================================
    getRoles: builder.query<RoleData[], void>({
      query: () => "/auth/roles",
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ id }) => ({ type: "Role" as const, id })),
              { type: "Role", id: "LIST" },
            ]
          : [{ type: "Role", id: "LIST" }],
    }),

    createRole: builder.mutation<RoleData, CreateRoleRequest>({
      query: (body) => ({
        url: "/auth/roles",
        method: "POST",
        body,
      }),
      invalidatesTags: [{ type: "Role", id: "LIST" }],
    }),

    deleteRole: builder.mutation<void, number>({
      query: (id) => ({
        url: `/auth/roles/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, id) => [
        { type: "Role", id },
        { type: "Role", id: "LIST" },
      ],
    }),

    updateRoleDescription: builder.mutation<
      RoleData,
      { roleId: number; description: string }
    >({
      query: ({ roleId, description }) => ({
        url: `/auth/roles/${roleId}/description`,
        method: "PUT",
        body: { description },
      }),
      invalidatesTags: (_, __, { roleId }) => [{ type: "Role", id: roleId }],
    }),

    updateRoleIncludes: builder.mutation<
      RoleData,
      { roleId: number; includes: string[] }
    >({
      query: ({ roleId, includes }) => ({
        url: `/auth/roles/${roleId}/includes`,
        method: "PUT",
        body: { includes },
      }),
      invalidatesTags: (_, __, { roleId }) => [{ type: "Role", id: roleId }],
    }),

    updateRoleScopes: builder.mutation<
      RoleData,
      { roleId: number; scopes: string[] }
    >({
      query: ({ roleId, scopes }) => ({
        url: `/auth/roles/${roleId}/scopes`,
        method: "PUT",
        body: scopes,
      }),
      invalidatesTags: (_, __, { roleId }) => [{ type: "Role", id: roleId }],
    }),

    getResolvedScopes: builder.query<string[], number>({
      query: (roleId) => `/auth/roles/${roleId}/scopes/resolved`,
      providesTags: (_, __, roleId) => [{ type: "Role", id: roleId }],
    }),

    // =========================================================================
    // API TOKEN ENDPOINTS
    // =========================================================================
    getApiTokens: builder.query<ApiTokenData[], void>({
      query: () => "/auth/tokens",
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ id }) => ({ type: "ApiToken" as const, id })),
              { type: "ApiToken", id: "LIST" },
            ]
          : [{ type: "ApiToken", id: "LIST" }],
    }),

    createApiToken: builder.mutation<CreateApiTokenResponse, CreateApiTokenRequest>({
      query: (body) => ({
        url: "/auth/tokens",
        method: "POST",
        body,
      }),
      invalidatesTags: [{ type: "ApiToken", id: "LIST" }],
    }),

    deleteApiToken: builder.mutation<void, string>({
      query: (id) => ({
        url: `/auth/tokens/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, id) => [
        { type: "ApiToken", id },
        { type: "ApiToken", id: "LIST" },
      ],
    }),

    revokeApiToken: builder.mutation<void, string>({
      query: (id) => ({
        url: `/auth/tokens/${id}/revoke`,
        method: "POST",
      }),
      invalidatesTags: (_, __, id) => [{ type: "ApiToken", id }],
    }),

    // =========================================================================
    // PLAYER ENDPOINTS (Container-scoped)
    // =========================================================================
    getPlayersInContainer: builder.query<PlayerData[], number>({
      query: (containerId) => `/containers/${containerId}/players`,
      providesTags: (result, _, containerId) =>
        result
          ? [
              ...result.map(({ id }) => ({ type: "Player" as const, id })),
              { type: "Player", id: `CONTAINER_${containerId}` },
            ]
          : [{ type: "Player", id: `CONTAINER_${containerId}` }],
    }),

    createPlayerInContainer: builder.mutation<
      PlayerData,
      { containerId: number; id?: number }
    >({
      query: ({ containerId, id }) => ({
        url: `/containers/${containerId}/players`,
        method: "POST",
        body: { id },
      }),
      invalidatesTags: (_, __, { containerId }) => [
        { type: "Player", id: `CONTAINER_${containerId}` },
      ],
    }),

    deletePlayerInContainer: builder.mutation<
      void,
      { containerId: number; playerId: number }
    >({
      query: ({ containerId, playerId }) => ({
        url: `/containers/${containerId}/players/${playerId}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, { containerId, playerId }) => [
        { type: "Player", id: playerId },
        { type: "Player", id: `CONTAINER_${containerId}` },
      ],
    }),

    getPlayersInMatch: builder.query<
      PlayerMatchData[],
      { containerId: number; matchId: number }
    >({
      query: ({ containerId, matchId }) =>
        `/containers/${containerId}/matches/${matchId}/players`,
      providesTags: (_, __, { containerId, matchId }) => [
        { type: "Player", id: `MATCH_${containerId}_${matchId}` },
      ],
    }),

    // =========================================================================
    // SESSION ENDPOINTS (Container-scoped)
    // =========================================================================
    getAllContainerSessions: builder.query<SessionData[], number>({
      query: (containerId) => `/containers/${containerId}/sessions`,
      providesTags: (result, _, containerId) =>
        result
          ? [
              ...result.map(({ id }) => ({ type: "Session" as const, id })),
              { type: "Session", id: `CONTAINER_${containerId}` },
            ]
          : [{ type: "Session", id: `CONTAINER_${containerId}` }],
    }),

    connectSessionInContainer: builder.mutation<
      SessionData,
      { containerId: number; matchId: number; playerId: number }
    >({
      query: ({ containerId, matchId, playerId }) => ({
        url: `/containers/${containerId}/matches/${matchId}/sessions`,
        method: "POST",
        body: { playerId },
      }),
      invalidatesTags: (_, __, { containerId }) => [
        { type: "Session", id: `CONTAINER_${containerId}` },
      ],
    }),

    disconnectSessionInContainer: builder.mutation<
      void,
      { containerId: number; matchId: number; playerId: number }
    >({
      query: ({ containerId, matchId, playerId }) => ({
        url: `/containers/${containerId}/matches/${matchId}/sessions/${playerId}/disconnect`,
        method: "POST",
      }),
      invalidatesTags: (_, __, { containerId }) => [
        { type: "Session", id: `CONTAINER_${containerId}` },
      ],
    }),

    reconnectSessionInContainer: builder.mutation<
      SessionData,
      { containerId: number; matchId: number; playerId: number }
    >({
      query: ({ containerId, matchId, playerId }) => ({
        url: `/containers/${containerId}/matches/${matchId}/sessions/${playerId}/reconnect`,
        method: "POST",
      }),
      invalidatesTags: (_, __, { containerId }) => [
        { type: "Session", id: `CONTAINER_${containerId}` },
      ],
    }),

    abandonSessionInContainer: builder.mutation<
      void,
      { containerId: number; matchId: number; playerId: number }
    >({
      query: ({ containerId, matchId, playerId }) => ({
        url: `/containers/${containerId}/matches/${matchId}/sessions/${playerId}/abandon`,
        method: "POST",
      }),
      invalidatesTags: (_, __, { containerId }) => [
        { type: "Session", id: `CONTAINER_${containerId}` },
      ],
    }),

    // =========================================================================
    // COMMAND ENDPOINTS (Container-scoped)
    // =========================================================================
    getContainerCommands: builder.query<CommandData[], number>({
      query: (containerId) => `/containers/${containerId}/commands`,
      providesTags: (_, __, containerId) => [
        { type: "Command", id: `CONTAINER_${containerId}` },
      ],
    }),

    sendCommandToContainer: builder.mutation<
      void,
      { containerId: number; body: EnqueueCommandRequest }
    >({
      query: ({ containerId, body }) => ({
        url: `/containers/${containerId}/commands`,
        method: "POST",
        body,
      }),
      invalidatesTags: ["Snapshot"],
    }),

    // =========================================================================
    // MODULE ENDPOINTS
    // =========================================================================
    getModules: builder.query<ModuleData[], void>({
      query: () => "/modules",
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ name }) => ({
                type: "Module" as const,
                id: name,
              })),
              { type: "Module", id: "LIST" },
            ]
          : [{ type: "Module", id: "LIST" }],
    }),

    getContainerModules: builder.query<string[], number>({
      query: (containerId) => `/containers/${containerId}/modules`,
      providesTags: (_, __, containerId) => [
        { type: "Module", id: `CONTAINER_${containerId}` },
      ],
    }),

    reloadContainerModules: builder.mutation<string[], number>({
      query: (containerId) => ({
        url: `/containers/${containerId}/modules/reload`,
        method: "POST",
      }),
      invalidatesTags: (_, __, containerId) => [
        { type: "Module", id: `CONTAINER_${containerId}` },
        { type: "Module", id: "LIST" },
      ],
    }),

    deleteModule: builder.mutation<void, string>({
      query: (name) => ({
        url: `/modules/${name}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, name) => [
        { type: "Module", id: name },
        { type: "Module", id: "LIST" },
      ],
    }),

    // =========================================================================
    // AI ENDPOINTS
    // =========================================================================
    getAIs: builder.query<AIData[], void>({
      query: () => "/ai",
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ name }) => ({ type: "AI" as const, id: name })),
              { type: "AI", id: "LIST" },
            ]
          : [{ type: "AI", id: "LIST" }],
    }),

    getContainerAI: builder.query<string[], number>({
      query: (containerId) => `/containers/${containerId}/ai`,
      providesTags: (_, __, containerId) => [
        { type: "AI", id: `CONTAINER_${containerId}` },
      ],
    }),

    deleteAI: builder.mutation<void, string>({
      query: (name) => ({
        url: `/ai/${name}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, name) => [
        { type: "AI", id: name },
        { type: "AI", id: "LIST" },
      ],
    }),

    // =========================================================================
    // RESOURCE ENDPOINTS
    // =========================================================================
    getResources: builder.query<ResourceData[], void>({
      query: () => "/resources",
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ id }) => ({ type: "Resource" as const, id })),
              { type: "Resource", id: "LIST" },
            ]
          : [{ type: "Resource", id: "LIST" }],
    }),

    getContainerResources: builder.query<ResourceData[], number>({
      query: (containerId) => `/containers/${containerId}/resources`,
      providesTags: (_, __, containerId) => [
        { type: "Resource", id: `CONTAINER_${containerId}` },
      ],
    }),

    deleteResource: builder.mutation<void, string>({
      query: (id) => ({
        url: `/resources/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, id) => [
        { type: "Resource", id },
        { type: "Resource", id: "LIST" },
      ],
    }),

    deleteContainerResource: builder.mutation<
      void,
      { containerId: number; resourceId: number }
    >({
      query: ({ containerId, resourceId }) => ({
        url: `/containers/${containerId}/resources/${resourceId}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, { containerId, resourceId }) => [
        { type: "Resource", id: resourceId },
        { type: "Resource", id: `CONTAINER_${containerId}` },
      ],
    }),

    // =========================================================================
    // SNAPSHOT ENDPOINTS
    // =========================================================================
    getSnapshot: builder.query<SnapshotData, number>({
      query: (matchId) => `/snapshots/match/${matchId}`,
      providesTags: (_, __, matchId) => [{ type: "Snapshot", id: matchId }],
    }),

    // =========================================================================
    // HISTORY ENDPOINTS (Container-scoped)
    // =========================================================================
    getContainerHistorySummary: builder.query<HistorySummary, number>({
      query: (containerId) => `/containers/${containerId}/history`,
      providesTags: (_, __, containerId) => [
        { type: "History", id: `CONTAINER_${containerId}` },
      ],
    }),

    getContainerMatchHistorySummary: builder.query<
      MatchHistorySummary,
      { containerId: number; matchId: number }
    >({
      query: ({ containerId, matchId }) =>
        `/containers/${containerId}/matches/${matchId}/history`,
      providesTags: (_, __, { containerId, matchId }) => [
        { type: "History", id: `MATCH_${containerId}_${matchId}` },
      ],
    }),

    getContainerHistorySnapshots: builder.query<
      SnapshotData[],
      {
        containerId: number;
        matchId: number;
        fromTick?: number;
        toTick?: number;
        limit?: number;
      }
    >({
      query: ({ containerId, matchId, fromTick, toTick, limit }) => {
        const params = new URLSearchParams();
        if (fromTick !== undefined) params.set("fromTick", String(fromTick));
        if (toTick !== undefined) params.set("toTick", String(toTick));
        if (limit !== undefined) params.set("limit", String(limit));
        const query = params.toString();
        return `/containers/${containerId}/matches/${matchId}/history/snapshots${query ? `?${query}` : ""}`;
      },
      providesTags: (_, __, { containerId, matchId }) => [
        { type: "History", id: `MATCH_${containerId}_${matchId}` },
      ],
    }),

    getContainerLatestSnapshots: builder.query<
      SnapshotData[],
      { containerId: number; matchId: number; limit?: number }
    >({
      query: ({ containerId, matchId, limit = 10 }) =>
        `/containers/${containerId}/matches/${matchId}/history/snapshots/latest?limit=${limit}`,
      providesTags: (_, __, { containerId, matchId }) => [
        { type: "History", id: `MATCH_${containerId}_${matchId}` },
      ],
    }),

    // Legacy global history endpoints (kept for backwards compatibility)
    getHistorySummary: builder.query<HistorySummary, void>({
      query: () => "/history",
      providesTags: [{ type: "History", id: "SUMMARY" }],
    }),

    getMatchHistorySummary: builder.query<MatchHistorySummary, number>({
      query: (matchId) => `/history/${matchId}`,
      providesTags: (_, __, matchId) => [{ type: "History", id: matchId }],
    }),

    getHistorySnapshots: builder.query<
      SnapshotData[],
      { matchId: number; fromTick?: number; toTick?: number; limit?: number }
    >({
      query: ({ matchId, fromTick, toTick, limit }) => {
        const params = new URLSearchParams();
        if (fromTick !== undefined) params.set("fromTick", String(fromTick));
        if (toTick !== undefined) params.set("toTick", String(toTick));
        if (limit !== undefined) params.set("limit", String(limit));
        const query = params.toString();
        return `/history/${matchId}/snapshots${query ? `?${query}` : ""}`;
      },
      providesTags: (_, __, { matchId }) => [{ type: "History", id: matchId }],
    }),

    getLatestSnapshots: builder.query<
      SnapshotData[],
      { matchId: number; limit?: number }
    >({
      query: ({ matchId, limit = 10 }) =>
        `/history/${matchId}/snapshots/latest?limit=${limit}`,
      providesTags: (_, __, { matchId }) => [{ type: "History", id: matchId }],
    }),

    // =========================================================================
    // DELTA ENDPOINTS
    // =========================================================================
    getDelta: builder.query<
      DeltaSnapshotData,
      { matchId: number; fromTick: number; toTick: number }
    >({
      query: ({ matchId, fromTick, toTick }) =>
        `/snapshots/delta/${matchId}?fromTick=${fromTick}&toTick=${toTick}`,
      providesTags: (_, __, { matchId }) => [
        { type: "Snapshot", id: `DELTA_${matchId}` },
      ],
    }),

    restoreMatch: builder.mutation<void, { matchId: number; tick?: number }>({
      query: ({ matchId, tick }) => ({
        url: `/restore/match/${matchId}${tick !== undefined ? `?tick=${tick}` : ""}`,
        method: "POST",
      }),
      invalidatesTags: ["Snapshot", "Match"],
    }),

    // =========================================================================
    // CONTROL PLANE ENDPOINTS
    // =========================================================================

    // Dashboard
    getDashboardOverview: builder.query<DashboardOverviewData, void>({
      query: () => "/control-plane/dashboard/overview",
      providesTags: ["ClusterStatus", "ClusterNode", "ClusterMatch"],
    }),

    getDashboardNodes: builder.query<
      PagedResponse<ClusterNodeData>,
      { page?: number; pageSize?: number; status?: string }
    >({
      query: ({ page = 0, pageSize = 20, status }) => {
        const params = new URLSearchParams();
        params.set("page", String(page));
        params.set("pageSize", String(pageSize));
        if (status) params.set("status", status);
        return `/control-plane/dashboard/nodes?${params.toString()}`;
      },
      providesTags: ["ClusterNode"],
    }),

    getDashboardMatches: builder.query<
      PagedResponse<ClusterMatchData>,
      { page?: number; pageSize?: number; status?: string; nodeId?: string }
    >({
      query: ({ page = 0, pageSize = 20, status, nodeId }) => {
        const params = new URLSearchParams();
        params.set("page", String(page));
        params.set("pageSize", String(pageSize));
        if (status) params.set("status", status);
        if (nodeId) params.set("nodeId", nodeId);
        return `/control-plane/dashboard/matches?${params.toString()}`;
      },
      providesTags: ["ClusterMatch"],
    }),

    // Cluster
    getClusterStatus: builder.query<ClusterStatusData, void>({
      query: () => "/control-plane/cluster/status",
      providesTags: ["ClusterStatus"],
    }),

    getClusterNodes: builder.query<ClusterNodeData[], void>({
      query: () => "/control-plane/cluster/nodes",
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ nodeId }) => ({ type: "ClusterNode" as const, id: nodeId })),
              { type: "ClusterNode", id: "LIST" },
            ]
          : [{ type: "ClusterNode", id: "LIST" }],
    }),

    getClusterNode: builder.query<ClusterNodeData, string>({
      query: (nodeId) => `/control-plane/cluster/nodes/${nodeId}`,
      providesTags: (_, __, nodeId) => [{ type: "ClusterNode", id: nodeId }],
    }),

    // Cluster Matches
    getClusterMatches: builder.query<ClusterMatchData[], string | void>({
      query: (status) => `/control-plane/matches${status ? `?status=${status}` : ""}`,
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ matchId }) => ({ type: "ClusterMatch" as const, id: matchId })),
              { type: "ClusterMatch", id: "LIST" },
            ]
          : [{ type: "ClusterMatch", id: "LIST" }],
    }),

    getClusterMatch: builder.query<ClusterMatchData, string>({
      query: (matchId) => `/control-plane/matches/${matchId}`,
      providesTags: (_, __, matchId) => [{ type: "ClusterMatch", id: matchId }],
    }),

    createClusterMatch: builder.mutation<ClusterMatchData, { moduleNames: string[]; preferredNodeId?: string }>({
      query: (body) => ({
        url: "/control-plane/matches/create",
        method: "POST",
        body,
      }),
      invalidatesTags: [{ type: "ClusterMatch", id: "LIST" }, "ClusterStatus"],
    }),

    finishClusterMatch: builder.mutation<ClusterMatchData, string>({
      query: (matchId) => ({
        url: `/control-plane/matches/${matchId}/finish`,
        method: "POST",
      }),
      invalidatesTags: (_, __, matchId) => [
        { type: "ClusterMatch", id: matchId },
        { type: "ClusterMatch", id: "LIST" },
        "ClusterStatus",
      ],
    }),

    deleteClusterMatch: builder.mutation<void, string>({
      query: (matchId) => ({
        url: `/control-plane/matches/${matchId}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, matchId) => [
        { type: "ClusterMatch", id: matchId },
        { type: "ClusterMatch", id: "LIST" },
        "ClusterStatus",
      ],
    }),

    // Node Management
    drainNode: builder.mutation<ClusterNodeData, string>({
      query: (nodeId) => ({
        url: `/control-plane/nodes/${nodeId}/drain`,
        method: "POST",
      }),
      invalidatesTags: (_, __, nodeId) => [
        { type: "ClusterNode", id: nodeId },
        { type: "ClusterNode", id: "LIST" },
        "ClusterStatus",
      ],
    }),

    deregisterNode: builder.mutation<void, string>({
      query: (nodeId) => ({
        url: `/control-plane/nodes/${nodeId}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, nodeId) => [
        { type: "ClusterNode", id: nodeId },
        { type: "ClusterNode", id: "LIST" },
        "ClusterStatus",
      ],
    }),

    // Cluster Modules
    getClusterModules: builder.query<ClusterModuleData[], void>({
      query: () => "/control-plane/modules",
      providesTags: (result) =>
        result
          ? [
              ...result.map(({ name }) => ({ type: "ClusterModule" as const, id: name })),
              { type: "ClusterModule", id: "LIST" },
            ]
          : [{ type: "ClusterModule", id: "LIST" }],
    }),

    getClusterModule: builder.query<ClusterModuleData, string>({
      query: (name) => `/control-plane/modules/${name}`,
      providesTags: (_, __, name) => [{ type: "ClusterModule", id: name }],
    }),

    distributeModule: builder.mutation<{ moduleName: string; moduleVersion: string; nodesUpdated: number }, { name: string; version: string }>({
      query: ({ name, version }) => ({
        url: `/control-plane/modules/${name}/${version}/distribute`,
        method: "POST",
      }),
      invalidatesTags: (_, __, { name }) => [
        { type: "ClusterModule", id: name },
        { type: "ClusterNode", id: "LIST" },
      ],
    }),

    // Deploy
    deploy: builder.mutation<DeployResponse, DeployRequest>({
      query: (body) => ({
        url: "/control-plane/deploy",
        method: "POST",
        body,
      }),
      invalidatesTags: [{ type: "ClusterMatch", id: "LIST" }, "ClusterStatus"],
    }),

    getDeployment: builder.query<DeployResponse, string>({
      query: (matchId) => `/control-plane/deploy/${matchId}`,
      providesTags: (_, __, matchId) => [{ type: "ClusterMatch", id: matchId }],
    }),

    undeploy: builder.mutation<void, string>({
      query: (matchId) => ({
        url: `/control-plane/deploy/${matchId}`,
        method: "DELETE",
      }),
      invalidatesTags: (_, __, matchId) => [
        { type: "ClusterMatch", id: matchId },
        { type: "ClusterMatch", id: "LIST" },
        "ClusterStatus",
      ],
    }),

    // =========================================================================
    // AUTOSCALER ENDPOINTS
    // =========================================================================

    getAutoscalerStatus: builder.query<AutoscalerStatus, void>({
      query: () => "/control-plane/autoscaler/status",
      providesTags: ["Autoscaler"],
    }),

    getAutoscalerRecommendation: builder.query<ScalingRecommendation, void>({
      query: () => "/control-plane/autoscaler/recommendation",
      providesTags: ["Autoscaler"],
    }),

    acknowledgeScalingAction: builder.mutation<void, void>({
      query: () => ({
        url: "/control-plane/autoscaler/acknowledge",
        method: "POST",
      }),
      invalidatesTags: ["Autoscaler"],
    }),
  }),
});

// Export auto-generated hooks
export const {
  // Auth
  useLoginMutation,
  useGetCurrentUserQuery,
  // Containers
  useGetContainersQuery,
  useGetContainerQuery,
  useCreateContainerMutation,
  useDeleteContainerMutation,
  useStartContainerMutation,
  useStopContainerMutation,
  usePauseContainerMutation,
  useResumeContainerMutation,
  useAdvanceContainerTickMutation,
  usePlayContainerMutation,
  useStopContainerAutoAdvanceMutation,
  useGetContainerTickQuery,
  useGetContainerStatsQuery,
  useGetContainerMetricsQuery,
  useResetContainerMetricsMutation,
  // Matches
  useGetContainerMatchesQuery,
  useCreateMatchInContainerMutation,
  useDeleteMatchFromContainerMutation,
  // Users
  useGetUsersQuery,
  useCreateUserMutation,
  useUpdateUserRolesMutation,
  useDeleteUserMutation,
  useSetUserEnabledMutation,
  useUpdateUserPasswordMutation,
  // Roles
  useGetRolesQuery,
  useCreateRoleMutation,
  useDeleteRoleMutation,
  useUpdateRoleDescriptionMutation,
  useUpdateRoleIncludesMutation,
  useUpdateRoleScopesMutation,
  useGetResolvedScopesQuery,
  // API Tokens
  useGetApiTokensQuery,
  useCreateApiTokenMutation,
  useDeleteApiTokenMutation,
  useRevokeApiTokenMutation,
  // Players
  useGetPlayersInContainerQuery,
  useCreatePlayerInContainerMutation,
  useDeletePlayerInContainerMutation,
  useGetPlayersInMatchQuery,
  // Sessions
  useGetAllContainerSessionsQuery,
  useConnectSessionInContainerMutation,
  useDisconnectSessionInContainerMutation,
  useReconnectSessionInContainerMutation,
  useAbandonSessionInContainerMutation,
  // Commands
  useGetContainerCommandsQuery,
  useSendCommandToContainerMutation,
  // Modules
  useGetModulesQuery,
  useGetContainerModulesQuery,
  useReloadContainerModulesMutation,
  useDeleteModuleMutation,
  // AI
  useGetAIsQuery,
  useGetContainerAIQuery,
  useDeleteAIMutation,
  // Resources
  useGetResourcesQuery,
  useGetContainerResourcesQuery,
  useDeleteResourceMutation,
  useDeleteContainerResourceMutation,
  // Snapshots
  useGetSnapshotQuery,
  // History (Container-scoped)
  useGetContainerHistorySummaryQuery,
  useGetContainerMatchHistorySummaryQuery,
  useGetContainerHistorySnapshotsQuery,
  useGetContainerLatestSnapshotsQuery,
  // History (Legacy global)
  useGetHistorySummaryQuery,
  useGetMatchHistorySummaryQuery,
  useGetHistorySnapshotsQuery,
  useGetLatestSnapshotsQuery,
  // Delta
  useGetDeltaQuery,
  // Restore
  useRestoreMatchMutation,
  // Control Plane - Dashboard
  useGetDashboardOverviewQuery,
  useGetDashboardNodesQuery,
  useGetDashboardMatchesQuery,
  // Control Plane - Cluster
  useGetClusterStatusQuery,
  useGetClusterNodesQuery,
  useGetClusterNodeQuery,
  // Control Plane - Matches
  useGetClusterMatchesQuery,
  useGetClusterMatchQuery,
  useCreateClusterMatchMutation,
  useFinishClusterMatchMutation,
  useDeleteClusterMatchMutation,
  // Control Plane - Nodes
  useDrainNodeMutation,
  useDeregisterNodeMutation,
  // Control Plane - Modules
  useGetClusterModulesQuery,
  useGetClusterModuleQuery,
  useDistributeModuleMutation,
  // Control Plane - Deploy
  useDeployMutation,
  useGetDeploymentQuery,
  useUndeployMutation,
  // Control Plane - Autoscaler
  useGetAutoscalerStatusQuery,
  useGetAutoscalerRecommendationQuery,
  useAcknowledgeScalingActionMutation,
} = apiSlice;
