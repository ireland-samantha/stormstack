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


import { useState, useEffect, useCallback, useRef } from 'react';
import { WebSocketClient, SnapshotData } from '../services/websocket';

interface UseSnapshotResult {
  snapshot: SnapshotData | null;
  connected: boolean;
  error: string | null;
  requestSnapshot: () => void;
}

export function useSnapshot(containerId: number, matchId: number): UseSnapshotResult {
  const [snapshot, setSnapshot] = useState<SnapshotData | null>(null);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const clientRef = useRef<WebSocketClient | null>(null);

  useEffect(() => {
    const wsUrl = window.location.origin.replace(/^http/, 'ws');
    const client = new WebSocketClient(wsUrl, containerId, matchId);
    clientRef.current = client;

    const handleSnapshot = (data: SnapshotData) => {
      setSnapshot(data);
      setError(null);
    };

    client.addListener(handleSnapshot);

    // Check connection status periodically
    const checkConnection = () => {
      setConnected(client.isConnected());
    };

    client.connect();
    const interval = setInterval(checkConnection, 1000);

    return () => {
      clearInterval(interval);
      client.removeListener(handleSnapshot);
      client.disconnect();
      clientRef.current = null;
    };
  }, [containerId, matchId]);

  const requestSnapshot = useCallback(() => {
    if (clientRef.current) {
      clientRef.current.requestSnapshot();
    }
  }, []);

  return { snapshot, connected, error, requestSnapshot };
}
