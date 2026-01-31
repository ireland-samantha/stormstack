/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../test/testUtils";
import AutoscalerPanel from "./AutoscalerPanel";

describe("AutoscalerPanel", () => {
  it("renders the panel title", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByText("Autoscaler")).toBeInTheDocument();
    });
  });

  it("displays status section", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByText("Status")).toBeInTheDocument();
    });
  });

  it("shows active status when not in cooldown", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByText("Active")).toBeInTheDocument();
    });
  });

  it("displays current recommendation section", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByText("Current Recommendation")).toBeInTheDocument();
    });
  });

  it("shows no action recommendation when load is acceptable", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByText("No Scaling Needed")).toBeInTheDocument();
    });
  });

  it("displays reason for recommendation", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(
        screen.getByText("Cluster load is within acceptable range"),
      ).toBeInTheDocument();
    });
  });

  it("shows current nodes count", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByText("Current Nodes")).toBeInTheDocument();
    });
    // The current nodes value (3)
    expect(screen.getAllByText("3").length).toBeGreaterThan(0);
  });

  it("shows recommended nodes count", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByText("Recommended Nodes")).toBeInTheDocument();
    });
  });

  it("has refresh button", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByTitle("Refresh")).toBeInTheDocument();
    });
  });

  it("shows last acknowledged recommendation when available", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(
        screen.getByText("Last Acknowledged Recommendation"),
      ).toBeInTheDocument();
    });
  });

  it("displays scale up action from last recommendation", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByText("SCALE_UP")).toBeInTheDocument();
    });
  });

  it("shows reason for last recommendation", async () => {
    renderWithProviders(<AutoscalerPanel />);

    await waitFor(() => {
      expect(screen.getByText("High load detected")).toBeInTheDocument();
    });
  });
});
