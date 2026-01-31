/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../test/testUtils";
import DeploymentsPanel from "./DeploymentsPanel";

describe("DeploymentsPanel", () => {
  it("renders the panel title", async () => {
    renderWithProviders(<DeploymentsPanel />);

    await waitFor(() => {
      expect(screen.getByText("Deployments")).toBeInTheDocument();
    });
  });

  it("displays deployments when loaded", async () => {
    renderWithProviders(<DeploymentsPanel />);

    await waitFor(() => {
      expect(screen.getByText("match-001")).toBeInTheDocument();
    });
    expect(screen.getByText("match-002")).toBeInTheDocument();
    expect(screen.getByText("match-003")).toBeInTheDocument();
  });

  it("shows deployment status chips", async () => {
    renderWithProviders(<DeploymentsPanel />);

    await waitFor(() => {
      // Two RUNNING deployments
      const runningChips = screen.getAllByText("RUNNING");
      expect(runningChips.length).toBe(2);
    });
    // One CREATING deployment
    expect(screen.getByText("CREATING")).toBeInTheDocument();
  });

  it("displays node IDs for deployments", async () => {
    renderWithProviders(<DeploymentsPanel />);

    await waitFor(() => {
      expect(screen.getAllByText("node-1").length).toBeGreaterThan(0);
    });
    expect(screen.getAllByText("node-2").length).toBeGreaterThan(0);
  });

  it("shows module names for deployments", async () => {
    renderWithProviders(<DeploymentsPanel />);

    await waitFor(() => {
      expect(screen.getAllByText("EntityModule").length).toBeGreaterThan(0);
    });
    expect(screen.getAllByText("RigidBodyModule").length).toBeGreaterThan(0);
  });

  it("has new deployment button", async () => {
    renderWithProviders(<DeploymentsPanel />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /new deployment/i }),
      ).toBeInTheDocument();
    });
  });

  it("opens new deployment dialog when button clicked", async () => {
    const user = userEvent.setup();
    renderWithProviders(<DeploymentsPanel />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /new deployment/i })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /new deployment/i }));

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
    // Dialog has title "New Deployment" and there's also the button, so check dialog exists
    expect(screen.getByRole("dialog")).toHaveTextContent("New Deployment");
  });

  it("has refresh button", async () => {
    renderWithProviders(<DeploymentsPanel />);

    await waitFor(() => {
      expect(screen.getByTitle("Refresh")).toBeInTheDocument();
    });
  });

  it("shows player count for deployments", async () => {
    renderWithProviders(<DeploymentsPanel />);

    await waitFor(() => {
      // match-001 has 4 players
      expect(screen.getByText("4")).toBeInTheDocument();
    });
    // match-002 has 2 players
    expect(screen.getByText("2")).toBeInTheDocument();
  });
});
