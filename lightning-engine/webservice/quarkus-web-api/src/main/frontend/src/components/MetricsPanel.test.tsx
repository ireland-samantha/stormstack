/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../test/testUtils";
import MetricsPanel from "./MetricsPanel";

describe("MetricsPanel", () => {
  it("renders the panel title", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("Metrics")).toBeInTheDocument();
    });
  });

  it("displays module benchmarks section when benchmarks are available", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/Module Benchmarks/i)).toBeInTheDocument();
    });
  });

  it("displays benchmark module names correctly", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      const moduleNames = screen.getAllByText("PhysicsModule");
      expect(moduleNames.length).toBeGreaterThanOrEqual(2);
    });
  });

  it("displays benchmark scope names correctly", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("collision-detection")).toBeInTheDocument();
    });
    expect(screen.getByText("position-integration")).toBeInTheDocument();
  });

  it("displays benchmark execution times in milliseconds", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("0.500")).toBeInTheDocument();
    });
    expect(screen.getByText("0.300")).toBeInTheDocument();
  });

  it("displays benchmark execution times in nanoseconds", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("500,000")).toBeInTheDocument();
    });
    expect(screen.getByText("300,000")).toBeInTheDocument();
  });

  it("calculates percentage of tick time correctly", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    // 500_000 / 1_500_000 * 100 = 33.3%
    // 300_000 / 1_500_000 * 100 = 20.0%
    await waitFor(() => {
      expect(screen.getByText("33.3%")).toBeInTheDocument();
    });
    expect(screen.getByText("20.0%")).toBeInTheDocument();
  });

  it("shows no container selected message when container is null", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: null,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/no container selected/i)).toBeInTheDocument();
    });
  });

  it("displays container name in header", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("default")).toBeInTheDocument();
    });
  });

  it("displays all standard metric fields", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("Current Tick")).toBeInTheDocument();
    });
    expect(screen.getByText("Total Entities")).toBeInTheDocument();
    expect(screen.getByText("Total Component Types")).toBeInTheDocument();
  });

  it("shows benchmark count in section header", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/Module Benchmarks \(2\)/i)).toBeInTheDocument();
    });
  });

  it("displays table headers for benchmarks", async () => {
    renderWithProviders(<MetricsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "metrics",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("Module")).toBeInTheDocument();
    });
    expect(screen.getByText("Scope")).toBeInTheDocument();
    expect(screen.getByText(/Time \(ms\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Time \(ns\)/i)).toBeInTheDocument();
    expect(screen.getByText(/% of Tick/i)).toBeInTheDocument();
  });
});
