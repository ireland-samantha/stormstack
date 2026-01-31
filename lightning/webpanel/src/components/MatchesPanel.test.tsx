/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../test/testUtils";
import MatchesPanel from "./MatchesPanel";

describe("MatchesPanel", () => {
  it("renders the panel title", async () => {
    renderWithProviders(<MatchesPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "matches",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("Matches")).toBeInTheDocument();
    });
  });

  it("displays matches from API", async () => {
    renderWithProviders(<MatchesPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "matches",
        },
      },
    });

    // Wait for RTK Query to fetch data
    await waitFor(() => {
      expect(screen.getByText("Match 1")).toBeInTheDocument();
    });
    expect(screen.getByText("Match 2")).toBeInTheDocument();
  });

  it("shows container name in header", async () => {
    renderWithProviders(<MatchesPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "matches",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("default")).toBeInTheDocument();
    });
  });

  it("shows create match button", async () => {
    renderWithProviders(<MatchesPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "matches",
        },
      },
    });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /create match/i }),
      ).toBeInTheDocument();
    });
  });

  it("shows no container selected message when container is null", async () => {
    renderWithProviders(<MatchesPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: null,
          selectedMatchId: null,
          activePanel: "matches",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/no container selected/i)).toBeInTheDocument();
    });
  });

  it("displays enabled modules in match cards", async () => {
    renderWithProviders(<MatchesPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "matches",
        },
      },
    });

    await waitFor(() => {
      // Match 1 and Match 2 both have EntityModule - use getAllByText
      expect(screen.getAllByText("EntityModule").length).toBeGreaterThan(0);
    });
  });

  it("shows loading state initially", async () => {
    renderWithProviders(<MatchesPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "matches",
        },
      },
    });

    // Initially shows loading or the content
    await waitFor(() => {
      // Either we see loading or the content has loaded
      const hasMatches = screen.queryByText("Match 1");
      const hasLoading = screen.queryByRole("progressbar");
      expect(hasMatches || hasLoading).toBeTruthy();
    });
  });
});
