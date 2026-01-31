/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { server } from "../test/mocks/server";
import { renderWithProviders } from "../test/testUtils";
import PlayersPanel from "./PlayersPanel";

describe("PlayersPanel", () => {
  beforeEach(() => {
    server.resetHandlers();
  });

  it("renders the panel title", async () => {
    renderWithProviders(<PlayersPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "players",
          sidebarOpen: true,
          controlPlaneMenuOpen: true,
          engineMenuOpen: false,
          authMenuOpen: false,
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("Players")).toBeInTheDocument();
    });
  });

  it("shows loading state initially", () => {
    // Override handler to never resolve
    server.use(
      http.get("/api/containers/:id/players", async () => {
        await delay("infinite");
        return HttpResponse.json([]);
      }),
    );

    renderWithProviders(<PlayersPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "players",
          sidebarOpen: true,
          controlPlaneMenuOpen: true,
          engineMenuOpen: false,
          authMenuOpen: false,
        },
      },
    });

    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("displays players in table", async () => {
    renderWithProviders(<PlayersPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "players",
          sidebarOpen: true,
          controlPlaneMenuOpen: true,
          engineMenuOpen: false,
          authMenuOpen: false,
        },
      },
    });

    await waitFor(() => {
      // Look for the chip labels which show "Player X" in the ID column
      const player1Chips = screen.getAllByText("Player 1");
      expect(player1Chips.length).toBeGreaterThan(0);
    });
    const player2Chips = screen.getAllByText("Player 2");
    expect(player2Chips.length).toBeGreaterThan(0);
  });

  it("shows empty state when no players", async () => {
    server.use(
      http.get("/api/containers/:id/players", async () => {
        await delay(50);
        return HttpResponse.json([]);
      }),
    );

    renderWithProviders(<PlayersPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "players",
          sidebarOpen: true,
          controlPlaneMenuOpen: true,
          engineMenuOpen: false,
          authMenuOpen: false,
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/no players/i)).toBeInTheDocument();
    });
  });

  it("displays error on fetch failure", async () => {
    server.use(
      http.get("/api/containers/:id/players", async () => {
        await delay(50);
        return HttpResponse.json(
          { message: "Failed to fetch players" },
          { status: 500 },
        );
      }),
    );

    renderWithProviders(<PlayersPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "players",
          sidebarOpen: true,
          controlPlaneMenuOpen: true,
          engineMenuOpen: false,
          authMenuOpen: false,
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/failed to fetch players/i)).toBeInTheDocument();
    });
  });

  it("shows no container selected message when no container", async () => {
    renderWithProviders(<PlayersPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: null,
          selectedMatchId: null,
          activePanel: "players",
          sidebarOpen: true,
          controlPlaneMenuOpen: true,
          engineMenuOpen: false,
          authMenuOpen: false,
        },
      },
    });

    expect(screen.getByText(/no container selected/i)).toBeInTheDocument();
  });
});
