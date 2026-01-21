/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { server } from "../test/mocks/server";
import { renderWithProviders } from "../test/testUtils";
import AIPanel from "./AIPanel";

describe("AIPanel", () => {
  beforeEach(() => {
    server.resetHandlers();
  });

  it("renders no container selected message when no container is selected", () => {
    renderWithProviders(<AIPanel />, {
      preloadedState: { ui: { selectedContainerId: null } },
    });

    expect(screen.getByText(/no container selected/i)).toBeInTheDocument();
    expect(
      screen.getByText(/select a container from the sidebar/i),
    ).toBeInTheDocument();
  });

  it("renders loading state when fetching AI", () => {
    server.use(
      http.get("*/api/containers/1/ai", async () => {
        await new Promise((resolve) => setTimeout(resolve, 100));
        return HttpResponse.json([]);
      }),
    );

    renderWithProviders(<AIPanel />, {
      preloadedState: { ui: { selectedContainerId: 1 } },
    });

    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("renders AI list when data is loaded", async () => {
    server.use(
      http.get("*/api/containers/1/ai", () => {
        return HttpResponse.json(["TickCounterAI", "SpawnControllerAI"]);
      }),
    );

    renderWithProviders(<AIPanel />, {
      preloadedState: { ui: { selectedContainerId: 1 } },
    });

    await waitFor(() => {
      // AI names appear twice (in table and chip), use getAllByText
      expect(screen.getAllByText("TickCounterAI").length).toBeGreaterThan(0);
    });
    expect(screen.getAllByText("SpawnControllerAI").length).toBeGreaterThan(0);
    expect(screen.getByText(/available ai \(2\)/i)).toBeInTheDocument();
  });

  it("renders empty state when no AI installed", async () => {
    server.use(
      http.get("*/api/containers/1/ai", () => {
        return HttpResponse.json([]);
      }),
    );

    renderWithProviders(<AIPanel />, {
      preloadedState: { ui: { selectedContainerId: 1 } },
    });

    await waitFor(() => {
      expect(
        screen.getByText(/no ai installed in this container/i),
      ).toBeInTheDocument();
    });
  });

  it("renders error state when fetch fails", async () => {
    server.use(
      http.get("*/api/containers/1/ai", () => {
        return new HttpResponse(null, { status: 500 });
      }),
    );

    renderWithProviders(<AIPanel />, {
      preloadedState: { ui: { selectedContainerId: 1 } },
    });

    await waitFor(() => {
      expect(screen.getByText(/failed to fetch ais/i)).toBeInTheDocument();
    });
  });
});
