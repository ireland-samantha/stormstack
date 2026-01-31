/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

/**
 * WebSocket client for sending commands to a container.
 *
 * Uses Protocol Buffer-like JSON messages for command submission.
 * Requires JWT authentication via query parameter.
 */

export interface CommandRequest {
  commandName: string;
  matchId: number;
  playerId: number;
  spawn?: SpawnPayload;
  attachRigidBody?: AttachRigidBodyPayload;
  attachSprite?: AttachSpritePayload;
  generic?: GenericPayload;
}

export interface SpawnPayload {
  entityType: number;
  positionX: number;
  positionY: number;
}

export interface AttachRigidBodyPayload {
  entityId: number;
  mass: number;
  positionX: number;
  positionY: number;
  velocityX: number;
  velocityY: number;
}

export interface AttachSpritePayload {
  entityId: number;
  resourceId: number;
  width: number;
  height: number;
  visible: boolean;
}

export interface GenericPayload {
  stringParams?: Record<string, string>;
  longParams?: Record<string, number>;
  doubleParams?: Record<string, number>;
  boolParams?: Record<string, boolean>;
}

export interface CommandResponse {
  status: "UNKNOWN" | "ACCEPTED" | "ERROR" | "INVALID";
  message: string;
  commandName: string;
}

export type CommandResponseListener = (response: CommandResponse) => void;

export class CommandWebSocketClient {
  private ws: WebSocket | null = null;
  private containerId: number;
  private token: string;
  private listeners: Set<CommandResponseListener> = new Set();
  private connected: boolean = false;
  private connectionResolve: (() => void) | null = null;
  private connectionReject: ((error: Error) => void) | null = null;

  // Reconnection settings
  private reconnectAttempts: number = 0;
  private maxReconnectAttempts: number = 5;
  private reconnectDelay: number = 1000; // Start with 1 second
  private reconnectTimer: number | null = null;
  private shouldReconnect: boolean = true;

  constructor(containerId: number, token: string) {
    this.containerId = containerId;
    this.token = token;
  }

