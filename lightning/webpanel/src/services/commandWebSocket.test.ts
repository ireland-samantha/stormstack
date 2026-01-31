/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  buildAuthSubprotocol,
  buildCommandWebSocketUrl,
  CommandWebSocketClient,
} from "./commandWebSocket";

// Mock WebSocket
class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  url: string;
  protocols: string | string[] | undefined;
  readyState = MockWebSocket.CONNECTING;
  onopen: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  sentMessages: string[] = [];

  constructor(url: string, protocols?: string | string[]) {
    this.url = url;
    this.protocols = protocols;
  }

  send(data: string) {
    this.sentMessages.push(data);
  }

  close() {
    this.readyState = MockWebSocket.CLOSED;
    if (this.onclose) {
      this.onclose(new CloseEvent("close", { code: 1000, reason: "Normal" }));
    }
  }

  // Test helpers
  simulateOpen() {
    this.readyState = MockWebSocket.OPEN;
    if (this.onopen) {
      this.onopen(new Event("open"));
    }
  }

  simulateMessage(data: string) {
    if (this.onmessage) {
      this.onmessage(new MessageEvent("message", { data }));
    }
  }

  simulateError() {
    if (this.onerror) {
      this.onerror(new Event("error"));
    }
  }
}

describe("CommandWebSocketClient", () => {
  let mockWebSocket: MockWebSocket;

  beforeEach(() => {
    // Mock window.location for URL building
    vi.stubGlobal("location", {
      protocol: "http:",
      host: "localhost:8080",
    });

    // Mock WebSocket
    vi.stubGlobal("WebSocket", function (
      this: MockWebSocket,
      url: string,
      protocols?: string | string[],
    ) {
      mockWebSocket = new MockWebSocket(url, protocols);
      return mockWebSocket;
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe("constructor", () => {
    it("should store containerId and token", () => {
      const client = new CommandWebSocketClient(1, "test-token");
      expect(client).toBeDefined();
    });
  });

  describe("connect", () => {
    it("should create WebSocket with subprotocol auth", async () => {
      const client = new CommandWebSocketClient(1, "test-token");
      const connectPromise = client.connect();

      // Simulate successful connection
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({
          status: "ACCEPTED",
          message: "Connected to container 1",
          commandName: "",
        }),
      );

      await connectPromise;

      expect(mockWebSocket.url).toBe(
        "ws://localhost:8080/containers/1/commands",
      );
      expect(mockWebSocket.protocols).toEqual(["Bearer.test-token"]);
      expect(client.isConnected()).toBe(true);
    });

    it("should reject on connection error", async () => {
      const client = new CommandWebSocketClient(1, "test-token");
      const connectPromise = client.connect();

      // Simulate error response
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({
          status: "ERROR",
          message: "Authentication failed",
          commandName: "",
        }),
      );

      await expect(connectPromise).rejects.toThrow("Authentication failed");
    });

    it("should not create new connection if already connected", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      // First connection
      const connectPromise1 = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise1;

      const firstMockWs = mockWebSocket;

      // Second connection attempt should return immediately
      await client.connect();

      // Should still be the same WebSocket
      expect(mockWebSocket).toBe(firstMockWs);
    });
  });

  describe("sendCommand", () => {
    it("should throw if not connected", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      await expect(
        client.sendCommand({
          commandName: "test",
          matchId: 1,
          playerId: 1,
        }),
      ).rejects.toThrow("Not connected");
    });

    it("should validate commandName is required", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      // Connect first
      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      await expect(
        client.sendCommand({
          commandName: "",
          matchId: 1,
          playerId: 1,
        }),
      ).rejects.toThrow("commandName is required");
    });

    it("should validate matchId is non-negative", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      await expect(
        client.sendCommand({
          commandName: "test",
          matchId: -1,
          playerId: 1,
        }),
      ).rejects.toThrow("matchId must be a non-negative number");
    });

    it("should validate playerId is non-negative", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      await expect(
        client.sendCommand({
          commandName: "test",
          matchId: 1,
          playerId: -1,
        }),
      ).rejects.toThrow("playerId must be a non-negative number");
    });

    it("should send valid command as JSON", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      await client.sendCommand({
        commandName: "test",
        matchId: 1,
        playerId: 2,
      });

      expect(mockWebSocket.sentMessages).toHaveLength(1);
      const sent = JSON.parse(mockWebSocket.sentMessages[0]);
      expect(sent.commandName).toBe("test");
      expect(sent.matchId).toBe(1);
      expect(sent.playerId).toBe(2);
    });
  });

  describe("spawn", () => {
    it("should validate entityType is a number", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      await expect(
        client.spawn(1, 1, "invalid" as unknown as number, 0, 0),
      ).rejects.toThrow("entityType must be a number");
    });

    it("should send spawn command with correct payload", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      await client.spawn(1, 2, 100, 10, 20);

      expect(mockWebSocket.sentMessages).toHaveLength(1);
      const sent = JSON.parse(mockWebSocket.sentMessages[0]);
      expect(sent.commandName).toBe("spawn");
      expect(sent.matchId).toBe(1);
      expect(sent.playerId).toBe(2);
      expect(sent.spawn).toEqual({
        entityType: 100,
        positionX: 10,
        positionY: 20,
      });
    });
  });

  describe("sendGenericCommand", () => {
    it("should validate commandName", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      await expect(
        client.sendGenericCommand("", 1, 1, {}),
      ).rejects.toThrow("commandName is required");
    });

    it("should separate params by type", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      await client.sendGenericCommand("test", 1, 1, {
        strParam: "value",
        intParam: 42,
        floatParam: 3.14,
        boolParam: true,
      });

      expect(mockWebSocket.sentMessages).toHaveLength(1);
      const sent = JSON.parse(mockWebSocket.sentMessages[0]);
      expect(sent.generic.stringParams).toEqual({ strParam: "value" });
      expect(sent.generic.longParams).toEqual({ intParam: 42 });
      expect(sent.generic.doubleParams).toEqual({ floatParam: 3.14 });
      expect(sent.generic.boolParams).toEqual({ boolParam: true });
    });
  });

  describe("listeners", () => {
    it("should notify listeners on message", async () => {
      const client = new CommandWebSocketClient(1, "test-token");
      const listener = vi.fn();
      client.addListener(listener);

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({
          status: "ACCEPTED",
          message: "Connected",
          commandName: "",
        }),
      );
      await connectPromise;

      // Should have been called with connection message
      expect(listener).toHaveBeenCalledWith({
        status: "ACCEPTED",
        message: "Connected",
        commandName: "",
      });
    });

    it("should remove listener", async () => {
      const client = new CommandWebSocketClient(1, "test-token");
      const listener = vi.fn();
      client.addListener(listener);
      client.removeListener(listener);

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      // Should not have been called after removal
      expect(listener).not.toHaveBeenCalled();
    });
  });

  describe("disconnect", () => {
    it("should close WebSocket and set connected to false", async () => {
      const client = new CommandWebSocketClient(1, "test-token");

      const connectPromise = client.connect();
      mockWebSocket.simulateOpen();
      mockWebSocket.simulateMessage(
        JSON.stringify({ status: "ACCEPTED", message: "Connected" }),
      );
      await connectPromise;

      expect(client.isConnected()).toBe(true);

      client.disconnect();

      expect(client.isConnected()).toBe(false);
    });
  });
});

describe("buildCommandWebSocketUrl", () => {
  beforeEach(() => {
    vi.stubGlobal("location", {
      protocol: "https:",
      host: "example.com",
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("should build correct URL with wss for https", () => {
    const url = buildCommandWebSocketUrl(123);
    expect(url).toBe("wss://example.com/containers/123/commands");
  });
});

describe("buildAuthSubprotocol", () => {
  it("should create Bearer subprotocol", () => {
    const subprotocol = buildAuthSubprotocol("my-token");
    expect(subprotocol).toBe("Bearer.my-token");
  });
});
