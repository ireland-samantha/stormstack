/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useCommandWebSocket } from "./useCommandWebSocket";

// Mock the CommandWebSocketClient module
const mockConnect = vi.fn();
const mockDisconnect = vi.fn();
const mockIsConnected = vi.fn();
const mockAddListener = vi.fn();
const mockSendGenericCommand = vi.fn();

vi.mock("../services/commandWebSocket", () => ({
  CommandWebSocketClient: class MockCommandWebSocketClient {
    connect = mockConnect;
    disconnect = mockDisconnect;
    isConnected = mockIsConnected;
    addListener = mockAddListener;
    removeListener = vi.fn();
    sendGenericCommand = mockSendGenericCommand;
  },
}));

describe("useCommandWebSocket", () => {
  beforeEach(() => {
    // Reset mocks
    mockConnect.mockReset().mockResolvedValue(undefined);
    mockDisconnect.mockReset();
    mockIsConnected.mockReset().mockReturnValue(false);
    mockAddListener.mockReset();
    mockSendGenericCommand.mockReset().mockResolvedValue(undefined);

    // Mock localStorage
    vi.stubGlobal("localStorage", {
      getItem: vi.fn().mockReturnValue("test-token"),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe("initial state", () => {
    it("should have initial disconnected state", () => {
      const { result } = renderHook(() =>
        useCommandWebSocket({ containerId: 1 }),
      );

      expect(result.current.isConnected).toBe(false);
      expect(result.current.isConnecting).toBe(false);
      expect(result.current.error).toBeNull();
    });
  });

  describe("connect", () => {
    it("should set error when no container selected", async () => {
      const { result } = renderHook(() =>
        useCommandWebSocket({ containerId: null }),
      );

      await act(async () => {
        await result.current.connect();
      });

      expect(result.current.error).toBe("No container selected");
      expect(result.current.isConnected).toBe(false);
    });

    it("should set error when not authenticated", async () => {
      vi.stubGlobal("localStorage", {
        getItem: vi.fn().mockReturnValue(null),
      });

      const { result } = renderHook(() =>
        useCommandWebSocket({ containerId: 1 }),
      );

      await act(async () => {
        await result.current.connect();
      });

      expect(result.current.error).toBe("Not authenticated");
      expect(result.current.isConnected).toBe(false);
    });

    it("should connect successfully", async () => {
      const { result } = renderHook(() =>
        useCommandWebSocket({ containerId: 1 }),
      );

      await act(async () => {
        await result.current.connect();
      });

      expect(result.current.isConnected).toBe(true);
      expect(result.current.isConnecting).toBe(false);
      expect(result.current.error).toBeNull();
      expect(mockConnect).toHaveBeenCalled();
    });

    it("should register onResponse callback", async () => {
      const onResponse = vi.fn();
      const { result } = renderHook(() =>
        useCommandWebSocket({ containerId: 1, onResponse }),
      );

      await act(async () => {
        await result.current.connect();
      });

      expect(mockAddListener).toHaveBeenCalledWith(onResponse);
    });

    it("should handle connection error", async () => {
      const onError = vi.fn();
      mockConnect.mockRejectedValueOnce(new Error("Connection failed"));

      const { result } = renderHook(() =>
        useCommandWebSocket({ containerId: 1, onError }),
      );

      await act(async () => {
        await result.current.connect();
      });

      expect(result.current.error).toBe("Connection failed");
      expect(result.current.isConnected).toBe(false);
      expect(onError).toHaveBeenCalled();
    });
  });

  describe("disconnect", () => {
    it("should disconnect and update state", async () => {
      const { result } = renderHook(() =>
        useCommandWebSocket({ containerId: 1 }),
      );

      await act(async () => {
        await result.current.connect();
      });

      expect(result.current.isConnected).toBe(true);

      act(() => {
        result.current.disconnect();
      });

      expect(result.current.isConnected).toBe(false);
      expect(mockDisconnect).toHaveBeenCalled();
    });
  });

  describe("sendCommand", () => {
    it("should throw when not connected", async () => {
      const { result } = renderHook(() =>
        useCommandWebSocket({ containerId: 1 }),
      );

      await expect(
        result.current.sendCommand("test", 1, 1, {}),
      ).rejects.toThrow("Not connected to command WebSocket");
    });

    it("should send command when connected", async () => {
      // Make isConnected return true after connect
      mockIsConnected.mockReturnValue(true);

      const { result } = renderHook(() =>
        useCommandWebSocket({ containerId: 1 }),
      );

      await act(async () => {
        await result.current.connect();
      });

      await act(async () => {
        await result.current.sendCommand("testCommand", 1, 2, { foo: "bar" });
      });

      expect(mockSendGenericCommand).toHaveBeenCalledWith(
        "testCommand",
        1,
        2,
        { foo: "bar" },
      );
    });
  });
});
