/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import type { RootState } from "../store";

export type PanelType =
  // Control Plane panels
  | "clusterOverview"
  | "clusterNodes"
  | "clusterMatches"
  | "clusterModules"
  | "deployments"
  | "autoscaler"
  // Lightning Engine (Node) panels
  | "dashboard"
  | "snapshot"
  | "history"
  | "logs"
  | "metrics"
  | "matches"
  | "players"
  | "sessions"
  | "commands"
  | "modules"
  | "ai"
  | "resources"
  // Authentication panels
  | "users"
  | "roles"
  | "apiTokens";

interface UiState {
  selectedContainerId: number | null;
  selectedMatchId: number | null;
  activePanel: PanelType;
  sidebarOpen: boolean;
  controlPlaneMenuOpen: boolean;
  engineMenuOpen: boolean;
  authMenuOpen: boolean;
}

const initialState: UiState = {
  selectedContainerId: null,
  selectedMatchId: null,
  activePanel: "clusterOverview",
  sidebarOpen: true,
  controlPlaneMenuOpen: true,
  engineMenuOpen: false,
  authMenuOpen: false,
};

const uiSlice = createSlice({
  name: "ui",
  initialState,
  reducers: {
    setSelectedContainerId: (state, action: PayloadAction<number | null>) => {
      state.selectedContainerId = action.payload;
      // Reset match selection when container changes
      state.selectedMatchId = null;
    },
    setSelectedMatchId: (state, action: PayloadAction<number | null>) => {
      state.selectedMatchId = action.payload;
    },
    setActivePanel: (state, action: PayloadAction<PanelType>) => {
      state.activePanel = action.payload;
    },
    setSidebarOpen: (state, action: PayloadAction<boolean>) => {
      state.sidebarOpen = action.payload;
    },
    toggleSidebar: (state) => {
      state.sidebarOpen = !state.sidebarOpen;
    },
    setControlPlaneMenuOpen: (state, action: PayloadAction<boolean>) => {
      state.controlPlaneMenuOpen = action.payload;
    },
    toggleControlPlaneMenu: (state) => {
      state.controlPlaneMenuOpen = !state.controlPlaneMenuOpen;
    },
    setEngineMenuOpen: (state, action: PayloadAction<boolean>) => {
      state.engineMenuOpen = action.payload;
    },
    toggleEngineMenu: (state) => {
      state.engineMenuOpen = !state.engineMenuOpen;
    },
    setAuthMenuOpen: (state, action: PayloadAction<boolean>) => {
      state.authMenuOpen = action.payload;
    },
    toggleAuthMenu: (state) => {
      state.authMenuOpen = !state.authMenuOpen;
    },
  },
});

export const {
  setSelectedContainerId,
  setSelectedMatchId,
  setActivePanel,
  setSidebarOpen,
  toggleSidebar,
  setControlPlaneMenuOpen,
  toggleControlPlaneMenu,
  setEngineMenuOpen,
  toggleEngineMenu,
  setAuthMenuOpen,
  toggleAuthMenu,
} = uiSlice.actions;

// Selectors
export const selectSelectedContainerId = (state: RootState) =>
  state.ui.selectedContainerId;
export const selectSelectedMatchId = (state: RootState) =>
  state.ui.selectedMatchId;
export const selectActivePanel = (state: RootState) => state.ui.activePanel;
export const selectSidebarOpen = (state: RootState) => state.ui.sidebarOpen;
export const selectControlPlaneMenuOpen = (state: RootState) =>
  state.ui.controlPlaneMenuOpen;
export const selectEngineMenuOpen = (state: RootState) =>
  state.ui.engineMenuOpen;
export const selectAuthMenuOpen = (state: RootState) => state.ui.authMenuOpen;

export default uiSlice.reducer;
