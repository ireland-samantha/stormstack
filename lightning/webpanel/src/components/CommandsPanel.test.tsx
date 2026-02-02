/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { CommandParameter } from "../services/api";
import { renderWithProviders } from "../test/testUtils";
import CommandsPanel, { generateParameterTemplate } from "./CommandsPanel";

describe("CommandsPanel", () => {
  it("renders the panel title", async () => {
    renderWithProviders(<CommandsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "commands",
        },
      },
    });

    await waitFor(
      () => {
        expect(screen.getByText("Commands")).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it("displays commands in table", async () => {
    renderWithProviders(<CommandsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "commands",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("spawn")).toBeInTheDocument();
    });
    expect(screen.getByText("move")).toBeInTheDocument();
  });

  it("shows no container selected message when container is null", async () => {
    renderWithProviders(<CommandsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: null,
          selectedMatchId: null,
          activePanel: "commands",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText(/no container selected/i)).toBeInTheDocument();
    });
  });

  it("has tabs for available commands and quick send", async () => {
    renderWithProviders(<CommandsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "commands",
        },
      },
    });

    await waitFor(() => {
      expect(
        screen.getByRole("tab", { name: /available commands/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("tab", { name: /quick send/i }),
      ).toBeInTheDocument();
    });
  });

  it("shows module chip for commands with modules", async () => {
    renderWithProviders(<CommandsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "commands",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("EntityModule")).toBeInTheDocument();
    });
    expect(screen.getByText("RigidBodyModule")).toBeInTheDocument();
  });

  it("displays container name in header", async () => {
    renderWithProviders(<CommandsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "commands",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("default")).toBeInTheDocument();
    });
  });

  it("shows command count in header", async () => {
    renderWithProviders(<CommandsPanel />, {
      preloadedState: {
        ui: {
          selectedContainerId: 1,
          selectedMatchId: null,
          activePanel: "commands",
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByText("2 commands")).toBeInTheDocument();
    });
  });
});

describe("generateParameterTemplate", () => {
  it("returns empty object for no parameters", () => {
    expect(generateParameterTemplate(undefined)).toBe("{}");
    expect(generateParameterTemplate([])).toBe("{}");
  });

  it("generates integer placeholder for int types", () => {
    const params: CommandParameter[] = [
      { name: "count", type: "int", required: true },
      { name: "id", type: "Long", required: true },
      { name: "index", type: "Integer", required: false },
    ];
    const result = JSON.parse(generateParameterTemplate(params));
    expect(result.count).toBe(0);
    expect(result.id).toBe(0);
    expect(result.index).toBe(0);
  });

  it("generates float placeholder for float/double types", () => {
    const params: CommandParameter[] = [
      { name: "x", type: "double", required: true },
      { name: "y", type: "Float", required: true },
      { name: "z", type: "Number", required: false },
    ];
    const result = JSON.parse(generateParameterTemplate(params));
    expect(result.x).toBe(0.0);
    expect(result.y).toBe(0.0);
    expect(result.z).toBe(0.0);
  });

  it("generates boolean placeholder for boolean types", () => {
    const params: CommandParameter[] = [
      { name: "active", type: "boolean", required: true },
      { name: "enabled", type: "Boolean", required: false },
    ];
    const result = JSON.parse(generateParameterTemplate(params));
    expect(result.active).toBe(false);
    expect(result.enabled).toBe(false);
  });

  it("generates array placeholder for list/array types", () => {
    const params: CommandParameter[] = [
      { name: "items", type: "List<String>", required: true },
      { name: "ids", type: "int[]", required: true },
      { name: "values", type: "Array", required: false },
    ];
    const result = JSON.parse(generateParameterTemplate(params));
    expect(result.items).toEqual([]);
    expect(result.ids).toEqual([]);
    expect(result.values).toEqual([]);
  });

  it("generates object placeholder for map/object types", () => {
    const params: CommandParameter[] = [
      { name: "data", type: "Map<String, Object>", required: true },
      { name: "config", type: "Object", required: false },
    ];
    const result = JSON.parse(generateParameterTemplate(params));
    expect(result.data).toEqual({});
    expect(result.config).toEqual({});
  });

  it("generates string placeholder for string and unknown types", () => {
    const params: CommandParameter[] = [
      { name: "name", type: "String", required: true },
      { name: "description", type: "string", required: false },
      { name: "custom", type: "CustomType", required: false },
    ];
    const result = JSON.parse(generateParameterTemplate(params));
    expect(result.name).toBe("");
    expect(result.description).toBe("");
    expect(result.custom).toBe("");
  });

  it("generates proper JSON formatting", () => {
    const params: CommandParameter[] = [
      { name: "x", type: "int", required: true },
      { name: "y", type: "int", required: true },
    ];
    const result = generateParameterTemplate(params);
    expect(result).toContain("\n");
  });

  it("generates 0 for matchId field type", () => {
    const params: CommandParameter[] = [
      { name: "matchId", type: "long", required: true },
    ];
    const result = JSON.parse(generateParameterTemplate(params));
    expect(result.matchId).toBe(0);
  });

  it("generates 0 for playerId field type", () => {
    const params: CommandParameter[] = [
      { name: "playerId", type: "long", required: true },
    ];
    const result = JSON.parse(generateParameterTemplate(params));
    expect(result.playerId).toBe(0);
  });

  it("generates 0 for entityId field type", () => {
    const params: CommandParameter[] = [
      { name: "entityId", type: "long", required: true },
    ];
    const result = JSON.parse(generateParameterTemplate(params));
    expect(result.entityId).toBe(0);
  });
});
