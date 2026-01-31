/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { server } from "../test/mocks/server";
import { renderWithProviders } from "../test/testUtils";
import SimulationControls from "./SimulationControls";

describe("SimulationControls", () => {
  beforeEach(() => {
    server.resetHandlers();
    server.use(
      http.get("*/api/containers", () => {
        return HttpResponse.json([
          {
            id: 1,
            name: "test",
            status: "RUNNING",
            currentTick: 0,
            autoAdvancing: false,
          },
        ]);
      }),
      http.get("*/api/containers/1/matches", () => {
        return HttpResponse.json([]);
      }),
    );
  });

  it("renders select container message when no container is selected", () => {
    renderWithProviders(<SimulationControls />, {
      preloadedState: { ui: { selectedContainerId: null } },
    });

    expect(screen.getByText(/select a container/i)).toBeInTheDocument();
  });

  it("renders simulation buttons when container is selected", async () => {
    server.use(
      http.get("*/api/containers/1", () => {
        return HttpResponse.json({
          id: 1,
          name: "test",
          status: "RUNNING",
          currentTick: 0,
          autoAdvancing: false,
        });
      }),
    );

    renderWithProviders(<SimulationControls />, {
      preloadedState: { ui: { selectedContainerId: 1 } },
    });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /advance one tick/i }),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /start simulation/i }),
    ).toBeInTheDocument();
  });

  it("shows stop button when simulation is playing", async () => {
    server.use(
      http.get("*/api/containers/1", () => {
        return HttpResponse.json({
          id: 1,
          name: "test",
          status: "RUNNING",
          currentTick: 10,
          autoAdvancing: true,
        });
      }),
    );

    renderWithProviders(<SimulationControls />, {
      preloadedState: { ui: { selectedContainerId: 1 } },
    });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /stop simulation/i }),
      ).toBeInTheDocument();
    });
    // Tick button should be disabled when playing
    expect(
      screen.getByRole("button", { name: /advance one tick/i }),
    ).toBeDisabled();
  });

  it("calls tick endpoint when tick button is clicked", async () => {
    const user = userEvent.setup();
    let tickCalled = false;

    server.use(
      http.get("*/api/containers/1", () => {
        return HttpResponse.json({
          id: 1,
          name: "test",
          status: "RUNNING",
          currentTick: 0,
          autoAdvancing: false,
        });
      }),
      http.post("*/api/containers/1/tick", () => {
        tickCalled = true;
        return HttpResponse.json({ tick: 1 });
      }),
    );

    renderWithProviders(<SimulationControls />, {
      preloadedState: { ui: { selectedContainerId: 1 } },
    });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /advance one tick/i }),
      ).toBeEnabled();
    });

    await user.click(screen.getByRole("button", { name: /advance one tick/i }));

    await waitFor(() => {
      expect(tickCalled).toBe(true);
    });
  });

  it("calls play endpoint when play button is clicked", async () => {
    const user = userEvent.setup();
    let playCalled = false;

    server.use(
      http.get("*/api/containers/1", () => {
        return HttpResponse.json({
          id: 1,
          name: "test",
          status: "RUNNING",
          currentTick: 0,
          autoAdvancing: false,
        });
      }),
      http.post("*/api/containers/1/play", () => {
        playCalled = true;
        return HttpResponse.json({
          id: 1,
          name: "test",
          status: "RUNNING",
          currentTick: 0,
          autoAdvancing: true,
        });
      }),
    );

    renderWithProviders(<SimulationControls />, {
      preloadedState: { ui: { selectedContainerId: 1 } },
    });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /start simulation/i }),
      ).toBeEnabled();
    });

    await user.click(screen.getByRole("button", { name: /start simulation/i }));

    await waitFor(() => {
      expect(playCalled).toBe(true);
    });
  });

  it("calls stop-auto endpoint when stop button is clicked", async () => {
    const user = userEvent.setup();
    let stopCalled = false;

    server.use(
      http.get("*/api/containers/1", () => {
        return HttpResponse.json({
          id: 1,
          name: "test",
          status: "RUNNING",
          currentTick: 10,
          autoAdvancing: true,
        });
      }),
      http.post("*/api/containers/1/stop-auto", () => {
        stopCalled = true;
        return HttpResponse.json({
          id: 1,
          name: "test",
          status: "RUNNING",
          currentTick: 10,
          autoAdvancing: false,
        });
      }),
    );

    renderWithProviders(<SimulationControls />, {
      preloadedState: { ui: { selectedContainerId: 1 } },
    });

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /stop simulation/i }),
      ).toBeEnabled();
    });

    await user.click(screen.getByRole("button", { name: /stop simulation/i }));

    await waitFor(() => {
      expect(stopCalled).toBe(true);
    });
  });
});
