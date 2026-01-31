/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { server } from "../test/mocks/server";
import { renderWithProviders } from "../test/testUtils";
import HistoryPanel from "./HistoryPanel";

describe("HistoryPanel", () => {
  beforeEach(() => {
    server.resetHandlers();
  });

  const preloadedStateWithContainer = {
    ui: { selectedContainerId: 1 },
  };

  it("renders loading state initially", () => {
    // Set up a delayed response to show loading
    server.use(
      http.get("/api/containers/:containerId/history", async () => {
        await new Promise((resolve) => setTimeout(resolve, 100));
        return HttpResponse.json({
          totalSnapshots: 0,
          matchCount: 0,
          matchIds: [],
        });
      }),
    );

    renderWithProviders(<HistoryPanel />, {
      preloadedState: preloadedStateWithContainer,
    });

    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("renders error state", async () => {
    server.use(
      http.get("/api/containers/:containerId/history", () => {
        return new HttpResponse(null, { status: 500 });
      }),
    );

    renderWithProviders(<HistoryPanel />, {
      preloadedState: preloadedStateWithContainer,
    });

    await waitFor(() => {
      expect(
        screen.getByText(/failed to load history summary/i),
      ).toBeInTheDocument();
    });
  });

  it("renders summary data", async () => {
    server.use(
      http.get("/api/containers/:containerId/history", () => {
        return HttpResponse.json({
          totalSnapshots: 150,
          matchCount: 3,
          matchIds: [1, 2, 3],
        });
      }),
      http.get("/api/containers/:containerId/matches/:matchId/history", () => {
        return HttpResponse.json({
          matchId: 1,
          snapshotCount: 50,
          firstTick: 0,
          lastTick: 49,
        });
      }),
    );

    renderWithProviders(<HistoryPanel />, {
      preloadedState: preloadedStateWithContainer,
    });

    await waitFor(() => {
      expect(screen.getByText(/150 total snapshots/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/3 matches/i)).toBeInTheDocument();
  });

  it("renders empty state when no matches", async () => {
    server.use(
      http.get("/api/containers/:containerId/history", () => {
        return HttpResponse.json({
          totalSnapshots: 0,
          matchCount: 0,
          matchIds: [],
        });
      }),
    );

    renderWithProviders(<HistoryPanel />, {
      preloadedState: preloadedStateWithContainer,
    });

    await waitFor(() => {
      expect(screen.getByText(/0 total snapshots/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/no matches found/i)).toBeInTheDocument();
  });
});