  /**
   * Connect to the command WebSocket.
   *
   * Uses subprotocol authentication (Bearer.{token}) which is more secure than
   * query parameters because:
   * - Not logged in server access logs
   * - Not stored in browser history
   * - Not visible in network proxy logs
   */
  async connect(): Promise<void> {
    if (this.ws && this.connected) {
      return;
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${window.location.host}/containers/${this.containerId}/commands`;

    return new Promise((resolve, reject) => {
      this.connectionResolve = resolve;
      this.connectionReject = reject;

      try {
        // Use subprotocol for authentication (more secure than query param)
        const subprotocol = `Bearer.${this.token}`;
        this.ws = new WebSocket(wsUrl, [subprotocol]);
        this.setupEventHandlers();
      } catch (error) {
        reject(error);
      }
    });
  }

  private setupEventHandlers(): void {
    if (!this.ws) return;

    this.ws.onopen = () => {
      console.log("Command WebSocket connected");
    };

    this.ws.onmessage = (event) => {
      try {
        // Server sends JSON text for all responses (connection, commands, errors)
        // Binary protobuf is only used for native clients, not browsers
        let response: CommandResponse;
        if (typeof event.data === "string") {
          response = JSON.parse(event.data);
        } else {
          // Unexpected binary data - log and ignore
          console.warn(
            "Received unexpected binary WebSocket message, ignoring",
          );
          return;
        }

        // If this is the connection response, resolve the connection promise
        if (!this.connected && response.status === "ACCEPTED") {
          this.connected = true;
          this.resetReconnectState();
          this.connectionResolve?.();
          this.connectionResolve = null;
          this.connectionReject = null;
        } else if (!this.connected && response.status === "ERROR") {
          this.connectionReject?.(new Error(response.message));
          this.connectionResolve = null;
          this.connectionReject = null;
        }

        this.notifyListeners(response);
      } catch (error) {
        console.error("Failed to parse command response:", error);
      }
    };

    this.ws.onerror = (error) => {
      console.error("Command WebSocket error:", error);
      if (this.connectionReject) {
        this.connectionReject(new Error("WebSocket connection failed"));
        this.connectionResolve = null;
        this.connectionReject = null;
      }
    };

    this.ws.onclose = (event) => {
      console.log(`Command WebSocket closed: ${event.code} ${event.reason}`);
      const wasConnected = this.connected;
      this.connected = false;
      this.ws = null;

      if (this.connectionReject) {
        this.connectionReject(new Error("WebSocket closed before connecting"));
        this.connectionResolve = null;
        this.connectionReject = null;
      }

      // Attempt reconnection if we were connected and should reconnect
      if (wasConnected && this.shouldReconnect) {
        this.scheduleReconnect();
      }
    };
  }

  /**
   * Schedule a reconnection attempt with exponential backoff.
   */
  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.log(
        "Command WebSocket: Max reconnect attempts reached, giving up",
      );
      return;
    }

    // Exponential backoff: 1s, 2s, 4s, 8s, 16s
    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts);
    console.log(
      `Command WebSocket: Scheduling reconnect in ${delay}ms (attempt ${this.reconnectAttempts + 1}/${this.maxReconnectAttempts})`,
    );

    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectAttempts++;
      this.connect().catch((error) => {
        console.error("Command WebSocket: Reconnection failed", error);
      });
    }, delay);
  }

  /**
   * Reset reconnection state after successful connection.
   */
  private resetReconnectState(): void {
    this.reconnectAttempts = 0;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /**
   * Send a command through the WebSocket.
   * Validates required fields before sending.
   */
  async sendCommand(request: CommandRequest): Promise<void> {
    if (!this.ws || !this.connected) {
      throw new Error("Not connected");
    }

    // Validate required fields
    if (!request.commandName || request.commandName.trim() === "") {
      throw new Error("commandName is required and cannot be empty");
    }
    if (typeof request.matchId !== "number" || request.matchId < 0) {
      throw new Error("matchId must be a non-negative number");
    }
    if (typeof request.playerId !== "number" || request.playerId < 0) {
      throw new Error("playerId must be a non-negative number");
    }

    // Send as JSON (simplified for browser - protobuf would require additional library)
    const json = JSON.stringify(request);
    this.ws.send(json);
  }

  /**
   * Send a spawn command.
   * @param matchId - The match ID (must be non-negative)
   * @param playerId - The player ID (must be non-negative)
   * @param entityType - The entity type to spawn
   * @param posX - The X position
   * @param posY - The Y position
   */
  async spawn(
    matchId: number,
    playerId: number,
    entityType: number,
    posX: number,
    posY: number,
  ): Promise<void> {
    if (typeof entityType !== "number") {
      throw new Error("entityType must be a number");
    }
    if (typeof posX !== "number" || typeof posY !== "number") {
      throw new Error("positionX and positionY must be numbers");
    }

    return this.sendCommand({
      commandName: "spawn",
      matchId,
      playerId,
      spawn: {
        entityType,
        positionX: posX,
        positionY: posY,
      },
    });
  }

  /**
   * Send a generic command with parameters.
   * @param commandName - The command name (required, non-empty)
   * @param matchId - The match ID (must be non-negative)
   * @param playerId - The player ID (must be non-negative)
   * @param params - Additional command parameters
   */
  async sendGenericCommand(
    commandName: string,
    matchId: number,
    playerId: number,
    params: Record<string, unknown>,
  ): Promise<void> {
    if (!commandName || commandName.trim() === "") {
      throw new Error("commandName is required and cannot be empty");
    }
    if (typeof matchId !== "number" || matchId < 0) {
      throw new Error("matchId must be a non-negative number");
    }
    if (typeof playerId !== "number" || playerId < 0) {
      throw new Error("playerId must be a non-negative number");
    }
    if (!params || typeof params !== "object") {
      throw new Error("params must be an object");
    }

    // Separate params by type
    const stringParams: Record<string, string> = {};
    const longParams: Record<string, number> = {};
    const doubleParams: Record<string, number> = {};
    const boolParams: Record<string, boolean> = {};

    for (const [key, value] of Object.entries(params)) {
      if (typeof value === "string") {
        stringParams[key] = value;
      } else if (typeof value === "number") {
        if (Number.isInteger(value)) {
          longParams[key] = value;
        } else {
          doubleParams[key] = value;
        }
      } else if (typeof value === "boolean") {
        boolParams[key] = value;
      }
    }

    return this.sendCommand({
      commandName,
      matchId,
      playerId,
      generic: {
        stringParams,
        longParams,
        doubleParams,
        boolParams,
      },
    });
  }

  /**
   * Add a response listener.
   */
  addListener(listener: CommandResponseListener): void {
    this.listeners.add(listener);
  }

  /**
   * Remove a response listener.
   */
  removeListener(listener: CommandResponseListener): void {
    this.listeners.delete(listener);
  }

  private notifyListeners(response: CommandResponse): void {
    for (const listener of this.listeners) {
      try {
        listener(response);
      } catch (error) {
        console.error("Error in command response listener:", error);
      }
    }
  }

  /**
   * Check if connected.
   */
  isConnected(): boolean {
    return this.connected;
  }

  /**
   * Disconnect from the WebSocket.
   * @param preventReconnect If true, prevents automatic reconnection attempts
   */
  disconnect(preventReconnect: boolean = true): void {
    if (preventReconnect) {
      this.shouldReconnect = false;
    }

    // Cancel any pending reconnection
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.connected = false;
  }

  /**
   * Enable or disable automatic reconnection.
   */
  setAutoReconnect(enabled: boolean): void {
    this.shouldReconnect = enabled;
  }
}

/**
 * Build WebSocket URL for command endpoint.
 * Note: Token should be passed via subprotocol (Bearer.{token}), not in URL.
 */
export function buildCommandWebSocketUrl(containerId: number): string {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/containers/${containerId}/commands`;
}

/**
 * Build WebSocket subprotocol for authentication.
 */
export function buildAuthSubprotocol(token: string): string {
  return `Bearer.${token}`;
}
