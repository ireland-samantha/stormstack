/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithProviders } from "../test/testUtils";
import RolesPanel from "./RolesPanel";

describe("RolesPanel", () => {
  it("renders the panel title", async () => {
    renderWithProviders(<RolesPanel />);

    await waitFor(() => {
      expect(screen.getByText("Role Management")).toBeInTheDocument();
    });
  });

  it("displays roles when loaded", async () => {
    renderWithProviders(<RolesPanel />);

    await waitFor(() => {
      // Role names are displayed as chips - may appear multiple times due to "includes"
      expect(screen.getAllByText("admin").length).toBeGreaterThan(0);
    });
    // view_only appears as a role AND in admin's includes
    expect(screen.getAllByText("view_only").length).toBeGreaterThan(0);
    expect(screen.getAllByText("command_manager").length).toBeGreaterThan(0);
  });

  it("shows role descriptions", async () => {
    renderWithProviders(<RolesPanel />);

    await waitFor(() => {
      expect(screen.getByText("Full access")).toBeInTheDocument();
    });
    expect(screen.getByText("Read-only access")).toBeInTheDocument();
    expect(screen.getByText("Can execute commands")).toBeInTheDocument();
  });

  it("has add role button", async () => {
    renderWithProviders(<RolesPanel />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /add role/i }),
      ).toBeInTheDocument();
    });
  });

  it("shows included roles for roles with includes", async () => {
    renderWithProviders(<RolesPanel />);

    await waitFor(() => {
      // admin role includes view_only and command_manager - these appear as outlined chips
      expect(screen.getAllByText("view_only").length).toBeGreaterThan(1);
    });
  });

  it("displays scopes column header", async () => {
    renderWithProviders(<RolesPanel />);

    await waitFor(() => {
      expect(screen.getByText("Scopes")).toBeInTheDocument();
    });
  });

  it("shows scopes for roles with scopes", async () => {
    renderWithProviders(<RolesPanel />);

    await waitFor(() => {
      // view_only role has engine.snapshot.view scope
      expect(screen.getByText("engine.snapshot.view")).toBeInTheDocument();
    });
    // admin role has * scope
    expect(screen.getByText("*")).toBeInTheDocument();
  });

  it("shows command submit scope for command_manager", async () => {
    renderWithProviders(<RolesPanel />);

    await waitFor(() => {
      expect(screen.getByText("engine.command.submit")).toBeInTheDocument();
    });
  });
});
