/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../test/testUtils";
import LogsPanel from "./LogsPanel";

// Mock WebSocket URL builder
vi.mock("../services/api", async (importOriginal) => {
  const actual = (await importOriginal()) as object;
  return {
    ...actual,
    buildPlayerErrorWebSocketUrl: vi.fn(
      (matchId, playerId) =>
        `ws://localhost:8080/ws/matches/${matchId}/players/${playerId}/errors`,
    ),
  };
});

describe("LogsPanel", () => {
  it("renders panel after loading", async () => {
    renderWithProviders(<LogsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "logs",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("Logs")).toBeInTheDocument();
    });
  });

  it("renders match and player selection", async () => {
    renderWithProviders(<LogsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "logs",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("Error Stream Connection")).toBeInTheDocument();
    });
  });

  it("shows empty state message when not streaming", async () => {
    renderWithProviders(<LogsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "logs",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/no errors logged/i)).toBeInTheDocument();
    });
  });

  it("disables start button without selection", async () => {
    renderWithProviders(<LogsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "logs",
        },
      },
    });

    await waitFor(() => {
      const startButton = screen.getByRole("button", {
        name: /start streaming/i,
      });
      expect(startButton).toBeDisabled();
    });
  });

  it("has auto-scroll toggle", async () => {
    renderWithProviders(<LogsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "logs",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("Auto-scroll")).toBeInTheDocument();
    });
  });

  it("shows container name in header", async () => {
    renderWithProviders(<LogsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "logs",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("default")).toBeInTheDocument();
    });
  });

  it("shows no container selected message when container is null", async () => {
    renderWithProviders(<LogsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: null,
          selectedMatchId: null,
          activePanel: "logs",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/no container selected/i)).toBeInTheDocument();
    });
  });

  it("shows error count", async () => {
    renderWithProviders(<LogsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "logs",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/0 errors logged/i)).toBeInTheDocument();
    });
  });
});
