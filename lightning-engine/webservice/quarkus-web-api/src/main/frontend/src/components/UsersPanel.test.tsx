/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../test/testUtils";
import UsersPanel from "./UsersPanel";

describe("UsersPanel", () => {
  it("renders the panel title", async () => {
    renderWithProviders(<UsersPanel />);

    await waitFor(() => {
      expect(screen.getByText("User Management")).toBeInTheDocument();
    });
  });

  it("displays users when loaded", async () => {
    renderWithProviders(<UsersPanel />);

    await waitFor(() => {
      // 'admin' appears as username AND role chip
      expect(screen.getAllByText("admin").length).toBeGreaterThan(0);
    });
    expect(screen.getByText("viewer")).toBeInTheDocument();
  });

  it("shows user roles", async () => {
    renderWithProviders(<UsersPanel />);

    await waitFor(() => {
      // admin user has 'admin' role
      expect(screen.getAllByText("admin").length).toBeGreaterThan(0);
    });
    // viewer user has 'view_only' role
    expect(screen.getByText("view_only")).toBeInTheDocument();
  });

  it("has add user button", async () => {
    renderWithProviders(<UsersPanel />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /add user/i }),
      ).toBeInTheDocument();
    });
  });

  it("shows active status for enabled users", async () => {
    renderWithProviders(<UsersPanel />);

    await waitFor(() => {
      // Both mock users are enabled, so they show "Active" status
      expect(screen.getAllByText("Active").length).toBeGreaterThan(0);
    });
  });
});
