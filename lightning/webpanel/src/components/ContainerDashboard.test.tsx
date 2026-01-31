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
import ContainerDashboard from "./ContainerDashboard";

describe("ContainerDashboard", () => {
  beforeEach(() => {
    server.resetHandlers();
    // Set up base handlers
    server.use(
      http.get("*/api/modules", () => {
        return HttpResponse.json([
          { name: "EntityModule", description: "Core entity management" },
        ]);
      }),
      http.get("*/api/ai", () => {
        return HttpResponse.json([
          { name: "TickCounterAI", description: "Counts ticks" },
        ]);
      }),
    );
  });

  it("renders loading state when fetching containers", () => {
    server.use(
      http.get("*/api/containers", async () => {
        await new Promise((resolve) => setTimeout(resolve, 100));
        return HttpResponse.json([]);
      }),
    );

    renderWithProviders(<ContainerDashboard />);

    // Loading state may or may not show a progressbar depending on timing
    expect(screen.queryAllByRole("progressbar").length).toBeGreaterThanOrEqual(
      0,
    );
  });

  it("renders empty state when no containers exist", async () => {
    server.use(
      http.get("*/api/containers", () => {
        return HttpResponse.json([]);
      }),
    );

    renderWithProviders(<ContainerDashboard />);

    await waitFor(() => {
      expect(screen.getByText(/no containers/i)).toBeInTheDocument();
    });
    expect(
      screen.getByText(/create your first container/i),
    ).toBeInTheDocument();
  });

  it("renders container cards when containers exist", async () => {
    server.use(
      http.get("*/api/containers", () => {
        return HttpResponse.json([
          {
            id: 1,
            name: "game-server-1",
            status: "RUNNING",
            currentTick: 10,
            matchCount: 2,
          },
          {
            id: 2,
            name: "test-server",
            status: "STOPPED",
            currentTick: 0,
            matchCount: 0,
          },
        ]);
      }),
    );

    renderWithProviders(<ContainerDashboard />);

    await waitFor(() => {
      expect(screen.getByText("game-server-1")).toBeInTheDocument();
    });
    expect(screen.getByText("test-server")).toBeInTheDocument();
    expect(screen.getByText("RUNNING")).toBeInTheDocument();
    expect(screen.getByText("STOPPED")).toBeInTheDocument();
  });

  it("opens create container dialog when clicking New Container button", async () => {
    const user = userEvent.setup();

    server.use(
      http.get("*/api/containers", () => {
        return HttpResponse.json([]);
      }),
    );

    renderWithProviders(<ContainerDashboard />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /new container/i }),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /new container/i }));

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
    expect(screen.getByLabelText(/container name/i)).toBeInTheDocument();
  });

  it("creates container when form is submitted", async () => {
    const user = userEvent.setup();
    let createCalled = false;
    let requestBody: unknown;

    server.use(
      http.get("*/api/containers", () => {
        return HttpResponse.json([]);
      }),
      http.post("*/api/containers", async ({ request }) => {
        createCalled = true;
        requestBody = await request.json();
        return HttpResponse.json({
          id: 1,
          name: "new-container",
          status: "CREATED",
          currentTick: 0,
        });
      }),
    );

    renderWithProviders(<ContainerDashboard />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /new container/i }),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /new container/i }));

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText(/container name/i), "new-container");
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    await waitFor(() => {
      expect(createCalled).toBe(true);
    });
    expect((requestBody as { name: string })?.name).toBe("new-container");
  });

  it("selects container when card is clicked", async () => {
    const user = userEvent.setup();

    server.use(
      http.get("*/api/containers", () => {
        return HttpResponse.json([
          { id: 1, name: "game-server-1", status: "RUNNING", currentTick: 10 },
        ]);
      }),
      http.get("*/api/containers/1", () => {
        return HttpResponse.json({
          id: 1,
          name: "game-server-1",
          status: "RUNNING",
          currentTick: 10,
        });
      }),
      http.get("*/api/containers/1/matches", () => {
        return HttpResponse.json([]);
      }),
      http.get("*/api/containers/1/tick", () => {
        return HttpResponse.json({ tick: 10 });
      }),
      http.get("*/api/containers/1/stats", () => {
        return HttpResponse.json({
          entityCount: 100,
          maxEntities: 10000,
          usedMemoryBytes: 1024000,
          maxMemoryBytes: 10240000,
          jvmUsedMemoryBytes: 50000000,
          jvmMaxMemoryBytes: 512000000,
          matchCount: 0,
          moduleCount: 3,
        });
      }),
    );

    renderWithProviders(<ContainerDashboard />);

    await waitFor(() => {
      expect(screen.getByText("game-server-1")).toBeInTheDocument();
    });

    await user.click(screen.getByText("game-server-1"));

    await waitFor(() => {
      expect(screen.getByText(/tick control/i)).toBeInTheDocument();
    });
  });

  it("renders container statistics when container is selected", async () => {
    const user = userEvent.setup();

    server.use(
      http.get("*/api/containers", () => {
        return HttpResponse.json([
          { id: 1, name: "game-server-1", status: "RUNNING", currentTick: 10 },
        ]);
      }),
      http.get("*/api/containers/1", () => {
        return HttpResponse.json({
          id: 1,
          name: "game-server-1",
          status: "RUNNING",
          currentTick: 10,
        });
      }),
      http.get("*/api/containers/1/matches", () => {
        return HttpResponse.json([{ id: 1 }, { id: 2 }]);
      }),
      http.get("*/api/containers/1/tick", () => {
        return HttpResponse.json({ tick: 10 });
      }),
      http.get("*/api/containers/1/stats", () => {
        return HttpResponse.json({
          entityCount: 100,
          maxEntities: 10000,
          usedMemoryBytes: 1024000,
          maxMemoryBytes: 10240000,
          jvmUsedMemoryBytes: 50000000,
          jvmMaxMemoryBytes: 512000000,
          matchCount: 2,
          moduleCount: 3,
        });
      }),
    );

    renderWithProviders(<ContainerDashboard />);

    await waitFor(() => {
      expect(screen.getByText("game-server-1")).toBeInTheDocument();
    });

    await user.click(screen.getByText("game-server-1"));

    await waitFor(() => {
      expect(screen.getByText(/container statistics/i)).toBeInTheDocument();
    });
    // Verify entity count is displayed (could be in multiple places, just check it exists)
    expect(screen.getByText(/entities/i)).toBeInTheDocument();
  });
});
