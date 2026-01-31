/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../test/testUtils";
import ClusterModulesPanel from "./ClusterModulesPanel";

describe("ClusterModulesPanel", () => {
  it("renders the panel title", async () => {
    renderWithProviders(<ClusterModulesPanel />);

    await waitFor(() => {
      expect(screen.getByText("Cluster Modules")).toBeInTheDocument();
    });
  });

  it("displays module names when loaded", async () => {
    renderWithProviders(<ClusterModulesPanel />);

    await waitFor(() => {
      expect(screen.getByText("EntityModule")).toBeInTheDocument();
    });
    expect(screen.getByText("RigidBodyModule")).toBeInTheDocument();
    expect(screen.getByText("HealthModule")).toBeInTheDocument();
  });

  it("shows module versions", async () => {
    renderWithProviders(<ClusterModulesPanel />);

    await waitFor(() => {
      // 1.0.0 appears for EntityModule and RigidBodyModule
      expect(screen.getAllByText("1.0.0").length).toBeGreaterThan(0);
    });
    // HealthModule has version 1.2.0
    expect(screen.getAllByText("1.2.0").length).toBeGreaterThan(0);
  });

  it("displays table headers", async () => {
    renderWithProviders(<ClusterModulesPanel />);

    await waitFor(() => {
      expect(screen.getByText("Module")).toBeInTheDocument();
    });
    expect(screen.getByText("Current Version")).toBeInTheDocument();
    expect(screen.getByText("Description")).toBeInTheDocument();
    expect(screen.getByText("Available Versions")).toBeInTheDocument();
    expect(screen.getByText("Actions")).toBeInTheDocument();
  });

  it("has refresh button", async () => {
    renderWithProviders(<ClusterModulesPanel />);

    await waitFor(() => {
      expect(screen.getByTitle("Refresh")).toBeInTheDocument();
    });
  });

  it("shows no description placeholder for modules without description", async () => {
    renderWithProviders(<ClusterModulesPanel />);

    await waitFor(() => {
      // Mock data doesn't include descriptions
      expect(screen.getAllByText("No description").length).toBeGreaterThan(0);
    });
  });
});
