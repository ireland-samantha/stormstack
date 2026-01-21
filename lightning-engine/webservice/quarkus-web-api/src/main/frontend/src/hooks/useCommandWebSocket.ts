/*
 * Copyright (c) 2026 Samantha Ireland
 * MIT License
 */

import { useCallback, useEffect, useRef, useState } from "react";
import {
    CommandResponse, CommandWebSocketClient
} from "../services/commandWebSocket";

interface UseCommandWebSocketOptions {
  containerId: number | null;
  onResponse?: (response: CommandResponse) => void;
  onError?: (error: Error) => void;
}

interface UseCommandWebSocketResult {
  isConnected: boolean;
  isConnecting: boolean;
  error: string | null;
  sendCommand: (
    commandName: string,
    matchId: number,
    playerId: number,
    params: Record<string, unknown>,
  ) => Promise<void>;
  connect: () => Promise<void>;
  disconnect: () => void;
}

/**
 * React hook for using WebSocket to send commands to a container.
 */
export function useCommandWebSocket({
  containerId,
  onResponse,
  onError,
}: UseCommandWebSocketOptions): UseCommandWebSocketResult {
  const clientRef = useRef<CommandWebSocketClient | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Get token from localStorage with null safety
  const getToken = useCallback((): string | null => {
    try {
      return localStorage.getItem("authToken");
    } catch {
      // localStorage may not be available in some environments
      return null;
    }
  }, []);

  // Connect to WebSocket with comprehensive validation
  const connect = useCallback(async () => {
    // Validate containerId
    if (containerId === null || containerId === undefined) {
      setError("No container selected");
      return;
    }

    if (typeof containerId !== "number" || containerId < 0) {
      setError("Invalid container ID");
      return;
    }

    // Validate token
    const token = getToken();
    if (!token || token.trim() === "") {
      setError("Not authenticated");
      return;
    }

    // Disconnect existing client safely
    if (clientRef.current) {
      try {
        clientRef.current.disconnect();
      } catch {
        // Ignore disconnect errors
      }
      clientRef.current = null;
    }

    setIsConnecting(true);
    setError(null);

    try {
      const client = new CommandWebSocketClient(containerId, token);

      if (onResponse && typeof onResponse === "function") {
        client.addListener(onResponse);
      }

      await client.connect();
      clientRef.current = client;
      setIsConnected(true);
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "Failed to connect";
      setError(errorMessage);
      if (onError && typeof onError === "function") {
        onError(err instanceof Error ? err : new Error(errorMessage));
      }
    } finally {
      setIsConnecting(false);
    }
  }, [containerId, getToken, onResponse, onError]);

  // Disconnect from WebSocket safely
  const disconnect = useCallback(() => {
    if (clientRef.current) {
      try {
        clientRef.current.disconnect();
      } catch {
        // Ignore disconnect errors
      }
      clientRef.current = null;
    }
    setIsConnected(false);
  }, []);

  // Send a command with validation
  const sendCommand = useCallback(
    async (
      commandName: string,
      matchId: number,
      playerId: number,
      params: Record<string, unknown>,
    ) => {
      // Validate connection
      if (!clientRef.current || !clientRef.current.isConnected()) {
        throw new Error("Not connected to command WebSocket");
      }

      // Validate command parameters
      if (!commandName || typeof commandName !== "string" || commandName.trim() === "") {
        throw new Error("Command name is required");
      }

      if (typeof matchId !== "number" || matchId < 0) {
        throw new Error("Invalid match ID");
      }

      if (typeof playerId !== "number" || playerId < 0) {
        throw new Error("Invalid player ID");
      }

      await clientRef.current.sendGenericCommand(
        commandName,
        matchId,
        playerId,
        params ?? {},
      );
    },
    [],
  );

  // Cleanup on unmount or containerId change
  useEffect(() => {
    return () => {
      if (clientRef.current) {
        clientRef.current.disconnect();
        clientRef.current = null;
      }
    };
  }, [containerId]);

  return {
    isConnected,
    isConnecting,
    error,
    sendCommand,
    connect,
    disconnect,
  };
}

export default useCommandWebSocket;
